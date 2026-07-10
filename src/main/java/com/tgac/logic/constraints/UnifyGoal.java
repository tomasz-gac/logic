package com.tgac.logic.constraints;

// ABOUTME: Constraint-aware unification as a data goal: order 1 — it can only
// ABOUTME: prune or pass, never branch — so ordering passes sort it first.

import static com.tgac.functional.category.Nothing.nothing;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Unifiable;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class UnifyGoal<T> implements Goal, Bounded {
	Unifiable<T> u;
	Unifiable<T> v;

	@Override
	public Cont<Package, Nothing> apply(Package s) {
		return Cont.defer(() -> MiniKanren.unifyPrefix(s.substitution(), u, v)
				.map(prefix -> Propagation.resolve(prefix).apply(s))
				.getOrElse(() -> Cont.complete(nothing())));
	}

	@Override
	public long answers(Substitutions s) {
		return 1;
	}
}
