package com.tgac.logic.disjunction;

// ABOUTME: The scope profiler over the scheduling race: where the resident
// ABOUTME: lane's steps go — settle, imposition, and the rest, by workforce.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.interpreter.OriginCapture;
import com.tgac.functional.fibers.interpreter.ScopeProfiler;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.disjunction.SchedulingBenchmarkTest.Strip;
import com.tgac.logic.goals.Goal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * A measuring instrument: the two resident-lane scheduling workloads run
 * under the {@link ScopeProfiler}, and the per-workforce breakdown lands in
 * {@code target/scheduling-profile.txt}. The one assertion is that the
 * instrument sees inside the kernel — a settle workforce appears by its
 * derived construction-site label.
 */
public class SchedulingProfileTest {

	private static final String[] SKIP = {
			"com.tgac.functional.",
			"com.tgac.logic.goals.Exhaustion"
	};

	@Test
	public void profilesTheResidentLane() throws IOException {
		IOException[] failed = new IOException[1];
		OriginCapture.within(() -> {
			try {
				profile();
			} catch (IOException e) {
				failed[0] = e;
			}
		});
		if (failed[0] != null) {
			throw failed[0];
		}
	}

	private void profile() throws IOException {
		List<String> report = new ArrayList<>();

		ScopeProfiler race = new ScopeProfiler(SKIP);
		List<Strip> ss = SchedulingBenchmarkTest.sameSpace(5);
		solveProfiled(SchedulingBenchmarkTest.schedule(
				ss, 5, SchedulingBenchmarkTest::nonOverlapResident), ss, race);
		report.add("=== the race at five (resident, one space)");
		report.addAll(race.report());

		ScopeProfiler shop = new ScopeProfiler(SKIP);
		List<Goal> chains = new ArrayList<>();
		List<Strip> ops = SchedulingBenchmarkTest.jobShop(3, 3, chains);
		Goal g = SchedulingBenchmarkTest.schedule(
				ops, 6, SchedulingBenchmarkTest::nonOverlapResident);
		for (Goal c : chains) {
			g = g.and(c);
		}
		solveProfiled(g, ops, shop);
		report.add("");
		report.add("=== the job shop at three by three (resident)");
		report.addAll(shop.report());

		Files.write(Paths.get("target/scheduling-profile.txt"), report);

		assertThat(race.counts().keySet().stream()
				.anyMatch(label -> label.contains("Propagation")))
				.isTrue();
	}

	private static void solveProfiled(Goal g, List<Strip> ss, ScopeProfiler profiler) {
		g.solve(SchedulingBenchmarkTest.starts(ss), profiler)
				.collect(Collectors.toList());
	}
}
