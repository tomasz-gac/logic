package org.clauseway.logic;

// ABOUTME: The goal-plane profiler receipt: a profiled solve splits root by
// ABOUTME: relation, labels rendered from the goals' own .named() labels.

import static org.clauseway.logic.constraints.Constraints.unify;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.fibers.interpreter.OriginCapture;
import org.clauseway.functional.fibers.interpreter.ScopeProfiler;
import org.clauseway.logic.goals.Logic;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.unification.terms.Unifiable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

public class GoalProfileTest {

	@Test
	public void aProfiledSolveBucketsStepsByGoalName() throws IOException {
		OriginCapture.within(() -> {
			ScopeProfiler profiler = new ScopeProfiler(
					"org.clauseway.functional.",
					"org.clauseway.logic.goals.Exhaustion");
			Unifiable<LList<Integer>> front = lvar();
			Unifiable<LList<Integer>> back = lvar();
			Unifiable<LList<Integer>> both = lvar();
			unify(both, LList.ofAll(1, 2, 3, 4, 5, 6))
					.and(Logic.appendo(front, back, both))
					.solve(both, profiler)
					.collect(Collectors.toList());

			try {
				Files.write(Paths.get("target/goal-profile.txt"), profiler.report());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			Map<String, Long> counts = profiler.counts();
			// with origin capture off, goal labels come from the labels the goals
			// already carry, rendered against the empty package — appendo's shows
			// its ++ shape
			assertThat(counts.keySet().stream()
					.anyMatch(label -> label.contains("++")))
					.isTrue();
			assertThat(counts.values().stream().mapToLong(Long::longValue).sum())
					.isGreaterThan(100L);
		});
	}

	@Test
	public void originsRefineBucketsByMintSite() throws IOException {
		Map<String, Long>[] counts = new Map[1];
		OriginCapture.within(() -> {
			ScopeProfiler profiler = new ScopeProfiler();
			Unifiable<LList<Integer>> front = lvar();
			Unifiable<LList<Integer>> back = lvar();
			Unifiable<LList<Integer>> both = lvar();
			unify(both, LList.ofAll(1, 2, 3, 4, 5, 6))
					.and(Logic.appendo(front, back, both))
					.solve(both, profiler)
					.collect(Collectors.toList());
			counts[0] = profiler.counts();
			try {
				Files.write(Paths.get("target/goal-profile-refined.txt"), profiler.report());
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
		// the plain success bucket splits by who minted it — enforce's
		// ground floor shows its site as a chain segment
		assertThat(counts[0].keySet().stream()
				.anyMatch(label -> label.contains("success @ ")
						&& label.contains("Constraints.enforce")))
				.isTrue();
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
