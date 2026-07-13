package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer log (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.logic.tabling.primitives.MonotoneCell;
import com.tgac.logic.tabling.primitives.WorkLedger;
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
 * arrive. Both components are the generic primitives with this entry's
 * domain plugged in: the cell's value is an {@link AnswerSet}, the caught-up
 * check is the consumer's resume index, and "cannot wake" means parked home
 * or on a completed entry.
 */
public class TableEntry {
	/** The call being tabled */
	@Getter
	private final Call call;

	private final MonotoneCell<AnswerSet, Registration> answers =
			new MonotoneCell<>(AnswerSet.empty());

	@Getter
	private final WorkLedger<Registration, TableEntry> ledger = new WorkLedger<>();

	/** Whether a master has claimed this call */
	private final AtomicBoolean masterActive = new AtomicBoolean(false);

	/**
	 * KEYS-FINAL: no new answer bindings will ever arrive
	 * (docs/design/table-completion.md §5). Upward-closed — once set, forever
	 * set — so racy reads are sound: a stale false prices ∞, a true is
	 * permanent. Flipped by {@link #completeIfQuiescent()}; manual marking
	 * remains for tests.
	 */
	private final AtomicBoolean complete = new AtomicBoolean(false);

	public TableEntry(Call call) {
		this.call = call;
	}

	public void markComplete() {
		complete.set(true);
	}

	public boolean isComplete() {
		return complete.get();
	}

	/**
	 * The join rule, coordinated across the entry's two components: ledger
	 * quiescent (its monitor), flag flipped exactly once (CAS arbitrates
	 * racers), then the log's parked subscribers harvested (its monitor) —
	 * they are provably dead, returned for the {@link Completion} cascade.
	 * Between the ledger check and the CAS no legal transition can start new
	 * work for this entry: waking a home-parked sleeper needs a new answer
	 * here, which needs running work here, which the check just ruled out.
	 *
	 * @return the dead subscribers, or null when the rule does not fire
	 */
	List<Registration> completeIfQuiescent() {
		// home-parked sleepers cannot wake without a new answer here, which
		// needs running work here, which quiescence just ruled out
		if (!ledger.quiescent(at -> at == this || at.isComplete())) {
			return null;
		}
		if (!complete.compareAndSet(false, true)) {
			return null;
		}
		return answers.drainParked();
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
		return answers.grow(v -> v.append(answerTerm));
	}

	/** @return false if answers arrived past the consumer's index — keep reading */
	public boolean park(Registration registration) {
		return answers.park(registration,
				v -> registration.getNextIndex() >= v.size());
	}

	public Reified<?> getAnswerAt(int index) {
		return answers.read().answerAt(index);
	}

	public int getAnswerCount() {
		return answers.read().size();
	}

	public int registrationCount() {
		return answers.parkedCount();
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
