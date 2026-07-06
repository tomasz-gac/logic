package com.tgac.logic.debug;

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.debug.Trace.Tracer;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TraceTest {

	private static final class Recorder implements Tracer {
		final List<String> ports = new ArrayList<>();

		@Override
		public void onCall(String label, Package state) {
			ports.add("Call " + label);
		}

		@Override
		public void onExit(String label, Package state) {
			ports.add("Exit " + label);
		}

		@Override
		public void onFail(String label) {
			ports.add("Fail " + label);
		}
	}

	@Test
	public void shouldReportCallAndOneExitPerSolution() {
		Recorder recorder = new Recorder();
		Unifiable<Integer> x = lvar();

		Goal g = Trace.traced("g", x.unifies(1).or(x.unifies(2)), recorder);
		long count = g.solve(x).count();

		assertThat(count).isEqualTo(2);
		assertThat(recorder.ports).containsExactly("Call g", "Exit g", "Exit g");
	}

	@Test
	public void shouldReportFailWhenExhaustedWithNoSolution() {
		Recorder recorder = new Recorder();
		Unifiable<Integer> x = lvar();

		// contradictory: x cannot be both 1 and 2
		Goal g = Trace.traced("g", x.unifies(1).and(x.unifies(2)), recorder);
		long count = g.solve(x).count();

		assertThat(count).isEqualTo(0);
		assertThat(recorder.ports).containsExactly("Call g", "Fail g");
	}

	@Test
	public void shouldNestPorts() {
		Recorder recorder = new Recorder();
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal inner = Trace.traced("inner", y.unifies(10), recorder);
		Goal outer = Trace.traced("outer", x.unifies(1).and(inner), recorder);
		outer.solve(x).count();

		// outer Call, inner runs and exits, outer exits
		assertThat(recorder.ports).containsExactly("Call outer", "Call inner", "Exit inner", "Exit outer");
	}
}
