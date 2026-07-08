package com.tgac.logic.finitedomain;

import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.Worklist;
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
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		// each newly bound value must lie in its variable's domain; a var-var
		// binding aliases the two, so the domain follows the representative;
		// every bound variable's watchers re-examine, then the cascade drains
		Cascade acc = new Cascade(this);
		List<Term<?>> seeds = new ArrayList<>();
		for (Tuple2<LVar<?>, Term<?>> binding : prefix.bindings()) {
			seeds.add(binding._1);
			Domain dom = (Domain) acc.factor.getDomain((LVar) binding._1).getOrNull();
			if (dom == null) {
				continue;
			}
			boolean dead = DomainUpdate
					.apply(state, acc.factor, state.walk(binding._2), dom)
					.match(
							() -> true,
							() -> false,
							(narrowedFactor, x) -> {
								acc.factor = narrowedFactor;
								seeds.add(x);
								return false;
							},
							p -> {
								acc.inferred.add(p);
								return false;
							});
			if (dead) {
				return Fiber.done(Revision.fail());
			}
		}
		return cascade(state, acc, seeds);
	}

	@Override
	public Fiber<Revision> stated(Stored item, Package state) {
		if (!(item instanceof Propagator)) {
			return Fiber.done(Revision.unchanged());
		}
		Cascade acc = new Cascade(this);
		List<Term<?>> discovered = administer(
				java.util.Collections.singletonList((Propagator) item), state, acc);
		if (acc.dead) {
			return Fiber.done(Revision.fail());
		}
		return cascade(state, acc, discovered);
	}

	/**
	 * The statement-position re-examination seam ({@code dom} narrowing an
	 * existing domain, labelling's catch-up): drains this store's own cascade
	 * from {@code x} against the live state.
	 */
	static Fiber<Revision> reexamine(Term<?> x, Package state) {
		FiniteDomainConstraints self = getFDStore(state);
		return self.cascade(state, new Cascade(self), java.util.Collections.singletonList(x));
	}

	/**
	 * This store's propagation loop: one {@link Worklist} item is one term whose
	 * watchers re-examine; verdict updates discover further terms; the drain is
	 * fiber-stepped, so the driving scheduler interleaves fairly between items.
	 * Termination is contraction: {@link DomainUpdate} only ever shrinks domains.
	 */
	private Fiber<Revision> cascade(Package state, Cascade acc, List<Term<?>> seeds) {
		FiniteDomainConstraints original = this;
		return Worklist.drain(acc, seeds, (a, w) -> {
					List<Term<?>> discovered = administer(
							a.factor.constraints.toJavaStream()
									.filter(p -> p.watches(state.putStore(a.factor), w))
									.collect(Collectors.toList()),
							state, a);
					return a.dead ?
							Worklist.Step.stop(a) :
							Worklist.Step.proceed(a, discovered);
				})
				.map(a -> a.toRevision(original));
	}

	/**
	 * Runs the given parked propagators against the live state and administers
	 * their verdicts into the cascade state: fail kills the branch, keep stays
	 * parked, subsumed unparks, update applies to the current factor (narrowed
	 * domains feed the worklist, inferred collapses accumulate), run unparks and
	 * joins the run lane. Each propagator sees the factor as left by the verdicts
	 * before it.
	 */
	private static List<Term<?>> administer(List<Propagator> candidates, Package state, Cascade acc) {
		List<Term<?>> discovered = new ArrayList<>();
		for (Propagator p : candidates) {
			if (!acc.factor.contains(p)) {
				// an earlier verdict of this same trigger removed it
				continue;
			}
			Package live = state.putStore(acc.factor);
			boolean dead = p.propagate(live).match(
					() -> true,
					() -> false,
					() -> {
						acc.factor = (FiniteDomainConstraints) acc.factor.remove(p);
						return false;
					},
					f -> f.apply(live, acc.factor).match(
							() -> true,
							() -> false,
							upd -> {
								acc.factor = (FiniteDomainConstraints) upd.factor();
								acc.inferred.addAll(upd.inferred());
								acc.runs.addAll(upd.runs());
								discovered.addAll(upd.narrowed());
								return false;
							}),
					goal -> {
						acc.factor = (FiniteDomainConstraints) acc.factor.remove(p);
						acc.runs.add(goal);
						return false;
					});
			if (dead) {
				acc.dead = true;
				return discovered;
			}
		}
		return discovered;
	}

	/** The threaded state of one cascade: the evolving factor plus its harvest. */
	private static final class Cascade {
		FiniteDomainConstraints factor;
		final List<Prefix> inferred = new ArrayList<>();
		final List<Goal> runs = new ArrayList<>();
		boolean dead;

		Cascade(FiniteDomainConstraints factor) {
			this.factor = factor;
		}

		Revision toRevision(FiniteDomainConstraints original) {
			if (dead) {
				return Revision.fail();
			}
			if (factor == original && inferred.isEmpty() && runs.isEmpty()) {
				return Revision.unchanged();
			}
			Revision.Updated result = Revision.updated(factor);
			for (Prefix p : inferred) {
				result = result.withInferred(p);
			}
			for (Goal run : runs) {
				result = result.withRun(run);
			}
			return result;
		}
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
