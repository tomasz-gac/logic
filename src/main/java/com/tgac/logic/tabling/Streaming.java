package com.tgac.logic.tabling;

// ABOUTME: Streaming tabling: fold each answer's value into the cell by ⊕ and hand
// ABOUTME: it out as it is found. Plain (presence) and bounded-weighted are instances.

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;

import com.tgac.functional.algebra.BoundedSemiring;
import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The streaming algorithm: an answer's running value is read off its package,
 * folded into the cell, and handed straight on — all its work happens during
 * EXPLORE, and its emission is inert. Holding a {@link BoundedSemiring} is the
 * termination guarantee, not a convenience: {@code a* = 1} makes cyclic
 * re-derivation stationary, so streaming through a loop converges. A merely
 * idempotent semiring (provenance) would amplify around the loop forever —
 * that is the closed mode's job. The presence instance (a Boolean cell, no-op
 * accessors) is plain set tabling; a real semiring with real accessors is
 * bounded-weighted tabling.
 */
final class Streaming implements TablingMode {

	private final BoundedSemiring<Object> semiring;
	private final Function<Package, Object> weightReader;
	private final BiFunction<Package, Object, Package> weightWriter;

	Streaming(BoundedSemiring<Object> semiring,
			Function<Package, Object> weightReader,
			BiFunction<Package, Object, Package> weightWriter) {
		this.semiring = semiring;
		this.weightReader = weightReader;
		this.weightWriter = weightWriter;
	}

	@Override
	public IdempotentSemiring<Object> cellSemiring() {
		return semiring;
	}

	@Override
	public Package bodyState(Package callerPkg) {
		return weightWriter.apply(callerPkg, semiring.one());
	}

	@Override
	public Package absorb(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer,
			Object cellValue, TableEntry<Object> enclosingCall) {
		return weightWriter.apply(unifiedPkg, semiring.times(weightReader.apply(unifiedPkg), cellValue));
	}

	@Override
	public Fiber<Nothing> emitCaughtUp(TableEntry<Object> entry, Registration reader) {
		// the answers already flowed inline — a finished branch
		return done(nothing());
	}

	@Override
	public Tuple2<Reified<?>, Object> capture(TableEntry<Object> entry, Package answerPkg, Reified<?> answerTerm) {
		return Tuple.of(answerTerm, weightReader.apply(answerPkg));
	}

	@Override
	public Fiber<Nothing> emit(TableEntry<Object> entry, List<Registration> drained) {
		// answers streamed as they were found — the drained consumers are dead branches
		return done(nothing());
	}
}
