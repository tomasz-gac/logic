package com.tgac.logic;

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.logic.goals.Logic;
import com.tgac.logic.goals.Matche;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class MatcheTest {

	@Test
	public void shouldMatchSingleElementList() {
		Unifiable<LList<Integer>> i = LVar.lvar();
		List<List<Integer>> result = Utils.collect(Matche.matche(
						LList.ofAll(1),
						Matche.llist(a -> i.unifies(LList.of(a))),
						Matche.llist((a, d) -> i.unifies(LList.of(a, d))),
						Matche.llist(3, (lst, d) ->
								i.unifies(LList.ofAll(lst.toJavaArray(Unifiable[]::new)))))
				.solve(i)
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList())));
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(
						Collections.singletonList(1), // from 1
						Collections.singletonList(1) // from 2, empty d
				);
	}

	@Test
	public void shouldMatchTwoElementList() {
		Unifiable<LList<Integer>> i = LVar.lvar();
		List<List<Integer>> result = Utils.collect(Matche.matche(LList.ofAll(1, 2),
						Matche.llist(a -> i.unifies(LList.of(a))),
						Matche.llist((a, d) -> i.unifies(LList.of(a, d))),
						Matche.llist(3, (lst, d) ->
								i.unifies(LList.ofAll(lst.toJavaArray(Unifiable[]::new)))))
				.solve(i)
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList())));
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(
						Arrays.asList(1, 2) // from 2, d=2
				);
	}

	@Test
	public void shouldMatchMany() {
		Unifiable<LList<Integer>> i = LVar.lvar();
		List<List<Integer>> result = Utils.collect(Matche.matche(LList.ofAll(1, 2, 3, 4, 5),
						Matche.llist(a -> i.unifies(LList.of(a))),
						Matche.llist((a, d) -> i.unifies(LList.of(a, d))),
						Matche.llist(3, (lst, d) ->
								i.unifies(LList.ofAll(lst.toJavaArray(Unifiable[]::new)))))
				.solve(i)
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList())));
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(
						Arrays.asList(1, 2, 3, 4, 5), // from 2, d=2, 3, 4, 5
						Arrays.asList(1, 2, 3) // from 3, d is dropped
				);
	}

	@Test
	public void shouldMatchTuple() {
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> i = LVar.lvar();
		List<Tuple2<Integer, Integer>> result = Utils.collect(Matche.matche(lval(Tuple.of(lval(1), lval(2))),
						Matche.tuple((a, b) -> i.unifies(Tuple.of(a, b))))
				.solve(i)
				.map(Unifiable::get)
				.map(t -> t.map(MiniKanren.applyOnBoth(Unifiable::get))));
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(Tuple.of(1, 2));
	}

	@Test
	public void shouldMatchLVar() {
		Unifiable<Integer> i = LVar.lvar();
		List<Integer> result = Utils.collect(Matche.matche(i, Matche.variable(() -> i.unifies(123)))
				.solve(i)
				.map(Unifiable::get));
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(123);
	}

	@Test
	public void shouldMatchLVarAfterUnification() {
		Unifiable<Integer> i = LVar.lvar();
		List<Integer> result = Utils.collect(Logic.<Integer> exist(j ->
						j.unifies(i)
								.and(Matche.matche(j, Matche.variable(() -> j.unifies(123)))))
				.solve(i)
				.map(Unifiable::get));
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(123);
	}

	@Test
	public void shouldMatchLVarMultipleTimes() {
		Unifiable<Integer> i = LVar.lvar();
		List<Integer> result = Utils.collect(Matche.matche(i,
						Matche.variable(() -> i.unifies(123)),
						Matche.variable(() -> i.unifies(124)),
						Matche.variable(() -> i.unifies(125)))
				.solve(i)
				.map(Unifiable::get));
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(123, 124, 125);
	}

	@Test
	public void shouldMatchLVal() {
		Unifiable<Integer> v = lval(123);
		Unifiable<Integer> i = LVar.lvar();
		List<Integer> result = Utils.collect(Matche.matche(v,
						Matche.value(i::unifies))
				.solve(i)
				.map(Unifiable::get));
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(123);
	}

	@Test
	public void shouldMatchLValAfterUnification() {
		Unifiable<Integer> v = LVar.lvar();
		Unifiable<Integer> v2 = lval(123);
		Unifiable<Integer> i = LVar.lvar();
		List<Integer> result = Utils.collect(v2.unifies(v).and(
						Matche.matche(v2, Matche.value(i::unifies)))
				.solve(i)
				.map(Unifiable::get));
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(123);
	}

	@Test
	public void shouldMatchLValMultipleTimes() {
		Unifiable<Integer> v = lval(123);
		Unifiable<Integer> i = LVar.lvar();
		List<Integer> result =
				Utils.collect(Matche.matche(v,
								Matche.value(i::unifies),
								Matche.value(val -> i.unifies(val + 1)),
								Matche.value(val -> i.unifies(val + 2)))
						.solve(i)
						.map(Unifiable::get));
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(123, 124, 125);
	}
}
