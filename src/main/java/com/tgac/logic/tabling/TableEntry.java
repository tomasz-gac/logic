package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer log (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.functional.algebra.IdempotentSemiring;
import io.vavr.control.Option;
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
	 * The answer poset: ground answers are atoms (indexed, streamed);
	 * residue-carrying answers have comparable extent — a covers b when the
	 * terms are equal and b's residues are pointwise below a's. The cell's
	 * JoinMap keeps the covered region as its maximal antichain, so
	 * subsumption is the join's own algebra, not a protocol.
	 */
	private static final JoinMap.Dominance<AnswerKey> COVERS = new JoinMap.Dominance<AnswerKey>() {
		@Override
		public boolean partial(AnswerKey key) {
			return !key.getResidues().isEmpty();
		}

		@Override
		public boolean dominates(AnswerKey a, AnswerKey b) {
			return a.getTerm().equals(b.getTerm())
					&& AnswerKey.residuesLeq(b.getResidues(), a.getResidues());
		}
	};

	public TableEntry(Call call, IdempotentSemiring<V> semiring) {
		this.call = call;
		// consumers are frames awaiting the cell - growth and the seal wake
		// them through the runtime; the call names the channel, so a strand
		// refusal names the entry it starved at
		this.cell = new Channel<>(JoinMap.empty(semiring, COVERS), call.toString());
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
	 * The answer as a singleton delta for the master's emit. Dedup is the
	 * cell join's own algebra: an exact duplicate and an entailed newcomer
	 * are inert joins, a subsuming newcomer evicts what it covers — an
	 * ascent in the downset order, and the wake that goes with it.
	 */
	public JoinMap<AnswerKey, V> answerDelta(AnswerKey key, V value) {
		return JoinMap.<AnswerKey, V> empty(cell.read().semiring, COVERS).append(key, value).get();
	}

	public Tuple2<AnswerKey, V> getAnswerAt(int index) {
		return cell.read().get(index);
	}

	/** The answers as of now - the initial snapshot a subscription starts from. */
	public JoinMap<AnswerKey, V> answers() {
		return cell.read();
	}

	public int getAnswerCount() {
		JoinMap<AnswerKey, V> answers = cell.read();
		return answers.size() + answers.partial().size();
	}

	@Override
	public String toString() {
		return "TableEntry{" +
				"call=" + call +
				", answers=" + getAnswerCount() +
				'}';
	}
}
