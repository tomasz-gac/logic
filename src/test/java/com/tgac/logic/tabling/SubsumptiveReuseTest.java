package com.tgac.logic.tabling;

// ABOUTME: Pins subsumptive reuse: a SEALED general entry serves bound instance
// ABOUTME: calls as a read-only relation — no new master, answers filtered.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.stream.Collectors;
import org.junit.Test;

public class SubsumptiveReuseTest {

	private static Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> pairs() {
		return Tabling.define(t -> t.apply((x, y) ->
				unify(x, lval(1)).and(unify(y, lval(10)))
						.or(unify(x, lval(2)).and(unify(y, lval(20))))));
	}

	private static Reified<?> pattern(Object args) {
		return MiniKanren.reify(Substitutions.empty(), lval(args)).get();
	}

	// ---- the matcher ----

	@Test
	public void generalPatternSubsumesItsInstances() {
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> rel = pairs();
		Call general = Call.of(rel, pattern(Tuple.of(lvar(), lvar())));
		Call bound = Call.of(rel, pattern(Tuple.of(lval(1), lvar())));
		Call ground = Call.of(rel, pattern(Tuple.of(lval(1), lval(10))));

		assertThat(general.subsumes(bound)).isTrue();
		assertThat(general.subsumes(ground)).isTrue();
		assertThat(general.subsumes(general)).isTrue();
		// direction: an instance never subsumes its generalization
		assertThat(bound.subsumes(general)).isFalse();
		assertThat(ground.subsumes(bound)).isFalse();
	}

	@Test
	public void repeatedHolesDemandEqualSubterms() {
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> rel = pairs();
		Unifiable<Integer> shared = lvar();
		Call diagonal = Call.of(rel, pattern(Tuple.of(shared, shared)));

		assertThat(diagonal.subsumes(Call.of(rel, pattern(Tuple.of(lval(5), lval(5)))))).isTrue();
		assertThat(diagonal.subsumes(Call.of(rel, pattern(Tuple.of(lval(5), lval(6)))))).isFalse();
	}

	@Test
	public void distinctRelationsNeverSubsume() {
		Call a = Call.of(pairs(), pattern(Tuple.of(lvar(), lvar())));
		Call b = Call.of(pairs(), pattern(Tuple.of(lvar(), lvar())));
		assertThat(a.subsumes(b)).isFalse();
	}

	// ---- the reuse flow ----

	@Test
	public void sealedGeneralServesBoundCallsWithoutANewMaster() {
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> rel = pairs();
		Package p = Package.empty().withStore(Table.empty());

		// run the general call to exhaustion: Tier 1 seals it
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		assertThat(rel.apply(Tuple.of(x, y)).solveFrom(p, y, BreadthFirstScheduler::new).count())
				.isEqualTo(2);
		assertThat(p.getStore(Table.class).entries()).hasSize(1);

		// the bound call reads the sealed entry: filtered answers, still one entry
		Unifiable<Integer> out = lvar();
		assertThat(rel.apply(Tuple.of(lval(1), out))
				.solveFrom(p, out, BreadthFirstScheduler::new)
				.map(Object::toString)
				.collect(Collectors.toList()))
				.containsExactly("{10}");
		assertThat(p.getStore(Table.class).entries()).hasSize(1);
	}

	@Test
	public void unsealedGeneralDoesNotServe() {
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> rel = pairs();
		Package p = Package.empty().withStore(Table.empty());

		// a general entry exists but was never mastered: unsealed forever
		Call generalKey = Call.of(rel, pattern(Tuple.of(lvar(), lvar())));
		p.getStore(Table.class).getOrCreateEntry(generalKey);

		// the bound call must mint its own master and compute fresh
		Unifiable<Integer> out = lvar();
		assertThat(rel.apply(Tuple.of(lval(2), out)).solveFrom(p, out, BreadthFirstScheduler::new).count())
				.isEqualTo(1);
		assertThat(p.getStore(Table.class).entries()).hasSize(2);
	}

	@Test
	public void sealedSpecificDoesNotServeAGeneralCall() {
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> rel = pairs();
		Package p = Package.empty().withStore(Table.empty());

		// seal the bound variant first
		Unifiable<Integer> out1 = lvar();
		rel.apply(Tuple.of(lval(1), out1)).solveFrom(p, out1, BreadthFirstScheduler::new).count();
		assertThat(p.getStore(Table.class).entries()).allMatch(TableEntry::isComplete);

		// the general call has more answers than the sealed instance holds
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		assertThat(rel.apply(Tuple.of(x, y)).solveFrom(p, y, BreadthFirstScheduler::new).count())
				.isEqualTo(2);
		assertThat(p.getStore(Table.class).entries()).hasSize(2);
	}
}
