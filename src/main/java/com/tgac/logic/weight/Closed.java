package com.tgac.logic.weight;

// ABOUTME: Closed (star) tabling mode: explore for structure (presence cell + base/
// ABOUTME: edge capture into the DependencyGraph), solve each sealed closure jointly,
// ABOUTME: emit by replaying each entry's reader chains from its Life.

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
 * fully MARKED before any member is announced). The group's FIRST-announced
 * {@link Life} therefore solves the closure ({@link StarTabling#solveGroup})
 * and hands each member its values; later members' lives just release their
 * own readers.
 *
 * <p>EMIT replays reader chains. During explore every consumer delivery is a
 * fragment (dropped at the collector); a chain ends at a sealed entry — drained
 * by the seal or caught up after it — and a TOP-LEVEL chain is then replayed
 * once from index 0 with {@code x = A* ⊗ b}. A COATED reader is never replayed:
 * its contribution rides the edges it captured, and when it consumes an
 * already-SOLVED entry the value is ⊗'d inline ({@link #absorb}) so its
 * capture folds in the constant — the two paths agree, because an edge to a
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
	/** Each entry's {@link Life}, minted on first touch — identity plumbing; the state lives on the Life. */
	private final ConcurrentHashMap<TableEntry<Object>, Life> lives = new ConcurrentHashMap<>();

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
	public Package bodyState(Package callerPkg) {
		// fresh derivation: real value reset to ONE, no loop record
		return callerPkg.putStore(ring.one()).putStore(Recurrent.NONE);
	}

	@Override
	public Package absorb(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer,
			Object cellValue, TableEntry<Object> enclosingCall) {
		Map<Reified<?>, SemiringStore> solved = lifeOf(entry).values;
		boolean coated = enclosingCall != null;
		if (solved == null) {
			// open (or sealed mid-solve): record the loop, tag the fragment
			Recurrent prev = unifiedPkg.getStores().get(Recurrent.class)
					.map(Recurrent.class::cast).getOrElse(Recurrent.NONE);
			return unifiedPkg.putStore(prev.and(new Node(entry, consumedAnswer))).putStore(Fragment.MARKER);
		}
		SemiringStore x = solved.get(consumedAnswer);
		if (coated && x != null) {
			// a coated reader consumes a SOLVED entry: the value is a constant
			// its capture folds in (a base — or an edge that folds to the same)
			return unifiedPkg.putStore(ring.times(storeOf(unifiedPkg), x));
		}
		// top-level: still a fragment — the replay at the chain's end delivers
		return unifiedPkg.putStore(Fragment.MARKER);
	}

	@Override
	public Tuple2<Reified<?>, Object> capture(TableEntry<Object> entry, Package answerPkg, Reified<?> answerTerm) {
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

	@Override
	public Fiber<Nothing> sealed(TableEntry<Object> entry, List<Registration> drained) {
		return lifeOf(entry).sealed(drained);
	}

	@Override
	public Fiber<Nothing> caughtUp(TableEntry<Object> entry, Registration reader) {
		return lifeOf(entry).caughtUp(reader);
	}

	private Life lifeOf(TableEntry<Object> entry) {
		return lives.computeIfAbsent(entry, Life::new);
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
	 * One entry's life after explore: SEALED — the first-announced life of a
	 * fully marked group solves the closure jointly and hands every member its
	 * values; SOLVED — the values are recorded once and the stashed chains
	 * released. All transitions run under the one Closed monitor (the solve
	 * spans members); {@link #values} is volatile so {@link #absorb} reads the
	 * phase lock-free.
	 */
	private final class Life {

		private final TableEntry<Object> entry;
		/** This entry's solved answer values — null until SOLVED, the lifecycle phase. */
		private volatile Map<Reified<?>, SemiringStore> values;
		/** Ended top-level reader chains parked until the solve. Guarded by the Closed monitor. */
		private final ArrayList<Registration> stash = new ArrayList<>();

		Life(TableEntry<Object> entry) {
			this.entry = entry;
		}

		Fiber<Nothing> sealed(List<Registration> drained) {
			synchronized (Closed.this) {
				for (Registration reader : drained) {
					if (reader.getEnclosingCall() == null && !isFragment(reader.getPkg())) {
						stash.add(reader);
					}
				}
				if (values != null) {
					// a racing cascade's leader already solved this entry
					return releaseStash();
				}
				Set<TableEntry<Object>> closure = graph.dependencyClosure(entry);
				for (TableEntry<Object> member : closure) {
					if (!member.isComplete()) {
						// the mark is the only finality evidence that crosses the
						// Region boundary, and a group seal marks every member
						// before announcing any (RegionTest pins it) — an unmarked
						// closure member here means that invariant broke. Solving
						// would read a possibly-unfinal system, staying silent
						// would strand the stash — refuse loudly instead
						throw new IllegalStateException(
								"sealed " + entry.getCall() + " announced while closure member "
										+ member.getCall() + " is unsealed: group marking must "
										+ "complete before any announcement");
					}
				}
				return solveClosure(closure);
			}
		}

		Fiber<Nothing> caughtUp(Registration reader) {
			if (reader.getEnclosingCall() != null || isFragment(reader.getPkg())) {
				// a coated reader's contribution rides its captured edges; a fragment
				// chain's answers come from its valued twin
				return done(nothing());
			}
			synchronized (Closed.this) {
				if (values == null) {
					// sealed but the closure's solve has not landed — it will release
					stash.add(reader);
					return done(nothing());
				}
				return replay(reader);
			}
		}

		/**
		 * The group's first-announced life is the leader: one joint solve for the
		 * closure, then every member records its values and releases its readers
		 * (already-solved members keep their frozen values — same by determinism).
		 */
		private Fiber<Nothing> solveClosure(Set<TableEntry<Object>> closure) {
			Map<TableEntry<Object>, Map<Reified<?>, SemiringStore>> solved =
					StarTabling.solveGroup(closure, graph, ring);
			Fiber<Nothing> result = done(nothing());
			for (TableEntry<Object> member : closure) {
				Fiber<Nothing> emission = lifeOf(member).solved(
						solved.getOrDefault(member, new LinkedHashMap<Reified<?>, SemiringStore>()));
				result = result.flatMap(__ -> emission);
			}
			return result;
		}

		/** SOLVED: record the values once, release the stashed chains. */
		private Fiber<Nothing> solved(Map<Reified<?>, SemiringStore> answerValues) {
			if (values == null) {
				values = answerValues;
			}
			return releaseStash();
		}

		/** Replay and clear the stashed reader chains. */
		private Fiber<Nothing> releaseStash() {
			Fiber<Nothing> result = done(nothing());
			for (Registration reader : stash) {
				Fiber<Nothing> replay = replay(reader);
				result = result.flatMap(__ -> replay);
			}
			stash.clear();
			return result;
		}

		/** Replay one ended top-level chain from index 0 with the solved values. */
		private Fiber<Nothing> replay(Registration reader) {
			SemiringStore readerValue = storeOf(reader.getPkg());
			Fiber<Nothing> result = done(nothing());
			for (int i = 0; i < entry.getAnswerCount(); i++) {
				Reified<?> answerTerm = entry.getAnswerAt(i)._1;
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
	}

	/**
	 * Publish one sealed answer: instantiate it, unify it against the call pattern
	 * to bind the reader's variables, set the folded value on the SemiringStore,
	 * then hand it to {@code k}. The reader package already wears its own coat.
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
