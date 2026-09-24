package org.clauseway.logic.goals.optimizer;

// ABOUTME: Receipts for the doom pruning pass: doomed postings rewrite to failure,
// ABOUTME: dead conjuncts collapse their conjunction, dead conde alternatives drop.

import static org.clauseway.logic.constraints.Constraints.unify;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.category.Nothing;
import org.clauseway.functional.monad.Cont;
import org.clauseway.logic.finitedomain.FiniteDomain;
import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.Substitutions;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.functional.tuples.Tuple;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.Value;
import org.junit.Test;

public class DoomPrunerTest {

	private static final int N = 40;

	@Value
	private static class Probe implements Goal {
		AtomicLong spawns;

		@Override
		public Cont<Package, Nothing> apply(Package s) {
			spawns.incrementAndGet();
			return Cont.just(s);
		}
	}

	private static Goal oneOf(Unifiable<Long> x, AtomicLong spawns) {
		Goal acc = new Probe(spawns).and(unify(x, lval(1L)));
		for (long i = 2; i <= N; i++) {
			acc = acc.or(new Probe(spawns).and(unify(x, lval(i))));
		}
		return acc;
	}

	@Test
	public void aDeadPostCollapsesItsConjunctionBeforeGeneration() {
		Goal[] dead = {
				FiniteDomain.dom(lval(5L), Longs.range(0, 3)),
				Longs.leq(lval(5L), lval(2L)),
				Longs.separate(lval(1L), lval(1L)),
				exclude(lval(1L).unifies(lval(1L))),
				lval(1L).unifies(lval(2L)),
				lval(Tuple.of(lvar(), 1L)).unifies(lval(Tuple.of(lvar(), 2L)))};
		for (Goal deadPost : dead) {
			Unifiable<Long> x = lvar();
			AtomicLong spawns = new AtomicLong();
			assertThat(oneOf(x, spawns).and(deadPost)
					.solve(x, new DoomPruner()).count()).isZero();
			assertThat(spawns.get()).describedAs(deadPost.toString()).isZero();
		}
	}

	@Test
	public void aDeadAlternativeDropsWhileLiveOnesSurvive() {
		Unifiable<Long> x = lvar();
		AtomicLong spawns = new AtomicLong();
		Goal g = new Probe(spawns).and(unify(x, lval(1L))).and(Longs.leq(lval(5L), lval(2L)))
				.or(unify(x, lval(2L)));
		assertThat(g.solve(x, new DoomPruner())
				.map(Object::toString).collect(Collectors.toList()))
				.containsExactly("{2}");
		assertThat(spawns.get()).isZero();
	}

	@Test
	public void anOpenPostingClaimsNothing() {
		// x is free at rewrite time: the post may still fail later, but doom
		// under partial knowledge claims nothing — the tree is untouched
		Unifiable<Long> x = lvar();
		AtomicLong spawns = new AtomicLong();
		assertThat(oneOf(x, spawns).and(Longs.leq(x, lval(5L)))
				.solve(x, new DoomPruner()).count()).isEqualTo(5);
		assertThat(spawns.get()).isEqualTo(N);
	}

	@Test
	public void theAmbientHookPrunesAgainstLiveKnowledge() {
		// doom invisible at the root (no store yet), visible at the defer
		// unfold where the pass state carries x's live domain
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		AtomicLong spawns = new AtomicLong();
		Goal g = FiniteDomain.dom(x, Longs.interval(0, 4))
				.and(Goal.defer(() -> oneOf(y, spawns).and(FiniteDomain.dom(x, Longs.interval(8, 12)))));
		assertThat(g.solve(y, new DoomPruner()).count()).isZero();
		assertThat(spawns.get()).isZero();
	}

	@Test
	public void aBareDoomedPostingRewritesToFailure() {
		Goal pruned = Longs.leq(lval(5L), lval(2L)).accept(new DoomPruner()).ground();
		assertThat(((Bounded) pruned).answers(Substitutions.empty())).isZero();
	}
}
