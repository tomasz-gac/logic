package com.tgac.logic.unification;

// ABOUTME: Pins the coarse structural equivalence classes of unification — the
// ABOUTME: behavior decompose (migration step C) must preserve exactly.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.util.Arrays;
import java.util.Iterator;
import io.vavr.collection.Vector;
import org.junit.Test;

public class StructuralClassesTest {

	private static <T> boolean unifies(Term<T> l, Term<T> r) {
		return MiniKanren.unify(Substitutions.empty(), l, r).get().isDefined();
	}

	@Test
	public void anIterableWithoutARebuildRecipeIsAnAtom() {
		// vavr's Value hierarchy makes Either iterable, but no collector is
		// registered for it: structurality is ONE gate - a value decomposes
		// iff its class can also be rebuilt. Unregistered iterables unify as
		// atomic values, by equals
		assertThat(unifies(
				lval(Either.right(1)).getObjectUnifiable(),
				lval(List.of(1)).getObjectUnifiable()))
				.isFalse();
		assertThat(unifies(
				lval(Either.right(1)).getObjectUnifiable(),
				lval(Either.right(1)).getObjectUnifiable()))
				.isTrue();
		assertThat(unifies(
				lval(Either.right(1)).getObjectUnifiable(),
				lval(Either.right(2)).getObjectUnifiable()))
				.isFalse();
	}

	@Test
	public void aUserIterableIsAnAtomNotAStructure() {
		// implementing Iterable must not opt a domain type into element-wise
		// unification - structure is granted by the registry, never inherited
		final class Pair implements Iterable<Object> {
			final Object a, b;

			Pair(Object a, Object b) {
				this.a = a;
				this.b = b;
			}

			@Override
			public Iterator<Object> iterator() {
				return Arrays.asList(a, b).iterator();
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof Pair && a.equals(((Pair) o).a) && b.equals(((Pair) o).b);
			}

			@Override
			public int hashCode() {
				return a.hashCode() * 31 + b.hashCode();
			}
		}
		assertThat(unifies(
				lval(new Pair(1, 2)).getObjectUnifiable(),
				lval(List.of(1, 2)).getObjectUnifiable()))
				.isFalse();
		assertThat(unifies(
				lval(new Pair(1, 2)).getObjectUnifiable(),
				lval(new Pair(1, 2)).getObjectUnifiable()))
				.isTrue();
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
	public void sameShapeTreesUnifyThroughTheLeaves() {
		// LTree decomposes one level at a time — (value, children) — so a leaf
		// variable is reached through recursion, never through a flat member zip
		Unifiable<Integer> x = lvar();
		Substitutions s = MiniKanren.unify(Substitutions.empty(),
						LTree.ofAll(1, LTree.ofAll(2).get()).getObjectUnifiable(),
						LTree.of(lval(1), LList.ofAll(LTree.of(x).get())).getObjectUnifiable())
				.get().get();
		assertThat(s.walk(x)).isEqualTo(lval(2));
	}

	@Test
	public void differentShapeTreesDoNotUnify() {
		// branching factors differ: the children LLists disagree cons-vs-empty
		assertThat(unifies(
				LTree.ofAll(1, LTree.ofAll(2).get(), LTree.ofAll(3).get()).getObjectUnifiable(),
				LTree.ofAll(1, LTree.ofAll(2).get()).getObjectUnifiable()))
				.isFalse();
		assertThat(unifies(
				LTree.ofAll(1, LTree.ofAll(2).get()).getObjectUnifiable(),
				LTree.ofAll(1).getObjectUnifiable()))
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
