package com.tgac.logic.finitedomain;

// ABOUTME: Pins the FD store's single-sorted boundary algebra: named value-equal
// ABOUTME: propagators, lossless split, renaming across namespaces, absorbed replay.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.List;
import org.junit.Test;

public class ProjectionTest {

	private static Domain<Integer> dom(int... values) {
		return EnumeratedDomain.of(Array.ofAll(Arrays.stream(values).boxed())
				.map(Arithmetic::ofCasted));
	}

	private static LVar<?> varOf(Unifiable<?> u) {
		return (LVar<?>) u.asVar().get();
	}

	private static Propagator keeper(Unifiable<?>... watched) {
		return Propagator.of(FiniteDomainConstraints.class, "keep",
				Arrays.<Term<?>> asList(watched), (terms, pkg) -> Verdict.keep());
	}

	// ---- the propagator identity ----

	@Test
	public void absorbVerifiesIncomingKnowledgeAgainstBindings() {
		// meet is completed by normalize: knowledge arriving about an
		// already-bound name verifies against the binding — out-of-domain
		// fails the branch, in-domain is spent and drops
		Unifiable<Integer> x = lvar();
		FiniteDomainConstraints incoming = FiniteDomainConstraints.empty()
				.withDomain(varOf(x), dom(1, 2));

		assertThat(Constraints.unify(x, lval(7))
				.and(Propagation.absorb(incoming))
				.solve(x)
				.count()).isEqualTo(0);

		FiniteDomainConstraints wide = FiniteDomainConstraints.empty()
				.withDomain(varOf(x), dom(5, 7, 9));
		assertThat(Constraints.unify(x, lval(7))
				.and(Propagation.absorb(wide))
				.solve(x)
				.count()).isEqualTo(1);
	}

	@Test
	public void bindingPrunesTheDomainEntry() {
		// revise removes the entry the moment its verification passes — the
		// factor never drifts, and capture-normalization has nothing to drop
		Unifiable<Integer> x = lvar();
		boolean[] pruned = new boolean[1];
		FiniteDomain.dom(x, dom(1, 2, 3))
				.and(Constraints.unify(x, lval(2)))
				.and(p -> {
					pruned[0] = FiniteDomainConstraints.getFDStore(p).getDomains().isEmpty();
					return Goal.success().apply(p);
				})
				.solve(x)
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
		assertThat(Propagator.of(FiniteDomainConstraints.class, "other",
				Arrays.<Term<?>> asList(x, y), (terms, pkg) -> Verdict.keep()))
				.isNotEqualTo(posted);
	}

	@Test
	public void statingTheSameConstraintTwiceIsOnePropagator() {
		// the MeetSemilattice doctrine made structural: duplicate posts merge
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		FiniteDomainConstraints store = (FiniteDomainConstraints) FiniteDomainConstraints.empty()
				.prepend(keeper(x, y))
				.prepend(keeper(x, y));
		assertThat(store.getConstraints()).hasSize(1);
	}

	// ---- renaming ----

	@Test
	public void renamingByWalkDropsSpentEntries() {
		// normalization at answer capture: entries whose var walks to a value
		// are spent bookkeeping (verified when they bound) and fall away;
		// live entries keep their names
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		FiniteDomainConstraints store = FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(y), dom(7, 8));
		Substitutions bound = Substitutions.of(HashMap.of(varOf(x), lval(1)));

