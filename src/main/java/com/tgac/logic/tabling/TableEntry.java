package com.tgac.logic.tabling;

// ABOUTME: One tabled call's entry: its answer cell (what it has found) and its
// ABOUTME: production ledger (what is still working for it), behind one facade.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.fibers.interpreter.Channel;
import com.tgac.logic.unification.Reified;
import io.vavr.collection.Vector;
import lombok.Getter;

/**
 * A table entry for a specific tabled goal call — the call's notebook.
 *
 * The first invocation becomes the MASTER and executes the body, growing the
 * answer cell; later invocations are CONSUMERS reading its ascent log by
 * cursor, parking at its channel when they catch up. The entry IS a
 * {@link Channel} with this call's domain plugged in: the value is the
 * {@link JoinMap} from reified answer terms (alpha-equivalence rides their
 * equality) to their ⊕-folded cell values, a consumer is a frame awaiting
 * the cell with its log cursor as the readiness predicate, and the seal is
 * the keys-final flag, fired when the cell's workforce proves that nothing
 * working for this entry can ever grow it again.
 */
public class TableEntry<V> {
	/** The call being tabled */
	@Getter
	private final Call call;

	/**
	 * The answer cell: KEYS-FINAL is its seal (docs/reference/table-completion.md
	 * §5 — upward-closed, racy reads sound: a stale false prices ∞).
	 */
	private final Channel<JoinMap<Reified<?>, V>> cell;

	private final IdempotentSemiring<V> semiring;

	public TableEntry(Call call, IdempotentSemiring<V> semiring) {
		this.call = call;
		this.semiring = semiring;
		// consumers are frames awaiting the cell - growth and the seal wake
		// them through the runtime; the call names the channel, so a strand
		// refusal names the entry it starved at
		this.cell = new Channel<>(JoinMap.empty(semiring), call.toString());
	}

	/** The answer cell, as the channel consumers await. */
	public Channel<JoinMap<Reified<?>, V>> channel() {
		return cell;
	}

	public boolean isComplete() {
		return cell.isSealed();
	}

	/**
	 * The answer as a singleton delta for the master's emit. Dedup is the
	 * cell join's own algebra: a duplicate is an inert fold, an entailed
	 * region is absorbed, a subsuming one evicts — an ascent, and the wake
	 * that goes with it.
	 */
	public JoinMap<Reified<?>, V> answerDelta(Reified<?> term, V value) {
		return JoinMap.<Reified<?>, V> empty(semiring).append(term, value).get();
	}

	/** Answer terms in arrival order — the closed mode's replay walks these. */
	public Vector<Reified<?>> answerTerms() {
		return cell.read().order;
	}

	/** The answers as of now - the initial snapshot a subscription starts from. */
	public JoinMap<Reified<?>, V> answers() {
		return cell.read();
	}

	/** Deliverable atoms: a condition counts its regions, a weight counts one. */
	public int getAnswerCount() {
		JoinMap<Reified<?>, V> answers = cell.read();
		int count = 0;
		for (Reified<?> term : answers.order) {
			V value = answers.members.get(term).get();
			count += value instanceof Condition ? ((Condition) value).conjuncts().size() : 1;
		}
		return count;
	}

	@Override
	public String toString() {
		return "TableEntry{" +
				"call=" + call +
				", answers=" + getAnswerCount() +
				'}';
	}
}
