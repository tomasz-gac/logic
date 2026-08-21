package com.tgac.logic.finitedomain;

// ABOUTME: Pins the FD store's single-sorted boundary algebra: named value-equal
// ABOUTME: propagators, lossless split, renaming across namespaces, absorbed replay.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.lattice.Imposition;
import io.vavr.collection.HashSet;
import io.vavr.control.Option;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Any;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import com.tgac.logic.unification.Name;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class ProjectionTest {

	private static Domain<Integer> dom(int... values) {
		return EnumeratedDomain.of(Array.ofAll(Arrays.stream(values).boxed())
				.map(Arithmetic::ofCasted));
	}

	private static LVar<?> varOf(Unifiable<?> u) {
		return (LVar<?>) u.asVar().get();
	}

	private static Theory<FiniteDomainConstraints> theoryIn(Package p) {
		return Constraint.in(p, FiniteDomainConstraints.class).get().getTheory();
	}

	private static Theory<FiniteDomainConstraints> domained(
			Theory<FiniteDomainConstraints> base, Unifiable<?> x, Domain<Integer> d) {
		return FiniteDomainConstraints.withDomain(base, varOf(x), d);
	}

	/** The key crossing, theory-side: the covered half in canonical names. */
	private static com.tgac.functional.fibers.Fiber<Theory<FiniteDomainConstraints>> projected(
			Theory<FiniteDomainConstraints> theory, java.util.Map<LVar<?>, Any<?>> slots) {
		return theory.split(new java.util.ArrayList<>(slots.keySet()))._1
				.rename(Renaming.of(slots));
	}

	private static Option<Domain<?>> domainOf(Theory<FiniteDomainConstraints> theory, Term<?> target) {
		return theory.atom(FiniteDomainConstraints.class, "imposition", HashSet.of(target))
				.map(a -> (Domain<?>) ((Imposition<?, ?>) a).getValue());
	}

	private static Propagator keeper(Unifiable<?>... watched) {
		return Propagator.of(FiniteDomainConstraints.empty(), "keep",
				Arrays.<Term<?>> asList(watched), (terms, pkg) -> Verdict.keep());
	}

	// ---- the propagator identity ----

	@Test
	public void absorbVerifiesIncomingKnowledgeAgainstBindings() {
		// meet is completed by normalize: knowledge arriving about an
		// already-bound name verifies against the binding — out-of-domain
		// fails the branch, in-domain is spent and drops
		Unifiable<Integer> x = lvar();
		Theory<FiniteDomainConstraints> incoming = domained(Theory.empty(), x, dom(1, 2));

		assertThat(Constraints.unify(x, lval(7))
				.and(Propagation.absorb(incoming))
				.solve(x, TestSchedulers.factory())
				.count()).isEqualTo(0);

		Theory<FiniteDomainConstraints> wide = domained(Theory.empty(), x, dom(5, 7, 9));
		assertThat(Constraints.unify(x, lval(7))
				.and(Propagation.absorb(wide))
				.solve(x, TestSchedulers.factory())
				.count()).isEqualTo(1);
	}

	@Test
	public void bindingPrunesTheDomainEntry() {
		// revise removes the entry the moment its verification passes — the
		// theory never drifts, and capture-normalization has nothing to drop
		Unifiable<Integer> x = lvar();
		boolean[] pruned = new boolean[1];
		FiniteDomain.dom(x, dom(1, 2, 3))
				.and(Constraints.unify(x, lval(2)))
				.and(p -> {
					pruned[0] = FiniteDomainConstraints.getDomains(p).isEmpty();
					return Goal.success().apply(p);
				})
				.solve(x, TestSchedulers.factory())
				.count();
		assertThat(pruned[0]).isTrue();
	}

	@Test
	public void aPropagatorIsItsNameOverItsTerms() {
		// value equality (storeClass, name, watched): a constraint is "which
		// relation over which terms" — the body is determined by the name.
		// Renamed instances of one post compare equal, and so do two
		// independent posts of the same relation on the same vars: the same
		// knowledge, stated twice (idempotent re-posting)
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Propagator posted = keeper(x, y);

		assertThat(posted.watching(Array.of(x, y))).isEqualTo(posted);
		assertThat(keeper(x, y)).isEqualTo(posted);

		Unifiable<Integer> z = lvar();
		assertThat(keeper(x, z)).isNotEqualTo(posted);
		assertThat(Propagator.of(FiniteDomainConstraints.empty(), "other",
				Arrays.<Term<?>> asList(x, y), (terms, pkg) -> Verdict.keep()))
				.isNotEqualTo(posted);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void statingTheSameConstraintTwiceIsOnePropagator() {
		// the MeetSemilattice doctrine made structural: duplicate posts merge
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Theory<FiniteDomainConstraints> theory = Theory.<FiniteDomainConstraints> empty()
				.with(keeper(x, y))
				.with(keeper(x, y));
		assertThat(theory.kind(Propagator.class).count()).isEqualTo(1L);
	}

	// ---- renaming ----

	@Test
	public void renamingByWalkKeepsValResolvedEntriesForTheConsumer() {
		// the crossing is FAITHFUL: an entry whose var resolves to a value
		// stays, val-keyed — the consumer's wholesale normalize verifies and
		// spends it (the ground membership check), so nothing is silently
		// dropped at the boundary
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Theory<FiniteDomainConstraints> theory = domained(
				domained(Theory.empty(), x, dom(1, 2)), y, dom(7, 8));

		Theory<FiniteDomainConstraints> crossed = theory.rename(Renaming.of(
				Collections.<Name<?>, Term<?>> singletonMap(varOf(x), lval(1)))).ground();
		assertThat(domainOf(crossed, varOf(y)).isDefined()).isTrue();
		assertThat(domainOf(crossed, varOf(x)).isDefined()).isFalse();
		assertThat(domainOf(crossed, lval(1)).get()).isEqualTo(dom(1, 2));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void renamingIntoTargetsMintsSharedFreshVars() {
		// replay: seeded correspondences apply, unseeded vars mint fresh ones
		// (the ∃) — and ONE Renaming shared by both applications keeps a
		// shared local the same variable on both sides
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> w = lvar();
		Unifiable<Integer> a = lvar();
		Propagator coupling = keeper(x, w);
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		Theory<FiniteDomainConstraints> store = domained(theoryIn(p), w, dom(2, 3))
				.with(coupling);

		java.util.Map<LVar<?>, Term<?>> seed = new java.util.HashMap<>();
		seed.put(varOf(x), a);
		Renaming renaming = Renaming.minting(seed);

		Theory<FiniteDomainConstraints> renamed = store.rename(renaming).ground();
		assertThat(domainOf(renamed, (Term<?>) a).get()).isEqualTo(dom(1, 2));
		assertThat(domainOf(renamed, varOf(w)).isDefined()).isFalse();

		// w went somewhere fresh — and a SECOND application of the same
		// renaming sends w to the SAME fresh var
		Propagator<FiniteDomainConstraints> renamedCoupling =
				(Propagator<FiniteDomainConstraints>) renamed.kind(Propagator.class)
						.findFirst().get();
		Term<?> mintedW = renamedCoupling.watchedTerms().get(1);
		assertThat(mintedW.asVar().isDefined()).isTrue();
		assertThat(mintedW).isNotEqualTo(w);
		assertThat(renaming.apply(w).ground()).isSameAs(mintedW);
		assertThat(renamedCoupling.watchedTerms().get(0)).isEqualTo(a);
	}

	// ---- the canonical namespace: keys ----

	@Test
	public void projectReadsKnowledgePositionally() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		Theory<FiniteDomainConstraints> store = domained(theoryIn(p), y, dom(7, 8));

		Theory<FiniteDomainConstraints> keyed = projected(store, slots(varOf(x), varOf(y))).ground();
		assertThat(domainOf(keyed, Any.of(0)).get()).isEqualTo(dom(1, 2, 3));
		assertThat(domainOf(keyed, Any.of(1)).get()).isEqualTo(dom(7, 8));

		// unconstrained var: absent name = ⊤; order is the caller's
		Theory<FiniteDomainConstraints> sparse = projected(store, slots(varOf(z), varOf(y))).ground();
		assertThat(domainOf(sparse, Any.of(0)).isDefined()).isFalse();
		assertThat(domainOf(sparse, Any.of(1)).get()).isEqualTo(dom(7, 8));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void aCoveredCouplingProjectsCanonically() {
		// every watched var supplied: the coupling rides the key as the same
		// NAME over the anys — comparable across packages
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		Theory<FiniteDomainConstraints> store = domained(theoryIn(p), y, dom(1, 2, 3))
				.with(keeper(x, y, lval(4)));

		Theory<FiniteDomainConstraints> keyed = projected(store, slots(varOf(x), varOf(y))).ground();
		List<Propagator> carriedAll = keyed.kind(Propagator.class).collect(Collectors.toList());
		assertThat(carriedAll).hasSize(1);
		Propagator carried = carriedAll.get(0);
		assertThat(carried.watchedTerms()).containsExactly(Any.of(0), Any.of(1), lval(4));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void splitFactorsLosslessly() {
		// (covered, remainder) with _1 ∧ _2 = this: the escaping coupling and
		// the foreign domain land in the remainder — the CALLER decides what
		// to do with it (keys discard; nothing is ever silently widened here)
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> w = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		Theory<FiniteDomainConstraints> store = domained(theoryIn(p), w, dom(2, 3))
				.with(keeper(x, w, lval(6)));

		Tuple2<Theory<FiniteDomainConstraints>, Theory<FiniteDomainConstraints>> halves =
				store.split(Arrays.asList(varOf(x)));
		assertThat(domainOf(halves._1, varOf(x)).isDefined()).isTrue();
		assertThat(halves._1.kind(Propagator.class).count()).isZero();
		assertThat(domainOf(halves._2, varOf(w)).isDefined()).isTrue();
		assertThat(halves._2.kind(Propagator.class).count()).isEqualTo(1L);
		assertThat(halves._1.meet(halves._2)).isEqualTo(store);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void projectionIsCanonicalAcrossPostings() {
		// one store state projects equal keys twice, and an INDEPENDENT
		// same-shaped post projects the same key: name over slots, no lineage
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		Theory<FiniteDomainConstraints> store = theoryIn(p).with(keeper(x, y));

		Theory<FiniteDomainConstraints> first = projected(store, slots(varOf(x), varOf(y))).ground();
		Theory<FiniteDomainConstraints> again = projected(store, slots(varOf(x), varOf(y))).ground();
		assertThat(first).isEqualTo(again);

		Theory<FiniteDomainConstraints> reposted = theoryIn(p).with(keeper(x, y));
		assertThat(projected(reposted, slots(varOf(x), varOf(y))).ground())
				.isEqualTo(first);
	}

	// ---- stated: the store as a re-expressible goal ----

	@Test
	public void anAbsorbedStoreReimposesItsKnowledge() {
		Unifiable<Integer> x = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));

		List<Integer> values = Propagation.absorb(theoryIn(p))
				.solve(x, TestSchedulers.factory())
				.map(Term::<Integer>get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(values).containsExactly(1, 2);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void seedingRestatesTheKeyOntoTheCallVars() {
		// master seeding: the canonical key renamed back onto the call vars
		// and stated — the seeded store holds the constraint BY VALUE
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Propagator posted = keeper(x, y);
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		Theory<FiniteDomainConstraints> store = theoryIn(p).with(posted);

		Theory<FiniteDomainConstraints> keyed = projected(store, slots(varOf(x), varOf(y))).ground();
		Theory<FiniteDomainConstraints> seeded = keyed.rename(
				Renaming.restating(targets(x, y))).ground();
		assertThat(seeded.kind(Propagator.class).collect(Collectors.toList()))
				.containsExactly(posted);
		assertThat(domainOf(seeded, varOf(x)).get()).isEqualTo(dom(1, 2));
	}

	@Test
	public void aReplayedCouplingConstrainsTheTargetsNotTheOriginals() {
		// replay is a renaming: the constraint applies to the target vars,
		// and the original vars stay independent (no aliasing)
		Unifiable<Integer> orig = lvar();
		Propagator notSeven = Propagator.of(FiniteDomainConstraints.empty(), "not_seven",
				Arrays.<Term<?>> asList(orig), (terms, pkg) -> {
					Term<?> watched = pkg.walk(terms.get(0));
					return watched.isVal() && Integer.valueOf(7).equals(watched.get())
							? Verdict.fail()
							: Verdict.keep();
				});
		@SuppressWarnings("unchecked")
		Theory<FiniteDomainConstraints> store =
				Theory.<FiniteDomainConstraints> empty().with(notSeven);

		Unifiable<Integer> fresh = lvar();
		java.util.Map<LVar<?>, Term<?>> seed = new java.util.HashMap<>();
		seed.put(varOf(orig), fresh);
		assertThat(Propagation.absorb(store.rename(Renaming.minting(seed)).ground())
				.and(Constraints.unify(fresh, lval(7)))
				.solve(fresh, TestSchedulers.factory())
				.count()).isEqualTo(0);

		Unifiable<Integer> fresh2 = lvar();
		java.util.Map<LVar<?>, Term<?>> seed2 = new java.util.HashMap<>();
		seed2.put(varOf(orig), fresh2);
		assertThat(Propagation.absorb(store.rename(Renaming.minting(seed2)).ground())
				.and(Constraints.unify(orig, lval(7)))
				.and(Constraints.unify(fresh2, lval(3)))
				.solve(fresh2, TestSchedulers.factory())
				.count()).isEqualTo(1);
	}

	private static java.util.Map<LVar<?>, Any<?>> slots(LVar<?>... vars) {
		java.util.Map<LVar<?>, Any<?>> bySlot = new java.util.LinkedHashMap<>();
		for (int i = 0; i < vars.length; i++) {
			bySlot.put(vars[i], Any.of(i));
		}
		return bySlot;
	}

	private static java.util.Map<Any<?>, Term<?>> targets(Term<?>... terms) {
		java.util.Map<Any<?>, Term<?>> bySlot = new java.util.LinkedHashMap<>();
		for (int i = 0; i < terms.length; i++) {
			bySlot.put(Any.of(i), terms[i]);
		}
		return bySlot;
	}
}
