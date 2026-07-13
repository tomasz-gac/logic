package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer log (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.logic.tabling.primitives.JoinSet;
import com.tgac.logic.tabling.primitives.Region;
import com.tgac.logic.unification.Reified;
import io.vavr.collection.List;
import io.vavr.control.Option;
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
 * the region's value is a {@link JoinSet} of reified answer terms
 * (alpha-equivalence rides their equality), the caught-up check is the
 * consumer's resume index, "cannot wake" means parked home or at a sealed
 * entry, and the seal is the keys-final flag.
 */
public class TableEntry {
	/** The call being tabled */
	@Getter
	private final Call call;

	/**
	 * The region: KEYS-FINAL is its seal (docs/design/table-completion.md §5
	 * — upward-closed, racy reads sound: a stale false prices ∞).
	 */
	@Getter
	private final Region<JoinSet<Reified<?>>, Registration, TableEntry> region =
			new Region<>(JoinSet.empty());

	/** Whether a master has claimed this call */
	private final AtomicBoolean masterActive = new AtomicBoolean(false);

	public TableEntry(Call call) {
		this.call = call;
	}

	public void markComplete() {
		region.seal();
	}

	public boolean isComplete() {
		return region.isSealed();
	}

	/**
	 * The seal rule with this call's domain plugged in: home-parked sleepers
	 * cannot wake without a new answer here, which needs running work here,
	 * which quiescence just ruled out; sealed strangers never produce again.
	 *
	 * @return the dead subscribers for the {@link Completion} cascade, or
	 * 		null when the rule does not fire
	 */
	List<Registration> completeIfQuiescent() {
		return region.sealIfQuiescent(at -> at == this || at.isComplete());
	}

	/**
	 * Try to become the master for this table entry. The master's work unit
	 * is counted by {@link Completion#track} at produce time.
	 */
	public boolean tryBecomeMaster() {
		return masterActive.compareAndSet(false, true);
	}

	/** @return the drained subscribers to respawn, or none if the answer is a duplicate */
	public Option<List<Registration>> addAnswer(Reified<?> answerTerm) {
		return region.grow(v -> v.append(answerTerm));
	}

	/** @return false if answers arrived past the consumer's index — keep reading */
	public boolean park(Registration registration) {
		return region.park(registration,
				v -> registration.getNextIndex() >= v.size());
	}

	public Reified<?> getAnswerAt(int index) {
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
