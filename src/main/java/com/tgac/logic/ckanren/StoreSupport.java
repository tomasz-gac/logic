package com.tgac.logic.ckanren;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Functor;
import com.tgac.functional.category.Monad;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StoreSupport {

	public static <T extends Store> T getConstraintStore(Package p, Class<T> cls) {
		return p.getConstraints().get(cls)
				.map(Types.<T>cast())
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "No store associated with package"));
	}

	public static Package withoutConstraint(Package p, Stored c) {
		return Package.of(p.getSubstitutions(),
				p.getConstraints()
						.get(c.getStoreClass())
						.map(cs -> cs.remove(c))
						.map(newStore -> p.getConstraints()
								.put(c.getStoreClass(), newStore))
						.getOrElse(p::getConstraints));
	}

	/**
	 * Checks whether any item within v is unbound within r Original name: anyVar
	 */
	public static Boolean isAssociated(Package p, Term<?> v) {
		return v.asVar()
				.map(lvar -> MiniKanren.walk(p, lvar) != lvar)
				.getOrElse(true);
	}

	public static Package withConstraint(Package p, Stored c) {
		return Package.of(p.getSubstitutions(),
				p.getConstraints().get(c.getStoreClass())
						.map(cs -> cs.prepend(c))
						.map(s -> p.getConstraints().put(c.getStoreClass(), s))
						.getOrElse(p::getConstraints));
	}

	public static <T extends ConstraintStore> Package updateC(Package p, Class<T> cls, UnaryOperator<T> f) {
		return Package.of(
				p.getSubstitutions(),
				p.getConstraints().put(
						cls,
						p.getConstraints().get(cls)
								.flatMap(Types.<T> castAs(ConstraintStore.class))
								.map(f)
								.getOrElse(() -> null)));
	}

	/**
	 * The constraint chokepoint: applies a unification's {@link Prefix} and lets every
	 * constraint domain respond. This is the ONLY way substitutions may grow in
	 * constraint-aware code — user unification ({@link CKanren#unify}), finite-domain
	 * collapse inference and labelling all route through here, which is what makes an
	 * inferred binding indistinguishable from a unification.
	 *
	 * <p>An empty prefix is a no-op success; a package with no constraint stores takes
	 * the pure-relational fast path (apply the delta, skip all machinery). Otherwise
	 * the prefix enters the {@link Agenda} as a Bind item: if an agenda is already
	 * riding the package a drain is in flight and the item merely queues; if not, this
	 * call is the outermost trigger and drains to fixpoint. Applying a Bind
	 * revalidates the prefix against the live package (open variables bind their
	 * walked representatives, agreeing pairs drop, contradicting pairs fail the
	 * branch), extends the substitution once, folds every {@link ConstraintStore}'s
	 * {@code onPrefix} reaction, then queues a Wake per bound variable — woken
	 * propagators' verdicts feed further items, and that queue-until-empty loop is the
	 * propagation fixpoint, one item per deferred step.
	 *
	 * <p>Contract for callers: never extend substitutions directly — obtain a
	 * {@link Prefix} (from {@code MiniKanren.unifyPrefix} or
	 * {@code Prefix.binding}) and resolve it. Raw {@code MiniKanren.unify} bypasses
	 * all constraint processing and is legitimate only inside the unifier itself.
	 */
	public static Goal resolve(Prefix prefix) {
		return p -> {
			if (prefix.isEmpty()) {
				return Cont.just(p);
			}
			if (constraintStores(p).isEmpty()) {
				// pure-relational fast path: no reactions, no agenda — the prefix is
				// already the delta, so extension is a put per binding
				return Cont.just(p.withSubstitutions(prefix.appliedTo(p.getSubstitutions())));
			}
			return enqueue(p, new Agenda.Bind(prefix));
		};
	}

	private static List<ConstraintStore> constraintStores(Package p) {
		return p.getConstraints().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.collect(Collectors.toList());
	}

	/**
	 * The single entry to propagation work. A drain in flight (agenda present)?
	 * Append — the running loop will reach the item. Otherwise this is a trigger:
	 * install the agenda, drain to quiescence, then splice the collected runs.
	 */
	static Cont<Package, Nothing> enqueue(Package p, Agenda.Item item) {
		return p.getConstraints().get(Agenda.class)
				.map(a -> Cont.<Package, Nothing> just(p.putStore(((Agenda) a).append(item))))
				.getOrElse(() -> drain().apply(p.putStore(Agenda.seeded(item))));
	}

	/**
	 * The explicit propagation loop. Pops ONE item per deferred step, so the
	 * scheduler interleaves other fibers between items (a native loop would make an
	 * entire cascade a single scheduler step and break bottom-avoidance). Phase 2:
	 * when the items are exhausted, the agenda is REMOVED and the collected run
	 * goals splice as plain search — every trigger inside them starts a fresh drain.
	 */
	private static Goal drain() {
		return Goal.defer(() -> s -> {
			Agenda agenda = (Agenda) s.getConstraints().get(Agenda.class).get();
			if (agenda.itemsExhausted()) {
				Package cleared = s.withoutStore(Agenda.class);
				return agenda.runs()
						.foldLeft(Goal.success(), Goal::and)
						.apply(cleared);
			}
			Tuple2<Agenda.Item, Agenda> popped = agenda.pop();
			return applyItem(popped._1)
					.and(drain())
					.apply(s.putStore(popped._2));
		});
	}

	/** Queue a watcher wake for {@code changed} — the narrowing producer's entry. */
	public static Cont<Package, Nothing> enqueueWake(Term<?> changed, Package p) {
		return enqueue(p, new Agenda.Wake(changed));
	}

	/** Queue an inferred-bindings prefix — the bind producer's entry. */
	public static Cont<Package, Nothing> enqueueBind(Prefix prefix, Package p) {
		return enqueue(p, new Agenda.Bind(prefix));
	}

	private static Goal applyItem(Agenda.Item item) {
		return item instanceof Agenda.Bind ?
				applyBind(((Agenda.Bind) item).prefix) :
				wake(((Agenda.Wake) item).changed);
	}

	/**
	 * Applies an inferred-bindings delta: revalidate against the live package (a
	 * pair for a still-open variable binds its walked representative; one bound to
	 * the same value is dropped; one bound to a DIFFERENT value is a contradiction
	 * between constraint domains and the branch dies), extend, run store reactions,
	 * apply their inferences, and queue wakes for the newly bound variables.
	 */
	private static Goal applyBind(Prefix prefix) {
		return s -> {
			// the asserted prefix trichotomy: open binds its representative, same
			// drops, different is a contradiction between domains — the branch dies
			Prefix kept = prefix.revalidate(s).getOrNull();
			if (kept == null) {
				return Cont.complete(Nothing.nothing());
			}
			if (kept.isEmpty()) {
				return Cont.just(s);
			}
			Package extended = s.withSubstitutions(kept.appliedTo(s.getSubstitutions()));
			// reactions are DATA: fold them; each swaps at most its own factor and
			// hands inferences back for routing
			Package reacted = extended;
			List<Inference> inferred = new ArrayList<>();
			for (ConstraintStore cs : constraintStores(reacted)) {
				Package before = reacted;
				Package after = cs.onPrefix(kept, reacted).match(
						() -> null,
						() -> before,
						(store, inferences) -> {
							inferred.addAll(inferences);
							return before.putStore(store);
						});
				if (after == null) {
					return Cont.complete(Nothing.nothing());
				}
				reacted = after;
			}
			// queue wakes for the newly bound vars, then apply reaction inferences
			// inline (bounded: their cascades append rather than recurse)
			Agenda agenda = (Agenda) reacted.getConstraints().get(Agenda.class).get();
			for (Tuple2<LVar<?>, Term<?>> binding : kept.bindings()) {
				agenda = agenda.append(new Agenda.Wake(binding._1));
			}
			return inferred.stream()
					.distinct()
					.map(Inference::toGoal)
					.reduce(Goal.success(), Goal::and)
					.apply(reacted.putStore(agenda));
		};
	}

	/**
	 * The union of every store's suspended propagators — the cross-store wake list.
	 */
	public static Iterable<Propagator> pendingPropagators(Package p) {
		return p.getConstraints().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.flatMap(cs -> StreamSupport.stream(
						cs.pendingPropagators().spliterator(), false))
				.collect(Collectors.toList());
	}

	/**
	 * Parks the propagator in its store and immediately interprets its first
	 * verdict — the statement-time entry (a constraint goal's body).
	 */
	public static Cont<Package, Nothing> activate(Propagator p, Package s) {
		return interpret(p, withConstraint(s, p));
	}

	/**
	 * Runs the propagator and administers its verdict: fail kills the branch, keep
	 * leaves it parked untouched, discharge removes it, narrowed applies the
	 * deduplicated inferences with the propagator still parked, and run discharges
	 * it and defers the goal — collected in the in-flight drain's run lane for
	 * splicing after quiescence, or run inline at statement time when no drain is
	 * in flight.
	 */
	public static Cont<Package, Nothing> interpret(Propagator p, Package s) {
		return p.propagate(s).<Cont<Package, Nothing>>match(
				() -> Cont.complete(Nothing.nothing()),
				() -> Cont.just(s),
				() -> Cont.just(withoutConstraint(s, p)),
				inferences -> inferences.stream()
						.distinct()
						.map(Inference::toGoal)
						.reduce(Goal.success(), Goal::and)
						.apply(s),
				goal -> {
					Package discharged = withoutConstraint(s, p);
					// a drain in flight collects the run for the post-quiescence
					// splice; at statement time it runs inline at the goal's own
					// position in the search
					return discharged.getConstraints().get(Agenda.class)
							.map(a -> Cont.<Package, Nothing> just(discharged.putStore(
									((Agenda) a).appendRun(goal))))
							.getOrElse(() -> goal.apply(discharged));
				});
	}

	/**
	 * Wakes every store's propagators watching {@code changed}: each still-parked
	 * match is re-interpreted against the live package (an earlier wake in the same
	 * chain may have discharged it).
	 */
	public static Goal wake(Term<?> changed) {
		return s -> {
			Goal chain = StreamSupport
					.stream(pendingPropagators(s).spliterator(), false)
					.filter(p -> p.watches(s, changed))
					.<Goal> map(p -> st -> getConstraintStore(st, p.getStoreClass()).contains(p) ?
							interpret(p, st) :
							Cont.just(st))
					.reduce(Goal.success(), Goal::and);
			return chain.apply(s);
		};
	}

	public static <T> Goal enforceConstraints(Package p, Term<T> x) {
		return p.getConstraints().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.map(cs -> cs.enforceConstraints(x))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	public static <A> Term<A> reify(Package p, Term<A> unifiable, Package renameSubstitutions) {
		return p.getConstraints().values()
				.toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.reduce(Try.success(unifiable),
						(l, cs) -> l.flatMap(u -> Try.of(() -> cs.reify(u, renameSubstitutions, p))),
						Exceptions.throwingBiOp(UnsupportedOperationException::new))
				.get();
	}
}
