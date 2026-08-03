package com.tgac.logic.goals;

import static com.tgac.functional.monad.Cont.suspend;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.optimizer.Optimizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(access = AccessLevel.MODULE)
public class Conjunction implements Goal {
	List<Goal> clauses = new ArrayList<>();

	public static Conjunction of(Goal... goals) {
		Conjunction conj = new Conjunction();
		conj.clauses.addAll(Arrays.asList(goals));
		return conj;
	}

	public static Conjunction of(Iterable<Goal> goals) {
		Conjunction conj = new Conjunction();
		goals.forEach(conj.clauses::add);
		return conj;
	}

	public Conjunction and(Goal... goals) {
		if (goals.length == 0) {
			return this;
		}
		Conjunction next = new Conjunction();
		next.clauses.addAll(clauses);
		next.clauses.addAll(Arrays.asList(goals));
		return next;
	}

	@Override
	public Fiber<Goal> accept(Optimizer optimizer) {
		return optimizer.visit(this);
	}

	@Override
	public Cont<Package, Nothing> apply(Package s) {
		return clauses.stream()
				.reduce(suspend(k -> k.apply(s)),
						Cont::flatMap,
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	@Override
	public String toString() {
		return "(" + clauses.stream().map(Objects::toString).collect(Collectors.joining(" && ")) + ")";
	}
}
