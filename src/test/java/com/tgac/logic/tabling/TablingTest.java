package com.tgac.logic.tabling;

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.RoundRobin;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Tests for the Tabling.tabled() wrapper that actually uses tabling.
 */
public class TablingTest {

	/**
	 * Define parent relationships using proper types.
	 * parent(X, Y) means X is a parent of Y.
	 */
	private Goal parent(Unifiable<String> x, Unifiable<String> y) {
		// alice is parent of bob
		Goal case1 = x.unifies("alice").and(y.unifies("bob"));
		// bob is parent of charlie
		Goal case2 = x.unifies("bob").and(y.unifies("charlie"));
		// charlie is parent of david
		Goal case3 = x.unifies("charlie").and(y.unifies("david"));

		return case1.or(case2).or(case3).named("parent(" + x + ", " + y + ")");
	}

	/**
	 * Define ancestor relationships WITH tabling.
	 * ancestor(X, Y) :- parent(X, Y).
	 * ancestor(X, Y) :- parent(X, Z), ancestor(Z, Y).
	 */
	private Goal ancestor(Unifiable<String> x, Unifiable<String> y) {
		return Tabling.tabled(
				"ancestor",
				List.of(x.getObjectUnifiable(), y.getObjectUnifiable()),
				args -> {
					// Reconstruct typed variables from args
					@SuppressWarnings("unchecked")
					Unifiable<String> x1 = (Unifiable<String>) args.get(0);
					@SuppressWarnings("unchecked")
					Unifiable<String> y1 = (Unifiable<String>) args.get(1);

					// Base case: parent(X, Y)
					Goal baseCase = parent(x1, y1);

					// Recursive case: parent(X, Z), ancestor(Z, Y)
					Goal recursiveCase = defer(() -> {
						Unifiable<String> z = lvar();
						return parent(x1, z).and(ancestor(z, y1));
					});

					return baseCase.or(recursiveCase);
				}
		).named("ancestor(" + x + ", " + y + ")");
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

		Stream<Unifiable<String>> results = query.solve(result);

		// Should find answer (through bob)
		assertThat(results.count()).isEqualTo(1);
	}

	@Test
	public void testTabledAncestorFindDescendants() {
		// Query: Who are all of alice's descendants?
		Unifiable<String> x = lvar();
		Unifiable<String> y = lvar();

		Goal query = x.unifies("alice").and(ancestor(x, y));

		Stream<Unifiable<String>> results = query.solve(y);

		// alice is ancestor of: bob, charlie, david
		java.util.List<String> descendants = results
				.map(Unifiable::get)
				.collect(Collectors.toList());

		assertThat(descendants).hasSize(3);
		assertThat(descendants).containsExactlyInAnyOrder("bob", "charlie", "david");
	}

