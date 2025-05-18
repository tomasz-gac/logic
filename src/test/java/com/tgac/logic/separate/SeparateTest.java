package com.tgac.logic.separate;

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.LogicTest.runStream;
import static com.tgac.logic.separate.Disequality.rembero;
import static com.tgac.logic.separate.Disequality.separate;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.LogicTest;
import com.tgac.logic.Utils;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.ExtensionMethod;
import org.assertj.core.api.Assertions;
import org.junit.Test;

@SuppressWarnings("unchecked")
@ExtensionMethod(Disequality.class)
public class SeparateTest {

	@Test
	public void shouldUnifyWithConstraints() {
		Unifiable<Integer> out = lvar();
		List<Integer> result = LogicTest.runStream(out,
						separate(out, lval(2)),
						CKanren.unify(out, lval(3)))
				.map(Unifiable::get)
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result).containsExactly(3);
	}

	@Test
	public void shouldNotUnifyWithConstraints() {
		Unifiable<Integer> out = lvar();
		Assertions.assertThat(LogicTest.runStream(out,
						separate(out, lval(2)),
						CKanren.unify(out, lval(2))))
				.isEmpty();
	}

	@Test
	public void shouldNotUnifyWhenConstraintsAlreadyViolated() {
		Unifiable<Integer> out = lvar();
		Assertions.assertThat(LogicTest.runStream(out,
						CKanren.unify(out, lval(2)),
						separate(out, lval(2))))
				.isEmpty();
	}

	@Test
	public void shouldUnifyWithSimultaneousConstraints() {
		Unifiable<Integer> out = lvar();
		Assertions.assertThat(LogicTest.runStream(out, Logic.<Integer, Integer, Tuple2<Unifiable<Integer>, Unifiable<Integer>>> exist((x, y, p) ->
								p.unifies(Tuple.of(x, out))
										.and(separate(p, lval(Tuple.of(lval(3), lval(2)))))
										.and(x.unifies(3))
										.and(out.unifies(3))))
						.map(Unifiable::get))
				.containsExactly(3);
	}

	@Test
	public void shouldNotUnifyWithSimultaneousConstraints() {
		Unifiable<Integer> out = lvar();
		Assertions.assertThat(
						LogicTest.runStream(out,
								Logic.<Integer, Integer, Tuple2<Unifiable<Integer>, Unifiable<Integer>>> exist((x, y, p) ->
										p.unifies(Tuple.of(x, out))
												.and(separate(p, lval(Tuple.of(lval(3), lval(2)))))
												.and(x.unifies(3))
												.and(out.unifies(2)))))
				.isEmpty();
	}

	public static <T> Goal brokenRembero(Unifiable<LList<T>> ls, Unifiable<T> x, Unifiable<LList<T>> out) {
		return ls.unifies(LList.empty()).and(out.unifies(LList.empty()))
				.or(Logic.<T, LList<T>> exist((a, d) ->
						ls.unifies(LList.of(a, d))
								.and(x.unifies(a))
								.and(out.unifies(d))))
				.or(Logic.<T, LList<T>, LList<T>> exist((a, d, res) ->
						ls.unifies(LList.of(a, d))
								.and(out.unifies(LList.of(a, res)))
								.and(defer(() -> brokenRembero(d, x, res)))));
	}

	@Test
	public void shouldRemoveAllMembers() {
		Unifiable<LList<Integer>> out = lvar();
		List<List<Integer>> result = LogicTest.runStream(out,
						Logic.<LList<Integer>> exist(l ->
								l.unifies(LList.ofAll(3, 2, 3, 2))
										.and(brokenRembero(l, lval(2), out))))
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result.stream().map(Object::toString).collect(Collectors.joining("\n")));
		assertThat(result)
				.containsExactlyInAnyOrder(
						Arrays.asList(3, 3, 2),
						Arrays.asList(3, 2, 3),
						Arrays.asList(3, 2, 3, 2));
	}

	@Test
	public void shouldReturnConstraints() {
		Unifiable<LList<Tuple2<Unifiable<Integer>, Unifiable<Integer>>>> u = lvar();
		String result = LogicTest.runStream(u,
						Logic.<Tuple2<Unifiable<Integer>, Unifiable<Integer>>,
								LList<Tuple2<Unifiable<Integer>, Unifiable<Integer>>>,
								Integer, Integer, Integer> exist((a, d, dummy, x, y) ->
								u.unifies(LList.of(a, LList.of(lval(Tuple.of(y, x)), d)))
										.and(CKanren.unify(a, lval(Tuple.of(x, y))))
										.and(separate(x, lval(3)))
										.and(separate(y, lval(2)))
										.and(separate(a, lval(Tuple.of(lval(7), lval(8)))))
										.and(separate(dummy, lval(5)))))
				.map(Object::toString)
				.collect(Collectors.joining("\n"));
		System.out.println(result);
		// {({(<_.3>, <_.2>)}, {(<_.2>, <_.3>)} . <_.4>)} : (<_.3> ≠ {3}) || (<_.2> ≠ {2}) || (<_.2> ≠ {8} && <_.3> ≠ {7})
		assertThat(result)
				.contains("{({(<_.3>, <_.2>)}, {(<_.2>, <_.3>)} . <_.4>)}")
				.contains("(<_.3> ≠ {3})")
				.contains("(<_.2> ≠ {2})")
				.contains("<_.2> ≠ {8}")
				.contains("<_.3> ≠ {7}")
				.contains("||")
				.contains("&&");
	}

	@Test
	public void shouldNotIncludeUnrelatedConstraints() {
		Unifiable<Integer> u = lvar();
		String result = LogicTest.runStream(u,
						Logic.<Integer, Integer, Integer> exist((x, y, z) ->
								separate(u, lval(3))
										.and(separate(x, lval(2)))
										.and(separate(y, lval(2)))
										.and(separate(z, lval(2)))))
				.map(Object::toString)
				.collect(Collectors.joining("\n"));
		System.out.println(result);
		assertThat(result)
				.isEqualTo("<_.0> : (<_.0> ≠ {3})");
	}

	@Test
	public void shouldNotIncludeSubsumedConstraints() {
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> u = lvar();
		String result = LogicTest.runStream(u,
						Logic.<Tuple2<Unifiable<Integer>, Unifiable<Integer>>, Integer, Integer> exist((p, y, z) ->
								// p cannot be (3, 2)
								separate(p, lval(Tuple.of(lval(3), lval(2))))
										// p is y, z so y ≠ 3 && z ≠ 2
										.and(p.unifies(Tuple.of(y, z)))
										.and(u.unifies(p))
										// y ≠ 3 is more general than (y ≠ 3 && z ≠ 2)
										.and(separate(y, lval(3)))))
				.map(Object::toString)
				.collect(Collectors.joining("\n"));
		System.out.println(result);
		assertThat(result)
				.isEqualTo("{(<_.0>, <_.1>)} : (<_.0> ≠ {3})");
	}

	static <A> Goal removo(Unifiable<LList<A>> with, Unifiable<LList<A>> without, Unifiable<A> item) {
		return with.unifies(LList.empty()).and(without.unifies(LList.empty()))
				.or(Logic.<A, LList<A>> exist((a, d) ->
						with.unifies(LList.of(a, d))
								.and(a.unifies(item))
								.and(defer(() -> removo(d, without, item)))))
				.or(Logic.<A, LList<A>, LList<A>> exist((a, d, res) ->
						with.unifies(LList.of(a, d))
								.and(separate(a, item))
								.and(without.unifies(LList.of(a, res)))
								.and(defer(() -> removo(d, res, item)))));
	}

	@Test
	public void shouldRemovo() {
		Unifiable<LList<Integer>> r = lvar();
		System.out.println(LogicTest.runStream(r,
						Logic.<LList<Integer>> exist(l ->
								l.unifies(LList.ofAll(1, 2, 1, 3))
										.and(removo(l, r, lval(1)))))
				.limit(4)
				.collect(Collectors.toList()));
	}

	@Test
	public void shouldRemoveSingleMember() {
		Unifiable<LList<Integer>> out = lvar();
		List<List<Integer>> result = runStream(out,
				rembero(LList.ofAll(3, 2, 3, 2), lval(2), out))
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result.stream().map(Object::toString).collect(Collectors.joining("\n")));
		assertThat(result)
				.containsExactlyInAnyOrder(
						Arrays.asList(3, 3, 2));
	}

	@Test
	public void shouldAddSingleMember() {
		Unifiable<LList<Integer>> out = lvar();
		List<List<Integer>> result = LogicTest.runStream(out,
						Logic.<LList<Integer>> exist(l ->
								l.unifies(LList.ofAll(3, 2, 3, 2))
										.and(rembero(out, lval(2), l))))
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList()))
				.limit(10)
				.collect(Collectors.toList());
		System.out.println(result.stream().map(Object::toString).collect(Collectors.joining("\n")));
		assertThat(result)
				.containsExactlyInAnyOrder(
						Arrays.asList(2, 3, 2, 3, 2),
						Arrays.asList(3, 2, 2, 3, 2));
	}

	static <A> Goal removeAllo(Unifiable<LList<A>> with, Unifiable<LList<A>> without, Unifiable<A> item) {
		return with.unifies(LList.empty()).and(without.unifies(LList.empty()))
				.or(Logic.<LList<A>> exist(res ->
						rembero(with, item, res)
								.and(with.unifies(res).and(res.unifies(without))
										.or(with.separate(res)
												.and(defer(() -> removeAllo(res, without, item)))))));
	}

	@Test
	public void shouldRemoveAll() {
		Unifiable<LList<Integer>> r = lvar();
		System.out.println(LogicTest.runStream(r,
						Logic.<LList<Integer>> exist(l ->
								l.unifies(LList.ofAll(1, 2, 1, 3, 1, 4, 1))
										.and(removeAllo(l, r, lval(1)))))
				.limit(4)
				.collect(Collectors.toList()));
	}

	@Test
	public void shouldSeparate() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();

		System.out.println(LogicTest.runStream(
						LList.ofAll(x, y, z),
						x.separate(y),
						x.separate(z),

						y.separate(z),
						x.unifies(1))
				.collect(Collectors.toList()));
	}

	@Test
	public void shouldMakeDistinct() {
		Unifiable<LList<Integer>> r = lvar();
		System.out.println(LogicTest.runStream(r,
						Logic.<LList<Integer>> exist(l ->
								l.unifies(LList.ofAll(1, 2))
										.and(Disequality.distincto(r))))
				.limit(5)
				.map(Objects::toString)
				.collect(Collectors.joining("\n")));
	}

	@Test
	public void shouldReturnFromSingleGoalThatSucceeds() {
		Unifiable<Integer> x = lvar();
		List<Integer> results = Utils.collect(Goal.condu(
						x.separate(x),
						x.unifies(1).or(x.unifies(2)),
						x.unifies(3))
				.solve(x)
				.map(Unifiable::get));

		Assertions.assertThat(results)
				.containsExactly(1, 2);
	}

	@Test
	public void shouldReturnFromSingleBranch() {
		Unifiable<Integer> x = lvar();
		List<Integer> results =
				Goal.condu(
								x.unifies(2).or(x.unifies(3)),
								x.unifies(1),
								x.unifies(3))
						.solve(x)
						.map(Unifiable::get)
						.collect(Collectors.toList());

		Assertions.assertThat(results)
				.containsExactly(2, 3);
	}

		@Test
		public void shouldReturnSingleElementFromSingleGoalThatSucceeds() {
			Unifiable<Integer> x = lvar();
			List<Integer> results = Goal.conda(
							x.separate(x),
							x.unifies(1).or(x.unifies(2)),
							x.unifies(3))
					.solve(x)
					.map(Unifiable::get)
					.collect(Collectors.toList());

			Assertions.assertThat(results)
					.containsExactly(1);
		}
}
