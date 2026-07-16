package com.tgac.logic.tabling;

// ABOUTME: Streaming tabling: fold each answer's value into the cell by ⊕ and hand
// ABOUTME: it out as it is found. Plain (presence) and bounded-weighted are instances.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The streaming algorithm: an answer's running value is read off its package,
 * folded into the cell by the (idempotent) semiring, and handed straight on. A
 * bounded semiring makes cyclic re-derivation stationary ({@code a* = 1}), so the
 * search terminates without a seal-time solve. The presence instance (a Boolean
 * cell, no-op accessors) is plain set tabling; a real semiring with real
 * accessors is bounded-weighted tabling.
 */
final class Streaming implements TablingMode {

	private final IdempotentSemiring<Object> semiring;
	private final Function<Package, Object> weightReader;
	private final BiFunction<Package, Object, Package> weightWriter;

	Streaming(IdempotentSemiring<Object> semiring,
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
	public Package enterBody(Package callerPkg) {
		return weightWriter.apply(callerPkg, semiring.one());
	}

	@Override
	public Object callerValue(Package callerPkg) {
		return weightReader.apply(callerPkg);
	}

	@Override
	public Package onConsume(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer, Object cellValue) {
		return weightWriter.apply(unifiedPkg, semiring.times(weightReader.apply(unifiedPkg), cellValue));
	}

	@Override
	public Object cacheValue(Package answerPkg) {
		return weightReader.apply(answerPkg);
	}

	@Override
	public Reified<?> onProduce(TableEntry<Object> entry, Package answerPkg, Reified<?> answerTerm) {
		return answerTerm;
	}

	@Override
	public Package onExit(Package answerPkg, EnclosingCall callerCall, Object callerWeight, Object value) {
		return weightWriter.apply(answerPkg.putStore(callerCall), semiring.times(callerWeight, value));
	}

	@Override
	public void onMasterClaim(TableEntry<Object> entry, Fiber.Fn<Package, Nothing> k,
			Package callerPkg, Unifiable<?> argsTerm, EnclosingCall callerCall) {
		// nothing to do at seal — answers streamed as they were found
	}
}