	@Test
	public void testTabledAncestorFindAncestors() {
		// Query: Who are all of david's ancestors?
		Unifiable<String> x = lvar();
		Unifiable<String> y = lvar();

		Goal query = y.unifies("david").and(ancestor(x, y));

		Stream<Unifiable<String>> results = query.solve(x);

		// david's ancestors are: charlie, bob, alice
		java.util.List<String> ancestors = results
				.map(Unifiable::get)
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

			java.util.List<String> descendants = x.unifies("alice").and(ancestor(x, y))
					.solve(y)
					.map(Unifiable::get)
					.collect(Collectors.toList());

			assertThat(descendants).containsExactlyInAnyOrder("bob", "charlie", "david");
		}
	}

	// ============================================
	// SIMPLE TESTS - Basic edge cases
	// ============================================

	@Test
	public void testTabledGoalWithNoAnswers() {
		// A tabled goal that always fails (unifies with contradictory values)
		Goal alwaysFail = Tabling.tabled(
				"fail",
				List.of(lvar().getObjectUnifiable()),
				args -> {
					@SuppressWarnings("unchecked")
					Unifiable<Integer> x = (Unifiable<Integer>) args.get(0);
					// Contradictory unifications - can never succeed
					return x.unifies(1).and(x.unifies(2));
				}
		);

		Unifiable<Integer> x = lvar();
		long count = x.unifies(42).and(alwaysFail).solve(x).count();

		assertThat(count).isEqualTo(0);
	}

	@Test
	public void testTabledGoalWithSingleAnswer() {

		Unifiable<Integer> x = lvar();

		// A tabled goal that succeeds exactly once
		Goal single = Tabling.tabled(
				"single",
				List.of(x.getObjectUnifiable()),
				args -> {
					@SuppressWarnings("unchecked")
					Unifiable<Integer> x1 = (Unifiable<Integer>) args.get(0);
					return x1.unifies(42);
				}
		);
		java.util.List<Integer> results = single.solve(x)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		assertThat(results).containsExactly(42);
	}

	@Test
	public void testTabledGoalWithNonGroundArgs() {
		// When args are not ground, tabling should be bypassed
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		// Define a simple number relation: num(X, Y) where Y = X + 1
		Goal numRel = Tabling.tabled(
				"numRel",
				List.of(x.getObjectUnifiable(), y.getObjectUnifiable()),
				args -> {
					@SuppressWarnings("unchecked")
					Unifiable<Integer> a = (Unifiable<Integer>) args.get(0);
					@SuppressWarnings("unchecked")
					Unifiable<Integer> b = (Unifiable<Integer>) args.get(1);

					return a.unifies(1).and(b.unifies(2))
							.or(a.unifies(2).and(b.unifies(3)))
							.or(a.unifies(3).and(b.unifies(4)));
				}
		);

		// Should work even without ground args
		java.util.List<Tuple2<Integer, Integer>> results = numRel.solve(lval(Tuple.of(x, y)))
				.map(Unifiable::get)
				.map(t -> t.map1(Unifiable::get).map2(Unifiable::get))
				.collect(Collectors.toList());

		assertThat(results).hasSize(3);
	}

	// ============================================
	// MUTUALLY RECURSIVE PREDICATES
	// ============================================

	private Goal even(Unifiable<Integer> n) {
		return Tabling.tabled(
				"even",
				List.of(n.getObjectUnifiable()),
				args -> {
					@SuppressWarnings("unchecked")
					Unifiable<Integer> num = (Unifiable<Integer>) args.get(0);

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
				}
		);
	}

	private Goal odd(Unifiable<Integer> n) {
		return Tabling.tabled(
				"odd",
				List.of(n.getObjectUnifiable()),
				args -> {
					@SuppressWarnings("unchecked")
					Unifiable<Integer> num = (Unifiable<Integer>) args.get(0);

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
				}
		);
	}

	@Test
	public void testMutuallyRecursiveEvenOdd() {
		// Test mutually recursive tabled predicates
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
	// LEFT RECURSION TEST
	// ============================================

	@Test(timeout = 5000)
	public void testLeftRecursion() {
		// path(X, Y) :- edge(X, Y).
		// path(X, Y) :- path(X, Z), edge(Z, Y).  <- LEFT RECURSIVE!

		// Define path with LEFT recursion (tabling should handle this)
		class PathGoal {
			// Define edges: 1->2, 2->3, 3->4
			Goal edge(Unifiable<Integer> x, Unifiable<Integer> y) {
				return x.unifies(1).and(y.unifies(2))
						.or(x.unifies(2).and(y.unifies(3)))
						.or(x.unifies(3).and(y.unifies(4)));
			}

			Goal path(Unifiable<Integer> x, Unifiable<Integer> y) {
				return Tabling.tabled(
						"path",
						List.of(x.getObjectUnifiable(), y.getObjectUnifiable()),
						args -> {
							@SuppressWarnings("unchecked")
							Unifiable<Integer> x1 = (Unifiable<Integer>) args.get(0);
							@SuppressWarnings("unchecked")
							Unifiable<Integer> y1 = (Unifiable<Integer>) args.get(1);

							// Base case
							Goal base = edge(x1, y1);

							// LEFT recursive case: path(X, Z), edge(Z, Y)
							Goal rec = defer(() -> {
								Unifiable<Integer> z = lvar();
								return path(x1, z).and(edge(z, y1));
							});

							return base.or(rec);
						}
				);
			}
		}

		PathGoal pg = new PathGoal();
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		// Test ground queries - is there a path from 1 to 4?
		// With left recursion, this would loop infinitely without tabling
		long countTo4 = x.unifies(1).and(y.unifies(4)).and(pg.path(x, y))
				.solve(lvar())
				.count();
		assertThat(countTo4).isEqualTo(1);

		// Is there a path from 1 to 3?
		long countTo3 = x.unifies(1).and(y.unifies(3)).and(pg.path(x, y))
				.solve(lvar())
				.count();
		assertThat(countTo3).isEqualTo(1);

		// Is there a path from 1 to 2?
		long countTo2 = x.unifies(1).and(y.unifies(2)).and(pg.path(x, y))
				.solve(lvar())
				.count();
		assertThat(countTo2).isEqualTo(1);
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
				// Build the goal by chaining all the links
				Goal result = x.unifies(0).and(y.unifies(1));
				for (int i = 1; i < 50; i++) {
					final int from = i;
					final int to = i + 1;
					result = result.or(x.unifies(from).and(y.unifies(to)));
				}
				return result;
			}

			Goal reach(Unifiable<Integer> x, Unifiable<Integer> y) {
				return Tabling.tabled(
						"reach",
						List.of(x.getObjectUnifiable(), y.getObjectUnifiable()),
						args -> {
							@SuppressWarnings("unchecked")
							Unifiable<Integer> x1 = (Unifiable<Integer>) args.get(0);
							@SuppressWarnings("unchecked")
							Unifiable<Integer> y1 = (Unifiable<Integer>) args.get(1);

							Goal base = link(x1, y1);

							Goal rec = defer(() -> {
								Unifiable<Integer> z = lvar();
								return link(x1, z).and(reach(z, y1));
							});

							return base.or(rec);
						}
				);
			}
		}

		ReachGoal rg = new ReachGoal();
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		// Can we reach 50 from 0?
		long count = x.unifies(0).and(y.unifies(50)).and(rg.reach(x, y))
				.solve(lvar())
				.count();

		assertThat(count).isEqualTo(1);
	}

	// ============================================
	// STRESS TESTS - Many answers
	// ============================================

	@Test
	public void testManyAnswers() {
		// A tabled goal that generates many answers
		Unifiable<Integer> x = lvar();

		Goal manyNumbers = Tabling.tabled(
				"numbers",
				List.of(x.getObjectUnifiable()),
				args -> {
					@SuppressWarnings("unchecked")
					Unifiable<Integer> x1 = (Unifiable<Integer>) args.get(0);

					Goal result = Goal.failure();
					// Generate 100 numbers
					for (int i = 0; i < 100; i++) {
						final int num = i;
						result = result.or(x1.unifies(num));
					}
					return result;
				}
		);

		java.util.List<Integer> results = manyNumbers.solve(x)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		assertThat(results).hasSize(100);
		assertThat(results).contains(0, 50, 99);
	}

	// ============================================
	// STRESS TESTS - Multiple concurrent slaves
	// ============================================

	@Test(timeout = 5000)
	public void testMultipleConcurrentSlaves() {
		// Multiple queries to the same tabled predicate happening "concurrently"
		// (in the same solve stream)

		Unifiable<String> x1 = lvar();
		Unifiable<String> y1 = lvar();
		Unifiable<String> x2 = lvar();
		Unifiable<String> y2 = lvar();
		Unifiable<String> x3 = lvar();
		Unifiable<String> y3 = lvar();

		// Query that calls ancestor three times with the same arguments
		Goal query = x1.unifies("alice").and(y1.unifies("david"))
				.and(ancestor(x1, y1))  // First call - becomes master
				.and(x2.unifies("alice")).and(y2.unifies("david"))
				.and(ancestor(x2, y2))  // Second call - becomes slave
				.and(x3.unifies("alice")).and(y3.unifies("david"))
				.and(ancestor(x3, y3)); // Third call - becomes slave

		long count = query.solve(lvar()).count();

		// Should succeed exactly once (alice is ancestor of david via bob->charlie->david)
		assertThat(count).isEqualTo(1);
	}

	// ============================================
	// COMPLEX NESTED STRUCTURES
	// ============================================

	@Test(timeout = 5000)
	public void testSlaveReceivesAnswersProducedAfterRegistration() {
		// Two independent calls to the same tabled relation in one query.
		// The second call becomes a slave; it registers before the master has
		// produced all answers and must receive the later ones via reactivation.
		class PathGoal {
			Goal edge(Unifiable<Integer> x, Unifiable<Integer> y) {
				return x.unifies(1).and(y.unifies(2))
						.or(x.unifies(2).and(y.unifies(3)))
						.or(x.unifies(3).and(y.unifies(4)));
			}

			Goal path(Unifiable<Integer> x, Unifiable<Integer> y) {
				return Tabling.tabled(
						"path",
						List.of(x.getObjectUnifiable(), y.getObjectUnifiable()),
						args -> {
							@SuppressWarnings("unchecked")
							Unifiable<Integer> x1 = (Unifiable<Integer>) args.get(0);
							@SuppressWarnings("unchecked")
							Unifiable<Integer> y1 = (Unifiable<Integer>) args.get(1);

							Goal base = edge(x1, y1);

							Goal rec = defer(() -> {
								Unifiable<Integer> z = lvar();
								return path(x1, z).and(edge(z, y1));
							});

							return base.or(rec);
						}
				);
			}
		}

		PathGoal pg = new PathGoal();
		Unifiable<Integer> one = lvar();
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();

		// path(1, _) = {2, 3, 4}; the query pairs every a with every b
		long count = one.unifies(1)
				.and(pg.path(one, a))
				.and(pg.path(one, b))
				.solve(lval(Tuple.of(a, b)))
				.count();

		assertThat(count).isEqualTo(9);
	}

	@Test(timeout = 5000)
	public void testAlphaEquivalentAnswersAreDeduped() {
		// Both branches produce an answer of the same shape (fresh var, 1).
		// The answers are alpha-equivalent, so the table must keep only one.
		Unifiable<Object> p = lvar();

		Goal pairs = Tabling.tabled(
				"pairs",
				List.of(p.getObjectUnifiable()),
				args -> {
					Unifiable<Object> q = args.get(0).getObjectUnifiable();

					Goal first = defer(() -> {
						Unifiable<Integer> a = lvar();
						return q.unifies(Tuple.of(a, 1));
					});

					Goal second = defer(() -> {
						Unifiable<Integer> b = lvar();
						return q.unifies(Tuple.of(b, 1));
					});

					return first.or(second);
				}
		);

		long count = pairs.solve(p).count();

		assertThat(count).isEqualTo(1);
	}

	@Test
	public void testTablingWithNestedStructures() {
		// Test tabling with complex nested structures as arguments
		Unifiable<Tuple2<String, String>> pair = lvar();

		Goal familyPair = Tabling.tabled(
				"familyPair",
				List.of(pair.getObjectUnifiable()),
				args -> {
					@SuppressWarnings("unchecked")
					Unifiable<Tuple2<String, String>> p = (Unifiable<Tuple2<String, String>>) args.get(0);

					return p.unifies(Tuple.of("alice", "bob"))
							.or(p.unifies(Tuple.of("bob", "charlie")))
							.or(p.unifies(Tuple.of("charlie", "david")));
				}
		);

		java.util.List<Tuple2<String, String>> results = familyPair.solve(pair)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		assertThat(results).hasSize(3);
		assertThat(results).contains(
				Tuple.of("alice", "bob"),
				Tuple.of("bob", "charlie"),
				Tuple.of("charlie", "david")
		);
	}
}
