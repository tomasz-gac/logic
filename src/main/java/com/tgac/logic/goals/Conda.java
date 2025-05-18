package com.tgac.logic.goals;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(access = AccessLevel.MODULE)
public class Conda implements Goal {
	List<Goal> clauses = new ArrayList<>();

	@Override
	public Conda orElseFirst(Goal... goals) {
		if (goals.length == 0) {
			return this;
		} else if (goals.length == 1) {
			clauses.add(goals[0]);
			return this;
		} else {
			clauses.add(new Conjunction().and(goals));
			return this;
		}
	}

	@Override
	public Cont<Package, Nothing> apply(Package s) {
		return Cont.callCC(exit -> Cont.suspend(k -> {
			AtomicBoolean committed = new AtomicBoolean(false);
			return clauses.stream()
					.reduce(
							Recur.<Nothing> done(Nothing.nothing()),
							(acc, g) -> acc.flatMap(_0 -> {
								Recur<Nothing> collected = g.apply(s).runRec(s1 -> {
									if (committed.compareAndSet(false, true)) {
										return exit.<Package> with(s1).runRec(k);
									}
									return Recur.done(Nothing.nothing()); // ignore subsequent solutions
								});
								return collected.map(_1 -> Nothing.nothing()); // don’t emit past this point
							}),
							Exceptions.throwingBiOp(UnsupportedOperationException::new)
					);
		}));
	}

	@Override
	public Recur<Goal> optimize() {
		return clauses.stream()
				.map(Goal::optimize)
				.map(v -> v.map(g ->
						g instanceof Conda ?
								((Conda) g).clauses.stream() :
								java.util.stream.Stream.of(g)))
				.reduce(done(new Conda()),
						(l, r) -> Recur.zip(l, r).map(t -> t._1
								.or(t._2.toArray(Goal[]::new))),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	@Override
	public String toString() {
		return "(" + clauses.stream()
				.map(Objects::toString)
				.collect(Collectors.joining(" orElseFirst ")) + ")";
	}
}
