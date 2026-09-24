package org.clauseway.logic.finitedomain;

// ABOUTME: Test-only access to the package-private FD store: builds packages
// ABOUTME: with a recorded domain for pricing and law tests outside this package.

import static org.clauseway.logic.unification.terms.LVar.lvar;

import org.clauseway.logic.constraints.store.Constraint;
import org.clauseway.logic.constraints.store.Theory;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.lattice.Propagator;
import org.clauseway.logic.lattice.Verdict;
import org.clauseway.logic.lattice.TestPropagators;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FiniteDomainTestSupport {

	public static <T> Package withDomain(Unifiable<T> x, Domain<T> d) {
		Package p = FiniteDomainConstraints.register(Package.empty());
		Theory<FiniteDomainConstraints> theory = FiniteDomainConstraints.withDomain(
				Theory.empty(), (LVar<?>) x.asVar().get(), d);
		return p.putStore(FiniteDomainConstraints.class,
				Constraint.of(theory, FiniteDomainConstraints.empty()));
	}

	/** A keeper watching a fresh var — value-distinct per call (fresh var). */
	public static Propagator keeper() {
		LVar<?> x = (LVar<?>) lvar().asVar().get();
		return TestPropagators.of(FiniteDomainConstraints.empty(), "keep",
				Collections.<Term<?>> singletonList(x), (watched, state) -> Verdict.keep());
	}
}
