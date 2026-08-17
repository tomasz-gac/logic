package com.tgac.logic.unification;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.goals.Package;
import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import org.junit.Test;

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
		assertThat(any.asVar().isDefined()).isFalse();
		assertThat(any.asVal().isDefined()).isFalse();
		assertThat(any.asReified().isDefined()).isTrue();
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
				.withSubstitutions(Substitutions.of(HashMap.of(x.getVar(), any)));

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
