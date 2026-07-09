package com.tgac.logic.finitedomain;

import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.ckanren.store.ConstraintStore;
import com.tgac.logic.ckanren.store.Revision;
import com.tgac.logic.ckanren.store.Suspension;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.Predicates;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.control.Option;
import java.util.ArrayDeque;
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
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		// each newly bound value must lie in its variable's domain; a var-var
		// binding aliases the two, so the domain follows the representative;
		// every bound variable's watchers re-examine, then the cascade drains
		FiniteDomainConstraints factor = this;
		List<Prefix> inferred = new ArrayList<>();
		List<Goal> runs = new ArrayList<>();
		ArrayDeque<Term<?>> queue = new ArrayDeque<>();
		for (Tuple2<LVar<?>, Term<?>> binding : prefix.bindings()) {
			queue.add(binding._1);
			Domain dom = (Domain) factor.getDomain((LVar) binding._1).getOrNull();
			if (dom == null) {
				continue;
			}
			factor = consume(DomainUpdate.apply(state, factor, state.walk(binding._2), dom),
					factor, inferred, runs, queue);
			if (factor == null) {
				return Fiber.done(Revision.fail());
			}
		}
		return cascade(state, factor, inferred, runs, queue);
	}

	@Override
	public Fiber<Revision> stated(Stored item, Package state) {
		if (!(item instanceof Propagator)) {
			return Fiber.done(Revision.unchanged());
		}
		List<Prefix> inferred = new ArrayList<>();
		List<Goal> runs = new ArrayList<>();
		ArrayDeque<Term<?>> queue = new ArrayDeque<>();
		FiniteDomainConstraints factor = consume(
				examine((Propagator) item, state.putStore(this), this),
				this, inferred, runs, queue);
		if (factor == null) {
			return Fiber.done(Revision.fail());
		}
		return cascade(state, factor, inferred, runs, queue);
	}

	/**
	 * The statement-position re-examination seam ({@code dom} narrowing an
	 * existing domain, labelling's catch-up): drains this store's own cascade
	 * from {@code x} against the live state.
	 */
	static Fiber<Revision> reexamine(Term<?> x, Package state) {
		FiniteDomainConstraints self = getFDStore(state);
		return self.cascade(state, self, new ArrayList<>(), new ArrayList<>(),
				new ArrayDeque<>(java.util.Collections.singletonList(x)));
	}

	/**
	 * This store's propagation loop: one iteration is one term whose watchers
	 * re-examine; verdict updates discover further terms; contraction
	 * ({@link DomainUpdate} only ever shrinks domains) is what terminates it.
	 * A plain synchronous loop — every propagator here is cheap, so the whole
	 * cascade is one fiber step. A store hosting expensive propagators would
	 * defer between items instead ({@code functional}'s {@code Worklist} is
	 * that loop, fiber-stepped) — granularity is the store author's choice.
	 */
	private Fiber<Revision> cascade(Package state, FiniteDomainConstraints start,
			List<Prefix> inferred, List<Goal> runs, ArrayDeque<Term<?>> queue) {
		FiniteDomainConstraints factor = start;
		while (!queue.isEmpty()) {
			Term<?> next = queue.poll();
			List<Propagator> snapshot = factor.constraints.toJavaList();
			for (Propagator p : snapshot) {
				if (!factor.contains(p)) {
					// an earlier verdict of this same trigger removed it
					continue;
				}
				Package live = state.putStore(factor);
				if (!p.watches(live, next)) {
					continue;
				}
				factor = consume(examine(p, live, factor), factor, inferred, runs, queue);
				if (factor == null) {
					return Fiber.done(Revision.fail());
				}
			}
		}
		if (factor == this && inferred.isEmpty() && runs.isEmpty()) {
			return Fiber.done(Revision.unchanged());
		}
		Revision.Updated result = Revision.updated(factor);
		for (Prefix prefix : inferred) {
			result = result.withInferred(prefix);
		}
		for (Goal run : runs) {
			// a store-level search effect is a degenerate (already ripe) suspension
			result = result.withSuspend(Suspension.of(
					java.util.Collections.emptyList(), p -> true, run));
		}
		return Fiber.done(result);
	}

	/** One propagator's verdict as an {@link Update} step against the factor. */
	private static Update examine(Propagator p, Package live, FiniteDomainConstraints factor) {
		return p.propagate(live).match(
				Update::fail,
				Update::unchanged,
				() -> Update.applied(factor.remove(p)),
				f -> f.apply(live, factor));
	}

	/**
	 * Threads one step: the new factor (null when the branch died), payloads
	 * accumulated, re-examination notes queued.
	 */
	private static FiniteDomainConstraints consume(Update step, FiniteDomainConstraints factor,
			List<Prefix> inferred, List<Goal> runs, ArrayDeque<Term<?>> queue) {
		return step.match(
				() -> null,
				() -> factor,
				applied -> {
					inferred.addAll(applied.inferred());
					runs.addAll(applied.runs());
					queue.addAll(applied.reexamine());
					return (FiniteDomainConstraints) applied.factor();
				});
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
