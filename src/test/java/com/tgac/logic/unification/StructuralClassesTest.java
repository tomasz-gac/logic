package com.tgac.logic.unification;

// ABOUTME: Pins the coarse structural equivalence classes of unification — the
// ABOUTME: behavior decompose (migration step C) must preserve exactly.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import org.junit.Test;

public class StructuralClassesTest {

	private static <T> boolean unifies(Term<T> l, Term<T> r) {
		return MiniKanren.unify(Substitutions.empty(), l, r).get().isDefined();
	}

	@Test
	public void anyIterableUnifiesWithAnyIterable() {
		// the ITERABLE class is container-agnostic: only elements matter
		Unifiable<Integer> x = lvar();
		Substitutions s = MiniKanren.unify(Substitutions.empty(),
						lval(List.of(lval(1), x)).getObjectUnifiable(),
						lval(Vector.of(lval(1), lval(2))).getObjectUnifiable())
				.get().get();
		assertThat(s.walk(x)).isEqualTo(lval(2));
	}

	@Test
	public void tupleDoesNotUnifyWithListOfSameArity() {
		assertThat(unifies(
				lval(Tuple.of(1, 2)).getObjectUnifiable(),
				lval(List.of(1, 2)).getObjectUnifiable()))
				.isFalse();
	}

	@Test
	public void tuplesOfDifferentArityDoNotUnify() {
		assertThat(unifies(
				lval(Tuple.of(1, 2)).getObjectUnifiable(),
				lval(Tuple.of(1, 2, 3)).getObjectUnifiable()))
				.isFalse();
	}

	@Test
	public void iterablesOfDifferentLengthDoNotUnify() {
		assertThat(unifies(
				lval(List.of(1, 2, 3)).getObjectUnifiable(),
				lval(List.of(1, 2)).getObjectUnifiable()))
				.isFalse();
	}

	@Test
	public void emptyIterablesAreEqualAtoms() {
		assertThat(unifies(
				lval(List.empty()).getObjectUnifiable(),
				lval(List.empty()).getObjectUnifiable()))
				.isTrue();
	}

	@Test
	public void emptyIterableDoesNotUnifyWithNonEmpty() {
		assertThat(unifies(
				lval(List.of(1)).getObjectUnifiable(),
				lval(List.empty()).getObjectUnifiable()))
				.isFalse();
		assertThat(unifies(
				lval(List.empty()).getObjectUnifiable(),
				lval(List.of(1)).getObjectUnifiable()))
				.isFalse();
	}

	@Test
	public void emptyLListIsAnEqualityAtom() {
		assertThat(unifies(
				LList.<Integer> empty().getObjectUnifiable(),
				LList.<Integer> empty().getObjectUnifiable()))
				.isTrue();
		assertThat(unifies(
				LList.of(lval(1)).getObjectUnifiable(),
				LList.<Integer> empty().getObjectUnifiable()))
				.isFalse();
		assertThat(unifies(
				LList.<Integer> empty().getObjectUnifiable(),
				LList.of(lval(1)).getObjectUnifiable()))
				.isFalse();
	}

	@Test
	public void llistDoesNotUnifyWithIterable() {
		assertThat(unifies(
				LList.of(lval(1)).getObjectUnifiable(),
				lval(List.of(1)).getObjectUnifiable()))
				.isFalse();
	}
}
