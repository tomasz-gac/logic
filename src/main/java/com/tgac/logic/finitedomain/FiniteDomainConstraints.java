package com.tgac.logic.finitedomain;

import com.tgac.functional.reflection.Types;
import com.tgac.logic.ckanren.propagator.Propagator;
import com.tgac.logic.ckanren.store.ConstraintStore;
import com.tgac.logic.ckanren.store.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import com.tgac.logic.ckanren.propagator.Verdict;
import io.vavr.Predicates;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class FiniteDomainConstraints implements ConstraintStore {
	private static final FiniteDomainConstraints EMPTY = new FiniteDomainConstraints(LinkedHashMap.empty(), HashSet.empty());

	public static Package register(Package p) {
		return p.withStore(EMPTY);
	}

	// cKanren domains
	LinkedHashMap<LVar<?>, Domain<?>> domains;

	// cKanren constraints
	HashSet<Propagator> constraints;

	public static FiniteDomainConstraints empty() {
		return EMPTY;
	}

	@Override
	public boolean isEmpty() {
		return domains.isEmpty() && constraints.isEmpty();
	}

	@Override
	public ConstraintStore remove(Stored c) {
		return c instanceof Propagator ?
				FiniteDomainConstraints.of(domains, constraints.remove((Propagator) c)) :
				this;
	}

	@Override
	public ConstraintStore prepend(Stored c) {
		return c instanceof Propagator ?
				FiniteDomainConstraints.of(domains, constraints.add((Propagator) c)) :
				this;
	}

	@Override
	public boolean contains(Stored c) {
		return c instanceof Propagator &&
				constraints.contains((Propagator) c);
	}

	public static FiniteDomainConstraints getFDStore(Package p) {
		return p.getStore(FiniteDomainConstraints.class);
	}

	public static <T> Option<Domain<T>> getDom(Package p, LVar<T> x) {
		return getFDStore(p).getDomain(x);
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		return EnforceConstraintsFD.enforceConstraints(x);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public Revision revise(Prefix prefix, Package state) {
		// each newly bound value must lie in its variable's domain; a var-var
		// binding aliases the two, so the domain follows the representative
		FiniteDomainConstraints[] factor = {this};
		List<LVar<?>> changed = new ArrayList<>();
		List<Prefix> inferred = new ArrayList<>();
		for (Tuple2<LVar<?>, Term<?>> binding : prefix.bindings()) {
			Domain dom = (Domain) factor[0].getDomain((LVar) binding._1).getOrNull();
			if (dom == null) {
				continue;
			}
			boolean dead = DomainUpdate
					.apply(state, factor[0], state.walk(binding._2), dom)
					.match(
							() -> true,
							() -> false,
							(narrowedFactor, x) -> {
								factor[0] = narrowedFactor;
								changed.add(x);
								return false;
							},
							p -> {
								inferred.add(p);
								return false;
							});
			if (dead) {
				return Revision.fail();
			}
		}
		if (factor[0] == this && changed.isEmpty() && inferred.isEmpty()) {
			return Revision.unchanged();
		}
		Revision.Updated result = Revision.updated(factor[0]);
		for (LVar<?> x : changed) {
			result = result.withChanged(x);
		}
		for (Prefix p : inferred) {
			result = result.withInferred(p);
		}
		return result;
	}

	@Override
	public Revision changed(Term<?> x, Package state) {
		return administer(
				constraints.toJavaStream()
						.filter(p -> p.watches(state, x))
						.collect(Collectors.toList()),
				state);
	}

	@Override
	public Revision stated(Stored item, Package state) {
		return item instanceof Propagator ?
				administer(java.util.Collections.singletonList((Propagator) item), state) :
				Revision.unchanged();
	}

	/**
	 * Runs the given parked propagators against the live state and administers
	 * their verdicts into one revision: fail kills the branch, keep stays parked,
	 * subsumed unparks, update applies to the current factor (narrowed domains,
	 * inferred collapses), run unparks and joins the run lane. Each propagator
	 * sees the factor as left by the verdicts before it.
	 */
	private Revision administer(List<Propagator> candidates, Package state) {
		FiniteDomainConstraints[] factor = {this};
		List<Prefix> inferred = new ArrayList<>();
		List<Term<?>> changed = new ArrayList<>();
		List<Goal> runs = new ArrayList<>();
		for (Propagator p : candidates) {
			if (!factor[0].contains(p)) {
				// an earlier verdict of this same trigger removed it
				continue;
			}
			Package live = state.putStore(factor[0]);
			boolean dead = p.propagate(live).match(
					() -> true,
					() -> false,
					() -> {
						factor[0] = (FiniteDomainConstraints) factor[0].remove(p);
						return false;
					},
					f -> f.apply(live, factor[0]).match(
							() -> true,
							() -> false,
							upd -> {
								factor[0] = (FiniteDomainConstraints) upd.factor();
								inferred.addAll(upd.inferred());
								changed.addAll(upd.changed());
								runs.addAll(upd.runs());
								return false;
							}),
					goal -> {
						factor[0] = (FiniteDomainConstraints) factor[0].remove(p);
						runs.add(goal);
						return false;
					});
			if (dead) {
				return Revision.fail();
			}
		}
		if (factor[0] == this && inferred.isEmpty() && changed.isEmpty() && runs.isEmpty()) {
			return Revision.unchanged();
		}
		Revision.Updated result = Revision.updated(factor[0]);
		for (Term<?> x : changed) {
			result = result.withChanged(x);
		}
		for (Prefix p : inferred) {
			result = result.withInferred(p);
		}
		for (Goal run : runs) {
			result = result.withRun(run);
		}
		return result;
	}

	@Override
	public <A> Term<A> reify(Term<A> unifiable, Package renameSubstitutions, Package p) {
		Set<LVar<?>> varsWithDomains = domains.keySet().toJavaStream()
				.map(p::walk)
				.flatMap(u -> u.asVar().toJavaStream())
				.collect(Collectors.toSet());

		Set<LVar<?>> constrainedVarsWithoutDomains = constraints.toJavaStream()
				.map(Propagator::watchedTerms)
				.flatMap(ts -> StreamSupport.stream(ts.spliterator(), false))
				.map(p::walk)
				.flatMap(u -> u.asVar().toJavaStream())
				.filter(Predicates.not(varsWithDomains::contains))
				.collect(Collectors.toSet());

		if (!constrainedVarsWithoutDomains.isEmpty()) {
			throw new IllegalStateException("Variables without domain detected: " + constrainedVarsWithoutDomains);
		} else {
			return unifiable;
		}
	}

	public <T> Option<Domain<T>> getDomain(LVar<T> v) {
		return domains.get(v)
				.flatMap(Types.castAs(Domain.class));
	}

	public FiniteDomainConstraints withDomain(LVar<?> x, Domain<?> xd) {
		return FiniteDomainConstraints.of(domains.put(x, xd), constraints);
	}

}
