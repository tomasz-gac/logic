package com.tgac.logic.debug;

// ABOUTME: Box-model tracing of goals — Call when entered, Exit per solution, Fail when exhausted with none.
// ABOUTME: Wraps the goal's continuation, so success and failure are ordinary continuation events.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Package;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Wraps a goal to report the classic logic-debugger ports:
 * <ul>
 *   <li><b>Call</b> — the goal is entered;</li>
 *   <li><b>Exit</b> — the goal produced a solution (fires once per solution);</li>
 *   <li><b>Redo</b> — the goal was re-entered to produce a further solution
 *       (fires before every Exit after the first);</li>
 *   <li><b>Fail</b> — the goal was exhausted having produced no solution.</li>
 * </ul>
 *
 * Success is a call to the continuation, so Call and Exit are direct events;
 * Redo is the second and later Exit made explicit; Fail is "the exploration
 * finished without any Exit", detected by sequencing a check after the goal's
 * fiber completes. A goal that produced solutions and then exhausts closes
 * without a Fail — its Exit/Redo pairs already tell that story. A goal that
 * never terminates reports Call and neither Exit nor Fail, which is the honest
 * reading.
 *
 * On a parallel scheduler the ports of concurrently-explored goals interleave;
 * trace on the default sequential scheduler for an ordered reading.
 */
public final class Trace {

	public interface Tracer {
		void onCall(String label, Package state);

		void onExit(String label, Package state);

		void onRedo(String label, Package state);

		void onFail(String label, Package state);
	}

	private Trace() {
	}

	public static Goal traced(String label, Goal goal, Tracer tracer) {
		return pkg -> tracedCont(p -> label, goal, tracer, pkg, Function.identity());
	}

	/**
	 * Runs {@code goal} on the {@code entered} package while reporting ports,
	 * applying {@code restore} to each solution before handing it downstream —
	 * the seam through which a caller restores its own spine after a box exits.
	 * The label is rendered against the state at each port, so Call shows the
	 * arguments as entered and Exit shows them walked to their solution bindings.
	 */
	public static Cont<Package, Nothing> tracedCont(Function<Package, String> label, Goal goal, Tracer tracer,
			Package entered, Function<Package, Package> restore) {
		return k -> {
			tracer.onCall(label.apply(entered), entered);
			AtomicInteger exits = new AtomicInteger(0);
			Fiber<Nothing> exploration = goal.apply(entered).apply(answer -> {
				if (exits.getAndIncrement() > 0) {
					tracer.onRedo(label.apply(answer), answer);
				}
				tracer.onExit(label.apply(answer), answer);
				return k.apply(restore.apply(answer));
			});
			return exploration.flatMap(done -> {
				if (exits.get() == 0) {
					tracer.onFail(label.apply(entered), entered);
				}
				return Fiber.done(done);
			});
		};
	}

	/**
	 * A tracer that prints each port to the given sink.
	 */
	public static Tracer printing(java.util.function.Consumer<String> out) {
		return new Tracer() {
			@Override
			public void onCall(String label, Package state) {
				out.accept(line("Call", label, state));
			}

			@Override
			public void onExit(String label, Package state) {
				out.accept(line("Exit", label, state));
			}

			@Override
			public void onRedo(String label, Package state) {
				out.accept(line("Redo", label, state));
			}

			@Override
			public void onFail(String label, Package state) {
				out.accept(line("Fail", label, state));
			}
		};
	}

	private static String line(String port, String label, Package state) {
		int depth = DebugStore.from(state).map(DebugStore::depth).getOrElse(0);
		StringBuilder indent = new StringBuilder();
		for (int i = 1; i < depth; i++) {
			indent.append("  ");
		}
		return indent + port + " " + label;
	}

	public static Tracer printing() {
		return printing(System.out::println);
	}
}
