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
		return new Conjunction().and(goals);
	}

	public Conjunction and(Goal... goals) {
		clauses.addAll(Arrays.asList(goals));
		return this;
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
