package com.tgac.logic.weight;

// ABOUTME: Closed (star) tabling mode: explore for structure (presence cell + base/
// ABOUTME: edge capture into the DependencyGraph), solve each sealed closure jointly,
// ABOUTME: emit by replaying each entry's reader chains against its solved values.

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;

import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.goals.Package;
import com.tgac.logic.tabling.Condition;
import com.tgac.logic.tabling.Reader;
import com.tgac.logic.tabling.TableEntry;
import com.tgac.logic.tabling.TablingMode;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import java.util.ArrayList;
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
 * earlier or atomically with it (a sleeper ring group-seals, and the group is
 * fully MARKED before any member is announced). The group's first
 * sealed-woken reader therefore solves the closure
 * ({@link StarTabling#solveGroup}) and records each member's values; every
 * later reader wakes by itself and replays against them.
 *
 * <p>EMIT replays reader chains. During explore every consumer delivery is a
 * fragment (dropped at the collector); a chain ends at a sealed entry — drained
 * by the seal or caught up after it — and a TOP-LEVEL chain is then replayed
 * once from index 0 with {@code x = A* ⊗ b}. A reader INSIDE A BODY (its
 * package carries {@link Recurrent}) is never replayed: its contribution
 * rides the edges it captured, and when it consumes an already-SOLVED entry
 * the value is ⊗'d inline ({@link #absorb}) so its capture folds in the
 * constant — the two paths agree, because an edge to a solved entry folds
 * to exactly the inline value.
 */
final class Closed implements TablingMode {

	/** Explore is plain tabling — every capture is the constraint ring's 1. */
	@SuppressWarnings("unchecked")
	private static final IdempotentSemiring<Object> CONDITIONS =
			(IdempotentSemiring<Object>) (IdempotentSemiring<?>) Condition.RING;

	private final ClosedSemiring<SemiringStore> ring;
	/** The equation system built during explore, read at each seal. */
	private final DependencyGraph graph;
	/**
	 * Each entry's solved answer values — absent until its closure's joint
	 * solve, the lifecycle phase. ConcurrentHashMap publication lets
	 * {@link #absorb} read the phase lock-free; writes happen under the
	 * Closed monitor.
	 */
	private final ConcurrentHashMap<TableEntry<Object>, Map<Reified<?>, SemiringStore>> solvedValues =
			new ConcurrentHashMap<>();

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
		return CONDITIONS;
	}

	@Override
	public Package bodyState(Package callerPkg) {
		// fresh derivation: real value reset to ONE, no loop record
		return callerPkg.putStore(ring.one()).putStore(Recurrent.NONE);
	}

	@Override
	public Package absorb(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer,
			Object cellValue) {
		Map<Reified<?>, SemiringStore> solved = solvedValues.get(entry);
		if (solved == null) {
			// open (or sealed mid-solve): record the loop, tag the fragment
			Recurrent prev = unifiedPkg.getStores().get(Recurrent.class)
					.map(Recurrent.class::cast).getOrElse(Recurrent.NONE);
			return unifiedPkg.putStore(prev.and(new Node(entry, consumedAnswer))).putStore(Fragment.MARKER);
		}
		SemiringStore x = solved.get(consumedAnswer);
		if (insideBody(unifiedPkg) && x != null) {
			// a reader inside a body consumes a SOLVED entry: the value is a
			// constant its capture folds in (a base — or an edge that folds to
			// the same)
			return unifiedPkg.putStore(ring.times(storeOf(unifiedPkg), x));
		}
		// top-level: still a fragment — the replay at the chain's end delivers
		return unifiedPkg.putStore(Fragment.MARKER);
	}

	@Override
	public Tuple2<Reified<?>, Object> capture(TableEntry<Object> entry, Package answerPkg,
			Reified<?> answerTerm, io.vavr.collection.Map<Class<?>, Projectable<?>> residues) {
		// io.vavr Map qualified: this file's Map is java.util's (solved values)
		if (!residues.isEmpty()) {
			// replay-at-seal has no way to re-impose a region on a chain
			throw new IllegalStateException(
					"constrained answers are supported only under plain tabling: "
							+ "weights over conditional answers is an orthogonal, open concern");
		}
		// 0 loops consumed → base seed, 1 → edge coefficient, ≥2 → nonlinear (outside
		// the star). Captured before the dedup so multiplicity survives. The cell
		// itself caches 1 — the value lives in the DependencyGraph.
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
		return Tuple.of(answerTerm, Condition.ONE);
	}

	@Override
	public Fiber<Nothing> caughtUp(TableEntry<Object> entry, Reader reader) {
		if (insideBody(reader.getPkg()) || isFragment(reader.getPkg())) {
			// an inside-a-body reader's contribution rides its captured edges;
			// a fragment chain's answers come from its valued twin
			return done(nothing());
		}
		synchronized (this) {
			if (!solvedValues.containsKey(entry)) {
				// the first sealed-woken reader solves the closure: SEALED ⟹
				// SOLVABLE, because a group seal marks every member before
				// completing any waiter - an unmarked member here means that
				// invariant broke; refuse loudly rather than read an unfinal
				// system
				Set<TableEntry<Object>> closure = graph.dependencyClosure(entry);
				for (TableEntry<Object> member : closure) {
					if (!member.isComplete()) {
						throw new IllegalStateException(
								"caught up at " + entry.getCall() + " while closure member "
										+ member.getCall() + " is unsealed: group marking must "
										+ "complete before any completion");
					}
				}
				solveClosure(closure);
			}
			return replay(entry, reader);
		}
	}

	/**
	 * The first sealed-woken reader is the leader: one joint solve for the
	 * closure, then every member's values are recorded (already-solved
	 * members keep their frozen values — same by determinism). Each member's
	 * readers deliver themselves as they wake and find the values.
	 */
	private void solveClosure(Set<TableEntry<Object>> closure) {
		Map<TableEntry<Object>, Map<Reified<?>, SemiringStore>> solved =
				StarTabling.solveGroup(closure, graph, ring);
		for (TableEntry<Object> member : closure) {
			solvedValues.putIfAbsent(member,
					solved.getOrDefault(member, new LinkedHashMap<Reified<?>, SemiringStore>()));
		}
	}

	/** Replay one ended top-level chain from index 0 with the solved values. */
	private Fiber<Nothing> replay(TableEntry<Object> entry, Reader reader) {
		Map<Reified<?>, SemiringStore> values = solvedValues.get(entry);
		SemiringStore readerValue = storeOf(reader.getPkg());
		Fiber<Nothing> result = done(nothing());
		for (Reified<?> answerTerm : entry.answerTerms()) {
			SemiringStore x = values.get(answerTerm);
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
	 * A chain whose call-site package is itself an exploration fragment — a call
	 * reached during some entry's explore. Never replayed: the upstream replay
	 * re-runs the continuation with values, spawning this chain's valued twin.
	 */
	private static boolean isFragment(Package pkg) {
		return pkg.getStores().get(Fragment.class).isDefined();
	}

	/**
	 * A call site inside some tabled body: {@link #bodyState} stamps
	 * {@code Recurrent.NONE} on every body package, so the store's presence
	 * IS the inside-a-body fact — no separate tracking.
	 */
	private static boolean insideBody(Package pkg) {
		return pkg.getStores().get(Recurrent.class).isDefined();
	}

	/**
	 * Publish one sealed answer: instantiate it, unify it against the call pattern
	 * to bind the reader's variables, set the folded value on the SemiringStore,
	 * then hand it to {@code k} under the reader's own call-site package.
	 */
	private static Fiber<Nothing> emitAnswer(Fiber.Fn<Package, Nothing> k, Package callerPkg,
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
