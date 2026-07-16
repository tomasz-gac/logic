package com.tgac.logic.tabling;

// ABOUTME: Maps tabled goal calls to their table entries for the duration of one solve.
// ABOUTME: Rides the package's store map so every derived state shares it.

import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The table that maps tabled goal calls to their table entries.
 *
 * Each unique call (identified by goal name and reified arguments) gets its
 * own {@link TableEntry} where answers are cached. The table is scoped to a
 * single solve: {@link Goal#solve} seeds a fresh one into the root package's
 * store map, and all packages derived during the search share it.
 *
 * <p>The table carries the solve's cell algebra: the {@link IdempotentSemiring}
 * that folds answer values, and the two accessors that thread the running value
 * of a derivation through the package. For plain (unweighted) tabling these are
 * the degenerate case — a presence semiring and no-op accessors — so the cell
 * is a set and the weight machinery vanishes. Weighted tabling supplies a real
 * semiring and accessors that read and ⊗ a value store.
 *
 * <p>It is a {@link Packaged} payload — not a constraint store — so constraint
 * processing ignores it; the store map is only its transport.
 */
public class Table implements Packaged {

	/** Every answer carries the same presence marker — the set-tabling cell. */
	@SuppressWarnings("unchecked")
	private static final IdempotentSemiring<Object> PRESENCE =
			(IdempotentSemiring<Object>) (IdempotentSemiring<?>) Semirings.BOOLEAN;

	/** Map from calls to their table entries */
	private final ConcurrentHashMap<Call, TableEntry<Object>> entries = new ConcurrentHashMap<>();

	/** The cell's ⊕ (answer fold), ⊗ (consumption), and 1 (fresh derivation). */
	private final IdempotentSemiring<Object> semiring;

	/** The running value of a derivation, read off its package. */
	private final Function<Package, Object> weightReader;

	/** The package with its running value set to the given value. */
	private final BiFunction<Package, Object, Package> weightWriter;

	/** Why tabling is refused under this table, or null when it is allowed. */
	private final String tablingForbidden;

	/** Non-null only for a wait-mode (closed) solve — the star's ring and store accessors. */
	private final ClosedMode closedMode;

	private Table(IdempotentSemiring<Object> semiring,
			Function<Package, Object> weightReader,
			BiFunction<Package, Object, Package> weightWriter,
			String tablingForbidden,
			ClosedMode closedMode) {
		this.semiring = semiring;
		this.weightReader = weightReader;
		this.weightWriter = weightWriter;
		this.tablingForbidden = tablingForbidden;
		this.closedMode = closedMode;
	}

	/**
	 * The ring and SemiringStore accessors a wait-mode solve threads for the star:
	 * the presence cell drives explore, these drive probe/solve/emit at each seal.
	 */
	public static final class ClosedMode {
		public final ClosedSemiring<Object> semiring;
		public final Function<Package, Object> storeReader;
		public final BiFunction<Package, Object, Package> storeWriter;

		ClosedMode(ClosedSemiring<Object> semiring,
				Function<Package, Object> storeReader,
				BiFunction<Package, Object, Package> storeWriter) {
			this.semiring = semiring;
			this.storeReader = storeReader;
			this.storeWriter = storeWriter;
		}
	}

	/** Plain tabling: a presence cell and no running value to thread. */
	public static Table empty() {
		return new Table(PRESENCE, p -> Boolean.TRUE, (p, v) -> p, null, null);
	}

	/**
	 * Weighted tabling: the answer cell folds by {@code semiring}, and the
	 * accessors read and set the derivation's running value on the package.
	 */
	public static Table weighted(IdempotentSemiring<Object> semiring,
			Function<Package, Object> weightReader,
			BiFunction<Package, Object, Package> weightWriter) {
		return new Table(semiring, weightReader, weightWriter, null, null);
	}

	/**
	 * Closed (wait-mode) tabling: the cell is presence, so explore is plain tabling
	 * and terminates, while {@code closedSemiring} and the store accessors are held
	 * for the star to solve at each seal. The real value rides the SemiringStore,
	 * untouched by the presence cell.
	 */
	public static Table closed(ClosedSemiring<Object> closedSemiring,
			Function<Package, Object> storeReader,
			BiFunction<Package, Object, Package> storeWriter) {
		return new Table(PRESENCE, p -> Boolean.TRUE, (p, v) -> p, null,
				new ClosedMode(closedSemiring, storeReader, storeWriter));
	}

	/** Whether this solve defers values to a star at seal (closed) rather than streaming. */
	public boolean isWaitMode() {
		return closedMode != null;
	}

	/** The star's ring and store accessors, or null when not a wait-mode table. */
	public ClosedMode getClosedMode() {
		return closedMode;
	}

	/**
	 * A weighted solve that CANNOT thread weights through tabling — the plain
	 * {@code solve}/{@code solveEach}, whose semiring may be non-idempotent and
	 * non-closed. It presents as a plain table but refuses an actual tabled
	 * call, so dropped weights fail loudly instead of silently miscomputing.
	 */
	public static Table refusingTabling(String reason) {
		return new Table(PRESENCE, p -> Boolean.TRUE, (p, v) -> p, reason, null);
	}

	/** Loud failure when a tabled call runs under a table that cannot support it. */
	public void assertTablingAllowed() {
		if (tablingForbidden != null) {
			throw new IllegalStateException(tablingForbidden);
		}
	}

	// ---- the cell's weight algebra (identity when unweighted) ----

	/** A fresh derivation's weight — the ⊗ identity. */
	public Object one() {
		return semiring.one();
	}

	/** The running value carried by {@code pkg}'s derivation. */
	public Object weightOf(Package pkg) {
		return weightReader.apply(pkg);
	}

	/** {@code pkg} with its running value set to {@code weight}. */
	public Package withWeight(Package pkg, Object weight) {
		return weightWriter.apply(pkg, weight);
	}

	/** ⊗ of two weights. */
	public Object times(Object a, Object b) {
		return semiring.times(a, b);
	}

	/** Reset to ONE — a master's body runs from a caller-agnostic weight. */
	public Package resetWeight(Package pkg) {
		return withWeight(pkg, one());
	}

	/** ⊗ {@code value} into the running weight — a consumer folding in a cached answer. */
	public Package scaleWeight(Package pkg, Object value) {
		return withWeight(pkg, times(weightOf(pkg), value));
	}

	// ---- entries ----

	/**
	 * Get or create a table entry for the given call.
	 * If this is the first time we've seen this call, a new TableEntry is created.
	 */
	public TableEntry<Object> getOrCreateEntry(Call call) {
		return entries.computeIfAbsent(call, c -> new TableEntry<>(c, semiring));
	}

	/**
	 * A SEALED general entry to reuse for {@code key} instead of minting a fresh
	 * master — present only on an exact miss, since an exact entry is the call's
	 * own master. The general's answers are a superset filtered by consumption.
	 */
	public TableEntry<Object> reusableSubsumer(Call key) {
		return getEntry(key) == null ? findSealedSubsumer(key) : null;
	}

	/**
	 * Get an existing table entry, or null if the call hasn't been tabled yet.
	 */
	public Collection<TableEntry<Object>> entries() {
		return entries.values();
	}

	/**
	 * A SEALED entry whose call subsumes {@code key}, or null. Linear scan:
	 * entries per solve are few, and the scan runs only on exact misses —
	 * SubsumptionMap is this lookup's planned generalization when its next
	 * customer (the adornment memo) arrives.
	 */
	public TableEntry<Object> findSealedSubsumer(Call key) {
		for (TableEntry<Object> e : entries.values()) {
			if (e.isComplete() && e.getCall().subsumes(key)) {
				return e;
			}
		}
		return null;
	}

	public TableEntry<Object> getEntry(Call call) {
		return entries.get(call);
	}

	/**
	 * Get the number of distinct calls currently in the table.
	 */
	public int size() {
		return entries.size();
	}

	/**
	 * Check if the table contains an entry for the given call.
	 */
	public boolean contains(Call call) {
		return entries.containsKey(call);
	}

	@Override
	public String toString() {
		return "Table{entries=" + entries.size() + "}";
	}
}
