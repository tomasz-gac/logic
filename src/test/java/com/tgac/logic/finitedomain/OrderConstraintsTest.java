package com.tgac.logic.finitedomain;

import static com.tgac.logic.Goal.defer;
import static com.tgac.logic.Goal.success;
import static com.tgac.logic.Matche.llist;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.finitedomain.FiniteDomain.lss;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.Streams;
import com.tgac.logic.Goal;
import com.tgac.logic.Matche;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.var;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class OrderConstraintsTest {

	@Test
	public void shouldConstrainAsLeq() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Tuple2<Long, Long>> result =
				Goal.success()
						.and((FiniteDomain.leq(i, j)))
						.and(dom(i, EnumeratedDomain.range(0L, 4L)))
						.and(dom(j, EnumeratedDomain.range(0L, 4L)))
						.solve(lval(Tuple.of(i, j)))
						.map(Unifiable::get)
						.map(t -> t.map1(Unifiable::get).map2(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.allMatch(t -> t._1 <= t._2);
	}

	@Test
	public void shouldConstrainAsLeq2() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Tuple2<Long, Long>> result =
				Goal.success()
						.and(dom(i, EnumeratedDomain.range(0L, 4L)))
						.and(dom(j, EnumeratedDomain.range(0L, 4L)))
						.and((FiniteDomain.leq(i, j)))
						.solve(lval(Tuple.of(i, j)))
						.map(Unifiable::get)
						.map(t -> t.map1(Unifiable::get).map2(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.allMatch(t -> t._1 <= t._2);
	}

	@Test
	public void shouldConstrainAsLeqThanNumber() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();

		List<Tuple2<Long, Long>> results = Goal.success()
				.and(dom(x, EnumeratedDomain.range(3L, 6L)))
				.and(dom(z, EnumeratedDomain.range(3L, 6L)))
				.and(dom(y, EnumeratedDomain.range(1L, 5L)))
				.and(FiniteDomain.leq(x, lval(5L)))
				.and(CKanren.unify(x, y))
				.solve(lval(Tuple.of(y, z)))
				.map(Unifiable::get)
				.map(t -> t.map(Unifiable::get, Unifiable::get))
				.collect(Collectors.toList());

		System.out.println(results);

		Assertions.assertThat(results)
				.allMatch(t -> t._1 <= 5 && t._2 <= 5);
	}

	@Test
	public void shouldAssureLessTransitive() {
		Unifiable<Integer> v0 = lvar();
		Unifiable<Integer> v1 = lvar();
		Unifiable<Integer> v2 = lvar();
		Unifiable<Integer> v3 = lvar();
		Unifiable<Integer> v4 = lvar();
		Unifiable<Integer> v5 = lvar();
		int n = 6;

		Unifiable<LList<Integer>> lst = LList.ofAll(v0, v1, v2, v3, v4, v5);
		var result = allLesso(lst)
				.and(dom(v0, Interval.of(0, n)))
				.and(dom(v1, Interval.of(0, n)))
				.and(dom(v2, Interval.of(0, n)))
				.and(dom(v3, Interval.of(0, n)))
				.and(dom(v4, Interval.of(0, n)))
				.and(dom(v5, Interval.of(0, n)))
				.solve(lst)
				.map(Unifiable::get)
				.map(LList::toValueStream)
				.map(s -> s.collect(Collectors.toList()))
				.collect(Collectors.toList());

		System.out.println(result);
		HashSet<List<Integer>> unique = new HashSet<>(result);
		Assertions.assertThat(result)
				.hasSameElementsAs(unique)
				.allMatch(l ->
						Streams.zip(l.stream(), l.stream().skip(1), Tuple::of)
								.allMatch(lr -> lr.apply((lv, rv) -> lv < rv)));
	}

	public static Goal allLesso(Unifiable<LList<Integer>> lst) {
		return Matche.matche(lst,
				llist(() -> success()),
				llist((a) -> success()),
				llist((a, b, d) ->
						lss(a, b).and(defer(() -> allLesso(LList.of(b, d))))));
	}

}
