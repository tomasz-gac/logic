package com.tgac.logic.tabling;

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.Tuple2;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Behavioral tests for tabled relations over family-tree and graph domains.
 */
public class TablingTest {

	/**
	 * parent(X, Y) means X is a parent of Y.
	 */
	private Goal parent(Unifiable<String> x, Unifiable<String> y) {
		Goal case1 = x.unifies("alice").and(y.unifies("bob"));
		Goal case2 = x.unifies("bob").and(y.unifies("charlie"));
		Goal case3 = x.unifies("charlie").and(y.unifies("david"));

		return case1.or(case2).or(case3).named("parent(" + x + ", " + y + ")");
	}

	/**
	 * ancestor(X, Y) :- parent(X, Y).
	 * ancestor(X, Y) :- parent(X, Z), ancestor(Z, Y).
	 */
	private final Tabled<Tuple2<Unifiable<String>, Unifiable<String>>> ancestor =
			Tabling.define(args -> args.apply((x, y) ->
					parent(x, y)
							.or(defer(() -> {
								Unifiable<String> z = lvar();
								return parent(x, z).and(ancestor(z, y));
							}))));

	private Goal ancestor(Unifiable<String> x, Unifiable<String> y) {
		return ancestor.apply(Tuple.of(x, y));
	}

	@Test
	public void testTabledAncestorWithGroundArguments() {
		// Query: ancestor("alice", "charlie") - is alice an ancestor of charlie?
		Unifiable<String> alice = lvar();
		Unifiable<String> charlie = lvar();
		Unifiable<String> result = lvar();

		Goal query = alice.unifies("alice")
				.and(charlie.unifies("charlie"))
				.and(ancestor(alice, charlie))
				.and(result.unifies("yes"));

		Stream<Reified<String>> results = query.solve(result);

		// Should find answer (through bob)
		assertThat(results.count()).isEqualTo(1);
	}

	@Test
	public void testTabledAncestorFindDescendants() {
		// Query: Who are all of alice's descendants?
		Unifiable<String> x = lvar();
		Unifiable<String> y = lvar();

		Goal query = x.unifies("alice").and(ancestor(x, y));

		List<String> descendants = query.solve(y)
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(descendants).hasSize(3);
		assertThat(descendants).containsExactlyInAnyOrder("bob", "charlie", "david");
	}

	@Test(timeout = 10000)
	public void testParallelTabledDescendants() {
		// tabling under the fork/join scheduler: TableEntry's synchronization
		// must hold up under real concurrency
		for (int i = 0; i < 20; i++) {
			Unifiable<String> x = lvar();
			Unifiable<String> y = lvar();

			List<String> descendants = x.unifies("alice").and(ancestor(x, y))
					.solveParallel(y)
					.map(Term::get)
					.collect(Collectors.toList());

			assertThat(descendants).containsExactlyInAnyOrder("bob", "charlie", "david");
		}
	}

