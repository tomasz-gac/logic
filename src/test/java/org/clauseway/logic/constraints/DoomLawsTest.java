package org.clauseway.logic.constraints;

// ABOUTME: The doom law as seeded properties: doom claimed now implies failure
// ABOUTME: at every extension, and the verdict never leaks into the count.

import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.LVal.lval;
import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.finitedomain.FiniteDomain;
import org.clauseway.logic.finitedomain.Ints;
import java.util.stream.IntStream;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.ArrayList;
import java.util.Random;
import org.junit.Test;

/**
 * Doom is a TRUST SURFACE: a posting that claims doom must fail on
 * imposition, at the claiming state and every extension of it — the
 * refuted-permanent law. Checked over the whole vocabulary:
 * unifications, conjunctions, FD doors, exclusions, disjuncts.
 */
public class DoomLawsTest {

	private static final int SEEDS = 200;

	private static final class World {
		final java.util.List<Unifiable<Integer>> vars = new ArrayList<>();
		final Random r;

		World(long seed) {
			r = new Random(seed);
			for (int i = 0; i < 4; i++) {
				vars.add(lvar());
			}
		}

		Unifiable<Integer> var() {
			return vars.get(r.nextInt(vars.size()));
		}

		Posting literal() {
			switch (r.nextInt(5)) {
				case 0:
					return Posting.bind(var(), lval(r.nextInt(3)));
				case 1:
					return Posting.bind(var(), var());
				case 2:
					return Posting.all(Posting.bind(var(), lval(r.nextInt(3))),
							Posting.bind(var(), lval(r.nextInt(3))));
				case 3:
					return exclude(var().unifies(lval(r.nextInt(3))));
				default:
					int lo = r.nextInt(2);
					return FiniteDomain.dom(var(), Ints.enumerated(
							IntStream.rangeClosed(lo, lo + 2).boxed().toArray(Integer[]::new)));
			}
		}

		Package state(Package from, int bindings) {
			Package p = from;
			for (int i = 0; i < bindings; i++) {
				List<Package> worlds =
						new BreadthFirstScheduler<>(Trial.imposed(Posting.bind(var(), lval(r.nextInt(3))), p)).get();
				if (!worlds.isEmpty()) {
					p = worlds.head();
				}
			}
			return p;
		}
	}

	@Test
	public void doomImpliesFailureNowAndForever() {
		int exercised = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			Package p = w.state(Package.empty(), 3);
			Posting literal = w.literal();
			if (!literal.doomed(p)) {
				continue;
			}
			exercised++;
			assertThat(new BreadthFirstScheduler<>(Trial.imposed(literal, p)).get())
					.describedAs("seed %d: doomed posting imposed successfully", seed)
					.isEmpty();
			Package grown = w.state(p, 2);
			assertThat(literal.doomed(grown))
					.describedAs("seed %d: doom lifted by growth", seed)
					.isTrue();
			assertThat(new BreadthFirstScheduler<>(Trial.imposed(literal, grown)).get())
					.describedAs("seed %d: doomed posting imposed at extension", seed)
					.isEmpty();
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(10);
	}

	@Test
	public void doomNeverPricesTheAnswerBound() {
		// a count and a verdict are different trust surfaces: a posting's
		// price may come from its own arithmetic against the substitution
		// (a ground clash counts 0 honestly), but store knowledge — doom's
		// diet — never moves it; the kill is the pruning pass's (DoomPruner),
		// never the sort key's
		int exercised = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			Package p = w.state(Package.empty(), 2);
			Posting literal = w.literal();
			if (literal.doomed(p)) {
				exercised++;
			}
			assertThat(literal.answers(p))
					.describedAs("seed %d: store knowledge moved the price", seed)
					.isEqualTo(literal.answers(p.substitution()));
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(5);
	}
}
