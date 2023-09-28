package com.tgac.logic;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import lombok.val;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tgac.logic.Goal.runStream;
import static com.tgac.logic.LVal.lval;
import static com.tgac.logic.LVar.lvar;
import static com.tgac.logic.MiniKanren.Substitutions;
import static com.tgac.logic.MiniKanren.reify;
import static com.tgac.logic.MiniKanren.walk;
import static com.tgac.logic.MiniKanren.walkAll;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author TGa
 */
class MiniKanrenTest {
	@Test
	public void shouldFindX() {
		Unifiable<Integer> x = lvar();
		val subs = MiniKanren.unify(Substitutions.empty(), x, lval(3)).get().get();
		Optional<Integer> y = extractValue(x, subs);
		assertThat(y)
				.hasValue(3);
	}

	@Test
	public void shouldFindXWhenNotGround() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		val subs = MiniKanren.unify(Substitutions.empty(), x, y).get().get();
		Unifiable<Integer> z = walk(subs, x).get();
		assertThat(z)
				.isEqualTo(y);
	}

	@Test
	public void shouldFindZAfterSubstitution() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> z = lvar();
		val subs = MiniKanren.unify(Substitutions.empty(), x, lval(3)).get().get();
		val s2 = MiniKanren.unify(subs, z, x).get().get();
		Optional<Integer> y = extractValue(z, s2);
		assertThat(y)
				.hasValue(3);
	}

	@Test
	public void shouldNotExtendRecursion() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Substitutions subst = MiniKanren.unify(Substitutions.empty(), x, y).get().get();
		assertThat(MiniKanren.unify(subst, y, x).get().get())
				.isEqualTo(subst);
	}

	@Test
	public void shouldFindCircularity() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Unifiable<Integer> q = lvar();
		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, y).get().get();
		s = MiniKanren.unify(s, y, z).get().get();
		s = MiniKanren.unify(s, z, q).get().get();
		Substitutions seq = MiniKanren.unify(s, q, x).get().get();
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

		Substitutions s = Substitutions.empty();
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
		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, y).get().get();
		s = MiniKanren.unify(s, x, z).get().get();
		s = MiniKanren.unify(s, y, lval(3)).get().get();
		assertThat(s.get(z.asVar().get()).get()
				.get())
				.isEqualTo(3);
	}

	@Test
	public void shouldNotUnifyCycle() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, y).get().get();
		s = MiniKanren.unify(s, x, z).get().get();
		Substitutions t = MiniKanren.unify(s, y, z).get().get();
		assertThat(t)
				.isEqualTo(s);
	}

	@Test
	public void shouldNotUnifyInvalidValues() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Substitutions s = Substitutions.empty();
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

		Substitutions s = MiniKanren.unify(Substitutions.empty(),
				lval(xs), lval(ys.map(LVal::lval))).get().get();

		assertThat(xs.toStream()
				.map(x -> MiniKanren.walk(s, x).get())
				.flatMap(v -> v.asVal().toList())
				.collect(List.collector()))
				.isEqualTo(ys);
	}

	@Test
	public void shouldUnifyVarWithList() {
		Unifiable<List<Unifiable<Integer>>> x = lvar();
		Unifiable<List<Unifiable<Integer>>> y = lvar();
		List<Unifiable<Integer>> vals = IntStream.range(0, 1_000_000)
				.boxed()
				.map(LVal::lval)
				.collect(List.collector());

		List<Unifiable<Integer>> vs = IntStream.range(0, 1_000_000)
				.boxed()
				.map(i -> LVar.<Integer> lvar("_." + i))
				.collect(List.collector());
		java.util.List<Long> times0 = new ArrayList<>();
		java.util.List<Long> times1 = new ArrayList<>();
		for (int i = 0; i < 5; ++i) {
			long start = System.nanoTime();
			Substitutions s = Substitutions.empty();
			s = MiniKanren.unify(s, x, y).get().get();
			s = MiniKanren.unify(s, y, lval(vals)).get().get();
			s = MiniKanren.unify(s, y, lval(vs)).get().get();

			times0.add((System.nanoTime() - start) / 1000_000);
			start = System.nanoTime();

			List<Unifiable<Integer>> unifiables =
					MiniKanren.walkAll(s, x).get()
							.get();
			times1.add((System.nanoTime() - start) / 1000_000);
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

		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, lval(t1)).get().get();
		s = MiniKanren.unify(s, lval(t1), lval(t2)).get().get();

		Tuple3<Integer, Unifiable<String>, Unifiable<Boolean>> x1 =
				MiniKanren.walk(s, x).get().get();
		System.out.println(x1);
		assertThat(x1._1)
				.isEqualTo(3);
		assertThat(x1)
				.isEqualTo(t1);
		System.out.println(s);
		assertThat(MiniKanren.walkAll(s, t1._2).get())
				.isEqualTo(lval("Anthony"));
		assertThat(MiniKanren.walk(s, t2._3).get())
				.isEqualTo(lval(false));
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

		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, lval(m1)).get().get();
		s = MiniKanren.unify(s, lval(m1), lval(m2)).get().get();

		Unifiable<Map<String, Tuple2<Integer, Unifiable<Integer>>>> x1 = MiniKanren.walkAll(s, x).get();
		System.out.println(x1);
		System.out.println(x1.get().get("v1").get()._2.asVal());
		assertThat(MiniKanren.walk(s, x).get())
				.isEqualTo(lval(m1));
		assertThat(MiniKanren.walk(s, x).get())
				.isEqualTo(lval(m1));
		assertThat(MiniKanren.walk(s, m1.get("v1").get()._2).get())
				.isEqualTo(lval(1));
		assertThat(MiniKanren.walk(s, m2.get("v2").get()._2).get())
				.isEqualTo(lval(2));
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

		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, lval(ints), lval(ints2)).get().get();
		val listUnifiable = walk(s, lval(ints)).get();
		assertThat(
				listUnifiable
						.get()
						.get(2).get()
						.get(3).get())
				.isEqualTo(13);
		assertThat(listUnifiable
				.get()
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

		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, lval(ints), lval(ints2)).get().get();
		s = MiniKanren.unify(s, x, lval(ints)).get().get();

		val x1 = walkAll(s, x).get();
		assertThat(x1.get().get(3)
				.asVal().toJavaOptional())
				.isNotEmpty();
	}

	@Test
	public void shouldReify() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();

		Substitutions s = Substitutions.empty();
		s = MiniKanren.unify(s, x, y).get().get();
		s = MiniKanren.unify(s, z, lval(3)).get().get();

		assertThat(MiniKanren.walkAll(s, lval(List.of(x, y, z)))
				.get())
				.isEqualTo(lval(List.of(y, y, lval(3))));
		List<Unifiable<Integer>> x1 =
				reify(s, lval(List.of(x, y, z))).get()
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
				x.unify(y).and(x.unify(2))
						.or(x.unify(y), x.unify(3), y.unify(4))
						.or(x.unify(y), x.unify(3))
						.or(x.unify(y), x.unify(3), y.unify(3))
						.apply(Substitutions.empty())
						.map(s -> reify(s, lval(Tuple.of(x, y))).get())
						.collect(Collectors.toList());
		assertThat(result.get(0).get())
				.isEqualTo(Tuple.of(lval(2), lval(2)));
		assertThat(result.get(1).get())
				.isEqualTo(Tuple.of(lval(3), lval(3)));
		assertThat(result.get(2).get())
				.isEqualTo(Tuple.of(lval(3), lval(3)));
	}

	@Test
	public void shouldUnifyGoal2() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		val result = runStream(lval(Tuple.of(x, y)),
				x.unify(y).and(x.unify(2))
						.or(x.unify(y), x.unify(3), y.unify(4))
						.or(x.unify(y), x.unify(3))
						.or(x.unify(y), x.unify(3), y.unify(3)))
				.collect(Collectors.toList());
		assertThat(result.get(0).get())
				.isEqualTo(Tuple.of(lval(2), lval(2)));
		assertThat(result.get(1).get())
				.isEqualTo(Tuple.of(lval(3), lval(3)));
		assertThat(result.get(2).get())
				.isEqualTo(Tuple.of(lval(3), lval(3)));
	}

	@Test
	public void shouldWalkOption() {
		Substitutions s = Substitutions.empty();
		Unifiable<Option<Unifiable<Integer>>> u = lvar();

	}

	private static <T> Optional<T> extractValue(Unifiable<T> variable, Substitutions subs) {
		return MiniKanren.walk(subs, variable).get()
				.asVal()
				.toJavaOptional();
	}
}