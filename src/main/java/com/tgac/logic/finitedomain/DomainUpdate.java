package com.tgac.logic.finitedomain;

// ABOUTME: The FD store's domain-update primitive: applying "target ⊂ dom" against a
// ABOUTME: state and factor yields fail/unchanged/narrowed/collapsed as a value.

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import java.util.ArrayList;
import java.util.List;

/**
 * cKanren's process-δ as a value: a ground target is a membership check; a
 * variable's previous domain is intersected — an empty intersection fails, an
 * equal one is the termination guard of wake-on-narrowing, a singleton collapses
 * to an inferred binding (the domain map is deliberately NOT updated — stale
 * domain information under a binding is fine, domains are consulted only for
 * unbound variables), and anything else narrows the factor with a re-examination
 * note. All expressed as the toolkit's {@link Update} steps.
 */
final class DomainUpdate {

	private DomainUpdate() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static Update apply(Package state, FiniteDomainConstraints factor, Term<?> target, Domain<?> dom) {
		if (target.isVal()) {
			return ((Domain) dom).contains(target.get()) ? Update.unchanged() : Update.fail();
		}
		LVar<?> x = (LVar<?>) target.asVar().get();
		Domain previous = (Domain) factor.getDomain((LVar) x).getOrNull();
		Domain effective;
		if (previous != null) {
			effective = previous.intersect((Domain) dom);
			if (effective.isEmpty()) {
				return Update.fail();
			}
			if (effective.equals(previous)) {
				return Update.unchanged();
			}
		} else {
			effective = (Domain) dom;
		}
		if (effective instanceof Singleton) {
			Object v = ((Singleton) effective).getValue().getValue();
			// only open variables collapse, so the mint succeeds; the defensive
			// branch mirrors the old already-bound no-op
			return Prefix.binding(state.substitution(), x, lval(v))
					.<Update> map(prefix -> Update.applied(factor).withInferred(prefix))
					.getOrElse(Update.unchanged());
		}
		return Update.applied(factor.withDomain(x, effective)).withReexamine(x);
	}

	/**
	 * Folds a batch of updates into one {@link Update}, threading the factor:
	 * fail short-circuits, narrowings accumulate re-examination terms, collapses
	 * accumulate inferred prefixes.
	 */
	static Update narrowAll(Package state, FiniteDomainConstraints factor,
			List<FiniteDomain.VarWithDomain<?>> updates) {
		FiniteDomainConstraints current = factor;
		List<Prefix> inferred = new ArrayList<>();
		List<Term<?>> reexamine = new ArrayList<>();
		for (FiniteDomain.VarWithDomain<?> update : updates) {
			Update step = apply(state, current, update.getUnifiable(), update.getDomain());
			FiniteDomainConstraints before = current;
			current = step.match(
					() -> null,
					() -> before,
					applied -> {
						inferred.addAll(applied.inferred());
						reexamine.addAll(applied.reexamine());
						return (FiniteDomainConstraints) applied.factor();
					});
			if (current == null) {
				return Update.fail();
			}
		}
		if (current == factor && inferred.isEmpty() && reexamine.isEmpty()) {
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
