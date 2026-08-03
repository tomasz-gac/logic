package com.tgac.logic;

// ABOUTME: Pins the solve stream's lifecycle contract: closing the stream closes
// ABOUTME: the engine — the walk-away pattern is try-with-resources on the stream.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Scheduler;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Test;

public class SolveLifecycleTest {

	/** A delegating scheduler that records whether close() reached the engine. */
	private static final class ClosingProbe implements Scheduler<Nothing> {
		private final Scheduler<Nothing> inner;
		private final AtomicBoolean closed;

		ClosingProbe(Scheduler<Nothing> inner, AtomicBoolean closed) {
			this.inner = inner;
			this.closed = closed;
		}

		@Override
		public boolean step(Consumer<? super Nothing> sink) {
			return inner.step(sink);
		}

		@Override
		public boolean run(int iterations, Consumer<? super Nothing> sink) {
			return inner.run(iterations, sink);
		}

		@Override
		public void run(Consumer<? super Nothing> sink) {
			inner.run(sink);
		}

		@Override
		public Optional<Nothing> run(int iterations) {
			return inner.run(iterations);
		}

		@Override
		public boolean advance(Consumer<? super Nothing> sink) {
			return inner.advance(sink);
		}

		@Override
		public Nothing get() {
			return inner.get();
		}

		@Override
		public void close() {
			closed.set(true);
			try {
				inner.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Test
	public void closingTheSolveStreamClosesTheEngine() {
		AtomicBoolean closed = new AtomicBoolean();
		Unifiable<Integer> x = lvar();
		try (Stream<Reified<Integer>> answers = x.unifies(1).or(x.unifies(2))
				.solve(x, f -> new ClosingProbe(new BreadthFirstScheduler<>(f), closed))) {
			// walk away after one answer: the block boundary, not exhaustion,
			// ends the solve
			assertThat(answers.findAny()).isPresent();
			assertThat(closed.get()).isFalse();
		}
		assertThat(closed.get()).isTrue();
	}
}
