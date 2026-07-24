package com.tgac.logic.tabling;

// ABOUTME: A murder mystery showing TCLP with locals: the murder TIME is an
// ABOUTME: existential witness riding every cached answer; labelling eliminates.

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
 * Murder at the manor. Five rooms along one corridor; the body is found in
 * room 4, and the coroner can only say the murder happened at hour 1, 2 or
 * 3 — the EXACT hour is never established. The killer must have been in
 * room 4 at the murder hour and at the garden door (room 5) one hour later.
 *
 * The suspects move by habit, one law each:
 *   butler (1):   makes his rounds, room = hour
 *   cook (2):     kitchen circuit, room = 2·hour
 *   gardener (3): tends beds outward, room = hour + 2
 *
 * The tabled investigation caches CONDITIONAL answers: "this suspect could
 * have done it, GIVEN some hour t" — t is a body local, so it rides the
 * answer as an existential witness with its domain and couplings. Nobody
 * ever learns t; consumers label the witness and the impossible alibis
 * collapse. The butler dies in the body (his law forces t = 4, outside the
 * window). The cook survives to the CACHE — her multiplication law keeps
 * waiting — but no hour satisfies both her laws, so her cached answer never
 * emits. Only the gardener's witness (t = 2, escape at 3) checks out.
 */
public class WhodunnitTest {

	private static Domain<Integer> dom(int... values) {
		return EnumeratedDomain.of(Array.ofAll(Arrays.stream(values).boxed())
				.map(Arithmetic::ofCasted));
	}

	/** Suspect {@code s} is in room {@code r} at hour {@code t} — by habit. */
	private static Goal at(Unifiable<Integer> s, Unifiable<Integer> t, Unifiable<Integer> r) {
		return unify(s, lval(1)).and(unify(r, t))
				.or(unify(s, lval(2)).and(FiniteDomain.multo(t, lval(2), r)))
				.or(unify(s, lval(3)).and(FiniteDomain.addo(t, lval(2), r)));
	}

	/** The investigation: opportunity AND escape, both through the unknown hour. */
	private static Tabled<Tuple1<Unifiable<Integer>>> couldHaveDoneIt() {
		return Tabling.define(args -> args.apply(who ->
				Logic.<Integer, Integer> exist((t, after) ->
						FiniteDomain.dom(t, dom(1, 2, 3))            // the coroner's window
								.and(FiniteDomain.dom(after, dom(2, 3, 4)))
								.and(FiniteDomain.addo(t, lval(1), after))
								.and(at(who, t, lval(4)))            // at the scene…
								.and(at(who, after, lval(5))))));    // …then out the garden door
	}

	@Test
	public void theGardenerDidIt() {
		Tabled<Tuple1<Unifiable<Integer>>> investigation = couldHaveDoneIt();
		Package p = Package.empty().withStore(Table.empty());

		Unifiable<Integer> who = lvar();
		List<Integer> culprits = investigation.apply(Tuple.of(who))
				.solveFrom(p, who, BreadthFirstScheduler::new)
				.map(Term::<Integer> get)
				.collect(Collectors.toList());
		assertThat(culprits).containsExactly(3);

		// the cache is more interesting than the verdict: TWO conditional
		// answers were cached — the cook's alibi is not decided in the body
		// (her multiplication law waits for a ground hour), so she is cached
		// "guilty GIVEN some hour" and acquitted only when consumers label
		// her witness and no hour fits both laws. Elimination happens at
		// consumption, from the cached region, not in the search
		TableEntry<?> entry = p.getStore(Table.class).entries().iterator().next();
		assertThat(entry.getAnswerCount()).isEqualTo(2);
	}

	@Test
	public void accusingTheCookReusesTheInvestigation() {
		Tabled<Tuple1<Unifiable<Integer>>> investigation = couldHaveDoneIt();
		Package p = Package.empty().withStore(Table.empty());

		// the inspector's open question first — this runs the master once
		Unifiable<Integer> who = lvar();
		investigation.apply(Tuple.of(who))
				.solveFrom(p, who, BreadthFirstScheduler::new)
				.count();

		// the accusation is a BOUND call: it never re-runs the body, it reads
		// the open investigation's cache through subsumption and labels the
		// cook's cached witness — which clears her
		Unifiable<Integer> cook = lvar();
		long accusation = unify(cook, lval(2))
				.and(investigation.apply(Tuple.of(cook)))
				.solveFrom(p, cook, BreadthFirstScheduler::new)
				.count();
		assertThat(accusation).isEqualTo(0);
		assertThat(p.getStore(Table.class).entries()).hasSize(1);
	}
}
