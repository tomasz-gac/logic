package com.tgac.logic.finitedomain;

// ABOUTME: Test-only access to the package-private FD store: builds packages
// ABOUTME: with a recorded domain for pricing and law tests outside this package.

import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.collection.Array;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FiniteDomainTestSupport {

	public static <T> Package withDomain(Unifiable<T> x, Domain<T> d) {
		Package p = FiniteDomainConstraints.register(Package.empty());
		FiniteDomainConstraints store = FiniteDomainConstraints.getFDStore(p)
				.withDomain((LVar<?>) x.asVar().get(), d);
		return p.putStore(store);
	}

	/** A keeper watching a fresh var — value-distinct per call (fresh var). */
	public static Propagator keeper() {
		LVar<?> x = (LVar<?>) lvar().asVar().get();
		return Propagator.of(FiniteDomainConstraints.class, "keep",
				Collections.<Term<?>> singletonList(x), (watched, state) -> Verdict.keep());
	}
}