	@Test(timeout = 15000)
	public void testParallelLeftRecursion() {
		class PathGoal {
			Goal edge(Unifiable<Integer> x, Unifiable<Integer> y) {
				return x.unifies(1).and(y.unifies(2))
						.or(x.unifies(2).and(y.unifies(3)))
						.or(x.unifies(3).and(y.unifies(4)));
			}

			final Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> path =
					Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
							edge(x, y)
									.or(defer(() -> {
										Unifiable<Integer> z = lvar();
										return self.apply(Tuple.of(x, z)).and(edge(z, y));
									}))));
		}

		for (int i = 0; i < 20; i++) {
			PathGoal pg = new PathGoal();
			Unifiable<Integer> x = lvar();
			Unifiable<Integer> y = lvar();

			long count = x.unifies(1).and(y.unifies(4)).and(pg.path.apply(Tuple.of(x, y)))
					.solveParallel(lvar())
					.count();

			assertThat(count).isEqualTo(1);
		}
	}

	@Test
	public void testTabledAncestorFindAncestors() {
		// Query: Who are all of david's ancestors?
		Unifiable<String> x = lvar();
		Unifiable<String> y = lvar();

		Goal query = y.unifies("david").and(ancestor(x, y));

		List<String> ancestors = query.solve(x)
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(ancestors).hasSize(3);
		assertThat(ancestors).containsExactlyInAnyOrder("alice", "bob", "charlie");
	}

	@Test
	public void testRepeatedSolvesAreIndependent() {
		// Each solve gets a fresh table, so a second identical query is
		// unaffected by the first solve's cache
		for (int i = 0; i < 2; i++) {
			Unifiable<String> x = lvar();
			Unifiable<String> y = lvar();

			List<String> descendants = x.unifies("alice").and(ancestor(x, y))
					.solve(y)
					.map(Term::get)
					.collect(Collectors.toList());

			assertThat(descendants).containsExactlyInAnyOrder("bob", "charlie", "david");
		}
	}

	// ============================================
	// SIMPLE TESTS - Basic edge cases
	// ============================================

	@Test
	public void testTabledGoalWithNoAnswers() {
		// A tabled relation whose body can never succeed
		Tabled<Tuple1<Unifiable<Integer>>> alwaysFail =
				Tabling.define(args -> args.apply(x ->
						x.unifies(1).and(x.unifies(2))));

		Unifiable<Integer> x = lvar();
		long count = x.unifies(42).and(alwaysFail.apply(Tuple.of(x))).solve(x).count();

		assertThat(count).isEqualTo(0);
	}

	@Test
	public void testTabledGoalWithSingleAnswer() {
		Tabled<Tuple1<Unifiable<Integer>>> single =
				Tabling.define(args -> args.apply(x ->
						x.unifies(42)));

		Unifiable<Integer> x = lvar();
		List<Integer> results = single.apply(Tuple.of(x)).solve(x)
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(results).containsExactly(42);
	}

	@Test
	public void testTabledGoalWithNonGroundArgs() {
		// A call with free arguments tables on the variant key
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> numRel =
				Tabling.define(args -> args.apply((a, b) ->
						a.unifies(1).and(b.unifies(2))
								.or(a.unifies(2).and(b.unifies(3)))
								.or(a.unifies(3).and(b.unifies(4)))));

		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		List<Tuple2<Integer, Integer>> results = numRel.apply(Tuple.of(x, y))
				.solve(lval(Tuple.of(x, y)))
				.map(Term::get)
				.map(t -> t.map1(Term::get).map2(Term::get))
				.collect(Collectors.toList());

		assertThat(results).hasSize(3);
	}

	// ============================================
	// MUTUALLY RECURSIVE PREDICATES
	// ============================================

	private final Tabled<Tuple1<Unifiable<Integer>>> even =
			Tabling.define(args -> args.apply(num -> {
				// Base case: 0 is even
				Goal base = num.unifies(0);

				// Recursive: N is even if N-1 is odd
				Goal rec = defer(() -> {
					Unifiable<Integer> n1 = lvar();
					return num.unifies(2).and(n1.unifies(1).and(odd(n1)))
							.or(num.unifies(4).and(n1.unifies(3).and(odd(n1))))
							.or(num.unifies(6).and(n1.unifies(5).and(odd(n1))))
							.or(num.unifies(8).and(n1.unifies(7).and(odd(n1))))
							.or(num.unifies(10).and(n1.unifies(9).and(odd(n1))));
				});

				return base.or(rec);
			}));

	private final Tabled<Tuple1<Unifiable<Integer>>> odd =
			Tabling.define(args -> args.apply(num -> {
				// Base case: 1 is odd
				Goal base = num.unifies(1);

				// Recursive: N is odd if N-1 is even
				Goal rec = defer(() -> {
					Unifiable<Integer> n1 = lvar();
					return num.unifies(3).and(n1.unifies(2).and(even(n1)))
							.or(num.unifies(5).and(n1.unifies(4).and(even(n1))))
							.or(num.unifies(7).and(n1.unifies(6).and(even(n1))))
							.or(num.unifies(9).and(n1.unifies(8).and(even(n1))));
				});

				return base.or(rec);
			}));

	private Goal even(Unifiable<Integer> n) {
		return even.apply(Tuple.of(n));
	}

	private Goal odd(Unifiable<Integer> n) {
		return odd.apply(Tuple.of(n));
	}

	@Test
	public void testMutuallyRecursiveEvenOdd() {
		Unifiable<Integer> x = lvar();

		// Check that 10 is even
		long evenCount = x.unifies(10).and(even(x)).solve(x).count();
		assertThat(evenCount).isEqualTo(1);

		// Check that 9 is odd
		long oddCount = x.unifies(9).and(odd(x)).solve(x).count();
		assertThat(oddCount).isEqualTo(1);

		// Check that 10 is not odd
		long notOddCount = x.unifies(10).and(odd(x)).solve(x).count();
		assertThat(notOddCount).isEqualTo(0);
	}

	// ============================================
	// STRESS TESTS - Deep recursion
	// ============================================

	@Test(timeout = 10000)
	public void testDeepRecursionChain() {
		// Build a chain: 0->1->2->...->50
		// Test reachability with deep recursion
		class ReachGoal {
			// Define links: 0->1, 1->2, ..., 49->50
			Goal link(Unifiable<Integer> x, Unifiable<Integer> y) {
				Goal result = x.unifies(0).and(y.unifies(1));
				for (int i = 1; i < 50; i++) {
					final int from = i;
					final int to = i + 1;
					result = result.or(x.unifies(from).and(y.unifies(to)));
				}
				return result;
			}

			final Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> reach =
					Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
							link(x, y)
									.or(defer(() -> {
										Unifiable<Integer> z = lvar();
										return link(x, z).and(self.apply(Tuple.of(z, y)));
									}))));
		}

		ReachGoal rg = new ReachGoal();
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		// Can we reach 50 from 0?
		long count = x.unifies(0).and(y.unifies(50)).and(rg.reach.apply(Tuple.of(x, y)))
				.solve(lvar())
				.count();

		assertThat(count).isEqualTo(1);
	}

	// ============================================
	// STRESS TESTS - Many answers
	// ============================================

	@Test
	public void testManyAnswers() {
		Tabled<Tuple1<Unifiable<Integer>>> manyNumbers =
				Tabling.define(args -> args.apply(x -> {
					Goal result = Goal.failure();
					for (int i = 0; i < 100; i++) {
						final int num = i;
						result = result.or(x.unifies(num));
					}
					return result;
				}));

		Unifiable<Integer> x = lvar();
		List<Integer> results = manyNumbers.apply(Tuple.of(x)).solve(x)
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(results).hasSize(100);
		assertThat(results).contains(0, 50, 99);
	}

	// ============================================
	// STRESS TESTS - Multiple concurrent consumers
	// ============================================

	@Test(timeout = 5000)
	public void testMultipleConcurrentSlaves() {
		// Multiple applications of the same relation in one query: the first
		// becomes master, the others consume its cache
		Unifiable<String> x1 = lvar();
		Unifiable<String> y1 = lvar();
		Unifiable<String> x2 = lvar();
		Unifiable<String> y2 = lvar();
		Unifiable<String> x3 = lvar();
		Unifiable<String> y3 = lvar();

		Goal query = x1.unifies("alice").and(y1.unifies("david"))
				.and(ancestor(x1, y1))
				.and(x2.unifies("alice")).and(y2.unifies("david"))
				.and(ancestor(x2, y2))
				.and(x3.unifies("alice")).and(y3.unifies("david"))
				.and(ancestor(x3, y3));

		long count = query.solve(lvar()).count();

		// alice is ancestor of david via bob -> charlie -> david
		assertThat(count).isEqualTo(1);
	}

	// ============================================
	// COMPLEX NESTED STRUCTURES
	// ============================================

	@Test
	public void testTablingWithNestedStructures() {
		Tabled<Tuple1<Unifiable<Tuple2<String, String>>>> familyPair =
				Tabling.define(args -> args.apply(p ->
						p.unifies(Tuple.of("alice", "bob"))
								.or(p.unifies(Tuple.of("bob", "charlie")))
								.or(p.unifies(Tuple.of("charlie", "david")))));

		Unifiable<Tuple2<String, String>> pair = lvar();
		List<Tuple2<String, String>> results = familyPair.apply(Tuple.of(pair))
				.solve(pair)
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(results).hasSize(3);
		assertThat(results).contains(
				Tuple.of("alice", "bob"),
				Tuple.of("bob", "charlie"),
				Tuple.of("charlie", "david"));
	}

	@Test
	public void bareUnifiableArgumentsWorkViaInternalWrapping() {
		// a bare Unifiable is an equality atom to decompose, which once
		// silently collapsed all answers into one (#41); the chokepoint now
		// wraps it in a Tuple1 internally, so keys, answers and consumption
		// all take the structural path — the body still sees the bare arg
		Tabled<Unifiable<Integer>> rel = Tabling.define(x ->
				x.unifies(1).or(x.unifies(2)));
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();

		// second call consumes the cache: 2 × 2 pairs proves both the
		// producer and the consumer paths see through the wrapping
		List<String> pairs = rel.apply(a).and(rel.apply(b))
				.solve(lval(Tuple.of(a, b)))
				.map(Object::toString)
				.collect(Collectors.toList());
		assertThat(pairs).hasSize(4);
	}
}
