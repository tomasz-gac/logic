package com.tgac.logic.unification;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.goals.Package;
import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import org.junit.Test;

public class HoleTest {

	@Test
	public void shouldEqualByName() {
		assertThat(Hole.of(0))
				.isEqualTo(Hole.of(0))
				.isNotEqualTo(Hole.of(1));

		assertThat(Hole.of(0).hashCode())
				.isEqualTo(Hole.of(0).hashCode());
	}

	@Test
	public void shouldNeverEqualAnLVar() {
		// same name string, different world — never equal
		assertThat(Hole.of(0))
				.isNotEqualTo(lvar("_.0"));
	}

	@Test
	public void shouldDisplayLikeAVariable() {
		assertThat(Hole.of(0).toString())
				.isEqualTo("_.0");
	}

	@Test
	public void shouldBeNeitherVarNorVal() {
		Hole<Integer> hole = Hole.of(0);
		assertThat(hole.asVar().isDefined()).isFalse();
		assertThat(hole.asVal().isDefined()).isFalse();
		assertThat(hole.asReified().isDefined()).isTrue();
	}

	@Test
	public void shouldWalkToItself() {
		Hole<Integer> hole = Hole.of(0);
		assertThat(Package.empty().walk(hole)).isSameAs(hole);
	}

	@Test
	public void shouldTerminateWalkAtHole() {
		// a var bound to a reified var resolves to it and stops
		Unifiable<Integer> x = lvar();
		Hole<Integer> hole = Hole.of(0);
		Package s = Package.empty()
				.withSubstitutions(Substitutions.of(HashMap.of(x.getVar(), hole)));

		assertThat(s.walk(x)).isSameAs(hole);
	}

	@Test
	public void shouldRejectHoleInUnification() {
		Unifiable<Integer> x = lvar();
		Hole<Integer> hole = Hole.of(0);

		assertThatThrownBy(() -> MiniKanren.unify(Substitutions.empty(), x, hole).get())
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void shouldRejectHoleSmuggledInsideStructure() {
		Unifiable<Object> x = lvar();
		Unifiable<Object> smuggled = lval(Tuple.of(Hole.of(0), 1));

		assertThatThrownBy(() -> MiniKanren.unify(Substitutions.empty(), x, smuggled)
				.flatMap(s -> MiniKanren.unify(s, x, lval(Tuple.of(lvar(), 1))))
				.get())
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void shouldMakeLValAMemberOfBothWorlds() {
		assertThat(lval(42))
				.isInstanceOf(Unifiable.class)
				.isInstanceOf(Reified.class);
	}
}
