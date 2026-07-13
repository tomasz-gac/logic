package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer log (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.logic.unification.Reified;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;

/**
 * A table entry for a specific tabled goal call — the call's notebook.
 *
 * The first invocation becomes the MASTER and executes the body, appending
 * each new answer to the {@link AnswerLog}; later invocations are CONSUMERS
 * reading the log, parking in it when they catch up. The
 * {@link ProductionLedger} tracks everything working FOR this entry —
 * running fibers and sleeping consumers — so {@link #completeIfQuiescent()}
 * can decide that no new answer can ever arrive.
 */
public class TableEntry {
	/** The call being tabled */
	@Getter
	private final Call call;

	private final AnswerLog log = new AnswerLog();

	@Getter
	private final ProductionLedger ledger = new ProductionLedger();

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
		if (!ledger.quiescentAndBlockedOnlyBy(this)) {
			return null;
		}
		if (!complete.compareAndSet(false, true)) {
			return null;
		}
		return log.drainParked();
	}

	/**
	 * Try to become the master for this table entry. The master's work unit
	 * is counted by {@link Completion#track} at produce time.
	 */
	public boolean tryBecomeMaster() {
		return masterActive.compareAndSet(false, true);
	}

	/** @see AnswerLog#append */
	public Option<List<Registration>> addAnswer(Reified<?> answerTerm) {
		return log.append(answerTerm);
	}

	/** @see AnswerLog#park */
	public boolean park(Registration registration) {
		return log.park(registration);
	}

	public Reified<?> getAnswerAt(int index) {
		return log.answerAt(index);
	}

	public int getAnswerCount() {
		return log.answerCount();
	}

	public int registrationCount() {
		return log.parkedCount();
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
