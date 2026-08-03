package com.tgac.logic.goals;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.optimizer.Optimizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(access = AccessLevel.MODULE)
public class Conde implements Goal {
	List<Goal> clauses = new ArrayList<>();

	/** One alternative per argument — never conde clause-conjunction syntax. */
	public static Conde of(Iterable<Goal> alternatives) {
		Conde conde = new Conde();
		alternatives.forEach(conde.clauses::add);
		return conde;
	}

	@Override
	public Conde or(Goal... goals) {
		if (goals.length == 0) {
			return this;
		}
		Conde next = new Conde();
		next.clauses.addAll(clauses);
		next.clauses.add(goals.length == 1 ?
				goals[0] :
				Conjunction.of(goals));
		return next;
	}

	@Override
	public Cont<Package, Nothing> apply(Package s) {
		return k -> Fiber.fork(
				clauses.stream()
						.map(g -> g.apply(s).apply(k))
						.collect(Collectors.toList()));
	}

	@Override
	public Fiber<Goal> accept(Optimizer optimizer) {
		return optimizer.visit(this);
	}

	@Override
	public String toString() {
		return "(" + clauses.stream()
				.map(Objects::toString)
				.collect(Collectors.joining(" || ")) + ")";
	}
}
