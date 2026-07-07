package com.tgac.logic.ckanren;

// ABOUTME: The propagation engine: the chokepoint that applies prefixes, the agenda
// ABOUTME: worklist that makes the fixpoint explicit, and verdict administration.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.ckanren.propagator.Inference;
import com.tgac.logic.ckanren.store.ConstraintStore;
import com.tgac.logic.ckanren.store.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Data and its only interpreter in one class: the {@link Agenda} worklist — what
 * the old recursion kept as suspended frames — and the engine that drains it
 * (docs/design/capability-constraint-api.md, Steps 2.5 and 3.5).
 */
public final class Propagation {

	private Propagation() {
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
	 * {@code revise} revision, then queues a Wake per bound variable — woken
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
				// pure-relational fast path: no revisions, no agenda — the prefix is
				// already the delta, so extension is a put per binding
				return Cont.just(p.withSubstitutions(prefix.appliedTo(p.getSubstitutions())));
			}
			return enqueue(p, new Agenda.Bind(prefix));
		};
	}

	/** Queue a watcher wake for {@code changed} — the narrowing producer's entry. */
	public static Cont<Package, Nothing> enqueueWake(Term<?> changed, Package p) {
		return enqueue(p, new Agenda.Changed(changed));
	}

	/** Queue an inferred-bindings prefix — the bind producer's entry. */
	public static Cont<Package, Nothing> enqueueBind(Prefix prefix, Package p) {
		return enqueue(p, new Agenda.Bind(prefix));
	}

	/**
	 * The statement entry for store items: parks {@code item} in its store (which
	 * must already be registered) and queues its first examination — the owning
	 * store's {@code stated} hook decides everything decidable at statement time.
	 */
	public static Goal activate(Stored item) {
		return s -> enqueue(s.withStored(item), new Agenda.Stated(item));
	}

	/**
	 * The announcement entry: {@code x} changed — bound or narrowed — so every
	 * store re-examines whatever it has watching {@code x}. Mode-oblivious like
	 * the other entries; a spurious announcement is harmless.
	 */
	public static Goal changed(Term<?> x) {
		return s -> enqueue(s, new Agenda.Changed(x));
	}

	/**
	 * Interprets one inference: a bind queues its delta (the agenda's Bind
	 * application performs the single revalidation — the same trichotomy
	 * Disequality's record verification reads with the opposite polarity); a
	 * narrow walks its target at APPLICATION time (the target may have been bound
	 * meanwhile — by an earlier inference of the same verdict, or captured
	 * pre-walk by the emitter; narrowing a stale var object would re-bind a bound
	 * variable) and applies the narrowing to the live term.
	 */
	private static Goal apply(Inference inference) {
		return inference.match(
				prefix -> s -> enqueueBind(prefix, s),
				(target, narrowing) -> s -> narrowing.applyTo(MiniKanren.walk(s, target))
						.apply(s));
	}

	/**
	 * Folds a trigger over the constraint stores: each answers a {@link Revision} —
	 * at most its own factor swapped — and the driver routes the payloads: changed
	 * terms and inferred prefixes queue as agenda items, runs join the run lane,
	 * legacy inferences apply inline.
	 */
	private static Cont<Package, Nothing> reviseAll(
			Package s,
			java.util.function.BiFunction<ConstraintStore, Package, Revision> trigger,
			java.util.List<Term<?>> alsoChanged) {
		Package current = s;
		java.util.List<Inference> legacy = new ArrayList<>();
		java.util.List<Prefix> inferred = new ArrayList<>();
		java.util.List<Term<?>> changed = new ArrayList<>(alsoChanged);
		java.util.List<Goal> runs = new ArrayList<>();
		for (ConstraintStore cs : constraintStores(current)) {
			Package before = current;
			Package after = trigger.apply(cs, before).match(
					() -> null,
					() -> before,
					upd -> {
						legacy.addAll(upd.inferences());
						inferred.addAll(upd.inferred());
						changed.addAll(upd.changed());
						runs.addAll(upd.runs());
						return before.putStore(upd.factor());
					});
			if (after == null) {
				return Cont.complete(Nothing.nothing());
			}
			current = after;
		}
		Agenda agenda = (Agenda) current.getConstraints().get(Agenda.class).get();
		for (Term<?> x : changed) {
			agenda = agenda.append(new Agenda.Changed(x));
		}
		for (Prefix prefix : inferred) {
			agenda = agenda.append(new Agenda.Bind(prefix));
		}
		for (Goal run : runs) {
			agenda = agenda.appendRun(run);
		}
		return legacy.stream()
				.distinct()
				.map(Propagation::apply)
				.reduce(Goal.success(), Goal::and)
				.apply(current.putStore(agenda));
	}

	private static java.util.List<ConstraintStore> constraintStores(Package p) {
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
	private static Cont<Package, Nothing> enqueue(Package p, Agenda.Item item) {
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
			return popped._1.apply()
					.and(drain())
					.apply(s.putStore(popped._2));
		});
	}

	/**
	 * The explicit propagation worklist — pending Bind/Wake items plus the run
	 * lane, riding the package during a drain; its presence marks "drain in
	 * flight". Two item kinds drain FIFO, one per deferred step; collected run
	 * goals splice only after the items are exhausted and the agenda is removed.
	 * A plain, inert store: constraint processing never sees it.
	 */
	static final class Agenda implements Store {

		abstract static class Item {
			private Item() {
			}

			/** How this item executes against the state that popped it. */
			abstract Goal apply();
		}

		/** Inferred bindings — a prefix, revalidated against the live package at pop. */
		static final class Bind extends Item {
			final Prefix prefix;

			Bind(Prefix prefix) {
				this.prefix = prefix;
			}

			/**
			 * Applies the delta: revalidate against the live package (a pair for a
			 * still-open variable binds its walked representative; one bound to the
			 * same value is dropped; one bound to a DIFFERENT value is a
			 * contradiction between constraint domains and the branch dies), extend,
			 * revise every store, apply their inferences, and queue wakes for the
			 * newly bound variables.
			 */
			@Override
			Goal apply() {
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
					// revise every store against the delta; the newly bound vars queue
					// as changed terms so their watchers re-examine
					java.util.List<Term<?>> bound = new ArrayList<>();
					for (Tuple2<LVar<?>, Term<?>> binding : kept.bindings()) {
						bound.add(binding._1);
					}
					return reviseAll(extended, (cs, p) -> cs.revise(kept, p), bound);
				};
			}

			@Override
			public String toString() {
				return prefix.toString();
			}
		}

		/** A term changed — every store re-examines its watchers. */
		static final class Changed extends Item {
			final Term<?> changed;

			Changed(Term<?> changed) {
				this.changed = changed;
			}

			@Override
			Goal apply() {
				return s -> reviseAll(s,
						(cs, p) -> cs.changed(changed, p),
						java.util.Collections.emptyList());
			}

			@Override
			public String toString() {
				return "changed(" + changed + ")";
			}
		}

		/** A store item was just stated — its owning store examines it. */
		static final class Stated extends Item {
			final Stored item;

			Stated(Stored item) {
				this.item = item;
			}

			@Override
			Goal apply() {
				return s -> reviseAll(s,
						(cs, p) -> item.getStoreClass() == cs.getClass() ?
								cs.stated(item, p) :
								Revision.unchanged(),
						java.util.Collections.emptyList());
			}

			@Override
			public String toString() {
				return "stated(" + item + ")";
			}
		}

		private final List<Item> items;
		private final List<Goal> runs;

		private Agenda(List<Item> items, List<Goal> runs) {
			this.items = items;
			this.runs = runs;
		}

		static Agenda seeded(Item first) {
			return new Agenda(List.of(first), List.empty());
		}

		Agenda append(Item item) {
			return new Agenda(items.append(item), runs);
		}

		Agenda appendRun(Goal goal) {
			return new Agenda(items, runs.append(goal));
		}

		boolean itemsExhausted() {
			return items.isEmpty();
		}

		Tuple2<Item, Agenda> pop() {
			return Tuple.of(items.head(), new Agenda(items.tail(), runs));
		}

		List<Goal> runs() {
			return runs;
		}

		@Override
		public Store remove(Stored c) {
			return this;
		}

		@Override
		public Store prepend(Stored c) {
			return this;
		}

		@Override
		public boolean contains(Stored c) {
			return false;
		}

		@Override
		public String toString() {
			return "agenda" + items + (runs.isEmpty() ? "" : " runs" + runs);
		}
	}
}
