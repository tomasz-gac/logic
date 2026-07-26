package com.tgac.logic.lattice;

// ABOUTME: The generic constraint store over a component lattice: a name→value map
// ABOUTME: plus named propagators; instances supply only their capability record.

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.functional.algebra.Bottomed;
import com.tgac.functional.algebra.MonotoneDrain;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Suspension;
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

/**
 * The store residue that never mentions its value domain
 * (docs/design/lattice-store.md): entries keyed by NAME — a live {@link LVar}
 * or a canonical Hole — carrying a value of the component lattice {@code L},
 * plus the propagator kernel (named, value-equal, watched, cascaded, deduped).
 * Pointwise meet, slotwise leq, revise/normalize/stated, split and rename are
 * all inherited; the value's capability record ({@link Domain}) supplies
 * verification, collapse and the termination guard, and a concrete store
 * supplies only its construction seams {@link #create} and
 * {@link #bottomStore} plus its {@code enforce}.
 */
public abstract class LatticeStore<L extends Domain<L>, S extends LatticeStore<L, S>>
		implements Projectable<S>, Bottomed {

	// entries keyed by NAME: a live LVar or a canonical Hole
	protected final LinkedHashMap<Term<?>, L> values;

	protected final HashSet<Propagator> propagators;

	protected LatticeStore(LinkedHashMap<Term<?>, L> values, HashSet<Propagator> propagators) {
		this.values = values;
		this.propagators = propagators;
	}

	/** The same store kind over different contents. */
	protected abstract S create(LinkedHashMap<Term<?>, L> values, HashSet<Propagator> propagators);

	/**
	 * The canonical dead store: meets and cascades transition to it on failure,
	 * so ⊥ IS the branch death and has exactly one representative per kind.
	 */
	protected abstract S bottomStore();

	@SuppressWarnings("unchecked")
	private S self() {
		return (S) this;
	}

	public Option<L> getValue(Term<?> v) {
		return values.get(v);
	}

	public S withValue(Term<?> x, L value) {
		return create(values.put(x, value), propagators);
	}

	/**
	 * The store as a product order in the KNOWLEDGE direction: values
	 * pointwise (a missing name is ⊤), propagators by set union — more
	 * constraints is more knowledge, smaller region, lower. The cascade
	 * still terminates against this order: values strictly narrow (the
	 * {@link Domain#stabilized} guard) and discharge only ever REMOVES propagators
	 * (knowledge gone redundant — the factor rises, the region stands).
	 */
	@Override
	public S meet(S other) {
		if (isBottom() || other.isBottom()) {
			return bottomStore();
		}
		LinkedHashMap<Term<?>, L> met = values;
		for (Tuple2<Term<?>, L> entry : other.values) {
			L mine = met.get(entry._1).getOrNull();
			L narrowed = mine == null ? entry._2 : mine.meet(entry._2);
			if (narrowed.isBottom()) {
				return bottomStore();
			}
			met = met.put(entry._1, narrowed);
		}
		return create(met, propagators.union(other.propagators));
	}

	/**
	 * Entailment checked slotwise, without materializing the meet: every
	 * name {@code other} constrains must be at-least-as-narrow here, and
	 * every constraint {@code other} holds must ride here too. The same
	 * order the meet derives, at an early-exit, allocation-free cost —
	 * subsumption keys, entailment matching and answer dedup all fold this.
	 */
	@Override
	public boolean leq(S other) {
		if (isBottom()) {
			return true;
		}
		if (other.isBottom()) {
			return false;
		}
		return other.values.forAll(entry -> values.get(entry._1)
				.exists(mine -> mine.leq(entry._2)))
				&& propagators.containsAll(other.propagators);
	}

	@Override
	public boolean isBottom() {
		return this == bottomStore();
	}

	@Override
	public boolean isEmpty() {
		return !isBottom() && values.isEmpty() && propagators.isEmpty();
	}

	@Override
	public ConstraintStore remove(Stored c) {
		return c instanceof Propagator ?
				create(values, propagators.remove((Propagator) c)) :
				this;
	}

	@Override
	public ConstraintStore prepend(Stored c) {
		return c instanceof Propagator ?
				create(values, propagators.add((Propagator) c)) :
				this;
	}

	@Override
	public boolean contains(Stored c) {
		return c instanceof Propagator &&
				propagators.contains((Propagator) c);
	}

	/**
	 * cKanren's process-δ as a value: applying "target ⊂ value" against a
	 * state and this factor. A ground target is a membership check; a
	 * variable's previous value is met — a bottom meet fails, a stabilized
	 * one is the termination guard of wake-on-narrowing, a collapse to a
	 * point becomes an inferred binding (the value map is deliberately NOT
	 * updated — stale value information under a binding is fine, values are
	 * consulted only for unbound variables), and anything else narrows the
	 * factor with a re-examination note.
	 */
	public Update update(Package state, Term<?> target, L value) {
		if (target.isVal()) {
			return value.admits(target.get()) ? Update.unchanged() : Update.fail();
		}
		LVar<?> x = (LVar<?>) target.asVar().get();
		L previous = values.get(x).getOrNull();
		L effective;
		if (previous != null) {
			effective = previous.meet(value);
			if (effective.isBottom()) {
				return Update.fail();
			}
			if (effective.stabilized(previous)) {
				return Update.unchanged();
			}
		} else {
			effective = value;
		}
		Option<Object> point = effective.asPoint();
		if (point.isDefined()) {
			// only open variables collapse, so the mint succeeds; the defensive
			// branch mirrors an already-bound no-op
			return Prefix.binding(state.substitution(), x, lval(point.get()))
					.<Update> map(prefix -> Update.applied(this).withInferred(prefix))
					.getOrElse(Update.unchanged());
		}
		return Update.applied(withValue(x, effective)).withReexamine(x);
	}

	/**
	 * Statement-position imposition: walk the target, apply against the live
	 * factor (registering an empty store if absent), and route the outcome
	 * through the public entries — resolve for a collapse, re-examination for
	 * a strict narrowing.
	 */
	@SuppressWarnings("unchecked")
	public Goal impose(Term<?> target, L value) {
		return s -> {
			Package reg = s.getStores().containsKey(getClass()) ? s
					: s.withStore(create(LinkedHashMap.empty(), HashSet.empty()));
			S live = (S) reg.getStore(getClass());
			return live.update(reg, reg.walk(target), value)
					.<Cont<Package, Nothing>> match(
							() -> Cont.complete(Nothing.nothing()),
							() -> Cont.just(reg),
							applied -> {
								Goal binds = applied.inferred().stream()
										.map(Propagation::resolve)
										.reduce(Goal.success(), Goal::and);
								Goal wakes = applied.reexamine().stream()
										.map(this::reexamineOwn)
										.reduce(Goal.success(), Goal::and);
								return binds.and(wakes).apply(reg.putStore(applied.factor()));
							});
		};
	}

	/**
	 * Statement-position re-examination of this store's own watchers of
	 * {@code x}: the live store drains its cascade (a fiber — long cascades
	 * stay fairly stepped) and the collapses it yields re-enter through the
	 * chokepoint like any other inferred bindings.
	 */
	@SuppressWarnings("unchecked")
	protected Goal reexamineOwn(Term<?> x) {
		return s -> Cont.defer(() -> {
			S live = (S) s.getStore(getClass());
			return live.cascade(s, live, new ArrayList<>(), new ArrayList<>(),
							new ArrayDeque<>(Collections.<Term<?>> singletonList(x)))
					.map(revision -> revision.<Cont<Package, Nothing>> match(
							() -> Cont.complete(Nothing.nothing()),
							() -> Cont.just(s),
							upd -> {
								Package updated = s.putStore(upd.factor());
								return upd.inferred().stream()
										.map(Propagation::resolve)
										.reduce(Goal.success(), Goal::and)
										.apply(updated);
							}));
		});
	}

	@Override
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		// each newly bound value must lie in its variable's lattice value; a
		// var-var binding aliases the two, so the value follows the representative;
		// every bound variable's watchers re-examine, then the cascade drains
		S factor = self();
		List<Prefix> inferred = new ArrayList<>();
		List<Goal> runs = new ArrayList<>();
		ArrayDeque<Term<?>> queue = new ArrayDeque<>();
		for (Tuple2<LVar<?>, Term<?>> binding : prefix.bindings()) {
			queue.add(binding._1);
			L value = factor.values.get(binding._1).getOrNull();
			if (value == null) {
				continue;
			}
			factor = consume(factor.update(state, state.walk(binding._2), value),
					factor, inferred, runs, queue);
			if (factor == null) {
				return Fiber.done(Revision.fail());
			}
			// the entry is spent the moment its verification passed (ground) or
			// its value followed the representative (alias) — prune it here,
			// while we already hold it, so the factor never drifts
			factor = factor.create(factor.values.remove(binding._1), factor.propagators);
		}
		return cascade(state, factor, inferred, runs, queue);
	}

	/**
	 * Wholesale self-reaction — a factor was met into this store
	 * ({@link Propagation#absorb}): entries whose name no longer lives at its
	 * root verify against the binding (fail on miss) or follow the
	 * representative, every propagator takes its first examination against
	 * the met state, and the cascade drains.
	 */
	@Override
	public Fiber<Revision> normalize(Package state) {
		if (isBottom()) {
			return Fiber.done(Revision.fail());
		}
		S factor = self();
		List<Prefix> inferred = new ArrayList<>();
		List<Goal> runs = new ArrayList<>();
		ArrayDeque<Term<?>> queue = new ArrayDeque<>();
		for (Tuple2<Term<?>, L> entry : values) {
			Term<?> walked = state.walk(entry._1);
			if (walked == entry._1) {
				continue;    // live at its root
			}
			factor = consume(factor.update(state, walked, entry._2),
					factor, inferred, runs, queue);
			if (factor == null) {
				return Fiber.done(Revision.fail());
			}
			factor = factor.create(factor.values.remove(entry._1), factor.propagators);
		}
		for (Propagator propagator : propagators) {
			factor = consume(examine(propagator, state.putStore(factor), factor),
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
		S factor = consume(
				examine((Propagator) item, state.putStore(this), self()),
				self(), inferred, runs, queue);
		if (factor == null) {
			return Fiber.done(Revision.fail());
		}
		return cascade(state, factor, inferred, runs, queue);
	}

	/**
	 * This store's propagation loop: one iteration is one term whose watchers
	 * re-examine; verdict updates discover further terms. The loop is the
	 * unchecked {@link MonotoneDrain}: the store is the descending state
	 * (values narrow, subsumption discharges propagators) and a failing
	 * update transitions to ⊥, short-circuiting the drain — but the
	 * contraction laws hold by construction, not verification:
	 * {@link #update} couples re-examination to strict narrowing
	 * (DomainUpdateContractTest pins it), so the per-step leq/equals sweeps
	 * of the checked twin would verify what the toolkit cannot express
	 * violating. Synchronous, so the whole cascade stays one fiber step; a
	 * store hosting expensive propagators would use the fibered
	 * {@code Worklist} twin instead — granularity is the store author's choice.
	 */
	protected Fiber<Revision> cascade(Package state, S start,
			List<Prefix> inferred, List<Goal> runs, ArrayDeque<Term<?>> queue) {
		S factor = MonotoneDrain.drainUnsafe(start, queue, (current, next) -> {
			S stepped = current;
			ArrayDeque<Term<?>> discovered = new ArrayDeque<>();
			for (Propagator p : stepped.propagators.toJavaList()) {
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
					return MonotoneDrain.Step.stop(bottomStore());
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
					Collections.<Term<?>> emptyList(), p -> true, run));
		}
		return Fiber.done(result);
	}

	/** One propagator's verdict as an {@link Update} step against the factor. */
	private Update examine(Propagator p, Package live, S factor) {
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
	@SuppressWarnings("unchecked")
	private S consume(Update step, S factor,
			List<Prefix> inferred, List<Goal> runs, ArrayDeque<Term<?>> queue) {
		return step.match(
				() -> null,
				() -> factor,
				applied -> {
					inferred.addAll(applied.inferred());
					runs.addAll(applied.runs());
					queue.addAll(applied.reexamine());
					return (S) applied.factor();
				});
	}

	@Override
	public <A> Term<A> reify(Term<A> unifiable, Substitutions renameSubstitutions, Package p) {
		Set<LVar<?>> varsWithValues = values.keySet().toJavaStream()
				.map(p::walk)
				.flatMap(u -> u.asVar().toJavaStream())
				.collect(Collectors.toSet());

		Set<LVar<?>> constrainedVarsWithoutValues = propagators.toJavaStream()
				.map(Propagator::watchedTerms)
				.flatMap(ts -> StreamSupport.stream(ts.spliterator(), false))
				.map(p::walk)
				.flatMap(u -> u.asVar().toJavaStream())
				.filter(Predicates.not(varsWithValues::contains))
				.collect(Collectors.toSet());

		if (!constrainedVarsWithoutValues.isEmpty()) {
			throw new IllegalStateException("Variables without domain detected: " + constrainedVarsWithoutValues);
		} else {
			return unifiable;
		}
	}

	/**
	 * Lossless factoring: values partition by name membership, a propagator
	 * goes to the covered half iff every watched VAR is supplied (grounds
	 * are always covered). {@code _1 ∧ _2 = this}.
	 */
	@Override
	public Tuple2<S, S> split(List<LVar<?>> vars) {
		Set<Term<?>> covered = new java.util.HashSet<>(vars);
		LinkedHashMap<Term<?>, L> in = LinkedHashMap.empty();
		LinkedHashMap<Term<?>, L> out = LinkedHashMap.empty();
		for (Tuple2<Term<?>, L> entry : values) {
			if (covered.contains(entry._1)) {
				in = in.put(entry);
			} else {
				out = out.put(entry);
			}
		}
		HashSet<Propagator> inConstraints = HashSet.empty();
		HashSet<Propagator> outConstraints = HashSet.empty();
		for (Propagator propagator : propagators) {
			boolean fits = propagator.watchedTerms().forAll(watched ->
					!watched.asVar().isDefined() || covered.contains(watched));
			if (fits) {
				inConstraints = inConstraints.add(propagator);
			} else {
				outConstraints = outConstraints.add(propagator);
			}
		}
		return Tuple.of(create(in, inConstraints), create(out, outConstraints));
	}

	/**
	 * Values re-keyed through the renaming — an entry whose name resolves
	 * to a value is spent bookkeeping (verified when it bound) and drops;
	 * propagators re-watch their renamed terms.
	 */
	@Override
	public S rename(Renaming renaming) {
		LinkedHashMap<Term<?>, L> renamed = LinkedHashMap.empty();
		for (Tuple2<Term<?>, L> entry : values) {
			Term<?> target = renaming.apply(entry._1);
			if (!target.asVal().isDefined()) {
				renamed = renamed.put(target, entry._2);
			}
		}
		HashSet<Propagator> renamedConstraints = propagators.map(p ->
				p.watching(p.watchedTerms().map(renaming::apply)));
		return create(renamed, renamedConstraints);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LatticeStore<?, ?> that = (LatticeStore<?, ?>) o;
		if (isBottom() || that.isBottom()) {
			// ⊥ has exactly one representative per kind — identity, never structure,
			// so an empty live store can never compare equal to the dead one
			return false;
		}
		return values.equals(that.values) && propagators.equals(that.propagators);
	}

	@Override
	public int hashCode() {
		return values.hashCode() * 31 + propagators.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(" + values + ", " + propagators + ")";
	}
}
