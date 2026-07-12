package com.tgac.logic.goals;

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(access = AccessLevel.MODULE)
public class Condu implements Goal {
	List<Goal> clauses = new ArrayList<>();

	@Override
	public Condu orElse(Goal... goals) {
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
			List<Package> results = new ArrayList<>();
			return clauses.stream()
					.reduce(Fiber.done(nothing()),
							(acc, g) -> acc.flatMap(_0 ->
									g.apply(s).run(s1 -> {
										results.add(s1);
										return nothing();
									}).flatMap(_1 -> {
										if (committed.get() || results.isEmpty()) {
											return done(nothing());
										}
										committed.set(true);
										return results.stream()
												.map(exit::<Package>with)
												.map(c -> c.runRec(k))
												.reduce(done(nothing()),
														(l, r) -> l.flatMap(_2 -> r));
									})),
							Exceptions.throwingBiOp(UnsupportedOperationException::new));
		}));
	}

	@Override
	public String toString() {
		return "(" + clauses.stream()
				.map(Objects::toString)
				.collect(Collectors.joining(" orElse ")) + ")";
	}
}
