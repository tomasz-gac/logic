package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer log (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.functional.algebra.IdempotentSemiring;
import io.vavr.control.Option;
import com.tgac.functional.fibers.primitives.JoinMap;
import com.tgac.functional.fibers.interpreter.Channel;
import io.vavr.Tuple2;
import lombok.Getter;

/**
 * A table entry for a specific tabled goal call — the call's notebook.
 *
 * The first invocation becomes the MASTER and executes the body, growing the
 * answer cell; later invocations are CONSUMERS reading it by index, parking
 * at its channel when they catch up. The entry IS a {@link Channel} with this
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
	private final Channel<JoinMap<AnswerKey, V>> cell;

	/**
	 * Armed by the first residue-carrying answer — the delivery gate reads
	 * it. Upward-closed; a racy stale false only means an outside reader
	 * takes the streaming branch on an entry that is not yet constrained.
	 */
	private volatile boolean constrained;

	public TableEntry(Call call, IdempotentSemiring<V> semiring) {
		this.call = call;
		// consumers are frames awaiting the cell - growth and the seal wake
		// them through the runtime; the call names the channel, so a strand
		// refusal names the entry it starved at
		this.cell = new Channel<>(JoinMap.empty(semiring), call.toString());
	}

	/** The answer cell, as the channel consumers await. */
	public Channel<JoinMap<AnswerKey, V>> channel() {
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
			constrained = true;
			for (AnswerKey existing : cell.read().order) {
				if (existing.getTerm().equals(key.getTerm())
						&& AnswerKey.residuesLeq(key.getResidues(), existing.getResidues())) {
					return Option.none();
				}
			}
		}
		return Option.of(JoinMap.<AnswerKey, V> empty(cell.read().semiring).append(key, value).get());
	}

	boolean isConstrained() {
		return constrained;
	}

	/**
	 * The residue-carrying answers no other answer dominates — the antichain
	 * outside readers receive at the seal. ORDER-INVARIANT even though the
	 * log is not: domination, not arrival, decides membership (the log may
	 * hold a narrower answer that arrived before its wider dominator — no
	 * arrival-time check can drop what is already cached). Equivalent
	 * residues keep their first-arrived representative.
	 */
	java.util.List<Tuple2<AnswerKey, V>> maximalConstrained() {
		JoinMap<AnswerKey, V> answers = cell.read();
		java.util.List<Tuple2<AnswerKey, V>> result = new java.util.ArrayList<>();
		for (int i = 0; i < answers.size(); i++) {
			Tuple2<AnswerKey, V> candidate = answers.get(i);
			AnswerKey key = candidate._1;
			if (key.getResidues().isEmpty()) {
				continue;
			}
			boolean dominated = false;
			for (int j = 0; j < answers.size() && !dominated; j++) {
				if (j == i) {
					continue;
				}
				AnswerKey other = answers.get(j)._1;
				if (other.getResidues().isEmpty() || !other.getTerm().equals(key.getTerm())) {
					continue;
				}
				boolean below = AnswerKey.residuesLeq(key.getResidues(), other.getResidues());
				boolean above = AnswerKey.residuesLeq(other.getResidues(), key.getResidues());
				dominated = below && (!above || j < i);
			}
			if (!dominated) {
				result.add(candidate);
			}
		}
		return result;
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
