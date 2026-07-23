package com.tgac.logic.finitedomain;

import com.tgac.functional.algebra.Bottomed;
import com.tgac.functional.algebra.MeetSemilattice;
import com.tgac.functional.algebra.MonotoneDrain;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Suspension;
import com.tgac.logic.finitedomain.domains.Empty;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Predicates;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class FiniteDomainConstraints implements ConstraintStore,
		Projectable<DomainResidue>,
		MeetSemilattice<FiniteDomainConstraints>, Bottomed {
	private static final FiniteDomainConstraints EMPTY = new FiniteDomainConstraints(LinkedHashMap.empty(), HashSet.empty());

	// the canonical dead store: any-empty-domain meets normalize to it, and the
	// cascade transitions to it on a failing update, so ⊥ IS the branch death
	private static final LVar<?> SENTINEL = (LVar<?>) LVar.lvar().asVar().get();
	private static final FiniteDomainConstraints BOTTOM = new FiniteDomainConstraints(
			LinkedHashMap.of(SENTINEL, Empty.instance()), HashSet.empty());

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

	static FiniteDomainConstraints bottom() {
		return BOTTOM;
	}

	/**
	 * The store as a product order: domains pointwise (a missing variable is ⊤),
	 * propagators by set intersection. Both components only ever descend during
	 * propagation — narrowing shrinks domains, subsumption discharges propagators
	 * — so this is the cascade's termination measure.
	 */
	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public FiniteDomainConstraints meet(FiniteDomainConstraints other) {
		if (isBottom() || other.isBottom()) {
			return BOTTOM;
		}
		LinkedHashMap<LVar<?>, Domain<?>> met = domains;
		for (Tuple2<LVar<?>, Domain<?>> entry : other.domains) {
			Domain<?> mine = met.get(entry._1).getOrNull();
			Domain<?> narrowed = mine == null ? entry._2
					: (Domain<?>) ((Domain) mine).meet((Domain) entry._2);
			if (narrowed.isBottom()) {
				return BOTTOM;
			}
			met = met.put(entry._1, narrowed);
		}
		return new FiniteDomainConstraints(met, constraints.intersect(other.constraints));
	}

	/**
	 * Identity against the canonical instance: a live store never holds an
	 * empty domain ({@link DomainUpdate} fails before storing one, and meet
	 * short-circuits to {@link #BOTTOM}), so the dead store has exactly one
	 * representative.
	 */
	@Override
	public boolean isBottom() {
		return this == BOTTOM;
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
				new ArrayDeque<>(Collections.singletonList(x)));
	}

	/**
	 * This store's propagation loop: one iteration is one term whose watchers
	 * re-examine; verdict updates discover further terms. The loop is the
	 * unchecked {@link MonotoneDrain}: the store is the descending state
	 * (domains narrow, subsumption discharges propagators) and a failing
	 * update transitions to ⊥, short-circuiting the drain — but the
	 * contraction laws hold by construction, not verification:
	 * {@link DomainUpdate} couples re-examination to strict narrowing
	 * (DomainUpdateContractTest pins it), so the per-step leq/equals sweeps
	 * of the checked twin would verify what the toolkit cannot express
	 * violating. Synchronous, so the whole cascade stays one fiber step; a
	 * store hosting expensive propagators would use the fibered
	 * {@code Worklist} twin instead — granularity is the store author's choice.
	 */
	private Fiber<Revision> cascade(Package state, FiniteDomainConstraints start,
			List<Prefix> inferred, List<Goal> runs, ArrayDeque<Term<?>> queue) {
		FiniteDomainConstraints factor = MonotoneDrain.drainUnsafe(start, queue, (current, next) -> {
			FiniteDomainConstraints stepped = current;
			ArrayDeque<Term<?>> discovered = new ArrayDeque<>();
			for (Propagator p : stepped.constraints.toJavaList()) {
				if (!stepped.contains(p)) {
					// an earlier verdict of this same trigger removed it
					continue;
				}
				Package live = state.putStore(stepped);
				if (!p.watches(live, next)) {
					continue;
				}
				stepped = consume(examine(p, live, stepped), stepped, inferred, runs, discovered);
				if (stepped == null) {
					return MonotoneDrain.Step.stop(BOTTOM);
				}
			}
			return MonotoneDrain.Step.proceed(stepped, discovered);
		});
		if (factor.isBottom()) {
			return Fiber.done(Revision.fail());
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
					Collections.emptyList(), p -> true, run));
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
	public <A> Term<A> reify(Term<A> unifiable, Substitutions renameSubstitutions, Package p) {
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

	/**
	 * TRANSCRIBES everything expressible: per-slot domains (post-propagation)
	 * plus every wholly-covered coupling as its LIVE propagator object with a
	 * (var → slot) map. {@code wideningAllowed} governs only the escapes.
	 */
	@Override
	public DomainResidue project(List<LVar<?>> vars, boolean wideningAllowed) {
		HashMap<Integer, Domain<?>> slots = HashMap.empty();
		for (int i = 0; i < vars.size(); i++) {
			Option<Domain<?>> d = domains.get(vars.get(i));
			if (d.isDefined()) {
				slots = slots.put(i, d.get());
			}
		}
		io.vavr.collection.HashSet<CarriedConstraint> carried = io.vavr.collection.HashSet.empty();
		for (Propagator propagator : constraints) {
			CarriedConstraint coupling = carry(propagator, vars);
			if (coupling != null) {
				carried = carried.add(coupling);
			} else if (!wideningAllowed) {
				// exactness was demanded and this coupling escapes the vars —
				// the declared context makes the refusal ours to raise
				throw new IllegalStateException(
						"exact projection demanded but a live constraint escapes the "
								+ "projected vars — ground the escaping var or include it");
			}
			// else: dropped by permission — the caller declared widening sound
		}
		return DomainResidue.of(slots, carried);
	}

	/**
	 * A propagator is carriable when every watched VAR is supplied — carried
	 * as the LIVE OBJECT plus its (var → slot) map; grounds need no entry.
	 * Identity comparison against walked roots: a watcher aliased away from
	 * its root fails carriage and falls to the wideningAllowed handling.
	 */
	private static CarriedConstraint carry(Propagator propagator, List<LVar<?>> vars) {
		java.util.List<Tuple2<LVar<?>, Integer>> varSlots = new ArrayList<>();
		for (Term<?> watched : propagator.watchedTerms()) {
			if (watched.asVar().isDefined()) {
				LVar<?> var = (LVar<?>) watched.asVar().get();
				int slot = vars.indexOf(var);
				if (slot < 0) {
					return null;    // escapes to an unsupplied var
				}
				varSlots.add(Tuple.of(var, slot));
			}
		}
		return CarriedConstraint.of(propagator, Array.ofAll(varSlots));
	}

	/**
	 * Domains under bindings are STALE by design ({@link DomainUpdate} — the
	 * map is not pruned on collapse), so live knowledge is: any pending
	 * propagator, or a domain whose variable still walks to a variable.
	 */
	@Override
	public boolean discharged(Package state) {
		return constraints.isEmpty()
				&& domains.keySet().forAll(v -> state.walk(v).isVal());
	}

}
