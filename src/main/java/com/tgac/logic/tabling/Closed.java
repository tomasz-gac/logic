package com.tgac.logic.tabling;

// ABOUTME: Closed (star) tabling: explore for structure (presence cell + base/edge
// ABOUTME: capture), solve each edge-graph SCC jointly once all its entries seal, emit.

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
import io.vavr.Tuple3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The closed (star) algorithm. Explore runs as plain set tabling — the cell is
 * presence, so it terminates — while the real value rides the SemiringStore
 * (read/write through {@link #storeReader}/{@link #storeWriter}) and every
 * derivation's contribution is captured on the entry: a NON-looping derivation is
 * a base seed, a one-loop derivation is an edge coefficient carrying the consumed
 * (entry, answer) (star-tabling.md §4).
 *
 * <p>SEALED IS NOT SOLVED. The completion machinery seals entries in whatever
 * order quiescence allows — a mutually recursive pair can seal one at a time —
 * but an entry can only be SOLVED with its whole dependency closure over the
 * captured edge graph, which is exactly the equation system's coupling. So each
 * seal marks its entry pending, and {@link #onEntrySealed} solves a closure once
 * every member has sealed, then emits: a solved entry's master continuation is
 * replayed with {@code x = A* ⊗ b} only when its caller is the top level —
 * a caller inside another tabled entry already receives the value through the
 * edge its own capture recorded, so replaying there would double-count.
 */
final class Closed implements TablingMode {

	/** Explore is set tabling — every answer carries the same presence marker. */
	@SuppressWarnings("unchecked")
	private static final IdempotentSemiring<Object> PRESENCE =
			(IdempotentSemiring<Object>) (IdempotentSemiring<?>) Semirings.BOOLEAN;

	private final ClosedSemiring<Object> semiring;
	private final Function<Package, Object> storeReader;
	private final BiFunction<Package, Object, Package> storeWriter;
	/** The joint star: a whole dependency closure of entries → each entry's answer → its value. */
	private final Function<List<TableEntry<?>>, Map<TableEntry<?>, Map<Reified<?>, Object>>> starSolve;

	/** Each master's emit context, recorded at claim time. */
	private final Map<TableEntry<Object>, EmitContext> contexts = new ConcurrentHashMap<>();
	/** Sealed entries whose dependency closure has not fully sealed yet. Guarded by this. */
	private final Set<TableEntry<Object>> sealedPending = new HashSet<>();
	/** Entries already solved (and emitted where due). Guarded by this. */
	private final Set<TableEntry<Object>> solved = new HashSet<>();

	Closed(ClosedSemiring<Object> semiring,
			Function<Package, Object> storeReader,
			BiFunction<Package, Object, Package> storeWriter,
			Function<List<TableEntry<?>>, Map<TableEntry<?>, Map<Reified<?>, Object>>> starSolve) {
		this.semiring = semiring;
		this.storeReader = storeReader;
		this.storeWriter = storeWriter;
		this.starSolve = starSolve;
	}

	/** What emit needs to replay one master's continuation with the star values. */
	private static final class EmitContext {
		final TableEntry<Object> entry;
		final Fiber.Fn<Package, Nothing> k;
		final Package callerPkg;
		final Unifiable<?> argsTerm;
		final EnclosingCall callerCall;

		EmitContext(TableEntry<Object> entry, Fiber.Fn<Package, Nothing> k,
				Package callerPkg, Unifiable<?> argsTerm, EnclosingCall callerCall) {
			this.entry = entry;
			this.k = k;
			this.callerPkg = callerPkg;
			this.argsTerm = argsTerm;
			this.callerCall = callerCall;
		}
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
		// consuming a still-open call is a loop — record which entry's answer it was
		if (entry.isComplete()) {
			return unifiedPkg;
		}
		Recurrent prev = unifiedPkg.getStores().get(Recurrent.class)
				.map(Recurrent.class::cast).getOrElse(Recurrent.NONE);
		return unifiedPkg.putStore(prev.and(entry, consumedAnswer));
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
			Tuple2<TableEntry<Object>, Reified<?>> from = rec.consumed.head();
			entry.addEdge(answerTerm, from._1, from._2, value, semiring);
		} else {
			throw new IllegalStateException("nonlinear recursion: a derivation consumed "
					+ rec.consumed.size() + " looping calls; star handles only linear systems");
		}
		return answerTerm;
	}

	@Override
	public Package onExit(Package answerPkg, TableEntry<Object> entry, Reified<?> answerTerm, Package callerPkg, Object value) {
		// crossing back into the caller: restore ITS store and loop-record (the
		// callee's value is captured on entry, folded by the star), record that the
		// caller consumed (entry, answerTerm) so its produce captures the edge, and
		// tag the fragment so the closed collector drops it during explore
		Recurrent callerRec = callerPkg.getStores().get(Recurrent.class)
				.map(Recurrent.class::cast).getOrElse(Recurrent.NONE);
		return storeWriter.apply(answerPkg, storeReader.apply(callerPkg))
				.putStore(EnclosingCall.current(callerPkg))
				.putStore(callerRec.and(entry, answerTerm))
				.putStore(Exploration.MARKER);
	}

	@Override
	public void onMasterClaim(TableEntry<Object> entry, Fiber.Fn<Package, Nothing> k,
			Package callerPkg, Unifiable<?> argsTerm, EnclosingCall callerCall) {
		contexts.put(entry, new EmitContext(entry, k, callerPkg, argsTerm, callerCall));
		entry.getRegion().onSealed(() -> onEntrySealed(entry));
	}

	/**
	 * One entry sealed. Solve every pending dependency closure that is now fully
	 * sealed — possibly several, since one seal can complete closures deferred by
	 * earlier seals — and return their emits as one fiber.
	 */
	private synchronized Fiber<Nothing> onEntrySealed(TableEntry<Object> entry) {
		sealedPending.add(entry);
		Fiber<Nothing> result = done(nothing());
		boolean progress = true;
		while (progress) {
			progress = false;
			for (TableEntry<Object> pending : new ArrayList<>(sealedPending)) {
				Set<TableEntry<Object>> closure = dependencyClosure(pending);
				if (!sealedPending.containsAll(minus(closure, solved))) {
					continue;
				}
				Fiber<Nothing> emit = solveAndEmit(closure);
				result = result.flatMap(__ -> emit);
				progress = true;
				break;
			}
		}
		return result;
	}

	/** All entries {@code start}'s value transitively depends on, itself included. */
	private Set<TableEntry<Object>> dependencyClosure(TableEntry<Object> start) {
		Set<TableEntry<Object>> closure = new LinkedHashSet<>();
		ArrayDeque<TableEntry<Object>> frontier = new ArrayDeque<>();
		frontier.add(start);
		while (!frontier.isEmpty()) {
			TableEntry<Object> entry = frontier.poll();
			if (!closure.add(entry)) {
				continue;
			}
			for (Tuple3<Reified<?>, TableEntry<Object>, Reified<?>> edge : entry.edges().keySet()) {
				frontier.add(edge._2);
			}
		}
		return closure;
	}

	private static Set<TableEntry<Object>> minus(Set<TableEntry<Object>> a, Set<TableEntry<Object>> b) {
		Set<TableEntry<Object>> result = new LinkedHashSet<>(a);
		result.removeAll(b);
		return result;
	}

	/**
	 * Solve {@code closure} jointly (already-solved members recompute to the same
	 * values — the maps froze at their seal) and emit each NEWLY solved entry whose
	 * caller is the top level. A caller inside a tabled entry gets the value
	 * through its captured edge instead — replaying into its body would run the
	 * body suffix a second time and double-count the coefficient.
	 */
	private Fiber<Nothing> solveAndEmit(Set<TableEntry<Object>> closure) {
		Map<TableEntry<?>, Map<Reified<?>, Object>> values =
				starSolve.apply(new ArrayList<TableEntry<?>>(closure));
		Fiber<Nothing> result = done(nothing());
		for (TableEntry<Object> member : closure) {
			boolean newlySolved = solved.add(member);
			sealedPending.remove(member);
			EmitContext ctx = contexts.get(member);
			if (!newlySolved || ctx == null || ctx.callerCall.entry() != null) {
				continue;
			}
			Map<Reified<?>, Object> entryValues = values.get(member);
			if (entryValues == null) {
				continue;
			}
			Object callerValue = storeReader.apply(ctx.callerPkg);
			for (int i = 0; i < member.getAnswerCount(); i++) {
				Reified<?> answerTerm = member.getAnswerAt(i)._1;
				Object x = entryValues.get(answerTerm);
				if (x == null) {
					continue;
				}
				Object value = semiring.times(callerValue, x);
				result = result.flatMap(__ ->
						emitAnswer(ctx.k, ctx.callerPkg, ctx.argsTerm, ctx.callerCall, answerTerm, value));
			}
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
