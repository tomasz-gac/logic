package org.clauseway.logic.constraints;

// ABOUTME: Trial.now — the binding-shaped partition's synchronous face: answers
// ABOUTME: now or claims nothing, agrees with the fiber lane, budget-invariant.

import static org.clauseway.logic.unification.LVal.lval;
import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.fibers.interpreter.EngineGuard;
import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import org.clauseway.logic.finitedomain.FiniteDomain;
import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.ArrayList;
import java.util.Random;
import org.junit.Test;

public class TrialNowTest {

	@Test
	public void bindingLiteralsAnswerNow() {
		Unifiable<Integer> x = lvar();
		Package bound = state(Posting.bind(x, lval(3)), Package.empty());

		assertThat(Trial.now(Posting.bind(x, lval(3)), bound).get().isEntailed()).isTrue();
		assertThat(Trial.now(Posting.bind(x, lval(4)), bound).get().isRefuted()).isTrue();

		Unifiable<Integer> free = lvar();
		Trial.Outcome owed = Trial.now(Posting.bind(free, lval(1)), bound).get();
		assertThat(owed.isRefuted()).isFalse();
		assertThat(owed.isEntailed()).isFalse();
		assertThat(owed.getRemainder()).isNotNull();
	}

	@Test
	public void aStoreShapedPostingClaimsNothing() {
		Unifiable<Long> x = lvar();
		assertThat(Trial.now(FiniteDomain.dom(x, Longs.range(1, 3)), Package.empty())
				.isDefined())
				.isFalse();
	}

	@Test
	public void aMixedConjunctionClaimsNothing() {
		Unifiable<Long> x = lvar();
		Posting mixed = Posting.all(
				Posting.bind(x, lval(1L)),
				FiniteDomain.dom(x, Longs.range(1, 3)));
		assertThat(Trial.now(mixed, Package.empty()).isDefined()).isFalse();
	}

	@Test
	public void nowAgreesWithTheFiberLane() {
		for (long seed = 0; seed < 100; seed++) {
			Random r = new Random(seed);
			java.util.List<Unifiable<Integer>> vars = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				vars.add(lvar());
			}
			Package p = Package.empty();
			for (int i = 0; i < 2; i++) {
				// conflicting random bindings legitimately fail: skip, like the laws kit
				List<Package> worlds = new BreadthFirstScheduler<>(
						Trial.imposed(Posting.bind(vars.get(r.nextInt(4)), lval(r.nextInt(4))), p)).get();
				if (!worlds.isEmpty()) {
					p = worlds.head();
				}
			}
			Posting literal = r.nextBoolean() ?
					Posting.bind(vars.get(r.nextInt(4)), lval(r.nextInt(4))) :
					Posting.all(
							Posting.bind(vars.get(r.nextInt(4)), lval(r.nextInt(4))),
							Posting.bind(vars.get(r.nextInt(4)), vars.get(r.nextInt(4))));
			Trial.Outcome now = Trial.now(literal, p).get();
			Trial.Outcome fiber = new BreadthFirstScheduler<>(Trial.trial(literal, p)).get();
			assertThat(now).describedAs("seed %d", seed).isEqualTo(fiber);
		}
	}

	@Test
	public void nowAndDoomAreBudgetInvariant() {
		Unifiable<Integer> x = lvar();
		Package bound = state(Posting.bind(x, lval(3)), Package.empty());
		Posting refuted = Posting.bind(x, lval(4));

		int pinned = EngineGuard.eagerBudget();
		try {
			EngineGuard.setEagerBudget(0);
			assertThat(Trial.now(refuted, bound).get().isRefuted()).isTrue();
			assertThat(Trial.doomed(refuted, bound)).isTrue();
		} finally {
			EngineGuard.setEagerBudget(pinned);
		}
		assertThat(Trial.doomed(refuted, bound)).isTrue();
	}

	private static Package state(Posting literal, Package from) {
		List<Package> worlds = new BreadthFirstScheduler<>(Trial.imposed(literal, from)).get();
		assertThat(worlds).isNotEmpty();
		return worlds.head();
	}
}
