package com.tgac.logic.goals;

// ABOUTME: The goal algebra as semiring witnesses — or is ⊕, and is ⊗, failure
// ABOUTME: is 0, success is 1 — each at the answer quotient its laws hold up to.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semiring;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Goal equality is semantic — same answers when solved — so each witness names
 * the quotient its laws are checked up to, and the quotient decides the
 * capabilities. These are the licenses the optimizer's algebra pass and the
 * table-cell folds rewrite on: distributivity here is the statement that
 * restructuring the search tree cannot change what a query computes.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GoalSemirings {

	/**
	 * Goals up to answer MULTISET equality: every derivation contributes its
	 * answer once. The quotient counting rides — a rewrite lawful here
	 * preserves how many ways each answer arises. Not idempotent:
	 * {@code g.or(g)} doubles every derivation.
	 */
	public static final Semiring<Goal> DERIVATIONS = new DerivationOps();

	/**
	 * Goals up to answer SET equality: duplicate derivations collapse, so ⊕
	 * is idempotent — the dedup license. Boolean-style queries live here;
	 * counts do not survive this quotient.
	 */
	public static final IdempotentSemiring<Goal> ANSWERS = new AnswerOps();

	private static class DerivationOps implements Semiring<Goal> {
		@Override
		public Goal zero() {
			return Goal.failure();
		}

		@Override
		public Goal one() {
			return Goal.success();
		}

		// fresh nodes, not a.or(b)/a.and(b): the instance combinators are
		// accretive on Conde/Conjunction receivers, so dispatching through
		// them would mutate the operands
		@Override
		public Goal plus(Goal a, Goal b) {
			return Conde.of(Arrays.asList(a, b));
		}

		@Override
		public Goal times(Goal a, Goal b) {
			return Conjunction.of(a, b);
		}
	}

	private static final class AnswerOps extends DerivationOps implements IdempotentSemiring<Goal> {
	}
}
