package com.tgac.logic.tabling;

// ABOUTME: Streaming tabling: fold each answer's value into the cell by ⊕ and hand
// ABOUTME: it out by finality. Plain (conditions) and bounded-weighted are instances.

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
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The streaming algorithm: an answer's value is folded into the cell and
 * handed on by FINALITY — a value at ⊕'s top streams on arrival, anything
 * below waits for the seal. Holding a {@link BoundedSemiring} is the
 * termination guarantee, not a convenience: {@code a* = 1} makes cyclic
 * re-derivation stationary, so streaming through a loop converges. A merely
 * idempotent semiring (provenance) would amplify around the loop forever —
 * that is the closed mode's job. The PLAIN instance's cell is the
 * constraint ring ({@link Condition}): every ground answer is 1 and
 * streams, every conditional answer sums its regions toward the seal. A
 * real weight ring with real accessors is bounded-weighted tabling.
 */
final class Streaming implements TablingMode {

	private final BoundedSemiring<Object> semiring;
	private final Function<Package, Object> weightReader;
	private final BiFunction<Package, Object, Package> weightWriter;
	/** The plain instance (conditions cell) — the only one residues may ride. */
	private final boolean plain;

	Streaming(BoundedSemiring<Object> semiring,
			Function<Package, Object> weightReader,
			BiFunction<Package, Object, Package> weightWriter,
			boolean plain) {
		this.semiring = semiring;
		this.weightReader = weightReader;
		this.weightWriter = weightWriter;
		this.plain = plain;
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
			Object cellValue) {
		if (plain) {
			// the condition was imposed by the delivery's restate; no value threads
			return unifiedPkg;
		}
		return weightWriter.apply(unifiedPkg, semiring.times(weightReader.apply(unifiedPkg), cellValue));
	}

	@Override
	public Tuple2<Reified<?>, Object> capture(TableEntry<Object> entry, Package answerPkg,
			Reified<?> answerTerm, Residues residues) {
		if (plain) {
			// the answer's value IS its condition: ground = 1, conditional = its region
			return Tuple.of(answerTerm, Condition.of(residues));
		}
		if (!residues.isTrue()) {
			// an entailed-but-cheaper answer would silently lose its value on
			// the overlap: weighted answer values and answer residues were
			// never designed together, so only the plain instance admits them
			throw new IllegalStateException(
					"constrained answers are supported only under plain tabling: "
							+ "weights over conditional answers is an orthogonal, open concern");
		}
		return Tuple.of(answerTerm, weightReader.apply(answerPkg));
	}

	@Override
	public Fiber<Nothing> caughtUp(TableEntry<Object> entry, Reader reader) {
		// the answers already flowed inline or at the seal — a finished branch
		return done(nothing());
	}
}
