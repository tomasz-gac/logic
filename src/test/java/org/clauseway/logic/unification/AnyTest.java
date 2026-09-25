package org.clauseway.logic.unification;

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.clauseway.logic.goals.Package;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.vavr.collection.HashMap;
import org.clauseway.logic.unification.terms.Any;
import org.clauseway.logic.unification.terms.Reified;
import org.clauseway.logic.unification.terms.Unifiable;
import org.junit.Test;
import java.util.Collections;

public class AnyTest {

	@Test
	public void shouldEqualByName() {
		assertThat(Any.of(0))
				.isEqualTo(Any.of(0))
				.isNotEqualTo(Any.of(1));

		assertThat(Any.of(0).hashCode())
				.isEqualTo(Any.of(0).hashCode());
	}

	@Test
	public void shouldNeverEqualAnLVar() {
		// same name string, different world — never equal
		assertThat(Any.of(0))
				.isNotEqualTo(lvar("_.0"));
	}

	@Test
	public void shouldDisplayLikeAVariable() {
		assertThat(Any.of(0).toString())
				.isEqualTo("_.0");
	}

	@Test
	public void shouldBeNeitherVarNorVal() {
		Any<Integer> any = Any.of(0);
		assertThat(any.asVar().isPresent()).isFalse();
		assertThat(any.isVal()).isFalse();
		assertThat(any.asReified().isPresent()).isTrue();
	}

	@Test
	public void shouldWalkToItself() {
		Any<Integer> any = Any.of(0);
		assertThat(Package.empty().walk(any)).isSameAs(any);
	}

	@Test
	public void shouldTerminateWalkAtHole() {
		// a var bound to a reified var resolves to it and stops
		Unifiable<Integer> x = lvar();
		Any<Integer> any = Any.of(0);
		Package s = Package.empty()
				.withSubstitutions(Substitutions.of(Collections.singletonMap(x.getVar(), any)));

		assertThat(s.walk(x)).isSameAs(any);
	}

	@Test
	public void shouldRejectHoleInUnification() {
		Unifiable<Integer> x = lvar();
		Any<Integer> any = Any.of(0);

		assertThatThrownBy(() -> MiniKanren.unify(Substitutions.empty(), x, any).ground())
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void shouldRejectHoleSmuggledInsideStructure() {
		Unifiable<Object> x = lvar();
		Unifiable<Object> smuggled = lval(Tuple.of(Any.of(0), 1));

		assertThatThrownBy(() -> MiniKanren.unify(Substitutions.empty(), x, smuggled)
				.flatMap(s -> MiniKanren.unify(s, x, lval(Tuple.of(lvar(), 1))))
				.ground())
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void shouldMakeLValAMemberOfBothWorlds() {
		assertThat(lval(42))
				.isInstanceOf(Unifiable.class)
				.isInstanceOf(Reified.class);
	}
}
