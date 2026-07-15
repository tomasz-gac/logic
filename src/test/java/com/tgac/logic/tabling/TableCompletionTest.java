package com.tgac.logic.tabling;

// ABOUTME: Pins Tier 1 completion detection: entries complete from counter events
// ABOUTME: (no end-of-search hook exists), variant cycles stay incomplete — sound.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.functional.fibers.schedulers.ForkJoinScheduler;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.Tuple2;
import org.junit.Test;

/**
 * Completion can only arrive through the production counters — there is no
 * end-of-search fallback — so observing a completed entry after the solve
 * proves the event-driven machinery, and observing an INCOMPLETE entry
 * proves its soundness on the cases Tier 1 refuses.
 */
public class TableCompletionTest {

	private static Goal edge(Unifiable<Integer> x, Unifiable<Integer> y) {
		return unify(x, lval(1)).and(unify(y, lval(2)))
				.or(unify(x, lval(2)).and(unify(y, lval(3))));
	}

	@Test
	public void nonRecursiveEntryCompletes() {
		Tabled<Tuple1<Unifiable<Integer>>> rel = Tabling.define(t -> t.apply(x ->
				unify(x, lval(1)).or(unify(x, lval(2)))));
		Unifiable<Integer> out = lvar();
		Package p = Package.empty().withStore(Table.empty());

		assertThat(rel.apply(Tuple.of(out)).solveFrom(p, out, BreadthFirstScheduler::new).count())
				.isEqualTo(2);
		assertThat(p.getStore(Table.class).entries())
				.allMatch(TableEntry::isComplete);
	}

