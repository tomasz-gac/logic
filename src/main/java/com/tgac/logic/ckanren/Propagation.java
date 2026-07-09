package com.tgac.logic.ckanren;

// ABOUTME: The propagation engine: the chokepoint that applies prefixes, the agenda
// ABOUTME: worklist that makes the fixpoint explicit, and verdict administration.

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.MFiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.ckanren.store.ConstraintStore;
import com.tgac.logic.ckanren.store.Revision;
import com.tgac.logic.ckanren.store.Suspension;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Stored;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

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
			if (!constraintStores(p).findAny().isPresent() && !suspensionsPending(p)) {
				// pure-relational fast path: no revisions, no suspensions to ripen,
				// no agenda — the prefix is already the delta, a put per binding
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
	 * The suspension entry: park {@code body} until {@code ripe} holds — checked
	 * when a watched chain binds. {@code ripe} must be monotone in the
	 * substitution and depend on nothing else. Already ripe at statement time:
	 * the body runs right here, at its own search position.
	 */
	public static Goal suspend(Iterable<? extends Term<?>> watched,
			java.util.function.Predicate<com.tgac.logic.unification.Substitutions> ripe, Goal body) {
		return s -> ripe.test(s.substitution()) ?
				body.apply(s) :
				Cont.just(s.withStore(Suspensions.EMPTY)
						.updateStore(Suspensions.class, sus -> sus.park(Suspension.of(watched, ripe, body))));
	}

	/**
	 * Folds a trigger over the constraint stores as one fiber: each store answers
	 * a {@link Revision} — at most its own factor swapped — possibly across many
	 * deferred steps (the store's scheduling choice); the driver routes the
	 * consequences: inferred prefixes queue as Bind items, runs join the run
	 * lane. Intra-store re-examination notes ride {@code Update}, not Revision —
	 * leaking one to the driver is unrepresentable.
	 */
	private static Cont<Package, Nothing> reviseAll(
			Package s,
			BiFunction<ConstraintStore, Package, Fiber<Revision>> trigger) {
		return Cont.defer(() ->
				constraintStores(s)
						.reduce(MFiber.mdone(s),
								(chain, cs) ->
										chain.flatMap(pkg -> MFiber.ofFiber(trigger.apply(cs, pkg))
												.flatMap(revision -> revision.match(
														MFiber::none,            // fail: branch dies
														() -> MFiber.mdone(pkg), // unchanged
														upd -> MFiber.mdone(queue(pkg.putStore(upd.factor()), upd))))),
								Exceptions.throwingBiOp(UnsupportedOperationException::new))
						.map(Cont::<Package, Nothing>just)
						.getOrElse(() -> Cont.complete(Nothing.nothing())));
	}

	/** Queues a revision's harvest: binds to the agenda, suspensions ripe-or-parked. */
	private static Package queue(Package pkg, Revision.Updated upd) {
		Package current = pkg;
		for (Suspension suspension : upd.suspensions()) {
			current = suspension.isRipe(current) ?
					current.putStore(agendaOf(current).appendRun(suspension.body())) :
					current.withStore(Suspensions.EMPTY)
							.updateStore(Suspensions.class, sus -> sus.park(suspension));
		}
		return current.putStore(agendaOf(current).queue(upd));
	}

	private static Agenda agendaOf(Package pkg) {
		return (Agenda) pkg.getConstraints().get(Agenda.class).get();
	}

	/**
	 * Ripens suspensions after a binding: parked bodies whose watched chains
	 * touch the bound variables and whose condition now holds move to the run
	 * lane — fired once, forever.
	 */
	private static Goal ripen(Prefix prefix) {
		return s -> {
			if (!s.getConstraints().get(Suspensions.class).isDefined()) {
				return Cont.just(s);
			}
			Package current = s;
			Suspensions parked = (Suspensions) s.getConstraints().get(Suspensions.class).get();
			for (Suspension suspension : parked.parked) {
				boolean touched = false;
				for (Tuple2<com.tgac.logic.unification.LVar<?>, com.tgac.logic.unification.Term<?>> b : prefix.bindings()) {
					if (suspension.watchesAny(current, b._1)) {
						touched = true;
						break;
					}
				}
				if (touched && suspension.isRipe(current)) {
					current = current
							.updateStore(Suspensions.class, sus -> sus.without(suspension))
							.putStore(agendaOf(current).appendRun(suspension.body()));
				}
			}
			return Cont.just(current);
		};
	}

	/** Answers may not leave while suspensions pend. */
	public static boolean suspensionsPending(Package p) {
		return p.getConstraints().get(Suspensions.class)
				.map(sus -> !((Suspensions) sus).parked.isEmpty())
				.getOrElse(false);
	}

	private static Stream<ConstraintStore> constraintStores(Package p) {
		return p.getConstraints().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast);
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

	/** Parked suspensions — persistent, branch-local, driver-owned. */
	static final class Suspensions implements Store {
		static final Suspensions EMPTY = new Suspensions(List.empty());

		final List<Suspension> parked;

		private Suspensions(List<Suspension> parked) {
			this.parked = parked;
		}

		Suspensions park(Suspension s) {
			return new Suspensions(parked.append(s));
		}

		Suspensions without(Suspension s) {
			return new Suspensions(parked.remove(s));
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
			return "suspensions" + parked;
		}
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
					return ((Goal) s2 -> reviseAll(s2, (cs, p) -> cs.revise(kept, p)))
							.and(ripen(kept))
							.apply(extended);
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

		/** A revision's inferred prefixes, queued as Bind items. */
		Agenda queue(Revision.Updated upd) {
			Agenda queued = this;
			for (Prefix prefix : upd.inferred()) {
				queued = queued.append(new Bind(prefix));
			}
			return queued;
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
