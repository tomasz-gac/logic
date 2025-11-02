package com.tgac.logic.tabling;

import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;

/**
 * A table entry for a specific tabled goal call.
 *
 * This implements the master/slave pattern from Byrd's dissertation:
 * - The MASTER is the first invocation that actually executes the goal
 * - SLAVES are subsequent invocations that read from the cached answers
 *
 * The master produces answer terms (reified arguments with fresh variables).
 * Slaves unify their arguments with cached answer terms.
 */
public class TableEntry {
	/** The call being tabled */
	@Getter
	private final Call call;

	/** Cached answer terms produced by the master - each is a list of reified arguments */
	private final CopyOnWriteArrayList<List<Unifiable<?>>> answers = new CopyOnWriteArrayList<>();

	/** Whether the master has completed producing all answers */
	private final AtomicBoolean complete = new AtomicBoolean(false);

	/** Whether a master is currently executing for this call */
	private final AtomicBoolean masterActive = new AtomicBoolean(false);

	/**
	 * Slaves waiting for answers at specific indices.
	 * Key: index in answers list where slave is waiting
	 * Value: future to complete when answer becomes available
	 */
	private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<CompletableFuture<AnswerStatus>>>
		waitingSlavesAtIndex = new ConcurrentHashMap<>();

	/**
	 * Status of an answer request - either an answer is available or master is done.
	 */
	public interface AnswerStatus {
		// Marker interface for sealed-like behavior
	}

	/**
	 * An answer is available at the requested index.
	 */
	@Getter
	public static class Answer implements AnswerStatus {
		private final List<Unifiable<?>> answerTerm;

		public Answer(List<Unifiable<?>> answerTerm) {
			this.answerTerm = answerTerm;
		}
	}

	/**
	 * The master has completed; no more answers will be produced.
	 */
	public static class Done implements AnswerStatus {
		public static final Done INSTANCE = new Done();

		private Done() {
		}
	}

	public TableEntry(Call call) {
		this.call = call;
	}

	/**
	 * Try to become the master for this table entry.
	 * Returns true if this caller became the master, false if another master exists.
	 */
	public boolean tryBecomeMaster() {
		return masterActive.compareAndSet(false, true);
	}

	/**
	 * Add an answer term to the cache and notify waiting slaves.
	 * Should only be called by the master.
	 */
	public void addAnswer(List<Unifiable<?>> answerTerm) {
		int index = answers.size();
		answers.add(answerTerm);

		// Notify all slaves waiting at this index
		CopyOnWriteArrayList<CompletableFuture<AnswerStatus>> waitingAtIndex =
			waitingSlavesAtIndex.remove(index);

		if (waitingAtIndex != null) {
			for (CompletableFuture<AnswerStatus> future : waitingAtIndex) {
				future.complete(new Answer(answerTerm));
			}
		}
	}

	/**
	 * Mark this table entry as complete (master finished).
	 * Notifies all waiting slaves that no more answers will come.
	 */
	public void markComplete() {
		complete.set(true);

		// Notify all waiting slaves at any index
		for (CopyOnWriteArrayList<CompletableFuture<AnswerStatus>> futures : waitingSlavesAtIndex.values()) {
			for (CompletableFuture<AnswerStatus> future : futures) {
				future.complete(Done.INSTANCE);
			}
		}
		waitingSlavesAtIndex.clear();
	}

	/**
	 * Get an answer term at the specified index, or null if not yet available.
	 */
	public List<Unifiable<?>> getAnswerAt(int index) {
		if (index < answers.size()) {
			return answers.get(index);
		}
		return null;
	}

	/**
	 * Check if the master has completed.
	 */
	public boolean isComplete() {
		return complete.get();
	}

	/**
	 * Get the current number of cached answers.
	 */
	public int getAnswerCount() {
		return answers.size();
	}

	/**
	 * Register a slave to wait for an answer at the specified index.
	 * Returns a future that will be completed when the answer becomes available
	 * or when the master completes.
	 */
	public CompletableFuture<AnswerStatus> waitForAnswerAt(int index) {
		CompletableFuture<AnswerStatus> future = new CompletableFuture<>();

		// Double-check: answer might already be available
		if (index < answers.size()) {
			future.complete(new Answer(answers.get(index)));
			return future;
		}

		// Double-check: master might be done
		if (complete.get()) {
			future.complete(Done.INSTANCE);
			return future;
		}

		// Register to wait
		waitingSlavesAtIndex
			.computeIfAbsent(index, k -> new CopyOnWriteArrayList<>())
			.add(future);

		// Triple-check after registering (race condition)
		if (index < answers.size()) {
			future.complete(new Answer(answers.get(index)));
		} else if (complete.get()) {
			future.complete(Done.INSTANCE);
		}

		return future;
	}

	@Override
	public String toString() {
		return "TableEntry{" +
			"call=" + call +
			", answers=" + answers.size() +
			", complete=" + complete.get() +
			'}';
	}
}
