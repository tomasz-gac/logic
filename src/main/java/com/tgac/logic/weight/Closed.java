package com.tgac.logic.weight;

// ABOUTME: Closed (star) tabling mode: explore for structure (presence cell + base/
// ABOUTME: edge capture into the DependencyGraph), solve each sealed closure jointly,
// ABOUTME: emit by replaying each top-level reader chain with the solved values.

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;

import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.tabling.Registration;
import com.tgac.logic.tabling.TableEntry;
import com.tgac.logic.tabling.TablingMode;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The closed (star) algorithm, plugged into the tabling skeleton as a
 * {@link TablingMode}. Explore runs as plain set tabling — the cell is presence,
 * so it terminates — while the real value rides the {@link SemiringStore} and
 * every derivation's contribution is captured in the {@link DependencyGraph}: a
 * NON-looping derivation is a base seed, a one-loop derivation an edge
 * coefficient carrying the consumed {@link Node} (star-tabling.md §4).
 *
 * <p>SEALED ⟹ SOLVABLE. The completion machinery seals entries in dependency
 * order: every caller reads through a consumer whose parked sleeper blocks the
 * caller's seal until the callee's, so at any entry's seal its whole dependency
 * closure over the graph — the equation system's coupling — has sealed too,
 * earlier or atomically with it (a sleeper ring group-seals). {@link #onSeal}
 * therefore solves immediately at the closure's last announcement
 * ({@link StarTabling#solveGroup}); nothing waits across cascades.
 *
 * <p>EMIT replays reader chains. During explore every consumer delivery is a
 * fragment (dropped at the collector); a chain ends at a sealed entry — drained
 * by the seal (handed to {@link #onSeal}) or caught up after it
 * ({@link #onCaughtUp}) — and a TOP-LEVEL chain is then replayed once from
 * index 0 with {@code x = A* ⊗ b}. A COATED reader is never replayed: its
 * contribution rides the edges it captured, and when it consumes an
 * already-SOLVED entry the value is ⊗'d inline ({@link #onConsume}) so its
 * produce captures the constant — the two paths agree, because an edge to a
 * solved entry folds to exactly the inline value.
 */
final class Closed implements TablingMode {

	/** Explore is set tabling — every answer carries the same presence marker. */
	@SuppressWarnings("unchecked")
	private static final IdempotentSemiring<Object> PRESENCE =
			(IdempotentSemiring<Object>) (IdempotentSemiring<?>) Semirings.BOOLEAN;

	private final ClosedSemiring<SemiringStore> ring;
	/** The equation system built during explore, read at each seal. */
	private final DependencyGraph graph;

	/** Each solved entry's answer values — read lock-free by {@link #onConsume}. */
	private final Map<TableEntry<Object>, Map<Reified<?>, SemiringStore>> solvedValues =
			new ConcurrentHashMap<>();
	/** Ended top-level reader chains awaiting their entry's solve. Guarded by this. */
	private final Map<TableEntry<Object>, ArrayList<Registration>> replayStash = new HashMap<>();

	Closed(ClosedSemiring<SemiringStore> ring) {
		this.ring = ring;
		this.graph = new DependencyGraph(ring);
	}

	/** The equation graph — for inspection in tests. */
	DependencyGraph graph() {
		return graph;
	}

	private SemiringStore storeOf(Package pkg) {
		return pkg.getStores().get(SemiringStore.class)
				.map(SemiringStore.class::cast)
				.getOrElse(ring::one);
	}

	@Override
	public IdempotentSemiring<Object> cellSemiring() {
		return PRESENCE;
	}

	@Override
	public Package onExplore(Package callerPkg) {
		// fresh derivation: real value reset to ONE, no loop record
		return callerPkg.putStore(ring.one()).putStore(Recurrent.NONE);
	}

	@Override
	public Package onConsume(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer,
			Object cellValue, TableEntry<Object> readerEntry) {
		Map<Reified<?>, SemiringStore> entryValues = solvedValues.get(entry);
		if (entryValues != null) {
			SemiringStore x = entryValues.get(consumedAnswer);
			if (readerEntry != null && x != null) {
				// a coated reader consumes a SOLVED entry: the value is a constant
				// its produce captures (a base — or an edge that folds to the same)
				return unifiedPkg.putStore(ring.times(storeOf(unifiedPkg), x));
			}
			// top-level: still a fragment — the replay at the chain's end delivers
			return unifiedPkg.putStore(Exploration.MARKER);
		}
		// open (or sealed mid-solve): record the loop, tag the fragment
		Recurrent prev = unifiedPkg.getStores().get(Recurrent.class)
				.map(Recurrent.class::cast).getOrElse(Recurrent.NONE);
		return unifiedPkg.putStore(prev.and(new Node(entry, consumedAnswer))).putStore(Exploration.MARKER);
	}

	@Override
	public Tuple2<Reified<?>, Object> onProduce(TableEntry<Object> entry, Package answerPkg, Reified<?> answerTerm) {
		// 0 loops consumed → base seed, 1 → edge coefficient, ≥2 → nonlinear (outside
		// the star). Captured before the dedup so multiplicity survives. The cell
		// itself caches bare presence — the value lives in the DependencyGraph.
		Recurrent rec = answerPkg.getStores().get(Recurrent.class)
				.map(Recurrent.class::cast).getOrElse(Recurrent.NONE);
		Node produced = new Node(entry, answerTerm);
		SemiringStore value = storeOf(answerPkg);
		if (rec.consumed.isEmpty()) {
			graph.addBase(produced, value);
		} else if (rec.consumed.size() == 1) {
			graph.addEdge(new Edge(produced, rec.consumed.head()), value);
		} else {
			throw new IllegalStateException("nonlinear recursion: a derivation consumed "
					+ rec.consumed.size() + " looping calls; star handles only linear systems");
		}
		return Tuple.of(answerTerm, Boolean.TRUE);
	}

	/**
	 * A chain whose call-site package is itself an exploration fragment — a call
	 * reached during some entry's explore. Never replayed: the upstream replay
	 * re-runs the continuation with values, spawning this chain's valued twin.
	 */
	private static boolean isFragment(Package pkg) {
		return pkg.getStores().get(Exploration.class).isDefined();
	}

	@Override
	public synchronized Fiber<Nothing> onCaughtUp(TableEntry<Object> entry, Registration reader) {
		if (reader.getEnclosingCall() != null || isFragment(reader.getPkg())) {
			// a coated reader's contribution rides its captured edges; a fragment
			// chain's answers come from its valued twin
			return done(nothing());
		}
		if (solvedValues.containsKey(entry)) {
			return replay(entry, reader);
		}
		// sealed but its closure not solved yet — the solve will replay it
		replayStash.computeIfAbsent(entry, e -> new ArrayList<>()).add(reader);
		return done(nothing());
	}

	/**
	 * One entry sealed; {@code drained} are its parked readers — the top-level
	 * ones become replay targets. SEALED ⟹ SOLVABLE: an edge exists only
	 * because a reader consumed the target while open, and that reader parks
	 * there, blocking this entry's seal until the target's — so every entry this
	 * one's equations reference sealed earlier or seals in this same cascade (a
	 * sleeper ring group-seals). Nothing is ever waited on across cascades: if a
	 * ring member has not announced yet, ITS hook — the ring's last — solves,
	 * replaying the stashes the earlier members left.
	 */
	@Override
	public synchronized Fiber<Nothing> onSeal(TableEntry<Object> entry, List<Registration> drained) {
		for (Registration reader : drained) {
			if (reader.getEnclosingCall() == null && !isFragment(reader.getPkg())) {
				replayStash.computeIfAbsent(entry, e -> new ArrayList<>()).add(reader);
			}
		}
		if (solvedValues.containsKey(entry)) {
			// sealed in this cascade, solved by a racing one — replay the late stash
			return replayStashed(entry);
		}
		Set<TableEntry<Object>> closure = graph.dependencyClosure(entry);
		for (TableEntry<Object> member : closure) {
			if (!member.isComplete()) {
				return done(nothing());
			}
		}
		return solveAndEmit(closure);
	}

	/**
	 * Solve {@code closure} jointly (already-solved members recompute to the same
	 * values — the maps froze at their seal), record the values, and replay every
	 * member's stashed top-level readers.
	 */
	private Fiber<Nothing> solveAndEmit(Set<TableEntry<Object>> closure) {
		Map<TableEntry<Object>, Map<Reified<?>, SemiringStore>> values =
				StarTabling.solveGroup(closure, graph, ring);
		Fiber<Nothing> result = done(nothing());
		for (TableEntry<Object> member : closure) {
			solvedValues.putIfAbsent(member,
					values.getOrDefault(member, new LinkedHashMap<Reified<?>, SemiringStore>()));
			Fiber<Nothing> replays = replayStashed(member);
			result = result.flatMap(__ -> replays);
		}
		return result;
	}

	/** Replay and clear {@code entry}'s stashed reader chains. */
	private Fiber<Nothing> replayStashed(TableEntry<Object> entry) {
		ArrayList<Registration> stashed = replayStash.remove(entry);
		Fiber<Nothing> result = done(nothing());
		if (stashed == null) {
			return result;
		}
		for (Registration reader : stashed) {
			Fiber<Nothing> replay = replay(entry, reader);
			result = result.flatMap(__ -> replay);
		}
		return result;
	}

	/** Replay one ended top-level chain from index 0 with the solved values. */
	private Fiber<Nothing> replay(TableEntry<Object> entry, Registration reader) {
		Map<Reified<?>, SemiringStore> entryValues = solvedValues.get(entry);
		SemiringStore readerValue = storeOf(reader.getPkg());
		Fiber<Nothing> result = done(nothing());
		for (int i = 0; i < entry.getAnswerCount(); i++) {
			Reified<?> answerTerm = entry.getAnswerAt(i)._1;
			SemiringStore x = entryValues.get(answerTerm);
			if (x == null) {
				continue;
			}
			SemiringStore value = ring.times(readerValue, x);
			result = result.flatMap(__ -> emitAnswer(reader.getContinuation(), reader.getPkg(),
					reader.getArgsTerm(), answerTerm, value));
		}
		return result;
	}

	/**
	 * Publish one sealed answer: instantiate it, unify it against the call pattern
	 * to bind the reader's variables, set the folded value on the SemiringStore,
	 * then hand it to {@code k}. The reader package already wears its own coat.
	 */
	private Fiber<Nothing> emitAnswer(Fiber.Fn<Package, Nothing> k, Package callerPkg,
			Unifiable<?> argsTerm, Reified<?> answerTerm, SemiringStore value) {
		return MiniKanren.instantiate(answerTerm).flatMap(freshTerm ->
				MiniKanren.unify(callerPkg.substitution(), argsTerm.getObjectTerm(), freshTerm.getObjectTerm())
						.map(callerPkg::withSubstitutions)
						.map(pkg -> pkg.putStore(value))
						.map(k::apply)
						.getOrElse(() -> done(nothing()))
						.flatMap(fib -> fib));
	}
}
