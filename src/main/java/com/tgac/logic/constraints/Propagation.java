package com.tgac.logic.constraints;

// ABOUTME: The propagation engine: the chokepoint that applies prefixes, the agenda
// ABOUTME: worklist that makes the fixpoint explicit, and verdict administration.

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.MFiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Doomed;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Suspension;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.constraints.store.Verifier;
import com.tgac.logic.debug.ProfilerStore;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.goals.Watermark;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.BiFunction;
import java.util.function.Function;
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
	 * branch), extends the substitution once, folds every {@link Factor}'s
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
	public static Posting resolve(Prefix prefix) {
		return new Posting.Resolution(prefix);
	}

	/** The imposition body behind {@link #resolve} — the bulk binding load. */
	static Goal resolution(Prefix prefix) {
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
	 * The one statement entry for store items: registration seeds an absent
	 * resident from the atom's own {@link Atom#empty}, doom is read through
	 * the declared {@link Doomed} capability (absent means the atom claims
	 * nothing), and the item parks in its store for its first examination —
	 * the owning store's statement-delta {@code normalize} decides
	 * everything decidable at statement time.
	 */
	public static Posting activate(Atom<?> item) {
		return new Posting.Activation(item,
				p -> Constraint.register(p, item.empty()),
				item instanceof Doomed ? ((Doomed) item)::doomed : p -> false);
	}

	/** The imposition body behind the {@link #activate} constructors. */
	static Goal activation(Atom<?> item) {
		return s -> enqueue(Constraint.stated(s, item), new Agenda.Stated(item));
	}

	/**
	 * The bulk statement entry — the trigger family's third row: a whole
	 * FACTOR arrives. Meets {@code factor} into its resident store
	 * (registering it when absent) and queues the store's
	 * {@link Factor#normalize re-normalization}: verification of
	 * what the meet brought in, first examinations, the internal fixpoint —
	 * meet is completed by normalize, and a met factor answers no queries in
	 * between (the two run inside one drain). How tabling seeds a master
	 * from its key and replays an answer's delta.
	 */
	public static Posting absorb(Theory<?> theory) {
		return new Posting.Absorption(theory, List.empty());
	}

	/** {@link #absorb} declaring the theory's watched surface alongside. */
	public static Posting absorb(Theory<?> theory, List<Term<?>> terms) {
		return new Posting.Absorption(theory, terms);
	}

	/** The imposition body behind the {@link #absorb} constructors. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static Goal absorption(Theory theory) {
		return p -> {
			if (theory.isEmpty()) {
				return Cont.just(p);
			}
			Atom<?> head = (Atom<?>) theory.atoms().head();
			Class<?> family = head.getFactorClass();
			Constraint pair = (Constraint) Constraint.in(p, (Class) family).getOrNull();
			if (pair == null) {
				// the atom's empty is the family identity's constructive
				// face — the theory seeds its own residence
				return enqueue(p.putStore((Class) family, Constraint.of(theory, (Factor) head.empty())),
						new Agenda.Absorbed(family));
			}
			if (pair.getTheory().leq(theory)) {
				// the covering door guard: the resident already entails the
				// incoming knowledge — no meet, no re-normalization, no trials
				return Cont.just(p);
			}
			Constraint met = Constraint.of(pair.getTheory().meet(theory), pair.getFactor());
			return enqueue(p.putStore((Class) family, met), new Agenda.Absorbed(family));
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
			BiFunction<Constraint<?>, Package, Fiber<Revision>> trigger) {
		return Cont.defer(() ->
				constraintStores(s)
						.reduce(MFiber.mdone(s),
								(chain, cs) ->
										chain.flatMap(pkg -> MFiber.ofFiber(trigger.apply(cs, pkg))
												.flatMap(revision -> revision.match(
														MFiber::none,            // fail: branch dies
														() -> MFiber.mdone(pkg), // unchanged
														upd -> MFiber.mdone(queue(
																landed(pkg, cs, upd), upd))))),
								Exceptions.throwingBiOp(UnsupportedOperationException::new))
						.map(Cont::<Package, Nothing>just)
						.getOrElse(() -> Cont.complete(Nothing.nothing())));
	}

	/**
	 * Custody: a revision may only replace its own entry — the javadoc
	 * contract, enforced. Package store entries are keyed by class, so a
	 * foreign-class replacement would silently overwrite ANOTHER family's
	 * pair.
	 */
	private static Package landed(Package pkg, Constraint<?> author, Revision.Updated upd) {
		Constraint<?> own = upd.constraint();
		if (own.getFactor().getClass() != author.getFactor().getClass()) {
			throw new IllegalStateException("a revision may only replace its own factor: "
					+ author.getFactor().getClass().getSimpleName() + " answered with "
					+ own.getFactor().getClass().getSimpleName());
		}
		return pkg.putStore(own.getFactor().getClass(), own);
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

	/**
	 * The revision fold's order: value families in store order, then the
	 * {@link Verifier}s — a trial-based family presupposes a base where
	 * every value family has reacted to the current trigger, and the fold
	 * honors it structurally (registration order must never decide whether
	 * a veto fires).
	 */
	private static Stream<Constraint<?>> constraintStores(Package p) {
		return Stream.concat(
				pairs(p).filter(cs -> !(cs.getFactor() instanceof Verifier)),
				pairs(p).filter(cs -> cs.getFactor() instanceof Verifier));
	}

	private static Stream<Constraint<?>> pairs(Package p) {
		return p.getStores().values().toJavaStream()
				.filter(Constraint.class::isInstance)
				.map(entry -> (Constraint<?>) entry);
	}

	/**
	 * The scratch face of {@code p}: the drain machinery stripped. A trial
	 * imposition must DRAIN, and a resident agenda would make {@code enqueue}
	 * append instead — the imposition would never execute and every trial
	 * would read "changed". Pending items and collected runs stay the real
	 * drain's business: the scratch answers against CURRENT knowledge, which
	 * is sound by monotonicity (entailment and refutation only grow with
	 * knowledge — the binding lane's standing doctrine), and a late verdict
	 * re-verifies when the queued work lands as its own trigger.
	 */
	public static Package scratch(Package p) {
		return p.withoutStore(Agenda.class);
	}

	/**
	 * The item loop: pop and apply to exhaustion, then hand the package —
	 * agenda resident, items done, collected runs intact — to
	 * {@code atExhaustion}. The continuation is a plain call, not a composed
	 * goal, so the loop prices exactly as a single-loop drain.
	 */
	private static Goal drainItems(Function<Package, Cont<Package, Nothing>> atExhaustion) {
		return Goal.defer(() -> s -> {
			Agenda agenda = (Agenda) s.getStores().get(Agenda.class).get();
			if (agenda.itemsExhausted()) {
				return atExhaustion.apply(s);
			}
			Tuple2<Agenda.Item, Agenda> popped = agenda.pop();
			return popped._1.apply()
					.and(drainItems(atExhaustion))
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
		return drainItems(s -> {
			Agenda agenda = (Agenda) s.getStores().get(Agenda.class).get();
			return Conjunction.of(agenda.runs())
					.apply(s.withoutStore(Agenda.class));
		});
	}

	/** Parked suspensions — persistent, branch-local, driver-owned. */
	static final class Suspensions implements Packaged {
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
	static final class Agenda implements Packaged {

		abstract static class Item {
			private Item() {
			}

			/** How this item executes against the state that popped it. */
			abstract Goal apply();
		}

		private static Fiber<Revision> getRevisionFiber(String name, Constraint<?> cs, Package p, Fiber<Revision> revise) {
			return ProfilerStore.from(p).isDefined() ?
					Fiber.named(origin -> name + " @ " + cs.getFactor().getClass().getSimpleName(), revise) :
					revise;
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
			@SuppressWarnings({"unchecked", "rawtypes"})
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
							return ((Goal) s2 -> reviseAll(s2, (cs, p) ->
									getRevisionFiber("Propagation.Bind", cs, p,
											cs.getFactor().normalize((Theory) cs.getTheory(), kept, p))))
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
			final Atom<?> item;

			Stated(Atom<?> item) {
				this.item = item;
			}

			@Override
			@SuppressWarnings({"unchecked", "rawtypes"})
			Goal apply() {
				return s -> reviseAll(s,
						(cs, p) -> item.getFactorClass() == cs.getFactor().getClass() ?
								getRevisionFiber("Propagation.Stated", cs, p,
										((Factor) cs.getFactor()).stated(item, cs.getTheory(), p)) :
								Fiber.done(Revision.unchanged()));
			}

			@Override
			public String toString() {
				return "stated(" + item + ")";
			}
		}

		/** A theory was met into its store — the owning store re-normalizes. */
		static final class Absorbed extends Item {
			final Class<?> family;

			Absorbed(Class<?> family) {
				this.family = family;
			}

			@Override
			@SuppressWarnings({"unchecked", "rawtypes"})
			Goal apply() {
				return s -> reviseAll(s,
						(cs, p) -> family == cs.getFactor().getClass() ?
								getRevisionFiber("Propagation.Absorbed", cs, p,
										((Factor) cs.getFactor()).normalize(cs.getTheory(), p)) :
								Fiber.done(Revision.unchanged()));
			}

			@Override
			public String toString() {
				return "absorbed(" + family.getSimpleName() + ")";
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
		public String toString() {
			return "agenda" + items + (runs.isEmpty() ? "" : " runs" + runs);
		}
	}
}
