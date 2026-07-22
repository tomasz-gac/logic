package com.tgac.logic.finitedomain;

// ABOUTME: Pins the FD store's Projectable capability: project reads knowledge
// ABOUTME: positionally (absent slot = ⊤), restate re-imposes it via dom posts.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
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
		List<Integer> values = store.restate(residue, Arrays.asList(fresh))
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

		List<Integer> values = store.restate(residue, Arrays.asList(fresh))
				.and(Constraints.unify(fresh, lval(5)))
				.solve(fresh)
				.map(Term::<Integer> get)
				.collect(Collectors.toList());
		assertThat(values).containsExactly(5);
	}
}
