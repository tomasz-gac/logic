package com.tgac.logic.goals;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
							Fiber.<Nothing> done(Nothing.nothing()),
							(acc, g) -> acc.flatMap(_0 -> {
								// DELIVERIES CROSS THE DELIMITER: collect the committed
								// solution inside the planted exploration, hand it to the
								// continuation only after the seal - running k inside
								// would bill downstream work to the clause's workforce
								AtomicReference<Package> won = new AtomicReference<>();
								Fiber<Nothing> collected = Exhaustion.exhausted(g.apply(s).runRec(s1 -> {
									if (committed.compareAndSet(false, true)) {
										won.set(s1);
									}
									return Fiber.done(Nothing.nothing()); // ignore subsequent solutions
								}));
								return collected.flatMap(_1 -> won.get() != null
										? exit.<Package> with(won.get()).runRec(k)
										: Fiber.done(Nothing.nothing()));
							}),
							Exceptions.throwingBiOp(UnsupportedOperationException::new)
					);
		}));
	}

	@Override
	public String toString() {
		return "(" + clauses.stream()
				.map(Objects::toString)
				.collect(Collectors.joining(" orElseFirst ")) + ")";
	}
}
