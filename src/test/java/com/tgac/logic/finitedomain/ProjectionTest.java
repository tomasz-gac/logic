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
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
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
		return Propagator.of(FiniteDomainConstraints.class,
				Arrays.<Term<?>> asList(watched), pkg -> Verdict.keep());
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
	public void carriedIdentityIsTheStoreObjectNotTheShape() {
		// one store state (e.g. down a recursion) yields EQUAL residues — the
		// literal same propagator object; an independent same-shaped post is
		// incomparable: a conservative false that costs reuse, never soundness
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
				.isNotEqualTo(first);
	}

	@Test
	public void aCarriedCouplingReplaysByAliasingOntoLiveVars() {
		// replay unifies the live var with the propagator's ORIGINAL watched
		// var and re-activates the object: binding the original reaches the
		// fresh var through the alias
		Unifiable<Integer> orig = lvar();
		CarriedConstraint carried = CarriedConstraint.of(keeper(orig),
				Array.of(Tuple.of(varOf(orig), 0)));
		DomainResidue residue = DomainResidue.of(HashMap.empty(), HashSet.of(carried));

		Unifiable<Integer> fresh = lvar();
		List<Integer> values = residue.restate(Arrays.<Unifiable<?>> asList(fresh))
				.and(Constraints.unify(orig, lval(7)))
				.solve(fresh)
				.map(Term::<Integer> get)
				.collect(Collectors.toList());
		assertThat(values).containsExactly(7);
	}
}
