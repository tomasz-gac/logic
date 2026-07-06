package com.tgac.logic.debug;

// ABOUTME: Box-model tracing of goals — Call when entered, Exit per solution, Fail when exhausted with none.
// ABOUTME: Wraps the goal's continuation, so success and failure are ordinary continuation events.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Package;
import java.util.concurrent.atomic.AtomicInteger;

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

		void onRedo(String label);

		void onFail(String label);
	}

	private Trace() {
	}

	public static Goal traced(String label, Goal goal, Tracer tracer) {
		return pkg -> k -> {
			tracer.onCall(label, pkg);
			AtomicInteger exits = new AtomicInteger(0);
			Fiber<Nothing> exploration = goal.apply(pkg).apply(answer -> {
				if (exits.getAndIncrement() > 0) {
					tracer.onRedo(label);
				}
				tracer.onExit(label, answer);
				return k.apply(answer);
			});
			return exploration.flatMap(done -> {
				if (exits.get() == 0) {
					tracer.onFail(label);
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
				out.accept("Call " + label);
			}

			@Override
			public void onExit(String label, Package state) {
				out.accept("Exit " + label);
			}

			@Override
			public void onRedo(String label) {
				out.accept("Redo " + label);
			}

			@Override
			public void onFail(String label) {
				out.accept("Fail " + label);
			}
		};
	}

	public static Tracer printing() {
		return printing(System.out::println);
	}
}
