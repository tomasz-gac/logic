package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer log (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.fibers.primitives.JoinMap;
import com.tgac.functional.fibers.primitives.Fixpoint;
import io.vavr.Tuple2;
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
 * arrive. The entry IS a {@link Fixpoint} with this call's domain plugged in:
 * the fixpoint's value is a {@link JoinMap} of reified answer terms
 * (alpha-equivalence rides their equality), the caught-up check is the
 * consumer's resume index, "cannot wake" means parked home or at a sealed
 * entry, and the seal is the keys-final flag.
 */
public class TableEntry<V> {
	/** The call being tabled */
	@Getter
	private final Call call;

	/**
	 * The fixpoint: KEYS-FINAL is its seal (docs/design/table-completion.md §5
	 * — upward-closed, racy reads sound: a stale false prices ∞). The one
	 * domain input is ownership: a sleeper belongs to the fixpoint of the
	 * call whose body it is a line of — its coat.
	 */
	@Getter
	private final Fixpoint<JoinMap<AnswerKey, V>, Registration> fixpoint;

	/** Whether a master has claimed this call */
	private final AtomicBoolean masterActive = new AtomicBoolean(false);

	public TableEntry(Call call, IdempotentSemiring<V> semiring) {
		this.call = call;
		this.fixpoint = new Fixpoint<JoinMap<AnswerKey, V>, Registration>(
				JoinMap.empty(semiring),
				r -> r.getEnclosingCall() == null ? null : r.getEnclosingCall().getFixpoint());
	}

	public void markComplete() {
		fixpoint.seal();
	}

	public boolean isComplete() {
		return fixpoint.isSealed();
	}

	/**
	 * Try to become the master for this table entry. The master's work unit
	 * is counted by {@link Fixpoint#track} at produce time.
	 */
	public boolean tryBecomeMaster() {
		return masterActive.compareAndSet(false, true);
	}

	/**
	 * @return the drained subscribers to respawn, or none if the answer is a
	 * 		duplicate — exact (the cell's fold refused) or ENTAILED: a same-term
	 * 		answer whose residues cover the new one's makes it redundant (its
	 * 		replay contributes a subset fixpoint). Append-only: a wider newcomer
	 * 		never retracts a narrower veteran — delivered answers stand.
	 */
	public Option<List<Registration>> addAnswer(AnswerKey key, V value) {
		if (!key.getResidues().isEmpty()) {
			for (AnswerKey existing : fixpoint.read().order) {
				if (existing.getTerm().equals(key.getTerm())
						&& AnswerKey.residuesLeq(key.getResidues(), existing.getResidues())) {
					return Option.none();
				}
			}
		}
		return fixpoint.grow(JoinMap.<AnswerKey, V> empty(fixpoint.read().semiring).append(key, value).get());
	}

	/** @return false if answers arrived past the consumer's index — keep reading */
	public boolean park(Registration registration) {
		return fixpoint.park(registration,
				v -> registration.getNextIndex() >= v.size());
	}

	public Tuple2<AnswerKey, V> getAnswerAt(int index) {
		return fixpoint.read().get(index);
	}

	public int getAnswerCount() {
		return fixpoint.read().size();
	}

	public int registrationCount() {
		return fixpoint.parkedCount();
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
