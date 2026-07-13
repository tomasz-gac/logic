package com.tgac.logic.tabling.primitives;

// ABOUTME: Pins the work ledger: quiescence = counters drained AND every sleeper
// ABOUTME: where it cannot wake; counted() ticks start at wrap time, not run time.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class WorkLedgerTest {

	@Test
	public void aFreshLedgerIsNotQuiescent() {
		// started == 0 means the region's work has not begun — completing an
		// entry whose master is about to run would be unsound
		WorkLedger<String, String> ledger = new WorkLedger<>();
		assertThat(ledger.quiescent(at -> true)).isFalse();
	}

	@Test
	public void quiescenceNeedsEveryStartMatchedByAFinish() {
		WorkLedger<String, String> ledger = new WorkLedger<>();
		ledger.taskStarted();
		assertThat(ledger.quiescent(at -> true)).isFalse();
		ledger.taskStarted();
		ledger.taskFinished();
		assertThat(ledger.quiescent(at -> true)).isFalse();
		ledger.taskFinished();
		assertThat(ledger.quiescent(at -> true)).isTrue();
	}

	@Test
	public void aSleeperBlocksQuiescenceUnlessItCannotWake() {
		WorkLedger<String, String> ledger = new WorkLedger<>();
		ledger.taskStarted();
		ledger.taskFinished();
		ledger.sleeping("consumer", "someEntry");

		assertThat(ledger.quiescent(at -> false)).isFalse();
		assertThat(ledger.quiescent(at -> at.equals("someEntry"))).isTrue();
	}

	@Test
	public void wakingRemovesTheSleeper() {
		WorkLedger<String, String> ledger = new WorkLedger<>();
		ledger.taskStarted();
		ledger.taskFinished();
		ledger.sleeping("consumer", "someEntry");
		ledger.awake("consumer");

		assertThat(ledger.quiescent(at -> false)).isTrue();
	}

	@Test
	public void countedTicksStartAtWrapTimeAndFinishAtFiberEnd() {
		WorkLedger<String, String> ledger = new WorkLedger<>();
		AtomicBoolean hookRan = new AtomicBoolean(false);

		// deferred, as production work always is — a done fiber would chain
		// its continuation eagerly at composition time
		Fiber<Nothing> counted = ledger.counted(
				Fiber.defer(() -> Fiber.done(Nothing.nothing())), () -> hookRan.set(true));

		// started ticked synchronously at wrap time: no gap for a racing
		// quiescence check to fall into
		assertThat(ledger.quiescent(at -> true)).isFalse();
		assertThat(hookRan.get()).isFalse();

		counted.get();

		assertThat(hookRan.get()).isTrue();
		assertThat(ledger.quiescent(at -> true)).isTrue();
	}
}
