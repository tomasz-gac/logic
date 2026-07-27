package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer log (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.primitives.Fixpoint;
import com.tgac.functional.fibers.primitives.JoinMap;
import io.vavr.Function3;
import io.vavr.Tuple2;
import io.vavr.control.Either;
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

	public TableEntry(Call call, IdempotentSemiring<V> semiring,
			Function3<TableEntry<V>, Registration, JoinMap<AnswerKey, V>, Fiber<Nothing>> feed) {
		this.call = call;
		this.fixpoint = new Fixpoint<>(
				JoinMap.empty(semiring),
				Registration::getEnclosing,
				// the FEED: growth pushes the grown answers into the parked
				// consumer's continuation from its cursor - no polling back
				(r, answers) -> feed.apply(this, r, answers));
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

	/**
	 * Park a consumer that caught up with the cache, its owner's ledger kept
	 * honest — sleeping-before-park, un-record on refusal, ownership derived
	 * from the registration's enclosing fixpoint ({@link Fixpoint#parkFrom}).
	 *
	 * @return right(seal attempt) when parked; left(the fresh answers) when
	 * 		answers arrived past the consumer's index — keep reading them
	 */
	public Either<JoinMap<AnswerKey, V>, Fiber<Nothing>> parkFrom(Registration registration) {
		return fixpoint.parkFrom(registration,
				v -> registration.getNextIndex() >= v.size());
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
