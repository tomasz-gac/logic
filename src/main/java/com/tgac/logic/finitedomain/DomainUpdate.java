package com.tgac.logic.finitedomain;

// ABOUTME: The FD store's domain-update primitive: applying "target ⊂ dom" against a
// ABOUTME: state and factor yields fail/unchanged/narrowed/collapsed as a value.

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.logic.ckanren.store.Revision;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * cKanren's process-δ as a value instead of a goal (the semantics of the old
 * {@code Domain.processDom}/{@code updateVarDomain}/{@code resolveStorableDom}
 * chain): a ground target is a membership check; a variable's previous domain is
 * intersected — an empty intersection fails, an equal one is the termination
 * guard of wake-on-narrowing (no re-wake on no-change), a singleton collapses to
 * an inferred binding (the domain map is deliberately NOT updated — stale domain
 * information under a binding is fine, domains are consulted only for unbound
 * variables), and anything else narrows the factor.
 */
abstract class DomainUpdate {

	private DomainUpdate() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static DomainUpdate apply(Package state, FiniteDomainConstraints factor, Term<?> target, Domain<?> dom) {
		if (target.isVal()) {
			return ((Domain) dom).contains(target.get()) ? UNCHANGED : FAIL;
		}
		LVar<?> x = (LVar<?>) target.asVar().get();
		Domain previous = (Domain) factor.getDomain((LVar) x).getOrNull();
		Domain effective;
		if (previous != null) {
			effective = previous.intersect((Domain) dom);
			if (effective.isEmpty()) {
				return FAIL;
			}
			if (effective.equals(previous)) {
				return UNCHANGED;
			}
		} else {
			effective = (Domain) dom;
		}
		if (effective instanceof Singleton) {
			Object v = ((Singleton) effective).getValue().getValue();
			// updateVarDomain only touches open variables, so the mint succeeds;
			// the defensive branch mirrors the old already-bound no-op
			return Prefix.binding(state, x, lval(v))
					.<DomainUpdate> map(Collapsed::new)
					.getOrElse(UNCHANGED);
		}
		return new Narrowed(factor.withDomain(x, effective), x);
	}

	/**
	 * Folds a batch of updates into one {@link Revision}, threading the factor:
	 * fail short-circuits, narrowings accumulate changed terms, collapses
	 * accumulate inferred prefixes.
	 */
	static Revision narrowAll(Package state, FiniteDomainConstraints factor,
			List<FiniteDomain.VarWithDomain<?>> updates) {
		FiniteDomainConstraints[] current = {factor};
		java.util.List<LVar<?>> changed = new java.util.ArrayList<>();
		java.util.List<Prefix> inferred = new java.util.ArrayList<>();
		for (FiniteDomain.VarWithDomain<?> update : updates) {
			boolean dead = DomainUpdate
					.apply(state, current[0], update.getUnifiable(), update.getDomain())
					.match(
							() -> true,
							() -> false,
							(narrowedFactor, x) -> {
								current[0] = narrowedFactor;
								changed.add(x);
								return false;
							},
							prefix -> {
								inferred.add(prefix);
								return false;
							});
			if (dead) {
				return Revision.fail();
			}
		}
		if (changed.isEmpty() && inferred.isEmpty()) {
			return Revision.unchanged();
		}
		Revision.Updated result = Revision.updated(current[0]);
		for (LVar<?> x : changed) {
			result = result.withChanged(x);
		}
		for (Prefix prefix : inferred) {
			result = result.withInferred(prefix);
		}
		return result;
	}

	abstract <R> R match(
			Supplier<R> onFail,
			Supplier<R> onUnchanged,
			BiFunction<FiniteDomainConstraints, LVar<?>, R> onNarrowed,
			Function<Prefix, R> onCollapsed);

	private static final DomainUpdate FAIL = new DomainUpdate() {
		@Override
		<R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				BiFunction<FiniteDomainConstraints, LVar<?>, R> onNarrowed,
				Function<Prefix, R> onCollapsed) {
			return onFail.get();
		}
	};

	private static final DomainUpdate UNCHANGED = new DomainUpdate() {
		@Override
		<R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				BiFunction<FiniteDomainConstraints, LVar<?>, R> onNarrowed,
				Function<Prefix, R> onCollapsed) {
			return onUnchanged.get();
		}
	};

	private static final class Narrowed extends DomainUpdate {
		private final FiniteDomainConstraints factor;
		private final LVar<?> x;

		private Narrowed(FiniteDomainConstraints factor, LVar<?> x) {
			this.factor = factor;
			this.x = x;
		}

		@Override
		<R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				BiFunction<FiniteDomainConstraints, LVar<?>, R> onNarrowed,
				Function<Prefix, R> onCollapsed) {
			return onNarrowed.apply(factor, x);
		}
	}

	private static final class Collapsed extends DomainUpdate {
		private final Prefix prefix;

		private Collapsed(Prefix prefix) {
			this.prefix = prefix;
		}

		@Override
		<R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				BiFunction<FiniteDomainConstraints, LVar<?>, R> onNarrowed,
				Function<Prefix, R> onCollapsed) {
			return onCollapsed.apply(prefix);
		}
	}
}
