package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer log (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semiring;
import com.tgac.logic.tabling.primitives.JoinMap;
import com.tgac.logic.tabling.primitives.Region;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;

/**
 * A table entry for a specific tabled goal call — the call's notebook.
 *
 * The first invocation becomes the MASTER and executes the body, growing the
 * answer cell; later invocations are CONSUMERS reading it by index, parking
 * in it when they catch up. The ledger tracks everything working FOR this
 * entry — running fibers and sleeping consumers — so
 * {@link #completeIfQuiescent()} can decide that no new answer can ever
 * arrive. The entry IS a {@link Region} with this call's domain plugged in:
 * the region's value is a {@link JoinMap} of reified answer terms
 * (alpha-equivalence rides their equality), the caught-up check is the
 * consumer's resume index, "cannot wake" means parked home or at a sealed
 * entry, and the seal is the keys-final flag.
 */
public class TableEntry<V> {
	/** The call being tabled */
	@Getter
	private final Call call;

	/**
	 * The region: KEYS-FINAL is its seal (docs/design/table-completion.md §5
	 * — upward-closed, racy reads sound: a stale false prices ∞). The one
	 * domain input is ownership: a sleeper belongs to the region of the
	 * call whose body it is a line of — its coat.
	 */
	@Getter
	private final Region<JoinMap<Reified<?>, V>, Registration> region;

	/** Whether a master has claimed this call */
	private final AtomicBoolean masterActive = new AtomicBoolean(false);

	public TableEntry(Call call, IdempotentSemiring<V> semiring) {
		this.call = call;
		this.region = new Region<JoinMap<Reified<?>, V>, Registration>(
				JoinMap.empty(semiring),
				r -> r.getEnclosingCall() == null ? null : r.getEnclosingCall().getRegion());
	}

	public void markComplete() {
		region.seal();
	}

	public boolean isComplete() {
		return region.isSealed();
	}

	/**
	 * Try to become the master for this table entry. The master's work unit
	 * is counted by {@link Region#track} at produce time.
	 */
	public boolean tryBecomeMaster() {
		return masterActive.compareAndSet(false, true);
	}

	/** @return the drained subscribers to respawn, or none if the answer is a duplicate */
	public Option<List<Registration>> addAnswer(Reified<?> answerTerm, V value) {
		return region.grow(v -> v.append(answerTerm, value));
	}

	/** @return false if answers arrived past the consumer's index — keep reading */
	public boolean park(Registration registration) {
		return region.park(registration,
				v -> registration.getNextIndex() >= v.size());
	}

	public Tuple2<Reified<?>, V> getAnswerAt(int index) {
		return region.read().get(index);
	}

	public int getAnswerCount() {
		return region.read().size();
	}

	public int registrationCount() {
		return region.parkedCount();
	}

	@Override
	public String toString() {
		return "TableEntry{" +
				"call=" + call +
				", answers=" + getAnswerCount() +
				", registrations=" + registrationCount() +
				'}';
	}
}