		FiniteDomainConstraints normalized = store.rename(Renaming.walking(bound));
		assertThat(normalized.getDomain(varOf(y)).isDefined()).isTrue();
		assertThat(normalized.getDomain(varOf(x)).isDefined()).isFalse();
	}

	@Test
	public void renamingIntoTargetsMintsSharedFreshVars() {
		// replay: seeded correspondences apply, unseeded vars mint fresh ones
		// (the ∃) — and ONE Renaming shared by both applications keeps a
		// shared local the same variable on both sides
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> w = lvar();
		Unifiable<Integer> a = lvar();
		Propagator coupling = keeper(x, w);
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		FiniteDomainConstraints store = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(w), dom(2, 3))
				.prepend(coupling);

		java.util.Map<LVar<?>, Term<?>> seed = new java.util.HashMap<>();
		seed.put(varOf(x), a);
		Renaming renaming = Renaming.into(seed);

		FiniteDomainConstraints renamed = store.rename(renaming);
		assertThat(renamed.getDomain(a).get()).isEqualTo(dom(1, 2));
		assertThat(renamed.getDomain(varOf(w)).isDefined()).isFalse();

		// w went somewhere fresh — and a SECOND application of the same
		// renaming sends w to the SAME fresh var
		Propagator renamedCoupling = renamed.getConstraints().head();
		Term<?> mintedW = renamedCoupling.watchedTerms().get(1);
		assertThat(mintedW.asVar().isDefined()).isTrue();
		assertThat(mintedW).isNotEqualTo(w);
		assertThat(renaming.apply(w)).isSameAs(mintedW);
		assertThat(renamedCoupling.watchedTerms().get(0)).isEqualTo(a);
	}

	// ---- the canonical namespace: keys ----

	@Test
	public void projectReadsKnowledgePositionally() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		FiniteDomainConstraints store = FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(y), dom(7, 8));

		FiniteDomainConstraints keyed = store.project(Arrays.asList(varOf(x), varOf(y)));
		assertThat(keyed.getDomain(Hole.of(0)).get()).isEqualTo(dom(1, 2, 3));
		assertThat(keyed.getDomain(Hole.of(1)).get()).isEqualTo(dom(7, 8));

		// unconstrained var: absent name = ⊤; order is the caller's
		FiniteDomainConstraints sparse = store.project(Arrays.asList(varOf(z), varOf(y)));
		assertThat(sparse.getDomain(Hole.of(0)).isDefined()).isFalse();
		assertThat(sparse.getDomain(Hole.of(1)).get()).isEqualTo(dom(7, 8));
	}

	@Test
	public void aCoveredCouplingProjectsCanonically() {
		// every watched var supplied: the coupling rides the key as the same
		// NAME over the slot holes — comparable across packages
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		FiniteDomainConstraints store = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(y), dom(1, 2, 3))
				.prepend(keeper(x, y, lval(4)));

		FiniteDomainConstraints keyed = store.project(Arrays.asList(varOf(x), varOf(y)));
		assertThat(keyed.getConstraints()).hasSize(1);
		Propagator carried = keyed.getConstraints().head();
		assertThat(carried.watchedTerms()).containsExactly(Hole.of(0), Hole.of(1), lval(4));
	}

	@Test
	public void splitFactorsLosslessly() {
		// (covered, remainder) with _1 ∧ _2 = this: the escaping coupling and
		// the foreign domain land in the remainder — the CALLER decides what
		// to do with it (keys discard; nothing is ever silently widened here)
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> w = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		FiniteDomainConstraints store = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(w), dom(2, 3))
				.prepend(keeper(x, w, lval(6)));

		Tuple2<FiniteDomainConstraints, FiniteDomainConstraints> halves =
				store.split(Arrays.asList(varOf(x)));
		assertThat(halves._1.getDomain(varOf(x)).isDefined()).isTrue();
		assertThat(halves._1.getConstraints()).isEmpty();
		assertThat(halves._2.getDomain(varOf(w)).isDefined()).isTrue();
		assertThat(halves._2.getConstraints()).hasSize(1);
		assertThat(halves._1.meet(halves._2)).isEqualTo(store);
	}

	@Test
	public void projectionIsCanonicalAcrossPostings() {
		// one store state projects equal keys twice, and an INDEPENDENT
		// same-shaped post projects the same key: name over slots, no lineage
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		FiniteDomainConstraints store = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.prepend(keeper(x, y));

		FiniteDomainConstraints first = store.project(Arrays.asList(varOf(x), varOf(y)));
		FiniteDomainConstraints again = store.project(Arrays.asList(varOf(x), varOf(y)));
		assertThat(first).isEqualTo(again);

		FiniteDomainConstraints reposted = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.prepend(keeper(x, y));
		assertThat(reposted.project(Arrays.asList(varOf(x), varOf(y))))
				.isEqualTo(first);
	}

	// ---- stated: the store as a re-expressible goal ----

	@Test
	public void anAbsorbedStoreReimposesItsKnowledge() {
		Unifiable<Integer> x = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		FiniteDomainConstraints store = FiniteDomainConstraints.getFDStore(p);

		List<Integer> values = Propagation.absorb(store)
				.solve(x)
				.map(Term::<Integer> get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(values).containsExactly(1, 2);
	}

	@Test
	public void seedingRestatesTheKeyOntoTheCallVars() {
		// master seeding: the canonical key renamed back onto the call vars
		// and stated — the seeded store holds the constraint BY VALUE
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Propagator posted = keeper(x, y);
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		FiniteDomainConstraints store = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.prepend(posted);

		FiniteDomainConstraints keyed = store.project(Arrays.asList(varOf(x), varOf(y)));
		FiniteDomainConstraints seeded = keyed.rename(
				Renaming.ofSlots(Arrays.<Term<?>> asList(x, y)));
		assertThat(seeded.getConstraints().head()).isEqualTo(posted);
		assertThat(seeded.getDomain(varOf(x)).get()).isEqualTo(dom(1, 2));
	}

	@Test
	public void aReplayedCouplingConstrainsTheTargetsNotTheOriginals() {
		// replay is a renaming: the constraint applies to the target vars,
		// and the original vars stay independent (no aliasing)
		Unifiable<Integer> orig = lvar();
		Propagator notSeven = Propagator.of(FiniteDomainConstraints.class, "not_seven",
				Arrays.<Term<?>> asList(orig), (terms, pkg) -> {
					Term<?> watched = pkg.walk(terms.get(0));
					return watched.isVal() && Integer.valueOf(7).equals(watched.get())
							? Verdict.fail()
							: Verdict.keep();
				});
		FiniteDomainConstraints store =
				(FiniteDomainConstraints) FiniteDomainConstraints.empty().prepend(notSeven);

		Unifiable<Integer> fresh = lvar();
		java.util.Map<LVar<?>, Term<?>> seed = new java.util.HashMap<>();
		seed.put(varOf(orig), fresh);
		assertThat(Propagation.absorb(store.rename(Renaming.into(seed)))
				.and(Constraints.unify(fresh, lval(7)))
				.solve(fresh)
				.count()).isEqualTo(0);

		Unifiable<Integer> fresh2 = lvar();
		java.util.Map<LVar<?>, Term<?>> seed2 = new java.util.HashMap<>();
		seed2.put(varOf(orig), fresh2);
		assertThat(Propagation.absorb(store.rename(Renaming.into(seed2)))
				.and(Constraints.unify(orig, lval(7)))
				.and(Constraints.unify(fresh2, lval(3)))
				.solve(fresh2)
				.count()).isEqualTo(1);
	}
}
