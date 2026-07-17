package com.tgac.logic.tabling;

// ABOUTME: Maps tabled goal calls to their table entries for the duration of one solve.
// ABOUTME: Rides the package's store map and delegates per-step decisions to its mode.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
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
 * <p>The table carries the solve's {@link TablingMode}: {@link Streaming} for
 * plain and bounded-weighted tabling (fold each answer's value and hand it out
 * now), the weight package's closed mode for star tabling (explore for
 * structure, solve at seal). The shared master / consumer / park / completion
 * skeleton in {@link Tabling} calls the mode's hooks and never branches on
 * which one it is.
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

	/** The algorithm: streaming vs closed/star. */
	private final TablingMode mode;

	/** Why tabling is refused under this table, or null when it is allowed. */
	private final String tablingForbidden;

	private Table(TablingMode mode, String tablingForbidden) {
		this.mode = mode;
		this.tablingForbidden = tablingForbidden;
	}

	/** Plain tabling: a presence cell and no running value to thread. */
	public static Table empty() {
		return new Table(new Streaming(PRESENCE, p -> Boolean.TRUE, (p, v) -> p), null);
	}

	/**
	 * Weighted tabling: the answer cell folds by {@code semiring}, and the
	 * accessors read and set the derivation's running value on the package.
	 */
	public static Table weighted(IdempotentSemiring<Object> semiring,
			Function<Package, Object> weightReader,
			BiFunction<Package, Object, Package> weightWriter) {
		return new Table(new Streaming(semiring, weightReader, weightWriter), null);
	}

	/** A table running the given mode — the door for modes defined outside this package. */
	public static Table of(TablingMode mode) {
		return new Table(mode, null);
	}

	/**
	 * A weighted solve that CANNOT thread weights through tabling — the plain
	 * {@code solve}/{@code solveEach}, whose semiring may be non-idempotent and
	 * non-closed. It presents as a plain table but refuses an actual tabled
	 * call, so dropped weights fail loudly instead of silently miscomputing.
	 */
	public static Table refusingTabling(String reason) {
		return new Table(new Streaming(PRESENCE, p -> Boolean.TRUE, (p, v) -> p), reason);
	}

	/** Loud failure when a tabled call runs under a table that cannot support it. */
	public void assertTablingAllowed() {
		if (tablingForbidden != null) {
			throw new IllegalStateException(tablingForbidden);
		}
	}

	// ---- the mode's per-step hooks (see TablingMode) ----

	Package enterBody(Package callerPkg) {
		return mode.enterBody(callerPkg);
	}

	Object callerValue(Package callerPkg) {
		return mode.callerValue(callerPkg);
	}

	Package onConsume(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer, Object cellValue) {
		return mode.onConsume(unifiedPkg, entry, consumedAnswer, cellValue);
	}

	Object cacheValue(Package answerPkg) {
		return mode.cacheValue(answerPkg);
	}

	Reified<?> onProduce(TableEntry<Object> entry, Package answerPkg, Reified<?> answerTerm) {
		return mode.onProduce(entry, answerPkg, answerTerm);
	}

	Package onExit(Package answerPkg, TableEntry<Object> entry, Reified<?> answerTerm, Package callerPkg, Object value) {
		return mode.onExit(answerPkg, entry, answerTerm, callerPkg, value);
	}

	void onMasterClaim(TableEntry<Object> entry, Fiber.Fn<Package, Nothing> k,
			Package callerPkg, Unifiable<?> argsTerm, TableEntry<Object> callerEntry) {
		mode.onMasterClaim(entry, k, callerPkg, argsTerm, callerEntry);
	}

	// ---- entries ----

	/**
	 * Get or create a table entry for the given call.
	 * If this is the first time we've seen this call, a new TableEntry is created.
	 */
	public TableEntry<Object> getOrCreateEntry(Call call) {
		return entries.computeIfAbsent(call, c -> new TableEntry<>(c, mode.cellSemiring()));
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
