package com.tgac.logic.disjunction;

// ABOUTME: The two scheduling lanes profiled side by side: what residence buys
// ABOUTME: and what it costs, bucket by bucket, against conde's forking.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.interpreter.OriginCapture;
import com.tgac.functional.fibers.interpreter.ScopeProfiler;
import com.tgac.logic.disjunction.SchedulingBenchmarkTest.Strip;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * The race at five run through both lanes under the two-plane profiler:
 * residence (one disjunct per pair, verification through the store) against
 * conde forking (2021's only option). Answer sets must agree — the lanes are
 * denotationally equal — and the profiles land side by side in
 * {@code target/lane-comparison-profile.txt}. The structural assertion: only
 * the store lane pays imposed-trial workforces; conde pays in forks instead.
 */
public class LaneComparisonProfileTest {

	private static final String[] SKIP = {
			"com.tgac.functional.",
			"com.tgac.logic.goals.Exhaustion"
	};

	@Test
	public void profilesResidentAgainstCondeAtFive() throws IOException {
		ScopeProfiler resident = new ScopeProfiler(SKIP);
		ScopeProfiler conde = new ScopeProfiler(SKIP);
		List<List<String>> answers = new ArrayList<>();
		OriginCapture.within(() -> {
			answers.add(solveProfiled(SchedulingBenchmarkTest::nonOverlapResident, resident));
			answers.add(solveProfiled(SchedulingBenchmarkTest::nonOverlapConde, conde));
		});

		List<String> report = new ArrayList<>();
		report.add("=== resident at five (total " + total(resident) + ")");
		report.addAll(resident.report());
		report.add("");
		report.add("=== conde at five (total " + total(conde) + ")");
		report.addAll(conde.report());
		Files.write(Paths.get("target/lane-comparison-profile.txt"), report);

		assertThat(answers.get(1)).isEqualTo(answers.get(0));
		assertThat(resident.counts().keySet().stream()
				.anyMatch(label -> label.contains("Trial.imposed")))
				.isTrue();
		assertThat(conde.counts().keySet().stream()
				.noneMatch(label -> label.contains("Trial.imposed")))
				.isTrue();
	}

	private static List<String> solveProfiled(SchedulingBenchmarkTest.Lane lane,
			ScopeProfiler profiler) {
		List<Strip> ss = SchedulingBenchmarkTest.sameSpace(5);
		return SchedulingBenchmarkTest.schedule(ss, 5, lane)
				.solve(SchedulingBenchmarkTest.starts(ss), profiler)
				.map(Object::toString)
				.distinct()
				.sorted()
				.collect(Collectors.toList());
	}

	private static long total(ScopeProfiler profiler) {
		return profiler.counts().values().stream().mapToLong(Long::longValue).sum();
	}
}
