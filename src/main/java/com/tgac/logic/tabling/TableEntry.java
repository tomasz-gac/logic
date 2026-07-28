package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer log (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.schedulers.Fixpoint;
import com.tgac.functional.fibers.primitives.JoinMap;
import com.tgac.functional.fibers.schedulers.MonotoneCell;
import io.vavr.Tuple2;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;

/**
 * A table entry for a specific tabled goal call — the call's notebook.
 *
 * The first invocation becomes the MASTER and executes the body, growing the
 * answer cell; later invocations are CONSUMERS reading it by index, parking
 * in it when they catch up. The entry IS a {@link Fixpoint} with this call's
 * domain plugged in: the value is a {@link JoinMap} of reified answer terms
 * (alpha-equivalence rides their equality), a subscriber is a
 * {@link Registration} — its cursor is the caught-up check, its enclosing
 * fixpoint is its owner — the feed re-enters consumption from the cursor,
 * and the seal is the keys-final flag, fired when the fixpoint's ledger
 * proves that nothing working for this entry can ever grow it again.
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
		// consumers are frames awaiting the cell - growth and the seal wake
		// them through the runtime, no feed re-enters domain code
		this.fixpoint = new Fixpoint<>(
				JoinMap.empty(semiring),
				Registration::getEnclosing,
				(r, answers) -> Fiber.done(Nothing.nothing()));
	}

	/** The answer cell, as the Source consumers await. */
	public MonotoneCell<JoinMap<AnswerKey, V>, Registration> source() {
		return fixpoint.source();
	}

	public void markComplete() {
		fixpoint.seal();
	}

	public boolean isComplete() {
		return fixpoint.isSealed();
	}

	/**
	 * Try to become the master for this table entry. The master's work unit
	 * runs ambiently owned via {@code Fiber.detachTo} at produce time.
	 */
	public boolean tryBecomeMaster() {
		return masterActive.compareAndSet(false, true);
	}

	/**
	 * Join the answer in as a singleton delta. Growth FEEDS every parked
	 * consumer the grown answers, billed-before-awoken, as the returned tail;
	 * a duplicate is inert — exact (the join absorbed it) or ENTAILED: a
	 * same-term answer whose residues cover the new one's makes it redundant
	 * (its replay contributes a subset fixpoint). Append-only: a wider
	 * newcomer never retracts a narrower veteran — delivered answers stand.
	 */
	public Fiber<Nothing> addAnswer(AnswerKey key, V value) {
		if (!key.getResidues().isEmpty()) {
			for (AnswerKey existing : fixpoint.read().order) {
				if (existing.getTerm().equals(key.getTerm())
						&& AnswerKey.residuesLeq(key.getResidues(), existing.getResidues())) {
					return Fiber.done(Nothing.nothing());
				}
			}
		}
		return fixpoint.grow(JoinMap.<AnswerKey, V> empty(fixpoint.read().semiring).append(key, value).get());
	}

	public Tuple2<AnswerKey, V> getAnswerAt(int index) {
		return fixpoint.read().get(index);
	}

	/** The answers as of now - the initial snapshot a subscription starts from. */
	public JoinMap<AnswerKey, V> answers() {
		return fixpoint.read();
	}

	public int getAnswerCount() {
		return fixpoint.read().size();
	}

	public int parkedCount() {
		return fixpoint.parkedCount();
	}

	@Override
	public String toString() {
		return "TableEntry{" +
				"call=" + call +
				", answers=" + getAnswerCount() +
				", parked=" + parkedCount() +
				'}';
	}
}
