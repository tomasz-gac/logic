package org.clauseway.logic.unification;

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.LogicTest.runStream;
import static org.clauseway.logic.constraints.Constraints.unify;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.fibers.Fiber;
import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import org.clauseway.logic.Utils;
import org.clauseway.logic.goals.Package;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple2;
import org.clauseway.functional.tuples.Tuple3;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.unification.structures.LTree;
import org.clauseway.logic.unification.terms.LVal;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.terms.Reified;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.junit.Test;

/**
 * @author TGa
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class MiniKanrenTest {

	@Test
	public void shouldRefuseCyclicUnification() {
		// x = (1 . x) — the occurs check must fail the unification, not admit
		// a cyclic substitution that no walk can ever ground
		Unifiable<LList<Integer>> x = lvar();
		assertThat(runStream(x, x.unifies(LList.of(lval(1), x))).count())
				.isZero();
	}

	@Test
	public void shouldRefuseCycleClosedThroughLaterBinding() {
		// the cycle closes on the SECOND binding: z walks to fresh, x walks to
		// (1 . z) — occurs must look through the substitution, not just at it
		Unifiable<LList<Integer>> x = lvar();
		Unifiable<LList<Integer>> z = lvar();
		assertThat(runStream(x,
				x.unifies(LList.of(lval(1), z)),
				z.unifies(x))
				.count())
				.isZero();
	}

	@Test
	public void shouldFindX() {
		Unifiable<Integer> x = lvar();
		val subs = MiniKanren.unify(Substitutions.empty(), x, lval(3)).ground().get();
		Optional<Integer> y = extractValue(x, subs);
		assertThat(y)
				.hasValue(3);
	}

	@Test
	public void shouldFindXWhenNotGround() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		val subs = MiniKanren.unify(Substitutions.empty(), x, y).ground().get();
		Term<Integer> z = subs.walk(x);
		assertThat(z)
				.isEqualTo(y);
	}

	@Test
	public void shouldFindZAfterSubstitution() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> z = lvar();
		val subs = MiniKanren.unify(Substitutions.empty(), x, lval(3)).ground().get();
		val s2 = MiniKanren.unify(subs, z, x).ground().get();
		Optional<Integer> y = extractValue(z, s2);
		assertThat(y)
				.hasValue(3);
	}

	@Test
	public void shouldNotExtendFibersion() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Substitutions subst = MiniKanren.unify(Substitutions.empty(), x, y).ground().get();
		assertThat(MiniKanren.unify(subst, y, x).ground().get())
				.isEqualTo(subst);
	}

	@Test
	public void shouldFindCircularity() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Unifiable<Integer> q = lvar();
		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, y).ground().get();
		s = MiniKanren.unify(s, y, z).ground().get();
		s = MiniKanren.unify(s, z, q).ground().get();
		Substitutions seq = MiniKanren.unify(s, q, x).ground().get();
		assertThat(seq)
				.isEqualTo(s);
	}

	@Test
	public void shouldNotFindCircularity() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Unifiable<Integer> q = lvar();
		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, y, z).ground().get();
		s = MiniKanren.unify(s, z, q).ground().get();
		val t = s;

		// does not trow
		MiniKanren.unify(t, q, x).ground();
	}

	@Test
	public void shouldSubstituteTwice() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();

		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, z, x).ground().get();
		s = MiniKanren.unify(s, y, x).ground().get();
		s = MiniKanren.unify(s, x, lval(3)).ground().get();
		assertThat(extractValue(z, s).get())
				.isEqualTo(3);
		assertThat(extractValue(y, s).get())
				.isEqualTo(3);
	}

	@Test
	public void shouldUnify() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, y).ground().get();
		s = MiniKanren.unify(s, x, z).ground().get();
		s = MiniKanren.unify(s, y, lval(3)).ground().get();
		Assertions.assertThat(s.binding(z.asVar().get()).get())
				.isEqualTo(3);
	}

	@Test
	public void shouldNotUnifyCycle() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, y).ground().get();
		s = MiniKanren.unify(s, x, z).ground().get();
		Substitutions t = MiniKanren.unify(s, y, z).ground().get();
		assertThat(t)
				.isEqualTo(s);
	}

	@Test
	public void shouldNotUnifyInvalidValues() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, y).ground().get();
		s = MiniKanren.unify(s, x, z).ground().get();
		s = MiniKanren.unify(s, y, lval(3)).ground().get();
		assertThat(MiniKanren.unify(s, z, lval(4)).ground().toJavaOptional()).isEmpty();
	}

	@Test
	public void shouldUnifyLists() {
		val xs = IntStream.range(0, 10)
				.mapToObj(i -> LVar.<Integer> lvar())
				.collect(List.collector());

		val ys = IntStream.range(0, 10)
				.boxed()
				.collect(List.collector());

		Substitutions s = MiniKanren.unify(Substitutions.empty(),
				lval(Tuple.ofAll(xs.toJavaArray())),
				lval(Tuple.ofAll(ys.map(LVal::lval).toJavaArray()))).ground().get();

		assertThat(xs.toStream()
				.map(x -> s.walk(x))
				.flatMap(v -> v.asVal().toList())
				.collect(List.collector()))
				.isEqualTo(ys);
	}

	@Test
	public void shouldUnifyVarWithWideTuple() {
		Unifiable<Tuple> x = lvar();
		Unifiable<Tuple> y = lvar();
		int n = 1_000_000;
		Object[] vals = IntStream.range(0, n)
				.boxed()
				.map(LVal::lval)
				.toArray();

		Object[] vs = IntStream.range(0, n)
				.boxed()
				.map(i -> LVar.<Integer> lvar("_." + i))
				.toArray();
		// unifying a variable with a million-wide tuple must not blow the stack
		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, y).ground().get();
		s = MiniKanren.unify(s, y, lval(Tuple.ofAll(vals))).ground().get();
		s = MiniKanren.unify(s, y, lval(Tuple.ofAll(vs))).ground().get();

		Tuple unifiables = MiniKanren.walkAll(s, x).ground().get();
		assertThat(unifiables.arity()).isEqualTo(n);
	}

	@Test
	public void shouldUnifyTuples() {
		Unifiable<Tuple3<Integer, Unifiable<String>, Unifiable<Boolean>>> x = lvar();
		Tuple3<Integer, Unifiable<String>, Unifiable<Boolean>> t1 = Tuple.of(
				3,
				lvar("name"),
				lval(false));

		Tuple3<Integer, Unifiable<String>, Unifiable<Boolean>> t2 = Tuple.of(
				3,
				lval("Anthony"),
				lvar("female"));

		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, lval(t1)).ground().get();
		s = MiniKanren.unify(s, lval(t1), lval(t2)).ground().get();

		Tuple3<Integer, Unifiable<String>, Unifiable<Boolean>> x1 =
				s.walk(x).get();
		assertThat(x1._1)
				.isEqualTo(3);
		assertThat(x1)
				.isEqualTo(t1);
		assertThat(MiniKanren.walkAll(s, t1._2).ground())
				.isEqualTo(lval("Anthony"));
		assertThat(s.walk(t2._3).get())
				.isEqualTo(false);
	}

	@Test
	public void shouldTreatMapsAsAtoms() {
		// a map is a value, not structure: equal maps unify by equals, and a
		// variable inside a map is invisible to unification
		Map<String, Integer> m1 = HashMap.of("v1", 1, "v2", 2);
		Map<String, Integer> m2 = HashMap.of("v1", 1, "v2", 2);
		assertThat(MiniKanren.unify(Substitutions.empty(), lval(m1), lval(m2))
				.ground().isDefined()).isTrue();

		Map<String, Unifiable<Integer>> withVar = HashMap.of("v1", lvar("v1"));
		Map<String, Unifiable<Integer>> withVal = HashMap.of("v1", lval(1));
		assertThat(MiniKanren.unify(Substitutions.empty(), lval(withVar), lval(withVal))
				.ground().isDefined()).isFalse();
	}

	Unifiable<Object> buildUni(int i, int delta) {
		if (i % 2 == delta) {
			return lval(Tuple.ofAll(IntStream.range(10, 20)
					.boxed()
					.map(LVal::lval)
					.toArray()));
		} else if (i % 2 == 1 + delta) {
			return lvar("_." + i);
		} else {
			return lval(Tuple.ofAll(IntStream.range(10, 20)
					.boxed()
					.map(j -> LVar.<Integer> lvar("_." + i + j))
					.toArray()));
		}
	}

	@Test
	public void shouldUnifyCompoundTypes() {
		Object[] ints = IntStream.range(0, 60)
				.boxed()
				.map(i -> buildUni(i, 0))
				.toArray();

		Object[] ints2 = IntStream.range(0, 60)
				.boxed()
				.map(i -> buildUni(i, 1))
				.toArray();

		Substitutions s = MiniKanren.unify(Substitutions.empty(),
				lval(Tuple.ofAll(ints)), lval(Tuple.ofAll(ints2))).ground().get();
		Tuple walked = (Tuple) s.walk(lval(Tuple.ofAll(ints))).get();
		assertThat(((Unifiable<?>)
				((Tuple) ((Unifiable<?>) walked.get(3)).get()).get(4)).get())
				.isEqualTo(13);
		assertThat(((Unifiable<?>) walked.get(2)).asVar().toJavaOptional()
				.map(LVar::getName))
				.hasValue("_.1");
	}

	@Test
	public void shouldUnifyCompoundTypes2() {
		Unifiable<Tuple> x = lvar();

		Object[] ints = IntStream.range(0, 60)
				.boxed()
				.map(i -> buildUni(i, 0))
				.toArray();

		Object[] ints2 = IntStream.range(0, 60)
				.boxed()
				.map(i -> buildUni(i, 1))
				.toArray();

		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, lval(Tuple.ofAll(ints)), lval(Tuple.ofAll(ints2))).ground().get();
		s = MiniKanren.unify(s, x, lval(Tuple.ofAll(ints))).ground().get();

		Tuple x1 = MiniKanren.walkAll(s, x).ground().get();
		assertThat(((Term<?>) x1.get(4))
				.asVal().toJavaOptional())
				.isNotEmpty();
	}

	@Test
	public void shouldReify() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();

		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, y).ground().get();
		s = MiniKanren.unify(s, z, lval(3)).ground().get();

		assertThat(MiniKanren.walkAll(s, lval(Tuple.of(x, y, z)))
				.ground())
				.isEqualTo(lval(Tuple.of(y, y, lval(3))));
		Tuple3<Term<Integer>, Term<Integer>, Term<Integer>> x1 =
				MiniKanren.reify(s, lval(Tuple.<Term<Integer>, Term<Integer>, Term<Integer>> of(x, y, z)))
						.ground().get();
		assertThat(x1._1())
				.matches(v -> v.asReified().isDefined())
				.isEqualTo(x1._2());

		assertThat(x1._3())
				.matches(v -> v.asVal().isDefined());
	}

	@Test
	public void shouldUnifyGoal() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		val result =
				Utils.collect(unify(x, y).and(unify(x, 2))
						.or(unify(x, y), unify(x, 3), unify(y, 4))
						.or(unify(x, y), unify(x, 3))
						.or(unify(x, y), unify(x, 3), unify(y, 3))
						.apply(Package.empty())
						.map(s -> MiniKanren.reify(s.substitution(), lval(Tuple.of(x, y))).ground()));
		Assertions.assertThat(result.get(0).get())
				.isEqualTo(Tuple.of(lval(2), lval(2)));
		Assertions.assertThat(result.get(1).get())
				.isEqualTo(Tuple.of(lval(3), lval(3)));
		Assertions.assertThat(result.get(2).get())
				.isEqualTo(Tuple.of(lval(3), lval(3)));
	}

	@Test
	public void shouldUnifyGoal2() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		val result = runStream(lval(Tuple.of(x, y)),
				unify(x, y).and(unify(x, 2))
						.or(unify(x, y), unify(x, 3), unify(y, 4))
						.or(unify(x, y), unify(x, 3))
						.or(unify(x, y), unify(x, 3), unify(y, 3)))
				.collect(Collectors.toList());
		// disjunct order is scheduler policy, not semantics: pin the multiset
		Assertions.assertThat(result.stream().map(r -> r.get()).collect(Collectors.toList()))
				.containsExactlyInAnyOrder(
						Tuple.of(lval(2), lval(2)),
						Tuple.of(lval(3), lval(3)),
						Tuple.of(lval(3), lval(3)));
	}

	@Test
	public void shouldTreatOptionsAsAtoms() {
		// an Option is a value, not structure: a variable inside one is
		// invisible to unification — the coupling below never closes
		Unifiable<Option<Unifiable<Integer>>> u = lvar();
		Unifiable<Option<Unifiable<Integer>>> v = lvar();
		Unifiable<Integer> val = lvar();
		Unifiable<Integer> val2 = lvar();
		Assertions.assertThat(Utils.collect(unify(u, Option.of(val2))
						.and(unify(v, Option.of(val)))
						.and(unify(u, v))
						.and(unify(val, 123))
						.solve(val2, TestSchedulers.factory())
						.map(Term::get)))
				.isEmpty();
	}

	@Test
	public void shouldUnifyLTree() {
		Unifiable<LTree<Integer>> x = lvar();

		Unifiable<LTree<Integer>> tlTree1 = LTree.of(
				lval(1), LList.ofAll(
						LTree.of(lvar(), LList.empty()),
						LTree.of(lval(3), LList.empty())));

		Assertions.assertThat(Utils.collect(x.unifies(tlTree1)
								.solve(x, TestSchedulers.factory())
								.map(Term::get))
						.toString())
				.isEqualTo("[LTree(value={1}, children={({LTree(value=_.0, children={()})}, {LTree(value={3}, children={()})})})]");
	}

	@Test
	public void shouldUnifyLTree2() {
		Unifiable<LTree<Integer>> x = lvar();
		Unifiable<Integer> y = lvar();

		Unifiable<LTree<Integer>> tlTree1 = LTree.of(
				y, LList.ofAll(
						LTree.of(lvar(), LList.empty()),
						LTree.of(lval(3), LList.empty())));

		Assertions.assertThat(
						Utils.collect(x.unifies(tlTree1)
										.and(y.unifies(1))
										.solve(x, TestSchedulers.factory())
										.map(Term::get))
								.toString())
				.isEqualTo("[LTree(value={1}, children={({LTree(value=_.0, children={()})}, {LTree(value={3}, children={()})})})]");
	}

	@Test
	public void shouldUnifyLTrees() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();

		Unifiable<LTree<Integer>> tlTree = LTree.of(
				x, LList.ofAll(
						LTree.of(y, LList.empty()),
						LTree.of(z, LList.empty())));
		Unifiable<LTree<Integer>> tlTree1 = LTree.of(
				lval(1), LList.ofAll(
						LTree.of(lval(2), LList.empty()),
						LTree.of(lval(3), LList.empty())));

		Assertions.assertThat(Utils.collect(tlTree1.unifies(tlTree)
						.solve(lval(Tuple.of(x, y, z)), TestSchedulers.factory())
						.map(Term::get)
						.map(t -> t.map(Term::get, Term::get, Term::get))))
				.containsExactly(Tuple.of(1, 2, 3));
	}

	@Test
	public void shouldUnifyLTrees2() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Unifiable<LList<LTree<Integer>>> children = lvar();

		Unifiable<LTree<Integer>> tlTree = LTree.of(
				x, children);
		Unifiable<LTree<Integer>> tlTree1 = LTree.of(
				lval(1), LList.ofAll(
						LTree.of(lval(2), LList.empty()),
						LTree.of(lval(3), LList.empty())));

		Assertions.assertThat(Utils.collect(tlTree1.unifies(tlTree)
								.solve(lval(Tuple.of(x, y, z, children)), TestSchedulers.factory())
								.map(Term::get))
						.toString())
				.isEqualTo("[({1}, _.0, _.1, {({LTree(value={2}, children={()})}, {LTree(value={3}, children={()})})})]");
	}

	@Test
	public void shouldUnifyLTrees3() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Unifiable<LList<LTree<Integer>>> children = lvar();

		Unifiable<LTree<Integer>> tlTree = LTree.of(
				x, LList.of(
						LTree.of(y, LList.empty()),
						children));
		Unifiable<LTree<Integer>> tlTree1 = LTree.of(
				lval(1), LList.ofAll(
						LTree.of(lval(2), LList.empty()),
						LTree.of(lval(3), LList.empty())));

		Assertions.assertThat(Utils.collect(tlTree1.unifies(tlTree)
								.solve(lval(Tuple.of(x, y, z, children)), TestSchedulers.factory())
								.map(Term::get))
						.toString())
				.isEqualTo("[({1}, {2}, _.0, {({LTree(value={3}, children={()})})})]");
	}

	@Test
	public void shouldUnifyEmptyLTree() {
		Unifiable<LTree<Integer>> tree = lvar();

		java.util.List<LTree<Integer>> collect = tree.unifies(LTree.empty())
				.solve(tree, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(collect)
				.containsExactly(LTree.<Integer> empty().get());
	}

	@Test
	public void shouldUnifyEmptyLTree2() {
		Unifiable<LTree<Integer>> tree = LTree.ofAll(3);

		java.util.List<LTree<Integer>> collect = tree.unifies(LTree.empty())
				.solve(tree, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(collect)
				.isEmpty();
	}

	@Test
	public void shouldReifyWithCanonicalNumbering() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Tuple3<Term<Integer>, Term<Integer>, Term<Integer>> reified =
				MiniKanren.reify(Substitutions.empty(),
						lval(Tuple.<Term<Integer>, Term<Integer>, Term<Integer>> of(x, y, x))).ground().get();

		assertThat(reified._1().asReified().get().getNumber()).isEqualTo(0);
		assertThat(reified._2().asReified().get().getNumber()).isEqualTo(1);
		assertThat(reified._3()).isSameAs(reified._1());
	}

	@Test
	public void shouldReifyRepeatedVarsInNestedStructuresCanonically() {
		Unifiable<Integer> h = lvar();
		Unifiable<LList<Integer>> t = lvar();

		// repeated vars inside nested structures keep first-occurrence numbering
		Term<?> reified = MiniKanren.reify(Substitutions.empty(),
				lval(Tuple.of(lval(LList.of(h).get()), t, lval(LList.of(h, t).get())))).ground();

		assertThat(reified.toString())
				.isEqualTo("{({(_.0)}, _.1, {(_.0 . _.1)})}");
	}

	@Test
	public void shouldTreatVariantTermsAsEqualWhenReified() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Term<Tuple2<Unifiable<Integer>, Integer>> left =
				MiniKanren.reify(Substitutions.empty(), lval(Tuple.of(x, 1))).ground();
		Term<Tuple2<Unifiable<Integer>, Integer>> right =
				MiniKanren.reify(Substitutions.empty(), lval(Tuple.of(y, 1))).ground();

		assertThat(left).isEqualTo(right);
	}

	@Test
	public void shouldDistinguishSharingStructureWhenReified() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		// (x, x) shares one variable; (x, y) has two distinct ones
		Term<?> shared =
				MiniKanren.reify(Substitutions.empty(), lval(Tuple.of(x, x))).ground();
		Term<?> distinct =
				MiniKanren.reify(Substitutions.empty(), lval(Tuple.of(x, y))).ground();

		assertThat(shared).isNotEqualTo(distinct);
		assertThat(shared).isEqualTo(shared);
	}

	@Test
	public void shouldInstantiateHolesAsFreshSharedVariables() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		// (a, b, a) reifies to (_.0, _.1, _.0); shared anys share the fresh variable
		Reified<?> template = MiniKanren.reify(Substitutions.empty(),
				lval(Tuple.<Term<Integer>, Term<Integer>, Term<Integer>> of(a, b, a))).ground();

		Unifiable<?> instantiated = MiniKanren.instantiate(template).ground();

		Tuple3<Term<Integer>, Term<Integer>, Term<Integer>> items =
				(Tuple3<Term<Integer>, Term<Integer>, Term<Integer>>) instantiated.get();
		assertThat(items._1().asVar().isDefined()).isTrue();
		assertThat(items._2().asVar().isDefined()).isTrue();
		assertThat(items._1()).isSameAs(items._3());
		assertThat(items._1()).isNotEqualTo(items._2());
	}

	@Test
	public void shouldInstantiateGroundStructureUnchanged() {
		Reified<?> template = MiniKanren.reify(Substitutions.empty(),
				lval(Tuple.of(1, "a"))).ground();

		assertThat(MiniKanren.instantiate(template).ground())
				.isEqualTo(lval(Tuple.of(1, "a")));
	}

	@Test
	public void shouldInstantiateNestedHoles() {
		Unifiable<Integer> h = lvar();
		Unifiable<LList<Integer>> t = lvar();
		// ({(h)}, t, {(h . t)}) — sharing must survive instantiation through structures
		Reified<?> template = MiniKanren.reify(Substitutions.empty(),
				lval(Tuple.of(lval(LList.of(h).get()), t, lval(LList.of(h, t).get())))).ground();

		Unifiable<?> instantiated = MiniKanren.instantiate(template).ground();

		Tuple3<Term<LList<Integer>>, Term<LList<Integer>>, Term<LList<Integer>>> tuple =
				(Tuple3<Term<LList<Integer>>, Term<LList<Integer>>, Term<LList<Integer>>>) instantiated.get();
		Term<?> firstHead = tuple._1.get().getHead();
		Term<?> consHead = tuple._3.get().getHead();
		Term<?> consTail = tuple._3.get().getTail();

		assertThat(firstHead.asVar().isDefined()).isTrue();
		assertThat(firstHead).isSameAs(consHead);
		assertThat(tuple._2).isSameAs(consTail);
	}

	/**
	 * Helper to run a Fiber synchronously and get its result.
	 */
	private <T> T runFiber(Fiber<T> fiber) {
		AtomicReference<T> result = new AtomicReference<>();
		BreadthFirstScheduler<T> scheduler = new BreadthFirstScheduler<>(fiber);
		scheduler.run(result::set);
		return result.get();
	}

	private static <T> Optional<T> extractValue(Unifiable<T> variable, Substitutions subs) {
		return subs.walk(variable)
				.asVal()
				.toJavaOptional();
	}
}