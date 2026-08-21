package com.tgac.logic.tabling;

// ABOUTME: Tabled evaluation of logic goals: answers are cached per call and shared,
// ABOUTME: which makes left-recursive and mutually recursive predicates terminate.

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Emitter;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.goals.optimizer.Barrier;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Provides tabling (memoization) for logic goals to prevent infinite loops
 * and improve performance by caching answers.
 *
 * The first application of a call becomes the master and executes the body
 * with a caching hook threaded through its continuation: every derived
 * answer is reified, deduplicated and cached before it flows on. Later
 * applications consume the cache. A consumer that exhausts the cache awaits
 * the entry's channel — the live frame parks, growth wakes it with the grown
 * log, and the entry's seal completes it with the final one. A consumer
 * completed at its cursor is a finished branch.
 *
 * Committed choice (conda/condu/orElse) over tabled goals is undefined
 * behavior: commitment depends on table state, incomplete tables never
 * signal failure, and pruning a branch cannot undo its table effects.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Tabling {

	/**
	 * Define a tabled relation over its formal parameters.
	 *
	 * <pre>
	 * Tabled&lt;Tuple2&lt;Unifiable&lt;Integer&gt;, Unifiable&lt;Integer&gt;&gt;&gt; path =
	 *     Tabling.defineRecursive(self -&gt; args -&gt; args.apply((x, y) -&gt;
	 *         edge(x, y).or(defer(() -&gt; exist(z -&gt;
	 *             self.apply(Tuple.of(x, z)).and(edge(z, y)))))));
	 * </pre>
	 *
	 * The cache is keyed on the relation's identity, so a relation must be
	 * created exactly once — a body that calls define mints a new cache per
	 * call. Name goals via {@link Goal#named} where traces should be legible.
	 *
	 * @param body the relation body, given the recursion handle and the
	 * 		argument tuple; use {@link #define(Function)} when the handle
	 * 		is not needed
	 */
	public static <T> Tabled<T> defineRecursive(Function<Tabled<T>, Function<T, Goal>> body) {
		return new Tabled<>(body);
	}

	/**
	 * Define a tabled relation without the recursion handle: non-recursive,
	 * or recursing through a field read at goal execution time.
	 */
	public static <T> Tabled<T> define(Function<T, Goal> body) {
		return defineRecursive(self -> body);
	}

	/**
	 * The tabled goal behind {@link Tabled#apply}:
	 *
	 * <pre>
	 * 1. Reify the argument tuple in the current state to create the lookup key
	 * 2. Look up the call in the solve-scoped table
	 * 3. A new call detaches an ANONYMOUS MASTER that executes the body, caching answer terms
	 * 4. Every caller consumes cached answer terms, parking when it catches up
	 * </pre>
	 */
	/*
	 * STATE FOLLOWS THE DATA, EXECUTION FOLLOWS THE FRAME:
	 *
	 *   callerPkg   the state at this call site.
	 *   bodyPkg     callerPkg re-based for the body on call ENTRY (stores
	 *               stripped to the key, mode state reset).
	 *   answerPkg   a body success; it ENDS at the answer cell. Answers
	 *               reach callers only through their consumers, each
	 *               running under its own caller's state.
	 *
	 * Billing needs no package-level tracking: the body runs as the entry's
	 * workforce (the frame's ambient scope), an inner call's consumer runs
	 * inside the body's frames and inherits it, and its park leaves the
	 * blocked record completion detection reads.
	 */
	static <T> Goal tabled(Tabled<T> relation, T args, Supplier<Goal> body) {
		// a bare Unifiable is an equality ATOM to decompose (no wrapped-Term
		// kind: tuple MEMBERS decompose via wrapTerm, a bare wrapping does
		// not), which would collapse every answer into one. Wrap it in a
		// Tuple1 internally — keys, answers and consumption all take the
		// structural path; the body still receives the bare argument
		Unifiable<?> argsTerm = lval(args instanceof Term ? Tuple.of(args) : args);
		// keyed widening: the call pattern is the table key, so no optimizer may
		// move binders across it — the contract as a type, not an accident of opacity
		return Barrier.priced(p -> tabledOrder(p, relation, argsTerm), callerPkg -> k ->
				Residues.about(callerPkg, argsTerm).flatMap(keyPair -> {
					// the call's REGION: the bindings factor's image (args
					// reified with anys) plus each store's slot-named factor
					Reified<?> reifiedArgs = keyPair._1;
					Residues keyResidues = keyPair._2;
					Call key = Call.of(relation, reifiedArgs, keyResidues);
					Reader reader = Reader.of(k, callerPkg, argsTerm);
					Table table = reader.getTable();
					// a weighted solve whose semiring cannot table (non-idempotent,
					// non-closed) would silently drop weights here — refuse loudly
					table.assertTablingAllowed();
					// subsumptive reuse: a sealed general entry is a read-only relation
					// containing every answer this instance call could produce (subset
					// property) — read it through consume's unification filter
					TableEntry<Object> subsumer = table.reusableSubsumer(key);
					if (subsumer != null) {
						return consume(subsumer, reader, subsumer.answers());
					}
					TableEntry<Object> entry = table.getOrCreateEntry(key);
					// the ANONYMOUS MASTER, selected by the claim-once CAS
					// (Fiber.produce): the body runs as this entry's
					// workforce and belongs to no caller; production is the
					// emitter, so billing and production cannot disagree.
					// Every caller, the first included, reads the cell as a
					// consumer; the sleeper it parks is the dependency edge
					// completion detection needs, so a caller can never seal
					// ahead of a call it depends on. The body runs FROM THE
					// KEY: the first caller's constraint stores are stripped
					// and the key's residues restated, so the cache holds
					// exactly the region the key names — every caller filters
					// at consumption by its own state
					return Fiber.produce(entry.channel(), emit -> {
								// the key cannot represent a parked suspension and the
								// caller-agnostic body must not inherit one — refuse loudly;
								// consuming an existing entry under one stays legal (the
								// caller's copy ripens through its own chokepoint)
								if (Propagation.suspensionsPending(callerPkg)) {
									throw new IllegalStateException(
											"a tabled call cannot become master under parked suspensions: "
													+ "the call key cannot see them and the body must not inherit them");
								}
								Package bodyPkg = stripConstraints(table.bodyState(callerPkg));
								// the master's goal: the key re-imposed at the live
								// anchor, then the body — the same restate delivery
								// uses; its image half re-unifies already-bound args
								// (idempotent), kept for uniformity
								Goal seeded = keyResidues.isTrue()
										? body.get()
										: Conjunction.of(
												Residues.restate(reifiedArgs, keyResidues, argsTerm),
												body.get());
								return produce(entry, seeded, bodyPkg, argsTerm, table, emit);
							})
							// a lost claim is a silent no-op: winner or loser, every
							// caller falls through to here and reads as a consumer
							.flatMap(__ -> consume(entry, reader, entry.answers()));
				}));
	}

	/**
	 * The ∞→exact transition (docs/reference/optimizer.md): an incomplete entry
	 * prices MAX — a barrier — because its answer count is still growing; a
	 * completed entry prices its exact count. Sound under reordering:
	 * execution is always at-or-more-bound than pricing, and a more-bound
	 * variant emits a subset of the priced variant's answers.
	 */
	private static <T> long tabledOrder(Package p, Tabled<T> relation, Unifiable<?> argsTerm) {
		return p.getStores().get(Table.class)
				.map(Table.class::cast)
				.map(table -> {
					Call key = Call.of(relation,
							MiniKanren.reify(p.substitution(), argsTerm).ground());
					TableEntry<?> entry = table.getEntry(key);
					// a sealed subsumer's count bounds the instance's emissions
					// (subset property) — the same lookup reuse consumes through
					return entry != null ? entry : table.findSealedSubsumer(key);
				})
				.filter(entry -> entry != null && entry.isComplete())
				.map(entry -> (long) entry.getAnswerCount())
				.getOrElse(Long.MAX_VALUE);
	}

	/** Remove every constraint-store factor: absence is ⊤, posting re-registers. */
	private static Package stripConstraints(Package pkg) {
		return pkg.getStores().toJavaStream()
				.filter(entry -> entry._2 instanceof Constraint)
				.reduce(pkg, (p, entry) -> p.withoutStore(entry._1),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	/**
	 * The anonymous master: execute the body as a pure producer. Each new
	 * answer is cached and emitted — growth wakes the consumers parked at
	 * the entry's channel — and the answer reaches callers only through
	 * their consumers, so this fiber's completion means BODY EXHAUSTED, the
	 * event the counters need. Duplicate answers fail their branch.
	 */
	private static Fiber<Nothing> produce(
			TableEntry<Object> entry,
			Goal goal,
			Package bodyPkg,
			Unifiable<?> argsTerm,
			Table table,
			Emitter<JoinMap<Reified<?>, Object>> emit) {
		return goal.apply(bodyPkg).apply(answerPkg -> {
			// the Table transport is the canary: a goal that returned a fresh
			// package instead of deriving from its input shed every store — the
			// damage downstream is SILENT (answers reified over fresh
			// substitutions cache over-general) — so refuse loudly here
			if (answerPkg.getStores().get(Table.class).getOrElse((Packaged) null) != table) {
				throw new IllegalStateException(
						"a goal inside a tabled body dropped its stores: packages must be "
								+ "derived from the incoming one, never minted fresh "
								+ "(the body's Table transport is missing or foreign)");
			}
			// a parked suspension is a condition the answer still owes; the
			// AnswerKey cannot carry it, so caching now would drop the debt
			if (Propagation.suspensionsPending(answerPkg)) {
				throw new IllegalStateException(
						"an answer may not leave a tabled body while suspensions pend: "
								+ "the owed condition cannot ride the answer");
			}
			// the WHOLE delta rides — body locals and their couplings
			// included, replayed as existentials at consumption and
			// verified by the consumer's labelling; whether residues
			// may ride at all is the mode's capture call
			return Residues.all(answerPkg, argsTerm).flatMap(answer -> {
				// what the cell caches: the term and the value this derivation
				// carries — caller-agnostic, since the body ran from ONE
				Tuple2<Reified<?>, Object> cached = table.capture(entry, answerPkg, answer._1, answer._2);
				// production is the emit: the fold FEEDS the parked
				// consumers as this producer's tail; a duplicate answer
				// is an inert fold, an entailed region is absorbed —
				// no delta, no wake
				return emit.emit(entry.answerDelta(cached._1, cached._2));
			});
		});
	}

	/**
	 * Consumer: unify instantiated cached answers with the argument tuple,
	 * yielding each success to the continuation. On catching up with the
	 * cache the consumer awaits the entry's channel: growth wakes the live
	 * frame with the grown log, and the seal completes it at its cursor —
	 * the mode decides what that honest end means.
	 *
	 * @param answers the snapshot the reader's cursor indexes into — a
	 * 		consumption starts from the answers as of the call; every later
	 * 		value arrives with the wake, never polled
	 */
	static Fiber<Nothing> consume(TableEntry<Object> entry, Reader reader, JoinMap<Reified<?>, Object> answers) {
		// the log walk: every ascent is one event - a fresh term, a new
		// region, an improved fold. Inside readers take them all (the
		// fixpoint's fuel - distributivity makes delivering each arrival
		// sound); outside readers take only values at ⊕'s top (1 ⊕ a = 1:
		// final on arrival) and collect the rest finalized at the seal.
		// Replay is a conde: every available delivery forks AT ONCE as a
		// same-depth sibling - the flat shape, not a right-nested chain
		boolean inside = reader.isInside();
		List<Fiber<Nothing>> deliveries = IntStream.range(reader.getCursor(), answers.logSize())
				.mapToObj(answers::logAt)
				.filter(arrival -> inside || answers.isTop(arrival._2))
				.map(arrival -> deliver(entry, reader, arrival._1, arrival._2))
				.collect(Collectors.toList());

		// the cursor moves past every WALKED arrival, delivered or skipped —
		// a skipped conditional still consumed its log position
		Reader atEnd = reader.advanced(answers.logSize() - reader.getCursor());
		if (!deliveries.isEmpty()) {
			deliveries.add(Fiber.defer(() -> consume(entry, atEnd, answers)));
			return Fiber.fork(deliveries);
		}

		// caught up: await ANY strict ascent. The channel swaps its value
		// object only when knowledge grew, so identity is the predicate -
		// upward-closed for free. The frame's ambient scope records the
		// wait - the sleeper edge completion detection reads
		JoinMap<Reified<?>, Object> snapshot = answers;
		return Fiber.await(entry.channel(), v -> v != snapshot)
				.flatMap(r -> {
					JoinMap<Reified<?>, Object> current = r.getValue();
					if (atEnd.getCursor() < current.logSize() || !r.isSealed()) {
						return Fiber.defer(() -> consume(entry, atEnd, current));
					}
					// the seal: an outside reader now receives each term's
					// converged fold - folds and maximality are order-
					// invariant, so the output cannot depend on the schedule
					return deliverSealed(entry, atEnd, current)
							.flatMap(__ -> reader.getTable().caughtUp(entry, atEnd));
				});
	}

	/**
	 * One answer's delivery. Fresh variables per consumption, so separate
	 * consumptions of the same answer don't alias each other's free
	 * variables. Delivery is a goal: unify the caller's args with the
	 * instantiation — through the public entry, so the caller's stores
	 * revise — then RESTATE the residues onto the fresh vars. The
	 * meet-at-consumption: a failed unification, a violated store or a
	 * violated residue all silently fail the delivery and consumption moves
	 * on.
	 */
	private static Fiber<Nothing> deliver(TableEntry<Object> entry, Reader reader,
			Reified<?> term, Object value) {
		if (value instanceof Condition) {
			// a condition delivers per region: each conjunct is one branch,
			// its residues restated onto that delivery's fresh vars; regions
			// are disjuncts, so they fork like any other alternatives
			return Fiber.fork(((Condition) value).conjuncts().toJavaStream()
					.map(conjunct -> deliverAtom(entry, reader, term, conjunct, value))
					.collect(Collectors.toList()));
		}
		return deliverAtom(entry, reader, term, Residues.TRUE, value);
	}

	private static Fiber<Nothing> deliverAtom(TableEntry<Object> entry, Reader reader,
			Reified<?> term, Residues residues, Object cellValue) {
		Fiber.Fn<Package, Nothing> k = reader.getContinuation();
		return Residues.restate(term, residues, reader.getArgsTerm())
				.apply(reader.getPkg())
				// streaming ⊗s the cell value in; closed records the loop
				.apply(constrainedPkg -> k.apply(reader.getTable().absorb(constrainedPkg,
						entry, term, cellValue)));
	}

	/**
	 * The seal's delivery to an OUTSIDE reader: everything that only now
	 * became FINAL - each term's converged fold, its top-valued arrivals
	 * excepted (those streamed the moment they logged, and 1 ⊕ a = 1 says
	 * nothing could have moved them since). Inside-a-body readers already
	 * streamed every ascent as fuel.
	 */
	private static Fiber<Nothing> deliverSealed(TableEntry<Object> entry, Reader reader,
			JoinMap<Reified<?>, Object> answers) {
		if (reader.isInside()) {
			return Fiber.done(Nothing.nothing());
		}
		return Fiber.fork(IntStream.range(0, answers.size())
				.mapToObj(answers::get)
				.filter(answer -> !answers.isTop(answer._2))
				.map(answer -> deliver(entry, reader, answer._1, answer._2))
				.collect(Collectors.toList()));
	}

}
