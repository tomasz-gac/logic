package com.tgac.logic.tabling;

// ABOUTME: Receipt: plain define-path tabling handles self-recursion through
// ABOUTME: completion detection — defineRecursive is Java knot-tying, not a semantic mode.

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.TestSchedulers;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.Tuple2;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * The residence arc's foundation: a recursive call routed through a plain
 * {@link Tabling#define}d relation — knot-tied by a holder instead of
 * {@code defineRecursive}'s self handle — is an ordinary tabled consumer
 * whose park completion detection reads, so rings seal and answers match
 * the self-handle oracle exactly.
 */
public class DefineRecursionReceiptTest {

	private static Goal edge(int[][] edges, Unifiable<Integer> x, Unifiable<Integer> y) {
		return Arrays.stream(edges)
				.map(e -> (Goal) x.unifies(e[0]).and(y.unifies(e[1])))
				.reduce(Goal::or)
				.orElseGet(Goal::failure);
	}

	/** reach = edge ∨ (reach ; edge), recursing through the HOLDER, not self. */
	private static Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> knotTied(int[][] edges) {
		AtomicReference<Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>>> hole =
				new AtomicReference<>();
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> reach =
				Tabling.define(args -> args.apply((x, y) ->
						edge(edges, x, y)
								.or(defer(() -> {
									Unifiable<Integer> z = lvar();
									return hole.get().apply(Tuple.of(x, z)).and(edge(edges, z, y));
								}))));
		hole.set(reach);
		return reach;
	}

	/** The same rule through defineRecursive — the oracle. */
	private static Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> withSelf(int[][] edges) {
		return Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
				edge(edges, x, y)
						.or(defer(() -> {
							Unifiable<Integer> z = lvar();
							return self.apply(Tuple.of(x, z)).and(edge(edges, z, y));
						}))));
	}

	private static List<String> reachableFrom(
			Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> reach, int from) {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		return x.unifies(from).and(reach.apply(Tuple.of(x, y)))
				.solve(y, TestSchedulers.factory())
				.map(Object::toString)
				.sorted()
				.collect(Collectors.toList());
	}

	private static final int[][] CHAIN = {{1, 2}, {2, 3}, {3, 4}};
	private static final int[][] DIAMOND = {{1, 2}, {1, 3}, {2, 4}, {3, 4}};
	private static final int[][] CYCLE = {{1, 2}, {2, 3}, {3, 1}};

	@Test(timeout = 5000)
	public void aChainReachesThroughPlainDefine() {
		assertThat(reachableFrom(knotTied(CHAIN), 1))
				.isEqualTo(reachableFrom(withSelf(CHAIN), 1))
				.containsExactlyInAnyOrder("{2}", "{3}", "{4}");
	}

	@Test(timeout = 5000)
	public void aDiamondFoldsItsDerivations() {
		assertThat(reachableFrom(knotTied(DIAMOND), 1))
				.isEqualTo(reachableFrom(withSelf(DIAMOND), 1))
				.containsExactlyInAnyOrder("{2}", "{3}", "{4}");
	}

	@Test(timeout = 5000)
	public void aCycleSealsAsAGroup() {
		assertThat(reachableFrom(knotTied(CYCLE), 1))
				.isEqualTo(reachableFrom(withSelf(CYCLE), 1))
				.containsExactlyInAnyOrder("{1}", "{2}", "{3}");
	}

	@Test(timeout = 5000)
	public void leftRecursionCompletes() {
		// recursive disjunct first, recursive call first in its conjunction
		AtomicReference<Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>>> hole =
				new AtomicReference<>();
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> reach =
				Tabling.define(args -> args.apply((x, y) ->
						defer(() -> {
							Unifiable<Integer> z = lvar();
							return hole.get().apply(Tuple.of(x, z)).and(edge(CHAIN, z, y));
						})
								.or(edge(CHAIN, x, y))));
		hole.set(reach);
		assertThat(reachableFrom(reach, 1)).containsExactlyInAnyOrder("{2}", "{3}", "{4}");
	}

	@Test(timeout = 5000)
	public void mutualRecursionSealsAcrossTwoRelations() {
		// even/odd over successor chains: even(n) ⟸ n=0 ∨ odd(n-1); odd(n) ⟸ even(n-1)
		int[][] pred = {{4, 3}, {3, 2}, {2, 1}, {1, 0}};
		AtomicReference<Tabled<Tuple1<Unifiable<Integer>>>> evenHole = new AtomicReference<>();
		AtomicReference<Tabled<Tuple1<Unifiable<Integer>>>> oddHole = new AtomicReference<>();
		Tabled<Tuple1<Unifiable<Integer>>> even =
				Tabling.define(args -> args.apply(n ->
						n.unifies(0)
								.or(defer(() -> {
									Unifiable<Integer> m = lvar();
									return edge(pred, n, m).and(oddHole.get().apply(Tuple.of(m)));
								}))));
		Tabled<Tuple1<Unifiable<Integer>>> odd =
				Tabling.define(args -> args.apply(n -> defer(() -> {
					Unifiable<Integer> m = lvar();
					return edge(pred, n, m).and(evenHole.get().apply(Tuple.of(m)));
				})));
		evenHole.set(even);
		oddHole.set(odd);

		Unifiable<Integer> n = lvar();
		List<String> evens = n.unifies(4).and(even.apply(Tuple.of(n)))
				.solve(n, TestSchedulers.factory())
				.map(Object::toString).collect(Collectors.toList());
		assertThat(evens).containsExactly("{4}");

		Unifiable<Integer> n2 = lvar();
		List<String> notOdd = n2.unifies(4).and(odd.apply(Tuple.of(n2)))
				.solve(n2, TestSchedulers.factory())
				.map(Object::toString).collect(Collectors.toList());
		assertThat(notOdd).isEmpty();
	}
}
