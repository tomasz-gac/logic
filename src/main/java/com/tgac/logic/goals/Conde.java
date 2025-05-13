package com.tgac.logic.goals;

import static com.tgac.functional.recursion.Recur.done;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.recursion.Recur;
import com.tgac.logic.unification.Package;
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

	@Override
	public Conde or(Goal... goals) {
		if (goals.length == 0) {
			return this;
		} else if (goals.length == 1) {
			clauses.add(goals[0]);
			return this;
		} else {
			clauses.add(new Conde().and(goals));
			return this;
		}
	}

	@Override
	public Cont<com.tgac.logic.unification.Package, Nothing> apply(Package s) {
		return k -> Recur.forEach(
				clauses.stream()
						.map(g -> g.apply(s).apply(k))
						.collect(Collectors.toList()),
				_0 -> {
				});
	}

	@Override
	public Recur<Goal> optimize() {
		return clauses.stream()
				.map(Goal::optimize)
				.map(v -> v.map(g ->
						g instanceof Conde ?
								((Conde) g).clauses.stream() :
								java.util.stream.Stream.of(g)))
				.reduce(done(new Conde()),
						(l, r) -> Recur.zip(l, r).map(t -> t._1
								.or(t._2.toArray(Goal[]::new))),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	@Override
	public String toString() {
		return "(" + clauses.stream()
				.map(Objects::toString)
				.collect(Collectors.joining(" || ")) + ")";
	}
}
