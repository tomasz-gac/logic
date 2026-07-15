package com.tgac.logic.tabling;

// ABOUTME: Maps tabled goal calls to their table entries for the duration of one solve.
// ABOUTME: Rides the package's store map so every derived state shares it.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.Getter;

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
	@Getter
	private final IdempotentSemiring<Object> semiring;

	/** The running value of a derivation, read off its package. */
	@Getter
	private final Function<Package, Object> weightReader;

	/** The package with its running value set to the given value. */
	@Getter
	private final BiFunction<Package, Object, Package> weightWriter;

	private Table(IdempotentSemiring<Object> semiring,
			Function<Package, Object> weightReader,
			BiFunction<Package, Object, Package> weightWriter) {
		this.semiring = semiring;
		this.weightReader = weightReader;
		this.weightWriter = weightWriter;
	}

	/** Plain tabling: a presence cell and no running value to thread. */
	public static Table empty() {
		return new Table(PRESENCE, p -> Boolean.TRUE, (p, v) -> p);
	}

	/**
	 * Weighted tabling: the answer cell folds by {@code semiring}, and the
	 * accessors read and set the derivation's running value on the package.
	 */
	public static Table weighted(IdempotentSemiring<Object> semiring,
			Function<Package, Object> weightReader,
			BiFunction<Package, Object, Package> weightWriter) {
		return new Table(semiring, weightReader, weightWriter);
	}

	/**
	 * Get or create a table entry for the given call.
	 * If this is the first time we've seen this call, a new TableEntry is created.
	 */
	public TableEntry<Object> getOrCreateEntry(Call call) {
		return entries.computeIfAbsent(call, c -> new TableEntry<>(c, semiring));
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
