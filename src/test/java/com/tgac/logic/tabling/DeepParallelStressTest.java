package com.tgac.logic.tabling;

// ABOUTME: Parallel completion stress: a deep tabled chain looped in-JVM, where a
// ABOUTME: premature seal loses answers. Caught the group-seal admission race.

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.unification.LVar.lvar;
import static org.junit.Assert.fail;

import com.tgac.functional.fibers.schedulers.ForkJoinScheduler;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * A depth-8 right-recursive tabled chain under the fork/join scheduler, looped
 * many times in one JVM: each level's answers propagate through a reader chain,
 * so any window where a seal races a respawn loses the deepest answers. On
 * failure the per-entry seal state is dumped — a sealed-short entry means the
 * completion machinery sealed under a running reader.
 */
public class DeepParallelStressTest {

	private static final int DEPTH = 9;

	private static Goal parent(Unifiable<Integer> x, Unifiable<Integer> y) {
		Goal g = x.unifies(1).and(y.unifies(2));
		for (int i = 2; i < DEPTH; i++) {
			g = g.or(x.unifies(i).and(y.unifies(i + 1)));
		}
		return g;
	}

	@Test(timeout = 120000)
	public void deepChainKeepsAllAnswersUnderParallelScheduler() {
		int iters = Integer.getInteger("tabling.iters", 300);
		for (int iter = 0; iter < iters; iter++) {
			Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> anc =
					Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
							parent(x, y).or(defer(() -> {
								Unifiable<Integer> z = lvar();
								return parent(x, z).and(self.apply(Tuple.of(z, y)));
							}))));
			Table table = Table.empty();
			Unifiable<Integer> x = lvar();
			Unifiable<Integer> y = lvar();

			List<Integer> descendants = x.unifies(1).and(anc.apply(Tuple.of(x, y)))
					.solveFrom(Package.empty().withStore(table), y, ForkJoinScheduler::new)
					.map(Term::get)
					.sorted()
					.collect(Collectors.toList());

			if (descendants.size() != DEPTH - 1) {
				StringBuilder sb = new StringBuilder("iter " + iter + ": got " + descendants
						+ " (" + descendants.size() + " of " + (DEPTH - 1) + ")\n");
				for (TableEntry<Object> e : table.entries()) {
					sb.append("  ").append(e.getCall())
							.append(" sealed=").append(e.isComplete())
							.append(" answers=").append(e.getAnswerCount())
							.append(" parked=").append(e.registrationCount())
							.append('\n');
				}
				fail(sb.toString());
			}
		}
	}
}
