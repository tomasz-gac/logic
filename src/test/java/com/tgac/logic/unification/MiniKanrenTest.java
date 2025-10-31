package com.tgac.logic.unification;

import static com.tgac.logic.LogicTest.runStream;
import static com.tgac.logic.ckanren.CKanren.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.Utils;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * @author TGa
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class MiniKanrenTest {

	@Test
	public void shouldFindX() {
		Unifiable<Integer> x = lvar();
		val subs = MiniKanren.unify(Package.empty(), x, lval(3)).get().get();
		Optional<Integer> y = extractValue(x, subs);
		assertThat(y)
				.hasValue(3);
	}

	@Test
	public void shouldFindXWhenNotGround() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		val subs = MiniKanren.unify(Package.empty(), x, y).get().get();
		Unifiable<Integer> z = MiniKanren.walk(subs, x);
		assertThat(z)
				.isEqualTo(y);
	}

	@Test
	public void shouldFindZAfterSubstitution() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> z = lvar();
		val subs = MiniKanren.unify(Package.empty(), x, lval(3)).get().get();
		val s2 = MiniKanren.unify(subs, z, x).get().get();
		Optional<Integer> y = extractValue(z, s2);
		assertThat(y)
				.hasValue(3);
	}

	@Test
	public void shouldNotExtendRecursion() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package subst = MiniKanren.unify(Package.empty(), x, y).get().get();
		assertThat(MiniKanren.unify(subst, y, x).get().get())
				.isEqualTo(subst);
	}

	@Test
	public void shouldFindCircularity() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Unifiable<Integer> q = lvar();
		Package s = Package.empty();
		s = MiniKanren.unify(s, x, y).get().get();
		s = MiniKanren.unify(s, y, z).get().get();
		s = MiniKanren.unify(s, z, q).get().get();
		Package seq = MiniKanren.unify(s, q, x).get().get();
		assertThat(seq)
				.isEqualTo(s);
	}

	@Test
	public void shouldNotFindCircularity() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Unifiable<Integer> q = lvar();
		Package s = Package.empty();
		s = MiniKanren.unify(s, y, z).get().get();
		s = MiniKanren.unify(s, z, q).get().get();
		val t = s;

		// does not trow
		MiniKanren.unify(t, q, x).get();
	}

	@Test
	public void shouldSubstituteTwice() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();

		Package s = Package.empty();
		s = MiniKanren.unify(s, z, x).get().get();
		s = MiniKanren.unify(s, y, x).get().get();
		s = MiniKanren.unify(s, x, lval(3)).get().get();
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
		Package s = Package.empty();
		s = MiniKanren.unify(s, x, y).get().get();
		s = MiniKanren.unify(s, x, z).get().get();
		s = MiniKanren.unify(s, y, lval(3)).get().get();
		Assertions.assertThat(s.get(z.asVar().get()).get())
				.isEqualTo(3);
	}

	@Test
	public void shouldNotUnifyCycle() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Package s = Package.empty();
		s = MiniKanren.unify(s, x, y).get().get();
		s = MiniKanren.unify(s, x, z).get().get();
		Package t = MiniKanren.unify(s, y, z).get().get();
		assertThat(t)
				.isEqualTo(s);
	}

	@Test
	public void shouldNotUnifyInvalidValues() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Package s = Package.empty();
		s = MiniKanren.unify(s, x, y).get().get();
		s = MiniKanren.unify(s, x, z).get().get();
		s = MiniKanren.unify(s, y, lval(3)).get().get();
		assertThat(MiniKanren.unify(s, z, lval(4)).get().toJavaOptional()).isEmpty();
	}

	@Test
	public void shouldUnifyLists() {
		val xs = IntStream.range(0, 10)
				.mapToObj(i -> LVar.<Integer> lvar())
				.collect(List.collector());

		val ys = IntStream.range(0, 10)
				.boxed()
				.collect(List.collector());

		Package s = MiniKanren.unify(Package.empty(),
				lval(xs), lval(ys.map(LVal::lval))).get().get();

		assertThat(xs.toStream()
				.map(x -> MiniKanren.walk(s, x))
				.flatMap(v -> v.asVal().toList())
				.collect(List.collector()))
				.isEqualTo(ys);
	}

	@Test
	public void shouldUnifyVarWithList() {
		Unifiable<List<Unifiable<Integer>>> x = lvar();
		Unifiable<List<Unifiable<Integer>>> y = lvar();
		int n = 1_000_000;
		List<Unifiable<Integer>> vals = IntStream.range(0, n)
				.boxed()
				.map(LVal::lval)
				.collect(List.collector());

		List<Unifiable<Integer>> vs = IntStream.range(0, n)
				.boxed()
				.map(i -> LVar.<Integer> lvar("_." + i))
				.collect(List.collector());
		java.util.List<Long> times0 = new ArrayList<>();
		java.util.List<Long> times1 = new ArrayList<>();
		for (int i = 0; i < 5; ++i) {
			long start = System.nanoTime();
			Package s = Package.empty();
			s = MiniKanren.unify(s, x, y).get().get();
			s = MiniKanren.unify(s, y, lval(vals)).get().get();
			s = MiniKanren.unify(s, y, lval(vs)).get().get();

			times0.add((System.nanoTime() - start) / n);
			start = System.nanoTime();

			List<Unifiable<Integer>> unifiables =
					MiniKanren.walkAll(s, x).get()
							.get();
			times1.add((System.nanoTime() - start) / n);
			System.out.println(i);
		}
		System.out.println(times0);
		System.out.println("unify mean: " + times0.stream().reduce(Long::sum).orElse(0L) / times0.size());
		System.out.println(times1);
		System.out.println("lookup mean: " + times1.stream().reduce(Long::sum).orElse(0L) / times1.size());
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

		Package s = Package.empty();
		s = MiniKanren.unify(s, x, lval(t1)).get().get();
		s = MiniKanren.unify(s, lval(t1), lval(t2)).get().get();

		Tuple3<Integer, Unifiable<String>, Unifiable<Boolean>> x1 =
				MiniKanren.walk(s, x).get();
		System.out.println(x1);
		assertThat(x1._1)
				.isEqualTo(3);
		assertThat(x1)
				.isEqualTo(t1);
		System.out.println(s);
		assertThat(MiniKanren.walkAll(s, t1._2).get())
				.isEqualTo(lval("Anthony"));
		assertThat(MiniKanren.walk(s, t2._3).get())
				.isEqualTo(false);
	}

	@Test
	public void shouldUnifyMaps() {
		Unifiable<Map<String, Tuple2<Integer, Unifiable<Integer>>>> x = lvar();
		Map<String, Tuple2<Integer, Unifiable<Integer>>> m1 = HashMap.of(
				"v1", Tuple.of(3, lvar("v1")),
				"v2", Tuple.of(4, lval(2)));

		Map<String, Tuple2<Integer, Unifiable<Integer>>> m2 = HashMap.of(
				"v1", Tuple.of(3, lval(1)),
				"v2", Tuple.of(4, lvar("v2")));

		Package s = Package.empty();
		s = MiniKanren.unify(s, x, lval(m1)).get().get();
		s = MiniKanren.unify(s, lval(m1), lval(m2)).get().get();

		Unifiable<Map<String, Tuple2<Integer, Unifiable<Integer>>>> x1 = MiniKanren.walkAll(s, x).get();
		System.out.println(x1);
		System.out.println(x1.get().get("v1").get()._2.asVal());
		assertThat(MiniKanren.walk(s, x).get())
				.isEqualTo(m1);
		assertThat(MiniKanren.walk(s, x).get())
				.isEqualTo(m1);
		assertThat(MiniKanren.walk(s, m1.get("v1").get()._2).get())
				.isEqualTo(1);
		assertThat(MiniKanren.walk(s, m2.get("v2").get()._2).get())
				.isEqualTo(2);
	}

	Unifiable<List<? extends Unifiable<Integer>>> buildUni(int i, int delta) {
		if (i % 2 == delta) {
			return lval(IntStream.range(10, 20)
					.boxed()
					.map(LVal::lval)
					.collect(List.collector()));
		} else if (i % 2 == 1 + delta) {
			return lvar("_." + i);
		} else {
			return lval(IntStream.range(10, 20)
					.boxed()
					.map(j -> LVar.<Integer> lvar("_." + i + j))
					.collect(List.collector()));
		}
	}

	@Test
	public void shouldUnifyCompoundTypes() {
		List<Unifiable<? extends List<? extends Unifiable<Integer>>>> ints = IntStream.range(0, 60)
				.boxed()
				.map(i -> buildUni(i, 0))
				.collect(List.collector());

		List<Unifiable<? extends List<? extends Unifiable<Integer>>>> ints2 = IntStream.range(0, 60)
				.boxed()
				.map(i -> buildUni(i, 1))
				.collect(List.collector());

		Package s = Package.empty();
		s = MiniKanren.unify(s, lval(ints), lval(ints2)).get().get();
		val listUnifiable = MiniKanren.walk(s, lval(ints)).get();
		assertThat(
				listUnifiable
						.get(2).get()
						.get(3).get())
				.isEqualTo(13);
		assertThat(listUnifiable
				.get(1).asVar().toJavaOptional()
				.map(LVar::getName))
				.hasValue("_.1");
	}

	@Test
	public void shouldUnifyCompoundTypes2() {
		Unifiable<List<Unifiable<? extends List<? extends Unifiable<Integer>>>>> x = lvar();

		List<Unifiable<? extends List<? extends Unifiable<Integer>>>> ints = IntStream.range(0, 60)
				.boxed()
				.map(i -> buildUni(i, 0))
				.collect(List.collector());

		List<Unifiable<? extends List<? extends Unifiable<Integer>>>> ints2 = IntStream.range(0, 60)
				.boxed()
				.map(i -> buildUni(i, 1))
				.collect(List.collector());

		Package s = Package.empty();
		s = MiniKanren.unify(s, lval(ints), lval(ints2)).get().get();
		s = MiniKanren.unify(s, x, lval(ints)).get().get();

		val x1 = MiniKanren.walkAll(s, x).get();
		assertThat(x1.get().get(3)
				.asVal().toJavaOptional())
				.isNotEmpty();
	}

	@Test
	public void shouldReify() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();

		Package s = Package.empty();
		s = MiniKanren.unify(s, x, y).get().get();
		s = MiniKanren.unify(s, z, lval(3)).get().get();

		assertThat(MiniKanren.walkAll(s, lval(List.of(x, y, z)))
				.get())
				.isEqualTo(lval(List.of(y, y, lval(3))));
		List<Unifiable<Integer>> x1 =
				MiniKanren.reify(s, lval(List.of(x, y, z))).get()
						.get();
		assertThat(x1.get(0))
				.matches(v -> v.asVar().isDefined())
				.isEqualTo(x1.get(1));

		assertThat(x1.get(2))
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
						.map(s -> MiniKanren.reify(s, lval(Tuple.of(x, y))).get()));
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
		System.out.println(result);
		Assertions.assertThat(result.get(0).get())
				.isEqualTo(Tuple.of(lval(2), lval(2)));
		Assertions.assertThat(result.get(1).get())
				.isEqualTo(Tuple.of(lval(3), lval(3)));
		Assertions.assertThat(result.get(2).get())
				.isEqualTo(Tuple.of(lval(3), lval(3)));
	}

	@Test
	public void shouldWalkOption() {
		Package s = Package.empty();
		Unifiable<Option<Unifiable<Integer>>> u = lvar();
		Unifiable<Option<Unifiable<Integer>>> v = lvar();
		Unifiable<Integer> val = lvar();
		Unifiable<Integer> val2 = lvar();
		Assertions.assertThat(Utils.collect(unify(u, Option.of(val2))
						.and(unify(v, Option.of(val)))
						.and(unify(u, v))
						.and(unify(val, 123))
						.solve(val2)
						.map(Unifiable::get)))
				.containsExactly(123);
	}

	@Test
	public void shouldUnifyLTree() {
		Unifiable<LTree<Integer>> x = lvar();

		Unifiable<LTree<Integer>> tlTree1 = LTree.of(
				lval(1), LList.ofAll(
						LTree.of(lvar(), LList.empty()),
						LTree.of(lval(3), LList.empty())));

		Assertions.assertThat(Utils.collect(x.unifies(tlTree1)
								.solve(x)
								.map(Unifiable::get))
						.toString())
				.isEqualTo("[LTree(value={1}, children={({LTree(value=<_.0>, children={()})}, {LTree(value={3}, children={()})})})]");
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
										.solve(x)
										.map(Unifiable::get))
								.toString())
				.isEqualTo("[LTree(value={1}, children={({LTree(value=<_.0>, children={()})}, {LTree(value={3}, children={()})})})]");
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
						.solve(lval(Tuple.of(x, y, z)))
						.map(Unifiable::get)
						.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get))))
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
								.solve(lval(Tuple.of(x, y, z, children)))
								.map(Unifiable::get))
						.toString())
				.isEqualTo("[({1}, <_.0>, <_.1>, {({LTree(value={2}, children={()})}, {LTree(value={3}, children={()})})})]");
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
								.solve(lval(Tuple.of(x, y, z, children)))
								.map(Unifiable::get))
						.toString())
				.isEqualTo("[({1}, {2}, <_.0>, {({LTree(value={3}, children={()})})})]");
	}

	@Test
	public void shouldUnifyEmptyLTree(){
		Unifiable<LTree<Integer>> tree = lvar();

		java.util.List<LTree<Integer>> collect = tree.unifies(LTree.empty())
				.solve(tree)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		assertThat(collect)
				.containsExactly(LTree.<Integer>empty().get());
	}

	@Test
	public void shouldUnifyEmptyLTree2(){
		Unifiable<LTree<Integer>> tree = LTree.ofAll(3);

		java.util.List<LTree<Integer>> collect = tree.unifies(LTree.empty())
				.solve(tree)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		assertThat(collect)
				.isEmpty();
	}


	private static <T> Optional<T> extractValue(Unifiable<T> variable, Package subs) {
		return MiniKanren.walk(subs, variable)
				.asVal()
				.toJavaOptional();
	}
}