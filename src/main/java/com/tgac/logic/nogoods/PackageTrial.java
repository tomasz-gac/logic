package com.tgac.logic.nogoods;

// ABOUTME: The whole trial as one visitor: binding rows step at the substitution
// ABOUTME: level, store rows impose on the scratch, conjuncts thread per part.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.UnifyGoal;
import com.tgac.logic.goals.Package;
import io.vavr.collection.List;
import lombok.Value;

/**
 * One owner for per-literal trial semantics, package-carrying: the fast rows
 * delegate to {@link SubstitutionTrial} (exact refuted/entailed, remainder =
 * the resolved residual) and lift into the scratch; store rows impose on the
 * scratch and classify by the change reading, keeping their ORIGINAL selves
 * when owed (nothing is read back out of a factor); a conjunct threads its
 * parts through the growing scratch, so a mixed nogood crosses off its
 * decided parts at PART granularity. The fast rows answer {@code Fiber.done}
 * — eager Done-composition keeps whole binding chains inside the current
 * step, so the visitor's fiber shape costs nothing until a real imposition
 * runs.
 */
final class PackageTrial implements Posting.Visitor<Fiber<PackageTrial.Outcome>> {

	private final Package scratch;

	PackageTrial(Package scratch) {
		this.scratch = scratch;
	}

	static Fiber<Outcome> trial(Posting forbidden, Package scratch) {
		return forbidden.accept(new PackageTrial(scratch));
	}

	/** Refuted: {@code remainder == null} and not entailed. */
	@Value
	static class Outcome {
		Posting remainder;
		Package grown;
		boolean entailed;

		static Outcome refuted() {
			return new Outcome(null, null, false);
		}

		static Outcome entailed(Package grown) {
			return new Outcome(null, grown, true);
		}

		static Outcome owed(Posting remainder, Package grown) {
			return new Outcome(remainder, grown, false);
		}

		boolean isRefuted() {
			return remainder == null && !entailed;
		}
	}

	private Fiber<Outcome> lifted(SubstitutionTrial.Outcome outcome) {
		if (outcome.isRefuted()) {
			return Fiber.done(Outcome.refuted());
		}
		Package grown = Package.of(outcome.getGrown(), scratch.getStores());
		return Fiber.done(outcome.isEntailed() ?
				Outcome.entailed(grown) :
				Outcome.owed(outcome.getRemainder(), grown));
	}

	@Override
	public Fiber<Outcome> visit(UnifyGoal<?> unification) {
		return lifted(SubstitutionTrial.step(unification, scratch.substitution()).get());
	}

	@Override
	public Fiber<Outcome> visit(Posting.Resolution resolution) {
		return lifted(SubstitutionTrial.step(resolution, scratch.substitution()).get());
	}

	@Override
	public Fiber<Outcome> visit(Posting.Activation activation) {
		return imposed(activation);
	}

	@Override
	public Fiber<Outcome> visit(Posting.Absorption absorption) {
		return imposed(absorption);
	}

	/**
	 * The imposition read three ways: FAILS = refuted; changes NOTHING =
	 * entailed (lawful by monotonicity); NEW knowledge = owed, the literal
	 * surviving as its ORIGINAL self. A fork reads conservatively — owed,
	 * with the scratch unthreaded (missed jointness only ever keeps more).
	 */
	private Fiber<Outcome> imposed(Posting literal) {
		return Verification.imposed(literal, scratch).map(worlds -> {
			if (worlds.isEmpty()) {
				return Outcome.refuted();
			}
			if (worlds.size() > 1) {
				return Outcome.owed(literal, scratch);
			}
			Package grown = worlds.head();
			return Verification.unchanged(scratch, grown) ?
					Outcome.entailed(grown) :
					Outcome.owed(literal, grown);
		});
	}

	/** Parts thread through the growing scratch; one refusal refutes the whole. */
	@Override
	public Fiber<Outcome> visit(Posting.AllOf all) {
		return parts(all.getParts(), List.empty(), scratch);
	}

	private static Fiber<Outcome> parts(List<Posting> pending, List<Posting> remainders, Package current) {
		if (pending.isEmpty()) {
			return Fiber.done(remainders.isEmpty() ?
					Outcome.entailed(current) :
					Outcome.owed(remainders.size() == 1 ?
							remainders.head() :
							Posting.all(remainders.toJavaArray(Posting[]::new)),
							current));
		}
		return trial(pending.head(), current).flatMap(outcome -> {
			if (outcome.isRefuted()) {
				return Fiber.done(Outcome.refuted());
			}
			return parts(pending.tail(),
					outcome.isEntailed() ? remainders : remainders.append(outcome.getRemainder()),
					outcome.getGrown());
		});
	}
}
