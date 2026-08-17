package com.tgac.logic.lattice;

// ABOUTME: The generic constraint store over a component lattice: a resident theory
// ABOUTME: of impositions and propagators; instances supply only their capability record.

import static com.tgac.logic.unification.LVal.lval;
import com.tgac.logic.constraints.store.Atom;

import com.tgac.functional.algebra.Absorbing;
import com.tgac.functional.algebra.MonotoneDrain;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Suspension;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Predicates;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * The store residue that never mentions its value domain
 * (docs/design/lattice-store.md): entries keyed by NAME — a live {@link LVar}
 * or a canonical Any — carrying a value of the component lattice {@code L},
 * plus the propagator kernel (named, value-equal, watched, cascaded, deduped).
 * Pointwise meet, slotwise leq, revise/normalize/stated, split and rename are
 * all inherited; the value's capability record ({@link Domain}) supplies
 * verification, collapse and the termination guard, and a concrete store
 * supplies only its construction seams {@link #create} and
 * {@link #bottomStore} plus its {@code enforce}.
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class LatticeFactor<L extends Domain<L>, S extends LatticeFactor<L, S>>
		implements Factor<S>, Absorbing {

	/**
	 * The resident theory: an {@link Imposition} per constrained NAME (a live
	 * LVar or a canonical Any) plus the parked propagators. The values-map
	 * face is the theory's slot index read by surface.
	 */
	protected final Theory<S> theory;

	/** The same store kind over different contents. */
	protected abstract S create(Theory<S> theory);

	@SuppressWarnings("unchecked")
	private Imposition<L, S> imposition(Term<?> target, L value) {
		return new Imposition<>((Class<S>) getClass(), target, value);
	}

	private Option<Atom<S>> valueAtom(Term<?> v) {
		return theory.atom("imposition", HashSet.of(v));
	}

	@SuppressWarnings("unchecked")
	protected java.util.stream.Stream<Imposition<L, S>> impositions() {
		return theory.kind(Imposition.class).map(i -> (Imposition<L, S>) i);
	}

	@SuppressWarnings("unchecked")
	protected java.util.stream.Stream<Propagator<S>> props() {
		return theory.kind(Propagator.class).map(p -> (Propagator<S>) p);
	}

	/** The factor without its entry at {@code name} — spent bookkeeping drops. */
	protected S spent(Term<?> name) {
		return valueAtom(name)
				.map(atom -> create(theory.without(atom)))
				.getOrElse(this::self);
	}

	/**
	 * The canonical dead store: meets and cascades transition to it on failure,
	 * so ⊥ IS the branch death and has exactly one representative per kind.
	 */
	protected abstract S bottomStore();

	@SuppressWarnings("unchecked")
	private S self() {
		return (S) this;
	}

	@SuppressWarnings("unchecked")
	public Option<L> getValue(Term<?> v) {
		return valueAtom(v).map(atom -> (L) atom.payload());
	}

	/** Narrowing write: the value FUSES with any existing entry at {@code x}. */
	public S withValue(Term<?> x, L value) {
		return create(theory.with(imposition(x, value)));
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
		if (isAbsorbing() || other.isAbsorbing()) {
			return bottomStore();
		}
		S met = create(theory.meet(other.theory));
		return met.impositions().anyMatch(i -> ((L) i.getValue()).isAbsorbing()) ?
				bottomStore() :
				met;
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
		if (isAbsorbing()) {
			return true;
		}
		if (other.isAbsorbing()) {
			return false;
		}
		return theory.leq(other.theory);
	}

	@Override
	public boolean isAbsorbing() {
		return this == bottomStore();
	}

	@Override
	public boolean isEmpty() {
		return !isAbsorbing() && theory.isEmpty();
	}

	@Override
	public S meet(Atom<S> c) {
		// an Imposition is a MESSAGE, not parkable content: its value enters
		// only through stated's routing (verification, collapse, inference) —
		// a silent park would put un-vetted knowledge in front of normalize
		return c instanceof Propagator ?
				create(theory.with(c)) :
				self();
	}

	@Override
	public Theory<S> theory() {
		return theory;
	}

	@Override
	public boolean contains(Atom<S> c) {
		return c instanceof Propagator && theory.atoms().contains(c);
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
		LVar<?> x = target.asVar().get();
		L previous = getValue(x).getOrNull();
		L effective;
		if (previous != null) {
			effective = previous.meet(value);
			if (effective.isAbsorbing()) {
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
	 * Posting-position imposition as the chokepoint's own statement: an
	 * {@link Imposition} item through the statement entry, consumed by this
	 * store's {@code stated} — the routing lives with the store, not at the
	 * call site. Doomed under partial knowledge exactly when the value cannot
	 * stand against the live state: a ground target the value refuses, or a
	 * live entry it meets to bottom.
	 */
	@SuppressWarnings("unchecked")
	public Posting impose(Term<?> target, L value) {
		return Propagation.activate(
				new Imposition<>((Class<S>) getClass(), target, value),
				p -> p.getStores().containsKey(getClass()) ? p
						: p.withStore(create(Theory.empty())),
				p -> doomedAt(p, target, value));
	}

	@SuppressWarnings("unchecked")
	private boolean doomedAt(Package p, Term<?> target, L value) {
		Term<?> w = p.substitution().walk(target);
		if (w.asVal().isDefined()) {
			return !value.admits(w.get());
		}
		return p.getStores().get(getClass())
				.map(store -> (S) store)
				.flatMap(live -> live.getValue(w))
				.exists(existing -> existing.meet(value).isAbsorbing());
	}

	/**
	 * Posting-position re-examination of this store's own watchers of
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
										.<Goal> map(Propagation::resolve)
										.reduce(Goal.success(), Goal::and)
										.apply(updated);
							}));
		});
	}

	@Override
	public Fiber<Revision> normalize(Prefix prefix, Package state) {
		// each newly bound value must lie in its variable's lattice value; a
		// var-var binding aliases the two, so the value follows the representative;
		// every bound variable's watchers re-examine, then the cascade drains
		S factor = self();
		List<Prefix> inferred = new ArrayList<>();
		List<Goal> runs = new ArrayList<>();
		ArrayDeque<Term<?>> queue = new ArrayDeque<>();
		for (Tuple2<LVar<?>, Term<?>> binding : prefix.bindings()) {
			queue.add(binding._1);
			L value = factor.getValue(binding._1).getOrNull();
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
			factor = factor.spent(binding._1);
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
		if (isAbsorbing()) {
			return Fiber.done(Revision.fail());
		}
		S factor = self();
		List<Prefix> inferred = new ArrayList<>();
		List<Goal> runs = new ArrayList<>();
		ArrayDeque<Term<?>> queue = new ArrayDeque<>();
		for (Imposition<L, S> entry : impositions().collect(Collectors.toList())) {
			Term<?> walked = state.walk(entry.getTarget());
			if (walked == entry.getTarget() && walked.asVar().isDefined()) {
				continue;    // a live var at its root
			}
			// anything else verifies: a rebound name follows its representative,
			// and a GROUND-keyed entry (the theory crossing keeps val-resolved
			// targets) takes its membership check instead of lingering unread
			factor = consume(factor.update(state, walked, entry.getValue()),
					factor, inferred, runs, queue);
			if (factor == null) {
				return Fiber.done(Revision.fail());
			}
			factor = factor.spent(entry.getTarget());
		}
		for (Propagator<S> propagator : props().collect(Collectors.toList())) {
			factor = consume(examine(propagator, state.putStore(factor), factor),
					factor, inferred, runs, queue);
			if (factor == null) {
				return Fiber.done(Revision.fail());
			}
		}
		return cascade(state, factor, inferred, runs, queue);
	}

	@Override
	public Fiber<Revision> stated(Atom<S> item, Package state) {
		if (item instanceof Imposition) {
			// update's verification/collapse/narrowing routing, inside the
			// store's method: the item is a message, the values map keeps
			// the knowledge
			Imposition<L, S> imposition = (Imposition<L, S>) item;
			List<Prefix> inferred = new ArrayList<>();
			List<Goal> runs = new ArrayList<>();
			ArrayDeque<Term<?>> queue = new ArrayDeque<>();
			S factor = consume(
					update(state, state.walk(imposition.getTarget()), imposition.getValue()),
					self(), inferred, runs, queue);
			if (factor == null) {
				return Fiber.done(Revision.fail());
			}
			return cascade(state, factor, inferred, runs, queue);
		}
		if (!(item instanceof Propagator)) {
			return Fiber.done(Revision.unchanged());
		}
		List<Prefix> inferred = new ArrayList<>();
		List<Goal> runs = new ArrayList<>();
		ArrayDeque<Term<?>> queue = new ArrayDeque<>();
		S factor = consume(
				examine((Propagator<S>) item, state.putStore(this), self()),
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
		java.util.List<Propagator<S>> parked = start.props().collect(Collectors.toList());
		S factor = MonotoneDrain.drainUnsafe(start, queue, (current, next) -> {
			S stepped = current;
			ArrayDeque<Term<?>> discovered = new ArrayDeque<>();
			for (Propagator<S> p : parked) {
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
		if (factor.isAbsorbing()) {
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
	private Update examine(Propagator<S> p, Package live, S factor) {
		return p.propagate(live).match(
				Update::fail,
				Update::unchanged,
				() -> Update.applied(create(factor.theory.without(p))),
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
	public <A> Term<A> reify(Term<A> unifiable, Renaming renaming, Package p) {
		Set<LVar<?>> varsWithValues = impositions()
				.map(Imposition::getTarget)
				.map(p::walk)
				.flatMap(u -> u.asVar().toJavaStream())
				.collect(Collectors.toSet());

		Set<LVar<?>> constrainedVarsWithoutValues = props()
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
		return theory.split(vars).map(this::create, this::create);
	}

	/**
	 * Values re-keyed through the renaming — an entry whose name resolves
	 * to a value is spent bookkeeping (verified when it bound) and drops;
	 * propagators re-watch their renamed terms.
	 */
	@Override
	public Fiber<S> rename(Renaming renaming) {
		Fiber<Theory<S>> renamed = Fiber.done(Theory.empty());
		for (Imposition<L, S> entry : impositions().collect(Collectors.toList())) {
			// Imposition.rename keeps a val-resolved target (the faithful
			// crossing); dropping it here is this store's OPTIMIZATION — safe
			// either way, since normalize verifies ground-keyed entries
			renamed = renamed.flatMap(t -> renaming.apply(entry.getTarget())
					.map(target -> target.asVal().isDefined() ? t
							: t.with(imposition(target, entry.getValue()))));
		}
		for (Propagator<S> propagator : props().collect(Collectors.toList())) {
			renamed = renamed.flatMap(t -> propagator.rename(renaming).map(t::with));
		}
		return renamed.map(this::create);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LatticeFactor<?, ?> that = (LatticeFactor<?, ?>) o;
		if (isAbsorbing() || that.isAbsorbing()) {
			// ⊥ has exactly one representative per kind — identity, never structure,
			// so an empty live store can never compare equal to the dead one
			return false;
		}
		return theory.equals(that.theory);
	}

	@Override
	public int hashCode() {
		return theory.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(" + theory + ")";
	}
}
