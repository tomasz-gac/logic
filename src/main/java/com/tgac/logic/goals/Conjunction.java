package com.tgac.logic.goals;

import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.functional.monad.Cont.suspend;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.unification.Package;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(access = AccessLevel.MODULE)
public class Conjunction implements Goal {
	List<Goal> clauses = new ArrayList<>();

	public Conjunction and(Goal... goals) {
		clauses.addAll(Arrays.asList(goals));
		return this;
	}

	@Override
	public Fiber<Goal> optimize() {
		return clauses.stream()
				.map(Goal::optimize)
				.map(v -> v.map(g -> g instanceof Conjunction ?
						((Conjunction) g).clauses.stream() :
						Stream.of(g)))
				.reduce((acc, r) -> Fiber.zip(acc, r)
						.map(lr -> lr.apply(Stream::concat)))
				.map(r -> r.map(s -> s.toArray(Goal[]::new))
						.map(new Conjunction()::and)
						.map(Goal.class::cast))
				.orElseGet(() -> done(Goal.success()));
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
