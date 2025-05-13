package com.tgac.logic.goals;

import static com.tgac.functional.monad.Cont.suspend;
import static com.tgac.functional.recursion.Recur.done;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.recursion.Recur;
import com.tgac.logic.unification.Package;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(access = AccessLevel.MODULE)
public class Conjunction implements Goal {
	List<Goal> clauses = new ArrayList<>();

	public com.tgac.logic.goals.Conjunction and(Goal... goals) {
		clauses.addAll(Arrays.asList(goals));
		return this;
	}

	@Override
	public Recur<Goal> optimize() {
		return clauses.stream()
				.map(Goal::optimize)
				.map(v -> v.map(g -> g instanceof com.tgac.logic.goals.Conjunction ?
						((com.tgac.logic.goals.Conjunction) g).clauses.stream() :
						java.util.stream.Stream.of(g)))
				.reduce((acc, r) -> Recur.zip(acc, r)
						.map(lr -> lr.apply(java.util.stream.Stream::concat)))
				.map(r -> r.map(s -> s.toArray(Goal[]::new))
						.map(new com.tgac.logic.goals.Conjunction()::and)
						.map(Goal.class::cast))
				.orElseGet(() -> done(Goal.success()));
	}

	@Override
	public Cont<com.tgac.logic.unification.Package, Nothing> apply(Package s) {
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
