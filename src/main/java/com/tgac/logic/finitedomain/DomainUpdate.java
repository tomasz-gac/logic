package com.tgac.logic.finitedomain;

// ABOUTME: FD's domain-update entry: "target ⊂ dom" against a state and theory, and
// ABOUTME: the batch fold verdicts use; the process-δ primitive is LatticeStore.update.

import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Update;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import java.util.ArrayList;
import java.util.List;

/**
 * cKanren's process-δ over {@link Domain} values: a ground target is a
 * membership check; a variable's previous domain is intersected — an empty
 * intersection fails, an equal one is the termination guard of
 * wake-on-narrowing, a singleton collapses to an inferred binding (the domain
 * map is deliberately NOT updated — stale domain information under a binding
 * is fine, domains are consulted only for unbound variables), and anything
 * else narrows the theory with a re-examination note. All expressed as the
 * toolkit's {@link Update} steps by the store's inherited update primitive;
 * this class carries the FD-typed entry and the batch fold.
 */
final class DomainUpdate {

	private DomainUpdate() {
	}

	@SuppressWarnings("unchecked")
	static Update apply(Package state, Theory<FiniteDomainConstraints> theory, Term<?> target, Domain<?> dom) {
		return FiniteDomainConstraints.empty().update(theory, state, target, (Domain<Object>) dom);
	}

	/**
	 * Folds a batch of updates into one {@link Update}, threading the theory:
	 * fail short-circuits, narrowings accumulate re-examination terms, collapses
	 * accumulate inferred prefixes.
	 */
	@SuppressWarnings("unchecked")
	static Update narrowAll(Package state, Theory<FiniteDomainConstraints> theory,
			List<FiniteDomain.VarWithDomain<?>> updates) {
		Theory<FiniteDomainConstraints> current = theory;
		List<Prefix> inferred = new ArrayList<>();
		List<Term<?>> reexamine = new ArrayList<>();
		for (FiniteDomain.VarWithDomain<?> update : updates) {
			Update step = apply(state, current, update.getUnifiable(), update.getDomain());
			Theory<FiniteDomainConstraints> before = current;
			current = step.match(
					() -> null,
					() -> before,
					applied -> {
						inferred.addAll(applied.inferred());
						reexamine.addAll(applied.reexamine());
						return (Theory<FiniteDomainConstraints>) applied.theory();
					});
			if (current == null) {
				return Update.fail();
			}
		}
		if (current == theory && inferred.isEmpty() && reexamine.isEmpty()) {
			return Update.unchanged();
		}
		Update.Applied result = Update.applied(current);
		for (Prefix prefix : inferred) {
			result = result.withInferred(prefix);
		}
		for (Term<?> x : reexamine) {
			result = result.withReexamine(x);
		}
		return result;
	}
}
