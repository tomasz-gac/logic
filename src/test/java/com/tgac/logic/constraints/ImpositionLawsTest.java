package com.tgac.logic.constraints;

// ABOUTME: The imposition law as seeded properties per store: idempotence,
// ABOUTME: quiescent normalize is a fixpoint, no silent swallowing, ground decides.

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.Test;

/**
 * The claims Verification.unchanged leans on ("idempotent normalization,
 * the ground floor, no silent swallowing — the logic laws kit's claims"),
 * checked over seeded random store-shaped impositions: FD values (dom), FD
 * couplings (leq, addo), exclusions, disjuncts. Store-shaped companion to
 * {@link TrialLawsTest}'s binding tier.
 */
public class ImpositionLawsTest {

	private static final int SEEDS = 120;

	private static final class World {
		final java.util.List<Unifiable<Long>> vars = new ArrayList<>();
		final Random r;

		World(long seed) {
			r = new Random(seed);
			for (int i = 0; i < 3; i++) {
				vars.add(lvar());
			}
		}

		Unifiable<Long> var() {
			return vars.get(r.nextInt(vars.size()));
		}

		Domain<Long> dom() {
			int lo = r.nextInt(3);
			return EnumeratedDomain.of(Array.ofAll(
					IntStream.rangeClosed(lo, lo + 1 + r.nextInt(3)).boxed())
					.map(i -> Arithmetic.ofCasted((long) i)));
		}

		/** A random store-shaped posting over the world's variables. */
		Posting literal() {
			switch (r.nextInt(4)) {
				case 0:
					return FiniteDomain.dom(var(), dom());
				case 1:
					return FiniteDomain.leq(var(), var());
				case 2:
					return FiniteDomain.addo(var(), lval(1L), var());
				default:
					return exclude(var().unifies(lval((long) r.nextInt(4))));
			}
		}

		/** A random consistent state grown by store-shaped impositions. */
		Package state(int impositions) {
			Package p = Package.empty();
			for (int i = 0; i < impositions; i++) {
				List<Package> worlds = new BreadthFirstScheduler<>(Trial.imposed(literal(), p)).get();
				if (worlds.size() == 1) {
					p = worlds.head();
				}
			}
			return p;
		}
	}

	@Test
	public void impositionIsIdempotent() {
		// imposing the same item twice equals once: the second imposition
		// changes nothing the classifier can see
		int exercised = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			Package p = w.state(2);
			Posting literal = w.literal();
			List<Package> once = new BreadthFirstScheduler<>(Trial.imposed(literal, p)).get();
			if (once.size() != 1) {
				continue;
			}
			exercised++;
			List<Package> twice = new BreadthFirstScheduler<>(Trial.imposed(literal, once.head())).get();
			assertThat(twice).describedAs("seed %d: re-imposition failed", seed).hasSize(1);
			assertThat(Trial.unchanged(once.head(), twice.head()))
					.describedAs("seed %d: re-imposition changed the package", seed)
					.isTrue();
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(10);
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void quiescentNormalizeIsAFixpoint() {
		// a delivered world is post-drain: every resident store's normalize
		// answers unchanged (or an equal replacement) — quiescence is real
		int exercised = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			Package p = w.state(3);
			for (Object store : p.getStores().values()) {
				if (!(store instanceof Constraint)) {
					continue;
				}
				exercised++;
				Constraint<?> pair = (Constraint<?>) store;
				Factor<?> cs = pair.getFactor();
				final long s = seed;
				new BreadthFirstScheduler<>((Fiber<Revision>) ((Factor) cs).normalize((Theory) pair.getTheory(), p)).get().match(
						() -> {
							throw new AssertionError(
									"seed " + s + ": quiescent normalize failed: " + cs);
						},
						() -> null,
						updated -> {
							assertThat((Object) updated.constraint().getTheory())
									.describedAs("seed %d: quiescent normalize moved: %s", s, cs)
									.isEqualTo(pair.getTheory());
							return null;
						});
			}
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(10);
	}

	@Test
	public void noSilentSwallowing() {
		// a contradictory imposition must FAIL (zero worlds), never vanish
		// into an alive-but-wrong package
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			Unifiable<Long> x = lvar();
			Package p = new BreadthFirstScheduler<>(Trial.imposed(
					FiniteDomain.dom(x, EnumeratedDomain.range(0L, 2L)), Package.empty())
					).get().head();

			List<Package> clash = new BreadthFirstScheduler<>(Trial.imposed(
					FiniteDomain.dom(x, EnumeratedDomain.range(5L, 7L)), p)).get();
			assertThat(clash).describedAs("seed %d: disjoint dom swallowed", seed).isEmpty();

			List<Package> bound = new BreadthFirstScheduler<>(Trial.imposed(Posting.bind(x, lval(9L)), p)).get();
			assertThat(bound).describedAs("seed %d: out-of-domain bind swallowed", seed)
					.isEmpty();
		}
	}

	@Test
	public void groundImpositionDecides() {
		// with ground arguments an imposition is decisive: it fails, or it
		// leaves the substitutions untouched — no residual choice survives
		int exercised = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			long a = w.r.nextInt(4);
			long b = w.r.nextInt(4);
			Posting ground = w.r.nextBoolean() ?
					FiniteDomain.leq(lval(a), lval(b)) :
					FiniteDomain.addo(lval(a), lval(1L), lval(a + (w.r.nextBoolean() ? 1 : 2)));
			exercised++;
			List<Package> worlds = new BreadthFirstScheduler<>(Trial.imposed(ground, Package.empty())).get();
			if (worlds.isEmpty()) {
				continue;
			}
			assertThat(worlds).describedAs("seed %d: ground imposition forked", seed).hasSize(1);
			assertThat(worlds.head().substitution())
					.describedAs("seed %d: ground imposition bound something", seed)
					.isEqualTo(Package.empty().substitution());
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(10);
	}
}
