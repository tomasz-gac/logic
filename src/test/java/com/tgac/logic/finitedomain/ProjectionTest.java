package com.tgac.logic.finitedomain;

// ABOUTME: Pins the FD store's Projectable capability: transcribe domains and
// ABOUTME: covered couplings; wideningAllowed governs only the escapes.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import java.util.ArrayList;
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

	@Test
	public void projectReadsKnowledgePositionally() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		FiniteDomainConstraints store = FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(y), dom(7, 8));

		DomainResidue residue = store.project(Arrays.asList(varOf(x), varOf(y)), true);
		assertThat(residue.getSlots().get(0).get()).isEqualTo(dom(1, 2, 3));
		assertThat(residue.getSlots().get(1).get()).isEqualTo(dom(7, 8));

		// unconstrained var: absent slot = ⊤; order is the caller's
		DomainResidue sparse = store.project(Arrays.asList(varOf(z), varOf(y)), true);
		assertThat(sparse.getSlots().containsKey(0)).isFalse();
		assertThat(sparse.getSlots().get(1).get()).isEqualTo(dom(7, 8));
	}

	@Test
	public void restateReimposesTheProjectedKnowledge() {
		Unifiable<Integer> x = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		FiniteDomainConstraints store = FiniteDomainConstraints.getFDStore(p);
		DomainResidue residue = store.project(Arrays.asList(varOf(x)), true);

		// restated onto a FRESH var, the knowledge constrains it identically:
		// solving enumerates exactly the projected domain
		Unifiable<Integer> fresh = lvar();
		List<Integer> values = residue.restate(Arrays.<Unifiable<?>> asList(fresh))
				.solve(fresh)
				.map(Term::<Integer> get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(values).containsExactly(1, 2);
	}

	@Test
	public void emptyResidueRestatesToSuccess() {
		Unifiable<Integer> fresh = lvar();
		FiniteDomainConstraints store = FiniteDomainConstraints.empty();
		DomainResidue residue = store.project(Arrays.asList(varOf(fresh)), true);

		List<Integer> values = residue.restate(Arrays.<Unifiable<?>> asList(fresh))
				.and(Constraints.unify(fresh, lval(5)))
				.solve(fresh)
				.map(Term::<Integer> get)
				.collect(Collectors.toList());
		assertThat(values).containsExactly(5);
	}

	@Test
	public void aCoveredCouplingIsCarriedAsItsLiveObject() {
		// every watched var supplied: the propagator itself rides the residue
		// with its (var → slot) map — under either strength
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Propagator coupling = keeper(x, y, lval(4));
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		FiniteDomainConstraints store = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(y), dom(1, 2, 3))
				.prepend(coupling);

		DomainResidue wide = store.project(Arrays.asList(varOf(x), varOf(y)), true);
		DomainResidue exact = store.project(Arrays.asList(varOf(x), varOf(y)), false);
		assertThat(wide.getCarried()).hasSize(1);
		assertThat(exact.getCarried()).hasSize(1);
		CarriedConstraint carried = wide.getCarried().head();
		assertThat(carried.getPropagator()).isSameAs(coupling);
		assertThat(carried.getVarSlots()).hasSize(2);   // grounds need no entry
	}

	@Test
	public void anEscapingCouplingDropsByPermissionOrThrows() {
		// one watched var unsupplied: dropped silently under wideningAllowed,
		// refused loudly when exactness was demanded
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> w = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		FiniteDomainConstraints store = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.prepend(keeper(x, w, lval(6)));

		DomainResidue widened = store.project(Arrays.asList(varOf(x)), true);
		assertThat(widened.getCarried()).isEmpty();
		assertThat(widened.getSlots().get(0).get()).isEqualTo(dom(1, 2, 3));

		assertThatThrownBy(() -> store.project(Arrays.asList(varOf(x)), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("escapes");
	}

	@Test
	public void carriedIdentityIsTheNameOverTheVars() {
		// one store state (e.g. down a recursion) yields EQUAL residues, and
		// so does an INDEPENDENT post of the same relation on the same vars:
		// same knowledge, stated twice — carried couplings compare by value
		// (name over terms), not by which posting minted the object
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Propagator posted = keeper(x, y);
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		FiniteDomainConstraints store = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.prepend(posted);

		DomainResidue first = store.project(Arrays.asList(varOf(x), varOf(y)), true);
		DomainResidue again = store.project(Arrays.asList(varOf(x), varOf(y)), true);
		assertThat(first).isEqualTo(again);

		FiniteDomainConstraints reposted = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.prepend(keeper(x, y));
		assertThat(reposted.project(Arrays.asList(varOf(x), varOf(y)), true))
				.isEqualTo(first);
	}

	@Test
	public void aCarriedCouplingReplaysAsAFreshInstanceOverTheGivenVars() {
		// the residue is a schema: replay registers the propagator rebuilt to
		// watch the given vars — the constraint applies to THEM, and the
		// original watched vars stay independent (no aliasing)
		Unifiable<Integer> orig = lvar();
		Propagator notSeven = Propagator.of(FiniteDomainConstraints.class, "not_seven",
				Arrays.<Term<?>> asList(orig), (terms, pkg) -> {
					Term<?> watched = pkg.walk(terms.get(0));
					return watched.isVal() && Integer.valueOf(7).equals(watched.get())
							? Verdict.fail()
							: Verdict.keep();
				});
		CarriedConstraint carried = CarriedConstraint.of(notSeven,
				Array.of(Tuple.of(varOf(orig), 0)));
		DomainResidue residue = DomainResidue.of(HashMap.empty(), HashSet.of(carried));

		Unifiable<Integer> fresh = lvar();
		assertThat(residue.restate(Arrays.<Unifiable<?>> asList(fresh))
				.and(Constraints.unify(fresh, lval(7)))
				.solve(fresh)
				.count()).isEqualTo(0);
		assertThat(residue.restate(Arrays.<Unifiable<?>> asList(fresh))
				.and(Constraints.unify(orig, lval(7)))
				.and(Constraints.unify(fresh, lval(3)))
				.solve(fresh)
				.count()).isEqualTo(1);
	}

	@Test
	public void restatingOntoTheWatchedVarsReactivatesTheLiveObject() {
		// an identity renaming yields the identical instance: the master
		// seeding its body from the key re-activates the carried object
		// itself, so a recursive call projects the SAME object and its key
		// lands in the same table entry
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Propagator posted = keeper(x, y, lval(4));
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		FiniteDomainConstraints store = (FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(y), dom(1, 2, 3))
				.prepend(posted);

		DomainResidue residue = store.project(Arrays.asList(varOf(x), varOf(y)), true);
		List<Integer> seen = new ArrayList<>();
		residue.restate(Arrays.<Unifiable<?>> asList(x, y))
				.and(pkg -> {
					FiniteDomainConstraints seeded = FiniteDomainConstraints.getFDStore(pkg);
					seen.add(seeded.getConstraints().exists(c -> c == posted) ? 1 : 0);
					return Goal.success().apply(pkg);
				})
				.and(Constraints.unify(x, lval(1)))
				.and(Constraints.unify(y, lval(3)))
				.solve(x)
				.count();
		assertThat(seen).containsExactly(1);
	}
}
