package com.tgac.logic;

// ABOUTME: The goal-plane profiler receipt: a profiled solve splits root by
// ABOUTME: relation, labels derived from the .named() construction sites.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.interpreter.OriginCapture;
import com.tgac.functional.fibers.interpreter.ScopeProfiler;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Unifiable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

public class GoalProfileTest {

	@Test
	public void aProfiledSolveBucketsStepsByGoalName() throws IOException {
		OriginCapture.enable(true);
		try {
			ScopeProfiler profiler = new ScopeProfiler(
					"com.tgac.functional.",
					"com.tgac.logic.goals.Exhaustion");
			Unifiable<LList<Integer>> front = lvar();
			Unifiable<LList<Integer>> back = lvar();
			Unifiable<LList<Integer>> both = lvar();
			unify(both, LList.ofAll(1, 2, 3, 4, 5, 6))
					.and(Logic.appendo(front, back, both))
					.solve(both, profiler)
					.collect(Collectors.toList());

			Files.write(Paths.get("target/goal-profile.txt"), profiler.report());

			Map<String, Long> counts = profiler.counts();
			assertThat(counts.keySet().stream()
					.anyMatch(label -> label.contains("Logic.appendo")))
					.isTrue();
			assertThat(counts.values().stream().mapToLong(Long::longValue).sum())
					.isGreaterThan(100L);
		} finally {
			OriginCapture.enable(false);
		}
	}

	@Test
	public void anUnprofiledSolveStaysUntouched() {
		Unifiable<LList<Integer>> front = lvar();
		Unifiable<LList<Integer>> back = lvar();
		Unifiable<LList<Integer>> both = lvar();
		long answers = unify(both, LList.ofAll(1, 2, 3))
				.and(Logic.appendo(front, back, both))
				.solve(both)
				.count();
		assertThat(answers).isEqualTo(4);
	}
}
