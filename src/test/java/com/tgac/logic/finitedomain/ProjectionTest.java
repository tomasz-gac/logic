package com.tgac.logic.finitedomain;

// ABOUTME: Pins the FD store's Projectable capability: project reads knowledge
// ABOUTME: positionally (absent slot = ⊤), restate re-imposes it via dom posts.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import java.util.Arrays;
import java.util.function.Function;
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

	@Test
	public void projectReadsKnowledgePositionally() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		FiniteDomainConstraints store = FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(y), dom(7, 8));

		DomainResidue residue = store.project(Arrays.asList(varOf(x), varOf(y)));
		assertThat(residue.getSlots().get(0).get()).isEqualTo(dom(1, 2, 3));
		assertThat(residue.getSlots().get(1).get()).isEqualTo(dom(7, 8));

		// unconstrained var: absent slot = ⊤; order is the caller's
		DomainResidue sparse = store.project(Arrays.asList(varOf(z), varOf(y)));
		assertThat(sparse.getSlots().containsKey(0)).isFalse();
		assertThat(sparse.getSlots().get(1).get()).isEqualTo(dom(7, 8));
	}

	@Test
	public void restateReimposesTheProjectedKnowledge() {
		Unifiable<Integer> x = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		FiniteDomainConstraints store = FiniteDomainConstraints.getFDStore(p);
		DomainResidue residue = store.project(Arrays.asList(varOf(x)));

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
		DomainResidue residue = store.project(Arrays.asList(varOf(fresh)));

		List<Integer> values = residue.restate(Arrays.<Unifiable<?>> asList(fresh))
				.and(Constraints.unify(fresh, lval(5)))
				.solve(fresh)
				.map(Term::<Integer> get)
				.collect(Collectors.toList());
		assertThat(values).containsExactly(5);
	}

	@Test
	public void aCoupledProjectionSaysItIsWidened() {
		// a live propagator watching a supplied var cannot ride the residue —
		// the residue must SAY it under-states; the boundary decides refusal
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		FiniteDomainConstraints store = ((FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(y), dom(1, 2, 3))
				.prepend(Propagator.of(FiniteDomainConstraints.class,
						Arrays.<Term<?>> asList(x, y),
						pkg -> Verdict.keep())));

		// wholly-covered coupling: still outside the vocabulary — widened
		assertThat(store.project(Arrays.asList(varOf(x), varOf(y))).isWidened()).isTrue();
		// escaping coupling: y unsupplied — widened
		assertThat(store.project(Arrays.asList(varOf(x))).isWidened()).isTrue();
		// even a propagator watching NO supplied var flags: uncarried live
		// knowledge anywhere means the residue does not capture the store
		Unifiable<Integer> z = lvar();
		assertThat(store.project(Arrays.asList(varOf(z))).isWidened()).isTrue();
	}

	@Test
	public void widenedIsAdvisoryNotIdentity() {
		// two callers widened to the same domains searched the same region —
		// the flag must not split keys
		HashMap<Integer, Domain<?>> slots = HashMap.of(0, dom(1, 2));
		assertThat(DomainResidue.of(slots, true))
				.isEqualTo(DomainResidue.of(slots, false));
	}

	@Test
	public void domainsOnlyProjectionIsNotWidened() {
		Unifiable<Integer> x = lvar();
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2));
		FiniteDomainConstraints store = FiniteDomainConstraints.getFDStore(p);
		assertThat(store.project(Arrays.asList(varOf(x))).isWidened()).isFalse();
	}

	@Test
	public void aRecipedCoupledProjectionCarriesInsteadOfWidening() {
		// a propagator WITH a recipe whose watched vars are all supplied is
		// CARRIED: expressible knowledge, not a widening
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Function<List<Unifiable<?>>, Goal> recipe =
				vs -> FiniteDomain.leq((Unifiable<Integer>) vs.get(0), (Unifiable<Integer>) vs.get(1));
		Package p = FiniteDomainTestSupport.withDomain(x, dom(1, 2, 3));
		FiniteDomainConstraints store = ((FiniteDomainConstraints) FiniteDomainConstraints.getFDStore(p)
				.withDomain(varOf(y), dom(1, 2, 3))
				.prepend(Propagator.of(FiniteDomainConstraints.class,
						Arrays.<Term<?>> asList(x, y),
						pkg -> Verdict.keep(),
						recipe)));

		DomainResidue covered = store.project(Arrays.asList(varOf(x), varOf(y)));
		assertThat(covered.isWidened()).isFalse();
		assertThat(covered.getCarried()).hasSize(1);

		// same coupling, one watched var unsupplied: escaped — widened, not carried
		DomainResidue escaped = store.project(Arrays.asList(varOf(x)));
		assertThat(escaped.isWidened()).isTrue();
		assertThat(escaped.getCarried()).isEmpty();
	}

	@Test
	public void aCarriedConstraintRestatesThroughItsRecipe() {
		// residue with dom(a,1..3) dom(b,1..3) + carried leq(a,b): restated
		// onto fresh vars the coupling filters — only pairs with a <= b
		Function<List<Unifiable<?>>, Goal> recipe =
				vs -> FiniteDomain.leq((Unifiable<Integer>) vs.get(0), (Unifiable<Integer>) vs.get(1));
		DomainResidue residue = DomainResidue.of(
				HashMap.of(0, dom(1, 2), 1, dom(1, 2)),
				HashSet.of(CarriedConstraint.of(recipe, Array.of(0, 1))),
				false);

		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> out = lval(Tuple.of(a, b));
		long pairs = residue.restate(Arrays.<Unifiable<?>> asList(a, b))
				.solve(out)
				.count();

		assertThat(pairs).isEqualTo(3);  // (1,1) (1,2) (2,2) — not (2,1)
	}

	@Test
	public void carriedConstraintsShareIdentityAcrossEqualCalls() {
		// recipe constants are per-factory statics: two projections of the
		// same coupling shape yield EQUAL residues — alpha-stable keys
		Function<List<Unifiable<?>>, Goal> recipe =
				vs -> FiniteDomain.leq((Unifiable<Integer>) vs.get(0), (Unifiable<Integer>) vs.get(1));
		CarriedConstraint c1 = CarriedConstraint.of(recipe, Array.of(0, 1));
		CarriedConstraint c2 = CarriedConstraint.of(recipe, Array.of(0, 1));
		assertThat(c1).isEqualTo(c2);
		assertThat(c1).isNotEqualTo(CarriedConstraint.of(recipe, Array.of(1, 0)));
	}
}
