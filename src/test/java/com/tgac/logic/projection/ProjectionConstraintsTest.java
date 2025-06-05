package com.tgac.logic.projection;

import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ProjectionConstraintsTest {

	@Test
	public void shouldJustProjectImmediately(){
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		List<Integer> results = x.unifies(3)
				.and(ProjectionConstraints.project(x, v -> y.unifies(2 * v)))
				.solve(y)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		Assertions.assertThat(results)
				.containsExactly(6);
	}

	@Test
	public void shouldProjectDeferred(){
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		List<Integer> results =
				ProjectionConstraints.project(x, v -> y.unifies(2 * v))
						.and(x.unifies(3))
				.solve(y)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		Assertions.assertThat(results)
				.containsExactly(6);
	}

	@Test
	public void shouldProjectDeferredWithOtherVariable(){
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();

		List<Integer> results =
				ProjectionConstraints.project(y, v -> z.unifies(2 * v))
						.and(x.unifies(y))
						.and(y.unifies(3))
				.solve(z)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		Assertions.assertThat(results)
				.containsExactly(6);
	}

}