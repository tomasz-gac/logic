package com.tgac.logic.tabling;

// ABOUTME: A murder mystery where tabling and TCLP are both load-bearing: cyclic
// ABOUTME: reachability diverges untabled, and the murder hour rides as a witness.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.collection.Array;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.List;
import org.junit.Test;

/**
 * Murder at the manor, and this time the manor fights back.
 *
 * The floor plan is a RING: rooms 1→2→3→4→5 along the corridor, and the
 * servants' stair closes the loop 5→1. The body is found in room 4; the
 * coroner's window is hour 1..3, the exact hour never established. The
 * killer had to be AT the scene at the murder hour, at the garden door
 * (room 5) one hour later — and the garden door must have a ROUTE back to
 * the scene, because the killer returned to "discover" the body.
 *
 * Three mechanisms carry this program, and each is load-bearing:
 *
 * 1. TABLING. The route runs through a CYCLE: every lap of the ring is
 *    another derivation, so the untabled query has infinitely many proofs —
 *    the first test shows the stream never drying. Tabled, the ring seals
 *    at five rooms.
 * 2. REGION KEYS. The recursive reach call happens under live FD domains
 *    (and, inside the investigation, under the coroner's constraints) —
 *    the wall that stood here before TCLP refused any tabled call under a
 *    non-empty constraint store.
 * 3. WITNESS LOCALS. The murder hour is a body local: it rides each cached
 *    answer as an existential with its domain and couplings. The cook is
 *    cached "guilty GIVEN some hour" — her multiplication law waits — and
 *    is acquitted only at consumption, when labelling her witness finds no
 *    hour fitting both laws. Before stage 2.5, a constraint reaching a
 *    body local was refused at the answer.
 */
public class WhodunnitTest {

	private static Domain<Integer> dom(int... values) {
		return EnumeratedDomain.of(Array.ofAll(Arrays.stream(values).boxed())
				.map(Arithmetic::ofCasted));
	}

	private static final Domain<Integer> ROOMS = dom(1, 2, 3, 4, 5);

	/** One step along the ring: the corridor, or the servants' stair 5→1. */
	private static Goal doorBetween(Unifiable<Integer> from, Unifiable<Integer> to) {
		return FiniteDomain.addo(from, lval(1), to)
				.or(unify(from, lval(5)).and(unify(to, lval(1))));
	}

	/** Rooms reachable from the garden door — tabled transitive closure. */
	private static Tabled<Tuple1<Unifiable<Integer>>> reachableFromGarden() {
		return Tabling.defineRecursive(self -> args -> args.apply(room ->
				unify(room, lval(5))
						.or(Logic.<Integer> exist(prev ->
								FiniteDomain.dom(prev, ROOMS)
										.and(FiniteDomain.dom(room, ROOMS))
										.and(Goal.defer(() -> self.apply(Tuple.of(prev))))
										.and(doorBetween(prev, room))))));
	}

	/** The same relation, no table — every lap of the ring is a new proof. */
	private static Goal reachableUntabled(Unifiable<Integer> room) {
		return unify(room, lval(5))
				.or(Logic.<Integer> exist(prev ->
						FiniteDomain.dom(prev, ROOMS)
								.and(FiniteDomain.dom(room, ROOMS))
								.and(Goal.defer(() -> reachableUntabled(prev)))
								.and(doorBetween(prev, room))));
	}

	/** Suspect {@code s} is in room {@code r} at hour {@code t} — by habit. */
	private static Goal at(Unifiable<Integer> s, Unifiable<Integer> t, Unifiable<Integer> r) {
		return unify(s, lval(1)).and(unify(r, t))                          // butler: rounds, room = hour
				.or(unify(s, lval(2)).and(FiniteDomain.multo(t, lval(2), r)))   // cook: room = 2·hour
				.or(unify(s, lval(3)).and(FiniteDomain.addo(t, lval(2), r)));   // gardener: room = hour + 2
	}

