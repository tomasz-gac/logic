package com.tgac.logic.goals;

// ABOUTME: Committed choice under exhaustion: a nested condu inside a clause
// ABOUTME: must not leak the fallback when the head clause has solutions.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class ConduNestingTest {

	@Test
	public void aNestedConduCommitsWithoutLeakingTheFallback() {
		Unifiable<String> x = lvar();
		Goal g = Goal.condu(
				Goal.condu(unify(x, lval("keep")), unify(x, lval("inner-fallback"))),
				unify(x, lval("outer-fallback")));

		List<String> got = g.solve(x, TestSchedulers.factory()).map(Reified::toString).collect(Collectors.toList());

		assertThat(got).containsExactly("{keep}");
	}

	@Test
	public void aConduClauseGuardedByProjectionCommits() {
		// filter's clause shape: the guard runs through Logic.project
		Unifiable<Integer> a = lvar();
		Unifiable<String> x = lvar();
		Goal g = unify(a, lval(2)).and(Goal.condu(
				Goal.defer(() -> Logic.project(a, v -> v != 1 ? Goal.success() : Goal.failure())
						.and(unify(x, lval("keep")))),
				unify(x, lval("skip"))));

		List<String> got = g.solve(x, TestSchedulers.factory()).map(Reified::toString).collect(Collectors.toList());

		assertThat(got).containsExactly("{keep}");
	}

	@Test
	public void filterCommitsPerElement() {
		// SortingTest.filter, shrunk: keep elements != 1 of [1, 2]
		com.tgac.logic.unification.Unifiable<com.tgac.logic.unification.LList<Integer>> out =
				lvar();
		List<String> got = filter(com.tgac.logic.unification.LList.ofAll(1, 2, 1, 3, 1, 4), out,
				a -> Logic.project(a, v -> v != 1 ? Goal.success() : Goal.failure()))
				.solve(out, TestSchedulers.factory()).map(Reified::toString).collect(Collectors.toList());

		assertThat(got).hasSize(1);
	}

	private static <A> Goal filter(
			com.tgac.logic.unification.Unifiable<com.tgac.logic.unification.LList<A>> with,
			com.tgac.logic.unification.Unifiable<com.tgac.logic.unification.LList<A>> without,
			java.util.function.Function<Unifiable<A>, Goal> pred) {
		return com.tgac.logic.goals.Matche.matche(with,
				com.tgac.logic.goals.Matche.llist(() -> without.unifies(com.tgac.logic.unification.LList.empty())),
				com.tgac.logic.goals.Matche.llist((a, d) -> Goal.condu(
						Goal.defer(() -> pred.apply(a)
								.and(com.tgac.logic.goals.Matche.matche(without,
										com.tgac.logic.goals.Matche.llist((b, e) -> b.unifiesNc(a)
												.and(Goal.defer(() -> filter(d, e, pred))))))),
						Goal.defer(() -> filter(d, without, pred)))));
	}

	@Test
	public void aConduInsideARecursionCommitsPerLevel() {
		assertThat(countdown(3).size()).isEqualTo(1);
	}

	private static List<String> countdown(int n) {
		Unifiable<String> out = lvar();
		return level(n, out).solve(out, TestSchedulers.factory()).map(Reified::toString).collect(Collectors.toList());
	}

	/** level(n): condu(succeed with "hit-n" and recurse; fallback). */
	private static Goal level(int n, Unifiable<String> out) {
		if (n == 0) {
			return unify(out, lval("bottom"));
		}
		return Goal.condu(
				Goal.defer(() -> level(n - 1, out)),
				unify(out, lval("fallback-" + n)));
	}
}
