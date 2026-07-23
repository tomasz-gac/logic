package com.tgac.logic.tabling;

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.separate.Disequality;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.Tuple2;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Pins the definition/application API of tabled relations: the key and the
 * body are wired from the same argument tuple by the framework, and the
 * cache is keyed on relation identity, not on the display name.
 */
public class TabledTest {

	private Goal edge(Unifiable<Integer> x, Unifiable<Integer> y) {
		return x.unifies(1).and(y.unifies(2))
				.or(x.unifies(2).and(y.unifies(3)))
				.or(x.unifies(3).and(y.unifies(4)));
	}

	private final Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> path =
			Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
					edge(x, y)
							.or(defer(() -> {
								Unifiable<Integer> z = lvar();
								return self.apply(Tuple.of(x, z)).and(edge(z, y));
							}))));

	@Test(timeout = 5000)
	public void shouldTerminateLeftRecursionThroughSelf() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		long count = x.unifies(1).and(y.unifies(4))
				.and(path.apply(Tuple.of(x, y)))
				.solve(lvar())
				.count();

		assertThat(count).isEqualTo(1);
	}

	@Test(timeout = 5000)
	public void shouldShareTheCacheBetweenApplicationsOfOneRelation() {
		Unifiable<Integer> one = lvar();
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();

		// path(1, _) = {2, 3, 4}; the second application consumes the first's cache
		long count = one.unifies(1)
				.and(path.apply(Tuple.of(one, a)))
				.and(path.apply(Tuple.of(one, b)))
				.solve(lval(Tuple.of(a, b)))
				.count();

		assertThat(count).isEqualTo(9);
	}

	@Test
	public void shouldNotShareCachesBetweenDistinctRelations() {
		// two distinct relations, even with identical bodies, have separate caches
		Tabled<Tuple1<Unifiable<Integer>>> constant1 =
				Tabling.define(args -> args.apply(x -> x.unifies(1)));
		Tabled<Tuple1<Unifiable<Integer>>> constant2 =
				Tabling.define(args -> args.apply(x -> x.unifies(2)));

		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		List<Tuple2<Integer, Integer>> results =
				constant1.apply(Tuple.of(x))
						.and(constant2.apply(Tuple.of(y)))
						.solve(lval(Tuple.of(x, y)))
						.map(Term::get)
						.map(t -> t.map1(Term::get).map2(Term::get))
						.collect(Collectors.toList());

		assertThat(results).containsExactly(Tuple.of(1, 2));
	}

	@Test
	public void shouldDeduplicateAlphaEquivalentAnswers() {
		Tabled<Tuple1<Unifiable<Object>>> pairs =
				Tabling.define(args -> args.apply(q -> {
					Goal first = defer(() -> {
						Unifiable<Integer> a = lvar();
						return q.unifies(Tuple.of(a, 1));
					});
					Goal second = defer(() -> {
						Unifiable<Integer> b = lvar();
						return q.unifies(Tuple.of(b, 1));
					});
					return first.or(second);
				}));

		Unifiable<Object> p = lvar();
		long count = pairs.apply(Tuple.of(p)).solve(p).count();

		assertThat(count).isEqualTo(1);
	}

	@Test
	public void shouldRejectTabledCallsUnderActiveConstraints() {
		// variant keys ignore constraint stores, so a cache shared between
		// differently-constrained callers would be unsound
		Tabled<Tuple1<Unifiable<Integer>>> anything =
				Tabling.define(args -> args.apply(x -> x.unifies(1)));

		Unifiable<Integer> constrained = lvar();
		Unifiable<Integer> x = lvar();

		assertThatThrownBy(() ->
				Disequality.separate(constrained, lval(3))
						.and(anything.apply(Tuple.of(x)))
						.solve(x)
						.count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("constraint");
	}

	@Test
	public void shouldRejectConstrainedAnswers() {
		// an answer whose variables carry residual constraints would be cached
		// as an unconstrained template and replayed too generally
		Tabled<Tuple1<Unifiable<Integer>>> notThree =
				Tabling.define(args -> args.apply(x ->
						Disequality.separate(x, lval(3))));

		Unifiable<Integer> x = lvar();

		assertThatThrownBy(() -> notThree.apply(Tuple.of(x)).solve(x).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("non-projectable");
	}

	private final Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> pathNoSelf =
			Tabling.define(args -> args.apply((x, y) ->
					edge(x, y)
							.or(defer(() -> {
								Unifiable<Integer> z = lvar();
								return pathNoSelf(x, z).and(edge(z, y));
							}))));

	private Goal pathNoSelf(Unifiable<Integer> x, Unifiable<Integer> y) {
		return pathNoSelf.apply(Tuple.of(x, y));
	}

	@Test(timeout = 5000)
	public void shouldRecurseThroughAFieldWithoutTheSelfHandle() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		long count = x.unifies(1).and(y.unifies(4))
				.and(pathNoSelf(x, y))
				.solve(lvar())
				.count();

		assertThat(count).isEqualTo(1);
	}

}