	/** Opportunity, escape, and the return route — all through the unknown hour. */
	private static Tabled<Tuple1<Unifiable<Integer>>> couldHaveDoneIt(
			Tabled<Tuple1<Unifiable<Integer>>> reachable) {
		return Tabling.define(args -> args.apply(who ->
				Logic.<Integer, Integer> exist((t, after) ->
						FiniteDomain.dom(t, dom(1, 2, 3))                // the coroner's window
								.and(FiniteDomain.dom(after, dom(2, 3, 4)))
								.and(FiniteDomain.addo(t, lval(1), after))
								.and(at(who, t, lval(4)))                // at the scene…
								.and(at(who, after, lval(5)))            // …out the garden door…
								.and(reachable.apply(Tuple.of(lval(4)))))));  // …and back, to "discover" the body
	}

	@Test(timeout = 10_000)
	public void withoutTablingEveryLapOfTheRingIsAnotherProof() {
		// the ring makes reachability a program with infinitely many
		// derivations: 5, then 5→1, then 5→1→2, … — the stream yields as
		// many emissions as we care to take and would never complete. This
		// is the query tabling exists for
		Unifiable<Integer> room = lvar();
		long emissions = reachableUntabled(room)
				.solve(room)
				.limit(12)
				.count();
		assertThat(emissions).isEqualTo(12);   // …and it would keep going
	}

	@Test(timeout = 10_000)
	public void tablingSealsTheRing() {
		// same relation, tabled: the cycle collapses to five answers and the
		// query TERMINATES — the recursive call joins its own entry under
		// live domains (a region key; the pre-TCLP wall refused this call)
		Unifiable<Integer> room = lvar();
		List<Integer> rooms = reachableFromGarden().apply(Tuple.of(room))
				.solve(room)
				.map(Term::<Integer> get)
				.distinct()
				.sorted()
				.collect(Collectors.toList());
		assertThat(rooms).containsExactly(1, 2, 3, 4, 5);
	}

	@Test(timeout = 10_000)
	public void theGardenerDidIt() {
		Tabled<Tuple1<Unifiable<Integer>>> investigation =
				couldHaveDoneIt(reachableFromGarden());
		Package p = Package.empty().withStore(Table.empty());

		Unifiable<Integer> who = lvar();
		List<Integer> culprits = investigation.apply(Tuple.of(who))
				.solveFrom(p, who, BreadthFirstScheduler::new)
				.map(Term::<Integer> get)
				.distinct()
				.collect(Collectors.toList());
		assertThat(culprits).containsExactly(3);

		// the cache is more interesting than the verdict: the cook is cached
		// "guilty GIVEN some hour" — her multiplication law waits for a
		// ground hour, so the body cannot decide her. She is acquitted at
		// CONSUMPTION, when labelling her cached witness finds no hour that
		// fits both of her laws. Elimination from the cached region, not in
		// the search
		TableEntry<?> investigationEntry = p.getStore(Table.class).entries().stream()
				.filter(e -> e.getAnswerCount() == 2)
				.findFirst().orElse(null);
		assertThat(investigationEntry).isNotNull();
	}

	@Test(timeout = 10_000)
	public void accusingTheCookReusesTheInvestigation() {
		Tabled<Tuple1<Unifiable<Integer>>> investigation =
				couldHaveDoneIt(reachableFromGarden());
		Package p = Package.empty().withStore(Table.empty());

		// the inspector's open question first — this runs the masters once
		Unifiable<Integer> who = lvar();
		investigation.apply(Tuple.of(who))
				.solveFrom(p, who, BreadthFirstScheduler::new)
				.count();
		int investigated = p.getStore(Table.class).entries().size();

		// the accusation is a BOUND call: no body re-runs, no new entries —
		// it reads the open investigation's cache through subsumption and
		// labels the cook's cached witness, which clears her
		Unifiable<Integer> cook = lvar();
		long accusation = unify(cook, lval(2))
				.and(investigation.apply(Tuple.of(cook)))
				.solveFrom(p, cook, BreadthFirstScheduler::new)
				.count();
		assertThat(accusation).isEqualTo(0);
		assertThat(p.getStore(Table.class).entries()).hasSize(investigated);
	}
}
