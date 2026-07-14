package com.tgac.logic.debug;

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.separate.Disequality.separate;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.aggregate.Aggregate;
import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Tracing seeds a DebugStore alongside the Table and the constraint stores.
 * These tests fix the invariant that seeding a tracer does not change a solve's
 * results, across tabling, constraints and findall, and that ports still fire.
 */
public class TraceCompositionTest {

	private static final class Rec implements Trace.Tracer {
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
		public void onRedo(String label, Package state) {
			ports.add("Redo " + label);
		}

		@Override
		public void onFail(String label, Package state) {
			ports.add("Fail " + label);
		}
	}

	private Goal edge(Unifiable<Integer> x, Unifiable<Integer> y) {
		return x.unifies(1).and(y.unifies(2))
				.or(x.unifies(2).and(y.unifies(3)))
				.or(x.unifies(3).and(y.unifies(4)));
	}

	private final Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> path =
			Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
					edge(x, y)
							.or(defer(() -> {
								Unifiable<Integer> z = lvar();
								return self.apply(Tuple.of(x, z)).and(edge(z, y));
							}))));

	@Test(timeout = 5000)
	public void tracingDoesNotChangeTabledResults() {
		Rec rec = new Rec();
		long traced = x14().solve(lvar(), rec).count();
		long untraced = x14().solve(lvar()).count();

		assertThat(traced).isEqualTo(untraced).isEqualTo(1);
		assertThat(rec.ports).isNotEmpty();
	}

	private Goal x14() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		return x.unifies(1).and(y.unifies(4)).and(path.apply(Tuple.of(x, y)));
	}

	@Test
	public void tracingDoesNotChangeConstraintResults() {
		Rec rec = new Rec();
		Unifiable<Integer> out = lvar();
		Goal g = separate(out, lval(2)).and(Constraints.unify(out, lval(3))).named("constrained");

		List<Integer> traced = g.solve(out, rec).map(Term::get).collect(Collectors.toList());

		assertThat(traced).containsExactly(3);
		assertThat(rec.ports).contains("Call constrained", "Exit constrained");
	}

	private Goal oneTwoThree(Unifiable<Integer> x) {
		return x.unifies(1).or(x.unifies(2)).or(x.unifies(3));
	}

	@Test
	public void tracingDoesNotChangeFindallResults() {
		Rec rec = new Rec();
		Unifiable<Integer> x = lvar();
		Unifiable<LList<Integer>> result = lvar();
		Goal g = Aggregate.findall(x, oneTwoThree(x).named("member"), result);

		List<Integer> traced = g.solve(result, rec).findFirst().get().get()
				.toValueStream().collect(Collectors.toList());

		assertThat(traced).containsExactlyInAnyOrder(1, 2, 3);
		// the sub-search is traced too: the DebugStore propagates into findall's inner solve
		assertThat(rec.ports).contains("Call member");
	}
}
