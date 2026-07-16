package com.tgac.logic.tabling;

// ABOUTME: Closed (star) tabling: explore for structure (presence cell + base/edge
// ABOUTME: capture), solve the star at each seal, and emit each answer's folded value.

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;

import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The closed (star) algorithm. Explore runs as plain set tabling — the cell is
 * presence, so it terminates — while the real value rides the SemiringStore
 * (read/write through {@link #storeReader}/{@link #storeWriter}) and every
 * derivation's contribution is captured on the entry: a NON-looping derivation is
 * a base seed, a one-loop derivation is an edge coefficient (star-tabling.md §4).
 * At each seal the region's hook solves {@code x = A* ⊗ b} ({@link #starSolve})
 * and {@link #emit} replays the master's continuation once per answer with the
 * folded value — the escapes explore produced were dropped as fragments.
 */
final class Closed implements TablingMode {

	/** Explore is set tabling — every answer carries the same presence marker. */
	@SuppressWarnings("unchecked")
	private static final IdempotentSemiring<Object> PRESENCE =
			(IdempotentSemiring<Object>) (IdempotentSemiring<?>) Semirings.BOOLEAN;

	private final ClosedSemiring<Object> semiring;
	private final Function<Package, Object> storeReader;
	private final BiFunction<Package, Object, Package> storeWriter;
	private final Function<TableEntry<?>, Map<Reified<?>, Object>> starSolve;

	Closed(ClosedSemiring<Object> semiring,
			Function<Package, Object> storeReader,
			BiFunction<Package, Object, Package> storeWriter,
			Function<TableEntry<?>, Map<Reified<?>, Object>> starSolve) {
		this.semiring = semiring;
		this.storeReader = storeReader;
		this.storeWriter = storeWriter;
		this.starSolve = starSolve;
	}

	@Override
	public IdempotentSemiring<Object> cellSemiring() {
		return PRESENCE;
	}

	@Override
	public Package enterBody(Package callerPkg) {
		// fresh derivation: real value reset to ONE, no loop record
		return storeWriter.apply(callerPkg, semiring.one()).putStore(Recurrent.NONE);
	}

	@Override
	public Object callerValue(Package callerPkg) {
		return storeReader.apply(callerPkg);
	}

	@Override
	public Package onConsume(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer, Object cellValue) {
		// consuming a still-open call is a loop — record which SCC answer it was
		if (entry.isComplete()) {
			return unifiedPkg;
		}
		Recurrent prev = unifiedPkg.getStores().get(Recurrent.class)
				.map(Recurrent.class::cast).getOrElse(Recurrent.NONE);
		return unifiedPkg.putStore(prev.and(consumedAnswer));
	}

	@Override
	public Object cacheValue(Package answerPkg) {
		// the presence cell carries no value; the real one is captured in onProduce
		return Boolean.TRUE;
	}

	@Override
	public Reified<?> onProduce(TableEntry<Object> entry, Package answerPkg, Reified<?> answerTerm) {
		// 0 loops consumed → base seed, 1 → edge coefficient, ≥2 → nonlinear (outside
		// the star). Captured before the dedup so multiplicity survives.
		Recurrent rec = answerPkg.getStores().get(Recurrent.class)
				.map(Recurrent.class::cast).getOrElse(Recurrent.NONE);
		Object value = storeReader.apply(answerPkg);
		if (rec.consumed.isEmpty()) {
			entry.addBase(answerTerm, value, semiring);
		} else if (rec.consumed.size() == 1) {
			entry.addEdge(answerTerm, rec.consumed.head(), value, semiring);
		} else {
			throw new IllegalStateException("nonlinear recursion: a derivation consumed "
					+ rec.consumed.size() + " looping calls; star handles only linear systems");
		}
		return answerTerm;
	}

	@Override
	public Package onExit(Package answerPkg, EnclosingCall callerCall, Object callerWeight, Object value) {
		// explore's answer is a pre-star fragment — tag it so the closed collector drops it
		return answerPkg.putStore(callerCall).putStore(Exploration.MARKER);
	}

	@Override
	public void onMasterClaim(TableEntry<Object> entry, Fiber.Fn<Package, Nothing> k,
			Package callerPkg, Unifiable<?> argsTerm, EnclosingCall callerCall) {
		entry.getRegion().onSealed(() -> emit(entry, k, callerPkg, argsTerm, callerCall));
	}

	/**
	 * At seal, publish each answer to the master's caller with the star-folded value
	 * {@code x = A* ⊗ b}, ⊗ the caller's running value. Replays the master's
	 * continuation {@code k} once per answer (docs/design/star-tabling.md §4.5).
	 */
	private Fiber<Nothing> emit(TableEntry<Object> entry, Fiber.Fn<Package, Nothing> k,
			Package callerPkg, Unifiable<?> argsTerm, EnclosingCall callerCall) {
		Map<Reified<?>, Object> solved = starSolve.apply(entry);
		Object callerValue = storeReader.apply(callerPkg);
		Fiber<Nothing> result = done(nothing());
		for (int i = 0; i < entry.getAnswerCount(); i++) {
			Tuple2<Reified<?>, Object> answer = entry.getAnswerAt(i);
			Object x = solved.get(answer._1);
			if (x == null) {
				continue;
			}
			Reified<?> answerTerm = answer._1;
			Object value = semiring.times(callerValue, x);
			result = result.flatMap(__ ->
					emitAnswer(k, callerPkg, argsTerm, callerCall, answerTerm, value));
		}
		return result;
	}

	/**
	 * Publish one sealed answer: instantiate it, unify it against the call pattern to
	 * bind the caller's variables, re-coat to the caller and set the folded value on
	 * the SemiringStore, then hand it to {@code k}.
	 */
	private Fiber<Nothing> emitAnswer(Fiber.Fn<Package, Nothing> k, Package callerPkg,
			Unifiable<?> argsTerm, EnclosingCall callerCall, Reified<?> answerTerm, Object value) {
		return MiniKanren.instantiate(answerTerm).flatMap(freshTerm ->
				MiniKanren.unify(callerPkg.substitution(), argsTerm.getObjectTerm(), freshTerm.getObjectTerm())
						.map(callerPkg::withSubstitutions)
						.map(pkg -> pkg.putStore(callerCall))
						.map(pkg -> storeWriter.apply(pkg, value))
						.map(k::apply)
						.getOrElse(() -> done(nothing()))
						.flatMap(fib -> fib));
	}
}
