package com.tgac.logic.constraints;

// ABOUTME: The propagation engine: the chokepoint that applies prefixes, the agenda
// ABOUTME: worklist that makes the fixpoint explicit, and verdict administration.

import com.tgac.functional.Exceptions;
import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.MFiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.store.Absorbable;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Suspension;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.goals.Watermark;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.control.Option;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Data and its only interpreter in one class: the {@link Agenda} worklist — what
 * the old recursion kept as suspended frames — and the engine that drains it
 * (docs/reference/constraint-kernel.md, Steps 2.5 and 3.5).
 */
public final class Propagation {

	private Propagation() {
	}

	/**
	 * The constraint chokepoint: applies a unification's {@link Prefix} and lets every
	 * constraint domain respond. This is the ONLY way substitutions may grow in
	 * constraint-aware code — user unification ({@link Constraints#unify}), finite-domain
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
	 * {@code Prefix.binding}) and resolve it. The routing serves two coequal
	 * purposes: the veto — any store's {@code revise} may fail the branch before
	 * the binding stands — and the wake — this call is the only place the other
	 * stores hear of the binding at all (watchers fire, suspensions ripen). A
	 * bypass therefore does not fail loudly; it leaves every other store's
	 * knowledge silently stale, and the cost surfaces later as wrong answers
	 * rather than a refusal. Raw {@code MiniKanren.unify} bypasses
	 * all constraint processing and is legitimate only inside the unifier itself.
	 */
	public static Goal resolve(Prefix prefix) {
		return p -> {
			Watermark.check(p, prefix);
			if (prefix.isEmpty()) {
				return Cont.just(p);
			}
			if (!constraintStores(p).findAny().isPresent() && !suspensionsPending(p)) {
				// pure-relational fast path: no revisions, no suspensions to ripen,
				// no agenda — the prefix is already the delta, a put per binding
				return Cont.just(p.withSubstitutions(prefix.appliedTo(p.substitution())));
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
	 * The bulk statement entry — the trigger family's third row: a whole
	 * FACTOR arrives. Meets {@code factor} into its resident store
	 * (registering it when absent) and queues the store's
	 * {@link ConstraintStore#normalize re-normalization}: verification of
	 * what the meet brought in, first examinations, the internal fixpoint —
	 * meet is completed by normalize, and a met factor answers no queries in
	 * between (the two run inside one drain). How tabling seeds a master
	 * from its key and replays an answer's delta.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Goal absorb(Absorbable<?> factor) {
		return p -> {
			if (factor.isEmpty()) {
				return Cont.just(p);
			}
			ConstraintStore resident = (ConstraintStore) p.getStores()
					.get(factor.getClass()).getOrNull();
			ConstraintStore met = resident == null ? factor
					: (ConstraintStore) ((Semilattice) resident).combine((Semilattice) factor);
			return enqueue(p.putStore(met), new Agenda.Absorbed(factor));
		};
	}

	/**
	 * The suspension entry: park {@code body} until {@code ripe} holds — checked
	 * when a watched chain binds. {@code ripe} must be monotone in the
	 * substitution and depend on nothing else. Already ripe at statement time:
	 * the body runs right here, at its own search position.
	 */
	public static Goal suspend(Iterable<? extends Term<?>> watched,
			Predicate<Substitutions> ripe, Goal body) {
		return s -> {
			// watched is the body's DECLARED read surface — checked before
			// ripeness, because an upward-closed condition can pass without
			// the watched terms being bound, and reads are invisible to the
			// binding and statement seams
			Watermark.check(s, watched);
			return ripe.test(s.substitution()) ?
					body.apply(s) :
					Cont.just(s.withStore(Suspensions.EMPTY)
							.updateStore(Suspensions.class, sus -> sus.park(Suspension.of(watched, ripe, body))));
		};
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
														upd -> MFiber.mdone(queue(pkg.putStore(
																ownFactor(cs, upd)), upd))))),
								Exceptions.throwingBiOp(UnsupportedOperationException::new))
						.map(Cont::<Package, Nothing>just)
						.getOrElse(() -> Cont.complete(Nothing.nothing())));
	}

	/**
	 * A revision may only replace the store's OWN factor — the javadoc contract,
	 * enforced: package store entries are keyed by class, so a foreign-class
	 * replacement would silently overwrite ANOTHER store's factor.
	 */
	private static Store ownFactor(ConstraintStore author, Revision.Updated upd) {
		if (upd.factor().getClass() != author.getClass()) {
			throw new IllegalStateException("a revision may only replace its own factor: "
					+ author.getClass().getSimpleName() + " answered with "
					+ upd.factor().getClass().getSimpleName());
		}
		return upd.factor();
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
		return (Agenda) pkg.getStores().get(Agenda.class).get();
	}

	/**
	 * Ripens suspensions after a binding: parked bodies whose watched chains
	 * touch the bound variables and whose condition now holds move to the run
	 * lane — fired once, forever.
	 */
	private static Goal ripen(Prefix prefix) {
		return s -> {
			if (!s.getStores().get(Suspensions.class).isDefined()) {
				return Cont.just(s);
			}
			Package current = s;
			Suspensions parked = (Suspensions) s.getStores().get(Suspensions.class).get();
			for (Suspension suspension : parked.parked) {
				boolean touched = false;
				for (Tuple2<LVar<?>, Term<?>> b : prefix.bindings()) {
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
		return p.getStores().get(Suspensions.class)
				.map(sus -> !((Suspensions) sus).parked.isEmpty())
				.getOrElse(false);
	}

	private static Stream<ConstraintStore> constraintStores(Package p) {
		return p.getStores().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast);
	}

	/**
	 * Completes any in-flight agenda on {@code p}: pending ITEMS drain to
	 * exhaustion, and the settled package carries no agenda. Collected runs
	 * are search, not knowledge — they stay with the real drain and are
	 * discarded with the copy. none = a pending item failed: the branch this
	 * package came from is doomed on the same items, deterministically.
	 *
	 * <p>The seam a scratch verification needs: a caller may sit mid-drain,
	 * where evaluation on a copy would APPEND to the inherited agenda instead
	 * of draining, and where verdicts would compare a quiescent trial result
	 * against an unfinished original. Settling completes the knowledge first;
	 * both sides of every later comparison are quiescent.
	 */
	public static Fiber<Option<Package>> settled(Package p) {
		if (!p.getStores().get(Agenda.class).isDefined()) {
			return Fiber.done(Option.of(p));
		}
		AtomicReference<Package> quiesced = new AtomicReference<>();
		return drainItems().apply(p)
				.apply(pkg -> {
					quiesced.set(pkg);
					return Fiber.done(Nothing.nothing());
				})
				.map(done -> Option.of(quiesced.get()));
	}

	/**
	 * The item half of {@link #drain()}: pop and apply to exhaustion, then
	 * clear the agenda WITHOUT splicing the collected runs — the settling
	 * copy's knowledge is complete at item exhaustion, and its runs belong
	 * to the real drain.
	 */
	private static Goal drainItems() {
		return Goal.defer(() -> s -> {
			Agenda agenda = (Agenda) s.getStores().get(Agenda.class).get();
			if (agenda.itemsExhausted()) {
				return Cont.just(s.withoutStore(Agenda.class));
			}
			Tuple2<Agenda.Item, Agenda> popped = agenda.pop();
			return popped._1.apply()
					.and(drainItems())
					.apply(s.putStore(popped._2));
		});
	}

	/**
	 * The single entry to propagation work. A drain in flight (agenda present)?
	 * Append — the running loop will reach the item. Otherwise this is a trigger:
	 * install the agenda, drain to quiescence, then splice the collected runs.
	 */
	private static Cont<Package, Nothing> enqueue(Package p, Agenda.Item item) {
		return p.getStores().get(Agenda.class)
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
			Agenda agenda = (Agenda) s.getStores().get(Agenda.class).get();
			if (agenda.itemsExhausted()) {
				Package cleared = s.withoutStore(Agenda.class);
				return Conjunction.of(agenda.runs())
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
			 * Applies the delta: the BINDINGS FACTOR examines its own statement
			 * ({@code Substitutions.extended} — the trichotomy and the own-factor
			 * extension), and the driver only routes the consequences: revise
			 * every store with the kept delta, ripen suspensions.
			 */
			@Override
			Goal apply() {
				return s -> s.substitution().extended(prefix)
						.<Cont<Package, Nothing>> map(examined -> {
							Prefix kept = examined._2;
							if (kept.isEmpty()) {
								return Cont.just(s);
							}
							Package extended = s.withSubstitutions(examined._1);
							// each store's revise is COMPLETE: custody, its own watchers of the
							// newly bound variables, and its own cascade
							return ((Goal) s2 -> reviseAll(s2, (cs, p) -> cs.revise(kept, p)))
									.and(ripen(kept))
									.apply(extended);
						})
						.getOrElse(() -> Cont.complete(Nothing.nothing()));
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

		/** A factor was met into its store — the owning store re-normalizes. */
		static final class Absorbed extends Item {
			final Absorbable<?> factor;

			Absorbed(Absorbable<?> factor) {
				this.factor = factor;
			}

			@Override
			Goal apply() {
				return s -> reviseAll(s,
						(cs, p) -> factor.getClass() == cs.getClass() ?
								((Absorbable<?>) cs).normalize(p) :
								Fiber.done(Revision.unchanged()));
			}

			@Override
			public String toString() {
				return "absorbed(" + factor + ")";
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
