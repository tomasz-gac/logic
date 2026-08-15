package com.tgac.logic.constraints;

// ABOUTME: The doom law as seeded properties: doomed-at-pricing implies failure
// ABOUTME: at every extension; the doors' refinements obey the same contract.

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import static com.tgac.logic.disjunction.Disjunction.any;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import io.vavr.collection.Array;
import java.util.stream.IntStream;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.ArrayList;
import java.util.Random;
import org.junit.Test;

/**
 * Doom is a TRUST SURFACE: a posting that claims doom must fail on
 * imposition, at the claiming state and every extension of it — the
 * refuted-permanent law read at the pricing seat. Checked over the whole
 * vocabulary: unifications, conjunctions, FD doors, exclusions, disjuncts.
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
			switch (r.nextInt(6)) {
				case 0:
					return Posting.bind(var(), lval(r.nextInt(3)));
				case 1:
					return Posting.bind(var(), var());
				case 2:
					return Posting.all(Posting.bind(var(), lval(r.nextInt(3))),
							Posting.bind(var(), lval(r.nextInt(3))));
				case 3:
					return exclude(var().unifies(lval(r.nextInt(3))));
				case 4:
					return any(var().unifies(lval(r.nextInt(3))),
							var().unifies(lval(r.nextInt(3))));
				default:
					int lo = r.nextInt(2);
					return FiniteDomain.dom(var(), EnumeratedDomain.of(
							Array.ofAll(IntStream.rangeClosed(lo, lo + 2).boxed())
									.map(Arithmetic::ofCasted)));
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
	public void doomZeroesTheAnswerBound() {
		// answers(p) = doomed ? 0 : 1 — the pricing contract: a doomed
		// posting prices 0, an undoomed one prices exactly 1
		int exercised = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			Package p = w.state(Package.empty(), 2);
			Posting literal = w.literal();
			long declared = literal.answers(p);
			if (literal.doomed(p)) {
				exercised++;
				assertThat(declared).describedAs("seed %d: doomed but priced", seed).isZero();
			} else {
				assertThat(declared).describedAs("seed %d: undoomed price", seed).isEqualTo(1);
			}
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(5);
	}
}
