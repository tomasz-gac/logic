package com.tgac.logic.ckanren;

// ABOUTME: The propagation engine: the chokepoint that applies prefixes, the agenda
// ABOUTME: worklist that makes the fixpoint explicit, and verdict administration.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.fibers.Fiber;
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

	/**
	 * The statement entry for store items: parks {@code item} in its store (which
	 * must already be registered) and queues its first examination — the owning
	 * store's {@code stated} hook decides everything decidable at statement time.
	 */
	public static Goal activate(Stored item) {
		return s -> enqueue(s.withStored(item), new Agenda.Stated(item));
	}

	/**
	 * Folds a trigger over the constraint stores as one fiber: each store answers
	 * a {@link Revision} — at most its own factor swapped — possibly across many
	 * deferred steps (the store's scheduling choice); the driver routes the
	 * consequences: inferred prefixes queue as Bind items, runs join the run
	 * lane. Narrowed terms are intra-store notes and must never reach this fold.
	 */
	private static Cont<Package, Nothing> reviseAll(
			Package s,
			java.util.function.BiFunction<ConstraintStore, Package, Fiber<Revision>> trigger) {
		return Cont.defer(() -> fold(s, constraintStores(s), 0,
				new ArrayList<>(), new ArrayList<>(), trigger));
	}

	private static Fiber<Cont<Package, Nothing>> fold(
			Package current,
			java.util.List<ConstraintStore> stores,
			int i,
			java.util.List<Prefix> inferred,
			java.util.List<Goal> runs,
			java.util.function.BiFunction<ConstraintStore, Package, Fiber<Revision>> trigger) {
		if (i == stores.size()) {
			Agenda agenda = (Agenda) current.getConstraints().get(Agenda.class).get();
			for (Prefix prefix : inferred) {
				agenda = agenda.append(new Agenda.Bind(prefix));
			}
			for (Goal run : runs) {
				agenda = agenda.appendRun(run);
			}
			return Fiber.done(Cont.just(current.putStore(agenda)));
		}
		ConstraintStore cs = stores.get(i);
		Package before = current;
		return Fiber.defer(() -> trigger.apply(cs, before)
				.flatMap(revision -> revision.<Fiber<Cont<Package, Nothing>>> match(
						() -> Fiber.done(Cont.complete(Nothing.nothing())),
						() -> fold(before, stores, i + 1, inferred, runs, trigger),
						upd -> {
							if (!upd.narrowed().isEmpty()) {
								throw new IllegalStateException(
										"narrowed terms are store-internal: the owning store's"
												+ " cascade consumes them, the driver never does");
							}
							inferred.addAll(upd.inferred());
							runs.addAll(upd.runs());
							return fold(before.putStore(upd.factor()), stores, i + 1,
									inferred, runs, trigger);
						})));
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
					// each store's revise is COMPLETE: custody, its own watchers of the
					// newly bound variables, and its own cascade
					return reviseAll(extended, (cs, p) -> cs.revise(kept, p));
				};
			}

			@Override
			public String toString() {
				return prefix.toString();
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
								Fiber.done(Revision.unchanged()));
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
