package com.tgac.logic.constraints;

// ABOUTME: The trial's laws as seeded properties: refuted is permanent, entailed
// ABOUTME: is exact, the remainder preserves denotation, flattening is idempotent.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.disjunction.Disjunct;
import com.tgac.logic.disjunction.Disjunction;
import com.tgac.logic.goals.Package;
import com.tgac.logic.nogoods.Nogood;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.ArrayList;
import java.util.Random;
import org.junit.Test;

/**
 * The laws the engine leans on, checked over seeded random samples — the
 * lattice-three-way note's one live obligation, and the gate for every
 * consumer of the trial's verdicts (verification, subsumption, doom,
 * discharge, the comparison fast path when it arrives). Binding-shaped
 * literals only in this tier; store-shaped literals join with the
 * imposition-law tier.
 */
public class TrialLawsTest {

	private static final int SEEDS = 200;

	/** A small world: four variables, values 0..3, literals over both. */
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

		/** A random binding-shaped literal: var≡value, var≡var, or a small conjunction. */
		Posting literal() {
			int shape = r.nextInt(4);
			if (shape == 0) {
				return Posting.bind(var(), lval(r.nextInt(4)));
			}
			if (shape == 1) {
				return Posting.bind(var(), var());
			}
			Posting a = Posting.bind(var(), lval(r.nextInt(4)));
			Posting b = shape == 2 ?
					Posting.bind(var(), var()) :
					Posting.bind(var(), lval(r.nextInt(4)));
			return Posting.all(a, b);
		}

		/** A random consistent state: impose a few bindings, skipping failures. */
		Package state(Package from, int bindings) {
			Package p = from;
			for (int i = 0; i < bindings; i++) {
				Fiber<io.vavr.collection.List<Package>> imposed =
						Trial.imposed(Posting.bind(var(), lval(r.nextInt(4))), p);
				List<Package> worlds = imposed.ground();
				if (!worlds.isEmpty()) {
					p = worlds.head();
				}
			}
			return p;
		}
	}

	private static Trial.Outcome outcomeOf(Posting literal, Package p) {
		Fiber<Trial.Outcome> trial = Trial.trial(literal, p);
		assertThat(trial.isDone())
				.describedAs("binding-shaped trials are Done by construction")
				.isTrue();
		return trial.ground();
	}

	@Test
	public void refutedIsPermanent() {
		// refuted at P stays refuted at every P' with more knowledge — the
		// license for doom, discharge, and watched-literal laziness
		int exercised = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			Package p = w.state(Package.empty(), 2);
			Posting literal = w.literal();
			if (!outcomeOf(literal, p).isRefuted()) {
				continue;
			}
			exercised++;
			Package grown = w.state(p, 2);
			assertThat(outcomeOf(literal, grown).isRefuted())
					.describedAs("seed %d: refuted lifted by growth", seed)
					.isTrue();
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(10);
	}

	@Test
	public void entailedIsExactAndPermanent() {
		// entailed means imposing is a no-op NOW, and entailed at every P'
		int exercised = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			Package p = w.state(Package.empty(), 3);
			Posting literal = w.literal();
			Trial.Outcome outcome = outcomeOf(literal, p);
			if (!outcome.isEntailed()) {
				continue;
			}
			exercised++;
			List<Package> worlds = Trial.imposed(literal, p).ground();
			assertThat(worlds).describedAs("seed %d: entailed imposition delivers", seed)
					.hasSize(1);
			assertThat(worlds.head().substitution())
					.describedAs("seed %d: entailed imposition is a no-op", seed)
					.isEqualTo(p.substitution());
			Package grown = w.state(p, 2);
			assertThat(outcomeOf(literal, grown).isEntailed())
					.describedAs("seed %d: entailed lifted by growth", seed)
					.isTrue();
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(10);
	}

	@Test
	public void theRemainderPreservesDenotation() {
		// imposing the owed remainder lands the same world as imposing the
		// original literal — the crossed-off parts already held
		int exercised = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			Package p = w.state(Package.empty(), 2);
			Posting literal = w.literal();
			Trial.Outcome outcome = outcomeOf(literal, p);
			if (outcome.isRefuted() || outcome.isEntailed()) {
				continue;
			}
			exercised++;
			List<Package> viaLiteral = Trial.imposed(literal, p).ground();
			List<Package> viaRemainder = Trial.imposed(outcome.getRemainder(), p).ground();
			assertThat(viaRemainder.map(Package::substitution))
					.describedAs("seed %d: remainder diverged from literal", seed)
					.isEqualTo(viaLiteral.map(Package::substitution));
		}
		assertThat(exercised).describedAs("the law must not pass vacuously")
				.isGreaterThan(10);
	}

	@Test
	public void flatteningIsIdempotentAndNestingBlind() {
		// ∧ and ∨ are associative: nested envelopes equal their flat forms,
		// and re-flattening changes nothing — structural equality matches
		// semantic equality for dedup and key comparison
		for (long seed = 0; seed < SEEDS; seed++) {
			World w = new World(seed);
			Posting a = w.literal();
			Posting b = w.literal();
			Posting c = w.literal();

			Nogood nested = Nogood.of(Posting.all(Posting.all(a, b), c));
			Nogood flat = Nogood.of(Posting.all(a, b, c));
			assertThat(nested).describedAs("seed %d: ∧ nesting visible", seed)
					.isEqualTo(flat);
			assertThat(Nogood.of(flat.getForbidden()))
					.describedAs("seed %d: ∧ flattening not idempotent", seed)
					.isEqualTo(flat);

			Disjunct nestedOr = Disjunct.of(Disjunction.any(a, Disjunction.any(b, c)));
			Disjunct flatOr = Disjunct.of(Disjunction.any(a, b, c));
			assertThat(nestedOr).describedAs("seed %d: ∨ nesting visible", seed)
					.isEqualTo(flatOr);
		}
	}
}
