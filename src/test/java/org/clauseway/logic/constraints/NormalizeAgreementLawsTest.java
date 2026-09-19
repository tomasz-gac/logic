package org.clauseway.logic.constraints;

// ABOUTME: The overload agreement law: delta normalization lands where wholesale
// ABOUTME: normalization would — normalize(prefix, S1) == normalize(S1 + prefix).

import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.LVal.lval;
import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.fibers.Fiber;
import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import org.clauseway.logic.constraints.store.Constraint;
import org.clauseway.logic.constraints.store.Atom;
import org.clauseway.logic.constraints.store.Factor;
import org.clauseway.logic.constraints.store.Revision;
import org.clauseway.logic.constraints.store.Theory;
import org.clauseway.logic.finitedomain.FiniteDomain;
import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.LVar;
import org.clauseway.logic.unification.Prefix;
import org.clauseway.logic.unification.Substitutions;
import org.clauseway.logic.unification.Unifiable;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.Collections;
import java.util.Random;
import org.junit.Test;

/**
 * The two normalize overloads are one operation at two granularities, and the
 * signature carries the law: {@code normalize(prefix, S2) == normalize(S2)}
 * where {@code S2 = S1 + prefix}. The delta path may skip whatever the prefix
 * cannot have touched; it may never LAND anywhere else. Agreement is read on
 * the outcome — failure iff failure, otherwise the resulting factor
 * (unchanged ≡ updated-with-an-equal-factor). Inferred-prefix payloads are
 * not compared: the wholesale pass may re-derive consequences the delta pass
 * knows are already applied.
 */
public class NormalizeAgreementLawsTest {

	private static final int SEEDS = 200;

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void deltaNormalizationLandsWhereWholesaleWould() {
		int exercised = 0;
		int failures = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			Random r = new Random(seed);
			Unifiable<Long> x = lvar();
			Unifiable<Long> y = lvar();
			Package p = Package.empty();
			p = impose(p, FiniteDomain.dom(x, Longs.range(0, 5)));
			p = impose(p, FiniteDomain.dom(y, Longs.range(0, 5)));
			p = impose(p, exclude(x.unifies(lval((long) r.nextInt(5)))));
			if (r.nextBoolean()) {
				p = impose(p, Longs.leq(x, y));
			}

			// the binding under examination: sometimes consistent, sometimes
			// contradicting the exclusion or the domain — agreement must hold
			// on BOTH verdict directions
			long v = r.nextInt(7);
			Option<Prefix> minted = Prefix.binding(
					p.substitution(), (LVar<?>) x.asVar().get(), lval(v));
			if (!minted.isDefined()) {
				continue;
			}
			Option<Tuple2<Substitutions, Prefix>> examined =
					p.substitution().extended(minted.get());
			if (!examined.isDefined() || examined.get()._2.isEmpty()) {
				continue;
			}
			Prefix kept = examined.get()._2;
			Package extended = p.withSubstitutions(examined.get()._1);

			for (Object store : extended.getStores().values()) {
				if (!(store instanceof Constraint)) {
					continue;
				}
				exercised++;
				Constraint<?> pair = (Constraint<?>) store;
				Factor<?> cs = pair.getFactor();
				Revision delta = run((Fiber<Revision>) ((Factor) cs).normalize((Theory) pair.getTheory(), kept, extended));
				Revision wholesale = run((Fiber<Revision>) ((Factor) cs).normalize((Theory) pair.getTheory(),
						pair.getTheory().atoms(), extended));

				Option<Object> deltaLanding = landing(delta, pair);
				Option<Object> wholesaleLanding = landing(wholesale, pair);
				if (!deltaLanding.isDefined()) {
					failures++;
				}
				assertThat(deltaLanding.isDefined())
						.describedAs("seed %d, %s: one overload failed, the other did not",
								seed, cs.getClass().getSimpleName())
						.isEqualTo(wholesaleLanding.isDefined());
				if (deltaLanding.isDefined()) {
					assertThat(deltaLanding.get())
							.describedAs("seed %d, %s: the overloads landed on different factors",
									seed, cs.getClass().getSimpleName())
							.isEqualTo(wholesaleLanding.get());
				}
			}
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(50);
		assertThat(failures).describedAs("both verdict directions must be exercised")
				.isGreaterThan(5);
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void statedLandsWhereWholesaleNormalizeWould() {
		int exercised = 0;
		int failures = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			Random r = new Random(seed);
			Unifiable<Long> x = lvar();
			Unifiable<Long> y = lvar();
			Package p = Package.empty();
			boolean conflicting = r.nextBoolean();
			p = impose(p, FiniteDomain.dom(x, conflicting
					? Longs.range(3, 5)
					: Longs.range(0, 5)));
			p = impose(p, FiniteDomain.dom(y, conflicting
					? Longs.range(0, 2)
					: Longs.range(0, 5)));
			p = impose(p, exclude(x.unifies(lval(7L))));

			// a freshly PARKED atom, un-examined: only it is new in the package
			Posting posting = r.nextBoolean()
					? Longs.leq(x, y)
					: exclude(conflicting ? x.unifies(x) : x.unifies(lval((long) r.nextInt(5))));
			Atom atom = ((Posting.Activation) posting).getItem();
			Constraint<?> resident = (Constraint<?>) p.getStores()
					.get(atom.getFactorClass()).get();
			Theory met = resident.getTheory()
					.meet(Theory.of((Iterable) Collections.singletonList(atom)));
			Package parked = p.putStore(atom.getFactorClass(),
					Constraint.of(met, (Factor) resident.getFactor()));
			Constraint<?> pair = (Constraint<?>) parked.getStores()
					.get(atom.getFactorClass()).get();
			Factor<?> cs = pair.getFactor();

			exercised++;
			Revision delta = run((Fiber<Revision>) ((Factor) cs).normalize((Theory) pair.getTheory(),
					LinkedHashSet.of((Atom) atom), parked));
			Revision wholesale = run((Fiber<Revision>) ((Factor) cs).normalize((Theory) pair.getTheory(),
					pair.getTheory().atoms(), parked));
			Option<Object> deltaLanding = landing(delta, pair);
			Option<Object> wholesaleLanding = landing(wholesale, pair);
			if (!deltaLanding.isDefined()) {
				failures++;
			}
			assertThat(deltaLanding.isDefined())
					.describedAs("seed %d, %s: one overload failed, the other did not",
							seed, cs.getClass().getSimpleName())
					.isEqualTo(wholesaleLanding.isDefined());
			if (deltaLanding.isDefined()) {
				assertThat(deltaLanding.get())
						.describedAs("seed %d, %s: the overloads landed on different factors",
								seed, cs.getClass().getSimpleName())
						.isEqualTo(wholesaleLanding.get());
			}
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(50);
		assertThat(failures).describedAs("both verdict directions must be exercised")
				.isGreaterThan(5);
	}

	private static Revision run(Fiber<Revision> normalize) {
		return new BreadthFirstScheduler<>(normalize).get();
	}

	/** Failure = none; otherwise the theory the revision leaves resident. */
	private static Option<Object> landing(Revision revision, Constraint<?> pair) {
		return revision.match(
				Option::none,
				() -> Option.of(pair.getTheory()),
				updated -> Option.of(updated.constraint().getTheory()));
	}



	private static Package impose(Package p, Posting literal) {
		List<Package> worlds = new BreadthFirstScheduler<>(Trial.imposed(literal, p)).get();
		assertThat(worlds).describedAs("fixture imposition must be deterministic").hasSize(1);
		return worlds.head();
	}
}
