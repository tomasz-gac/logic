package com.tgac.logic.finitedomain;

import com.tgac.functional.reflection.Types;
import com.tgac.logic.ckanren.Propagator;
import com.tgac.logic.ckanren.ConstraintStore;
import com.tgac.logic.ckanren.Inference;
import com.tgac.logic.ckanren.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Predicates;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.control.Option;
import java.util.List;
import java.util.ArrayList;
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
		// this store's reaction: each newly bound value must lie in its variable's
		// domain; a var-var binding aliases the two, so the domain follows the
		// representative as a narrow inference
		List<Inference> narrows = new ArrayList<>();
		for (Tuple2<LVar<?>, Term<?>> binding : prefix.bindings()) {
			Domain dom = (Domain) getDomain((LVar) binding._1).getOrNull();
			if (dom == null) {
				continue;
			}
			Term<?> v = binding._2;
			if (v.isVal()) {
				if (!dom.contains(v.get())) {
					return Revision.fail();
				}
			} else {
				narrows.add(Inference.narrow(v, dom));
			}
		}
		return narrows.isEmpty() ? Revision.unchanged() : Revision.updated(this, narrows);
	}

	@Override
	public Iterable<Propagator> pendingPropagators() {
		return constraints;
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
