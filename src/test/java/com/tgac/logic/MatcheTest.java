package com.tgac.logic;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.tgac.logic.Goals.llist;
import static com.tgac.logic.Goals.matche;
import static com.tgac.logic.Goals.tuple;
import static com.tgac.logic.LVal.lval;
import static com.tgac.logic.LVar.lvar;

@SuppressWarnings("unchecked")
public class MatcheTest {
	@Test
	public void shouldMatchSingleElementList() {
		Unifiable<LList<Integer>> i = lvar();
		List<List<Integer>> result = Goal.runStream(i,
						matche(LList.ofAll(1),
								llist(a -> i.unify(LList.of(a))),
								llist((a, d) -> i.unify(LList.of(a, d))),
								llist(3, (lst, d) ->
										i.unify(LList.ofAll(lst.toJavaArray(Unifiable[]::new))))
						))
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(
						Collections.singletonList(1), // from 1
						Collections.singletonList(1) // from 2, empty d
				);
	}

	@Test
	public void shouldMatchTwoElementList() {
		Unifiable<LList<Integer>> i = lvar();
		List<List<Integer>> result = Goal.runStream(i,
						matche(LList.ofAll(1, 2),
								llist(a -> i.unify(LList.of(a))),
								llist((a, d) -> i.unify(LList.of(a, d))),
								llist(3, (lst, d) ->
										i.unify(LList.ofAll(lst.toJavaArray(Unifiable[]::new))))
						))
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(
						Arrays.asList(1, 2) // from 2, d=2
				);
	}

	@Test
	public void shouldMatchMany() {
		Unifiable<LList<Integer>> i = lvar();
		List<List<Integer>> result = Goal.runStream(i,
						matche(LList.ofAll(1, 2, 3, 4, 5),
								llist(a -> i.unify(LList.of(a))),
								llist((a, d) -> i.unify(LList.of(a, d))),
								llist(3, (lst, d) ->
										i.unify(LList.ofAll(lst.toJavaArray(Unifiable[]::new))))
						))
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(
						Arrays.asList(1, 2, 3, 4, 5), // from 2, d=2, 3, 4, 5
						Arrays.asList(1, 2, 3) // from 3, d is dropped
				);
	}

	@Test
	public void shouldMatchTuple() {
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> i = lvar();
		List<Tuple2<Integer, Integer>> result = Goal.runStream(i,
						matche(lval(Tuple.of(lval(1), lval(2))),
								tuple((a, b) -> i.unify(Tuple.of(a, b)))))
				.map(Unifiable::get)
				.map(t -> t.map(MiniKanren.applyOnBoth(Unifiable::get)))
				.collect(Collectors.toList());
		System.out.println(result);
		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(Tuple.of(1, 2));
	}
}
