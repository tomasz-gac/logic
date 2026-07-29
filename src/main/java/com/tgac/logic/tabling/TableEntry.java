package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer log (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.functional.algebra.IdempotentSemiring;
import io.vavr.control.Option;
import com.tgac.functional.fibers.primitives.JoinMap;
import com.tgac.functional.fibers.interpreter.MonotoneCell;
import io.vavr.Tuple2;
import lombok.Getter;

/**
 * A table entry for a specific tabled goal call — the call's notebook.
 *
 * The first invocation becomes the MASTER and executes the body, growing the
 * answer cell; later invocations are CONSUMERS reading it by index, parking
 * in it when they catch up. The entry IS a {@link MonotoneCell} with this
 * call's domain plugged in: the value is a {@link JoinMap} of reified
 * answer terms (alpha-equivalence rides their equality), a consumer is a
 * frame awaiting the cell with its cursor as the readiness predicate, and
 * the seal is the keys-final flag, fired when the cell's workforce proves
 * that nothing working for this entry can ever grow it again.
 */
public class TableEntry<V> {
	/** The call being tabled */
	@Getter
	private final Call call;

	/**
	 * The answer cell: KEYS-FINAL is its seal (docs/design/table-completion.md
	 * §5 — upward-closed, racy reads sound: a stale false prices ∞).
	 */
	private final MonotoneCell<JoinMap<AnswerKey, V>> cell;

	public TableEntry(Call call, IdempotentSemiring<V> semiring) {
		this.call = call;
		// consumers are frames awaiting the cell - growth and the seal wake
		// them through the runtime
		this.cell = new MonotoneCell<>(JoinMap.empty(semiring));
	}

	/** The answer cell, as the Source consumers await. */
	public MonotoneCell<JoinMap<AnswerKey, V>> source() {
		return cell;
	}

	public void markComplete() {
		cell.seal();
	}

	public boolean isComplete() {
		return cell.isSealed();
	}

	/**
	 * The answer as a singleton delta for the master's emit, or none when it
	 * is ENTAILED: a same-term answer whose residues cover the new one's
	 * makes it redundant (its replay contributes a subset fixpoint). An
	 * EXACT duplicate keeps its delta — the fold absorbs it as an inert
	 * join. Append-only: a wider newcomer never retracts a narrower veteran
	 * — delivered answers stand.
	 */
	public Option<JoinMap<AnswerKey, V>> answerDelta(AnswerKey key, V value) {
		if (!key.getResidues().isEmpty()) {
			for (AnswerKey existing : cell.read().order) {
				if (existing.getTerm().equals(key.getTerm())
						&& AnswerKey.residuesLeq(key.getResidues(), existing.getResidues())) {
					return Option.none();
				}
			}
		}
		return Option.of(JoinMap.<AnswerKey, V> empty(cell.read().semiring).append(key, value).get());
	}

	public Tuple2<AnswerKey, V> getAnswerAt(int index) {
		return cell.read().get(index);
	}

	/** The answers as of now - the initial snapshot a subscription starts from. */
	public JoinMap<AnswerKey, V> answers() {
		return cell.read();
	}

	public int getAnswerCount() {
		return cell.read().size();
	}

	@Override
	public String toString() {
		return "TableEntry{" +
				"call=" + call +
				", answers=" + getAnswerCount() +
				'}';
	}
}
