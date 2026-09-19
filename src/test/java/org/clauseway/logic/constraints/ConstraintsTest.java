package org.clauseway.logic.constraints;

import static org.clauseway.logic.unification.LVal.lval;

import org.clauseway.functional.category.Nothing;
import org.clauseway.functional.fibers.Fiber;
import org.clauseway.functional.monad.Cont;
import org.clauseway.logic.Utils;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.LVar;
import org.clauseway.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Test;

@SuppressWarnings("ALL")
public class ConstraintsTest {

	@Test
	public void shouldUnify() {
		Unifiable<Integer> u = LVar.lvar();
		Unifiable<Integer> v = lval(1);
		Cont<Package, Nothing> s = Constraints.unify(u, v)
				.apply(Package.empty());
		List<Integer> map = Utils.collect(s
				.map(p -> Fiber.done(p.walk(v))
						.map(v1 -> Utils.collect(Constraints.
										reify(p, v1))
								.stream()
								.collect(Collectors.toList()).get(0).get())
						.ground()));
		Assertions.assertThat(map)
				.containsExactly(1);
	}
}
