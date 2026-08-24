package com.tgac.logic.lattice;

// ABOUTME: The generic constraint behavior over a component lattice: normalize, stated
// ABOUTME: and the cascade drain a theory of impositions and propagators; instances supply their capability record.

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.functional.algebra.MonotoneDrain;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Suspension;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import io.vavr.Predicates;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashSet;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The store behavior that never mentions its value domain
 * (docs/design/lattice-store.md): the resident theory holds entries keyed by
 * NAME — a live {@link LVar} or a canonical Any — carrying a value of the
 * component lattice {@code L}, plus the propagator kernel (named, value-equal,
 * watched, cascaded, deduped). Verification, collapse and the termination
 * guard come from the value's capability record ({@link Domain}); a concrete
 * store supplies only its {@code enforce}. The class is stateless: every
 * trigger receives the pair's theory and answers with a revised one.
 */
public abstract class LatticeFactor<L extends Domain<L>, S extends LatticeFactor<L, S>>
		implements Factor<S> {

	@SuppressWarnings("unchecked")
	private Imposition<L, S> imposition(Term<?> target, L value) {
		return new Imposition<>((Class<S>) getClass(), target, value, self());
	}

	private Option<Imposition<L, S>> valueAtom(Theory<S> theory, Term<?> v) {
		return theory.atom(getClass(), "imposition", HashSet.of(v)).map(Types.cast());
	}

	@SuppressWarnings("unchecked")
	protected Stream<Imposition<L, S>> impositions(Theory<S> theory) {
		return theory.kind(Imposition.class).map(i -> (Imposition<L, S>) i);
	}

	@SuppressWarnings("unchecked")
	protected Stream<Propagator<S>> props(Theory<S> theory) {
		return theory.kind(Propagator.class).map(p -> (Propagator<S>) p);
	}

	/** The theory without its entry at {@code name} — spent bookkeeping drops. */
	protected Theory<S> spent(Theory<S> theory, Term<?> name) {
		return valueAtom(theory, name)
				.map(theory::without)
				.getOrElse(theory);
	}

	@SuppressWarnings("unchecked")
	private S self() {
		return (S) this;
	}

	/** The live package with {@code theory} in residence — what examinations read. */
	private Package resident(Package state, Theory<S> theory) {
		return state.putStore(getClass(), Constraint.of(theory, self()));
	}

	public Option<L> getValue(Theory<S> theory, Term<?> v) {
		return valueAtom(theory, v).map(Imposition::getValue);
	}

	/** Narrowing write: the value FUSES with any existing entry at {@code x}. */
	public Theory<S> withValue(Theory<S> theory, Term<?> x, L value) {
		return theory.with(imposition(x, value));
	}

	/**
	 * cKanren's process-δ as a value: applying "target ⊂ value" against a
	 * state and this family's theory. A ground target is a membership check; a
	 * variable's previous value is met — a bottom meet fails, a stabilized
	 * one is the termination guard of wake-on-narrowing, a collapse to a
	 * point becomes an inferred binding (the value map is deliberately NOT
	 * updated — stale value information under a binding is fine, values are
	 * consulted only for unbound variables), and anything else narrows the
	 * theory with a re-examination note.
	 */
	public Update update(Theory<S> theory, Package state, Term<?> target, L value) {
		if (target.isVal()) {
			return value.admits(target.get()) ? Update.unchanged() : Update.fail();
		}
		LVar<?> x = target.asVar().get();
		L previous = getValue(theory, x).getOrNull();
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
					.<Update> map(prefix -> Update.applied(theory).withInferred(prefix))
					.getOrElse(Update.unchanged());
		}
		return Update.applied(withValue(theory, x, effective)).withReexamine(x);
	}

	/**
	 * Posting-position imposition as the chokepoint's own statement: an
	 * {@link Imposition} item through the statement entry, consumed by this
	 * store's {@code stated} — the routing lives with the store, not at the
	 * call site. Doomed under partial knowledge exactly when the value cannot
	 * stand against the live state: a ground target the value refuses, or a
	 * live entry it meets to bottom.
	 */
	public Posting impose(Term<?> target, L value) {
		return Propagation.activate(imposition(target, value));
	}

	/**
	 * Posting-position re-examination of this store's own watchers of
	 * {@code x}: the live theory drains its cascade (a fiber — long cascades
	 * stay fairly stepped) and the collapses it yields re-enter through the
	 * chokepoint like any other inferred bindings.
	 */
	@SuppressWarnings("unchecked")
	protected Goal reexamineOwn(Term<?> x) {
		return s -> Cont.defer(() -> {
			Constraint<S> live = Constraint.in(s, (Class<S>) getClass()).get();
			return cascade(s, live.getTheory(), live.getTheory(), new ArrayList<>(), new ArrayList<>(),
							new ArrayDeque<>(Collections.<Term<?>> singletonList(x)))
					.map(revision -> revision.<Cont<Package, Nothing>> match(
							() -> Cont.complete(Nothing.nothing()),
							() -> Cont.just(s),
							upd -> {
								Package updated = s.putStore(getClass(), upd.constraint());
								return upd.inferred().stream()
										.<Goal> map(Propagation::resolve)
										.reduce(Goal.success(), Goal::and)
										.apply(updated);
							}));
		});
	}

	@Override
	public Fiber<Revision> normalize(Theory<S> incoming, Prefix prefix, Package state) {
		// each newly bound value must lie in its variable's lattice value; a
		// var-var binding aliases the two, so the value follows the representative;
		// every bound variable's watchers re-examine, then the cascade drains
		Theory<S> current = incoming;
		List<Prefix> inferred = new ArrayList<>();
		List<Goal> runs = new ArrayList<>();
		ArrayDeque<Term<?>> queue = new ArrayDeque<>();
		for (Tuple2<LVar<?>, Term<?>> binding : prefix.bindings()) {
			queue.add(binding._1);
			L value = getValue(current, binding._1).getOrNull();
			if (value == null) {
				continue;
			}
			current = consume(update(current, state, state.walk(binding._2), value),
					current, inferred, runs, queue);
			if (current == null) {
				return Fiber.done(Revision.fail());
			}
			// the entry is spent the moment its verification passed (ground) or
			// its value followed the representative (alias) — prune it here,
			// while we already hold it, so the theory never drifts
			current = spent(current, binding._1);
		}
		return cascade(state, incoming, current, inferred, runs, queue);
	}

	/**
	 * The focused reaction — knowledge moved into this family
	 * ({@link Propagation#activate} posted an atom, {@link Propagation#absorb}
	 * met a theory): each arrived imposition takes update's routing against
	 * the resident knowledge — verification (a ground or re-bound target
	 * fails or spends), collapse inference (a point mints its binding), or a
	 * narrowing wake — and each arrived propagator takes its first
	 * examination; then the cascade drains. The unified statement semantics:
	 * points collapse EAGERLY on every door (the stated/absorb asymmetry was
	 * ruled out with the rows' merge).
	 */
	@Override
	public Fiber<Revision> normalize(Theory<S> incoming, LinkedHashSet<Atom<S>> focus, Package state) {
		if (incoming.isAbsorbing()) {
			return Fiber.done(Revision.fail());
		}
		Theory<S> current = incoming;
		List<Prefix> inferred = new ArrayList<>();
		List<Goal> runs = new ArrayList<>();
		ArrayDeque<Term<?>> queue = new ArrayDeque<>();
		for (Atom<S> atom : focus) {
			if (atom instanceof Imposition) {
				// the door fused the value into its slot; re-state the fused
				// value through update's routing against the slot-stripped
				// theory, so the routing sees exactly the combined knowledge
				Imposition<L, S> imposition = (Imposition<L, S>) atom;
				Term<?> target = imposition.getTarget();
				L fused = valueAtom(current, target).map(Imposition::getValue)
						.getOrElse(imposition.getValue());
				if (fused.isAbsorbing()) {
					return Fiber.done(Revision.fail());
				}
				Theory<S> stripped = spent(current, target);
				current = consume(update(stripped, state, state.walk(target), fused),
						stripped, inferred, runs, queue);
			} else if (atom instanceof Propagator) {
				if (!current.atoms().contains(atom)) {
					continue;    // discharged earlier in this trigger, or never landed
				}
				current = consume(examine((Propagator<S>) atom, resident(state, current), current),
						current, inferred, runs, queue);
			}
			if (current == null) {
				return Fiber.done(Revision.fail());
			}
		}
		return cascade(state, incoming, current, inferred, runs, queue);
	}

	/**
	 * This store's propagation loop: one iteration is one term whose watchers
	 * re-examine; verdict updates discover further terms. The loop is the
	 * unchecked {@link MonotoneDrain} over the THEORY — {@link Theory}'s own
	 * {@code Semilattice}+{@code Absorbing} declarations type the termination
	 * theorem's premise, and the theory is the descending state (values
	 * narrow, subsumption discharges propagators). A failing update stops the
	 * drain and fails the revision — but the contraction laws hold by
	 * construction, not verification: {@link #update} couples re-examination
	 * to strict narrowing (DomainUpdateContractTest pins it), so the per-step
	 * leq/equals sweeps of the checked twin would verify what the toolkit
	 * cannot express violating. Synchronous, so the whole cascade stays one
	 * fiber step; a store hosting expensive propagators would use the fibered
	 * {@code Worklist} twin instead — granularity is the store author's choice.
	 * Unchanged is measured against the RESIDENT theory, not the working
	 * start: a caller that stripped or spent entries before cascading has
	 * already moved, and the driver must land the replacement.
	 */
	protected Fiber<Revision> cascade(Package state, Theory<S> resident, Theory<S> start,
			List<Prefix> inferred, List<Goal> runs, ArrayDeque<Term<?>> queue) {
		List<Propagator<S>> parked = props(start).collect(Collectors.toList());
		boolean[] dead = {false};
		Theory<S> outcome = MonotoneDrain.drainUnsafe(start, queue, (current, next) -> {
			Theory<S> stepped = current;
			ArrayDeque<Term<?>> discovered = new ArrayDeque<>();
			for (Propagator<S> p : parked) {
				if (!stepped.atoms().contains(p)) {
					// an earlier verdict of this same trigger removed it
					continue;
				}
				Package live = resident(state, stepped);
				if (!p.watches(live, next)) {
					continue;
				}
				stepped = consume(examine(p, live, stepped), stepped, inferred, runs, discovered);
				if (stepped == null) {
					dead[0] = true;
					return MonotoneDrain.Step.stop(current);
				}
			}
			return MonotoneDrain.Step.proceed(stepped, discovered);
		});
		if (dead[0] || outcome.isAbsorbing()) {
			return Fiber.done(Revision.fail());
		}
		if (outcome == resident && inferred.isEmpty() && runs.isEmpty()) {
			return Fiber.done(Revision.unchanged());
		}
		Revision.Updated result = Revision.updated(Constraint.of(outcome, self()));
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

	/** One propagator's verdict as an {@link Update} step against the theory. */
	private Update examine(Propagator<S> p, Package live, Theory<S> theory) {
		return p.propagate(live).match(
				Update::fail,
				Update::unchanged,
				() -> Update.applied(theory.without(p)),
				f -> f.apply(live, theory));
	}

	/**
	 * Threads one step: the new theory (null when the branch died), payloads
	 * accumulated, re-examination notes queued.
	 */
	@SuppressWarnings("unchecked")
	private Theory<S> consume(Update step, Theory<S> theory,
			List<Prefix> inferred, List<Goal> runs, ArrayDeque<Term<?>> queue) {
		return step.match(
				() -> null,
				() -> theory,
				applied -> {
					inferred.addAll(applied.inferred());
					runs.addAll(applied.runs());
					queue.addAll(applied.reexamine());
					return (Theory<S>) applied.theory();
				});
	}

	@Override
	public <A> Term<A> reify(Theory<S> incoming, Term<A> unifiable, Renaming renaming, Package p) {
		Set<LVar<?>> varsWithValues = impositions(incoming)
				.map(Imposition::getTarget)
				.map(p::walk)
				.flatMap(u -> u.asVar().toJavaStream())
				.collect(Collectors.toSet());

		Set<LVar<?>> constrainedVarsWithoutValues = props(incoming)
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
}