	@Test
	public void leftRecursivePathCompletes() {
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> path =
				Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
						edge(x, y).or(Goal.defer(() -> {
							Unifiable<Integer> z = lvar();
							return self.apply(Tuple.of(x, z)).and(edge(z, y));
						}))));
		Unifiable<Integer> y = lvar();
		Package p = Package.empty().withStore(Table.empty());

		// path(1, Y) over 1→2→3: answers 2 and 3
		assertThat(path.apply(Tuple.of(lval(1), y)).solveFrom(p, y, BreadthFirstScheduler::new).count())
				.isEqualTo(2);
		assertThat(p.getStore(Table.class).entries())
				.allMatch(TableEntry::isComplete);
	}

	@Test(timeout = 5000)
	public void pureSelfLoopSealsEmptyInsteadOfHanging() {
		// p(X) :- p(X). — self-recursion with the SAME argument, no base case.
		// The body's only derivation consumes p's own not-yet-produced answers,
		// catches up at index 0, and parks HOME (its coat is this very entry).
		// The master fiber then finishes (its body was that parked consume), the
		// ledger drains, and Tier 1 seals the entry — parked-home is safe. So it
		// resolves to ZERO answers instead of looping forever.
		Tabled<Tuple1<Unifiable<Integer>>> p =
				Tabling.defineRecursive(self -> t -> t.apply(x ->
						Goal.defer(() -> self.apply(Tuple.of(x)))));
		Unifiable<Integer> out = lvar();
		Package pkg = Package.empty().withStore(Table.empty());

		assertThat(p.apply(Tuple.of(out)).solveFrom(pkg, out, BreadthFirstScheduler::new).count())
				.isEqualTo(0);
		assertThat(pkg.getStore(Table.class).entries())
				.allMatch(TableEntry::isComplete);
	}

	@Test
	public void nestedMutualRecursionCompletesBottomUp() {
		// p :- 42 | q. q :- p. — q's master runs INSIDE p's production, so this
		// is nested mastering, not a parked ring: the cascade completes both
		Tabled<Tuple1<Unifiable<Integer>>>[] q = new Tabled[1];
		Tabled<Tuple1<Unifiable<Integer>>> pRel = Tabling.defineRecursive(self -> t -> t.apply(x ->
				unify(x, lval(42)).or(Goal.defer(() -> q[0].apply(Tuple.of(x))))));
		q[0] = Tabling.define(t -> t.apply(x ->
				Goal.defer(() -> pRel.apply(Tuple.of(x)))));
		Unifiable<Integer> out = lvar();
		Package pkg = Package.empty().withStore(Table.empty());

		assertThat(pRel.apply(Tuple.of(out)).solveFrom(pkg, out, BreadthFirstScheduler::new).count())
				.isEqualTo(1);
		assertThat(pkg.getStore(Table.class).entries())
				.allMatch(TableEntry::isComplete);
	}

	@Test
	public void crossConsumingRingSealsByGroupSeal() {
		// both relations mastered independently at the top level, each then
		// CONSUMING the other: a genuine parked ring — the singleton rule
		// refuses both, and the GROUP SEAL completes the sleeper-SCC at once
		Tabled<Tuple1<Unifiable<Integer>>>[] rels = new Tabled[2];
		rels[0] = Tabling.define(t -> t.apply(x ->
				unify(x, lval(1)).or(Goal.defer(() -> rels[1].apply(t)))));
		rels[1] = Tabling.define(t -> t.apply(x ->
				unify(x, lval(2)).or(Goal.defer(() -> rels[0].apply(t)))));
		Unifiable<Integer> a = lvar();
		Package pkg = Package.empty().withStore(Table.empty());

		Goal query = rels[0].apply(Tuple.of(a)).or(rels[1].apply(Tuple.of(a)));
		assertThat(query.solveFrom(pkg, a, BreadthFirstScheduler::new).distinct().count())
				.isEqualTo(2);
		assertThat(pkg.getStore(Table.class).entries())
				.allMatch(TableEntry::isComplete);
	}

	@Test
	public void variantChainOfNestedMastersSealsBottomUp() {
		// RIGHT recursion splits variants: path(1,_) mints path(2,_) mints
		// path(3,_) — each a fresh call event mastered under its own coat,
		// the caller's coat remembered only for answer billing. A pure DAG
		// of master edges, no sleeper anywhere: every variant seals.
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> path =
				Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
						edge(x, y).or(Goal.defer(() -> {
							Unifiable<Integer> z = lvar();
							return edge(x, z).and(self.apply(Tuple.of(z, y)));
						}))));
		Unifiable<Integer> y = lvar();
		Package p = Package.empty().withStore(Table.empty());

		assertThat(path.apply(Tuple.of(lval(1), y)).solveFrom(p, y, BreadthFirstScheduler::new).count())
				.isEqualTo(2);
		assertThat(p.getStore(Table.class).entries())
				.hasSize(3)
				.allMatch(TableEntry::isComplete);
	}

	@Test
	public void secondReaderRingSealsByGroupSeal() {
		// p :- 42 | q | q.   q :- p.  — single root, but the SECOND q-call is
		// a reader: two sleeper edges, a cycle from one root — the group
		// seal completes both together
		Tabled<Tuple1<Unifiable<Integer>>>[] q = new Tabled[1];
		Tabled<Tuple1<Unifiable<Integer>>> pRel = Tabling.defineRecursive(self -> t -> t.apply(x ->
				unify(x, lval(42))
						.or(Goal.defer(() -> q[0].apply(t)))
						.or(Goal.defer(() -> q[0].apply(t)))));
		q[0] = Tabling.define(t -> t.apply(x ->
				Goal.defer(() -> pRel.apply(Tuple.of(x)))));
		Unifiable<Integer> out = lvar();
		Package pkg = Package.empty().withStore(Table.empty());

		assertThat(pRel.apply(Tuple.of(out)).solveFrom(pkg, out, BreadthFirstScheduler::new).count())
				.isEqualTo(1);
		assertThat(pkg.getStore(Table.class).entries())
				.allMatch(TableEntry::isComplete);
	}

	@Test
	public void duplicateHeavyRelationStillCompletes() {
		// both branches derive the same answer: the duplicate fails its branch,
		// the counters must still drain
		Tabled<Tuple1<Unifiable<Integer>>> rel = Tabling.define(t -> t.apply(x ->
				unify(x, lval(5)).or(unify(x, lval(5)))));
		Unifiable<Integer> out = lvar();
		Package p = Package.empty().withStore(Table.empty());

		assertThat(rel.apply(Tuple.of(out)).solveFrom(p, out, BreadthFirstScheduler::new).count())
				.isEqualTo(1);
		assertThat(p.getStore(Table.class).entries())
				.allMatch(TableEntry::isComplete);
	}

	@Test
	public void completedEntryPricesExactlyWithoutManualMarking() {
		// the ∞→exact transition end to end: solve completes the entry, the
		// SAME call then prices its answer count
		Tabled<Tuple1<Unifiable<Integer>>> rel = Tabling.define(t -> t.apply(x ->
				unify(x, lval(1)).or(unify(x, lval(2)))));
		Unifiable<Integer> out = lvar();
		Goal call = rel.apply(Tuple.of(out));
		Package p = Package.empty().withStore(Table.empty());

		assertThat(call.solveFrom(p, out, BreadthFirstScheduler::new).count()).isEqualTo(2);
		assertThat(((Bounded) call).answers(p)).isEqualTo(2);
	}

	@Test
	public void parallelSchedulerReachesTheSameCompletions() {
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> path =
				Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
						edge(x, y).or(Goal.defer(() -> {
							Unifiable<Integer> z = lvar();
							return self.apply(Tuple.of(x, z)).and(edge(z, y));
						}))));
		Unifiable<Integer> y = lvar();
		Package p = Package.empty().withStore(Table.empty());

		assertThat(path.apply(Tuple.of(lval(1), y)).solveFrom(p, y, ForkJoinScheduler::new).count())
				.isEqualTo(2);
		assertThat(p.getStore(Table.class).entries())
				.allMatch(TableEntry::isComplete);
	}

	@Test
	public void completedEntriesDiscardTheirDeadRegistrations() {
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> path =
				Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
						edge(x, y).or(Goal.defer(() -> {
							Unifiable<Integer> z = lvar();
							return self.apply(Tuple.of(x, z)).and(edge(z, y));
						}))));
		Unifiable<Integer> y = lvar();
		Package p = Package.empty().withStore(Table.empty());
		path.apply(Tuple.of(lval(1), y)).solveFrom(p, y, BreadthFirstScheduler::new).count();

		assertThat(p.getStore(Table.class).entries())
				.allMatch(e -> e.registrationCount() == 0);
	}
}
