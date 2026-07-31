package com.tgac.logic.tabling;

// ABOUTME: Parked suspensions at a tabled-call boundary refuse loudly: the call key
// ABOUTME: cannot see them and an answer cannot carry them, so silence would be unsound.

import static com.tgac.logic.projection.Projection.project;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * The tabling–suspension boundary. A suspension parked in the caller is
 * caller-private knowledge the call key cannot represent, and the master's
 * body package must not inherit it — becoming master under one refuses. A
 * suspension parked inside a tabled body is a condition the answer still
 * owes; an answer may not leave while it pends. Consuming an existing entry
 * under caller-parked suspensions stays legal: the caller's own copy ripens
 * through its chokepoint at consumption.
 */
public class SuspensionGuardTest {

	@Test
	public void becomingMasterUnderAParkedSuspensionRefuses() {
		Tabled<Unifiable<Integer>> rel = Tabling.define(x ->
				x.unifies(1).or(x.unifies(2)));
		Unifiable<Integer> pending = lvar();
		Unifiable<Integer> out = lvar();

		Goal query = project(pending, v -> Goal.success())
				.and(rel.apply(out));

		assertThatThrownBy(() -> query.solve(out).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("parked suspensions");
	}

	@Test
	public void anAnswerMayNotLeaveATabledBodyWhileASuspensionPends() {
		Tabled<Unifiable<Integer>> rel = Tabling.define(x ->
				Goal.defer(() -> {
					Unifiable<Integer> local = lvar();
					return x.unifies(1).and(project(local, v -> Goal.success()));
				}));
		Unifiable<Integer> out = lvar();

		assertThatThrownBy(() -> rel.apply(out).solve(out).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("suspension");
	}

	@Test
	public void consumingAnExistingEntryUnderAParkedSuspensionIsLegal() {
		Tabled<Unifiable<Integer>> rel = Tabling.define(x ->
				x.unifies(1).or(x.unifies(2)));
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> pending = lvar();

		// the first call becomes master suspension-free; the second call only
		// consumes the entry, so the parked suspension between them is legal —
		// it ripens caller-side when pending grounds, before reification
		Goal query = rel.apply(a)
				.and(project(pending, v -> Goal.success()))
				.and(rel.apply(b))
				.and(pending.unifies(7));

		List<String> pairs = query.solve(lval(Tuple.of(a, b)))
				.map(Object::toString)
				.collect(Collectors.toList());
		assertThat(pairs).hasSize(4);
	}
}
