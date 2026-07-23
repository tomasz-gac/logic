package com.tgac.logic.separate;

// ABOUTME: Pins the Neq store's Projectable capability: records transcribe onto
// ABOUTME: positional slots as data — canonical across lineages, replayed by copy.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class NeqProjectionTest {

	private static LVar<?> varOf(Unifiable<?> u) {
		return (LVar<?>) u.asVar().get();
	}

	private static NeqConstraints store(NeqConstraint... records) {
		return NeqConstraints.of(LinkedHashSet.of(records));
	}

	private static NeqConstraint record(Object... varTermPairs) {
		HashMap<LVar<?>, Term<?>> separate = HashMap.empty();
		for (int i = 0; i < varTermPairs.length; i += 2) {
			separate = separate.put((LVar<?>) varTermPairs[i], (Term<?>) varTermPairs[i + 1]);
		}
		return NeqConstraint.of(separate);
	}

	@Test
	public void projectTranscribesRecordsPositionally() {
		Unifiable<Integer> x = lvar();
		NeqConstraints neq = store(record(varOf(x), lval(5)));

		NeqResidue residue = neq.project(Arrays.asList(varOf(x)), true);
		assertThat(residue.getRecords()).containsExactly(
				HashMap.<Integer, Term<?>> of(0, lval(5)));

		// a record referencing only OTHER vars is not knowledge about this list
		Unifiable<Integer> z = lvar();
		assertThat(neq.project(Arrays.asList(varOf(z)), true).getRecords()).isEmpty();
	}

	@Test
	public void transcriptionIsCanonicalAcrossLineages() {
		// x≠y from two unrelated var pairs projects to the SAME residue: the
		// transcribed record is data over slots, no lineage in it — so
		// independent same-shaped contexts compare equal and share entries
		Unifiable<Integer> x1 = lvar();
		Unifiable<Integer> y1 = lvar();
		Unifiable<Integer> x2 = lvar();
		Unifiable<Integer> y2 = lvar();

		NeqResidue first = store(record(varOf(x1), y1))
				.project(Arrays.asList(varOf(x1), varOf(y1)), true);
		NeqResidue second = store(record(varOf(x2), y2))
				.project(Arrays.asList(varOf(x2), varOf(y2)), true);

		assertThat(first).isEqualTo(second);
		assertThat(first.getRecords()).containsExactly(
				HashMap.<Integer, Term<?>> of(0, Hole.of(1)));
	}

	@Test
	public void anEscapingRecordDropsByPermissionOrThrows() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> w = lvar();
		NeqConstraints neq = store(
				record(varOf(x), lval(5)),
				record(varOf(x), w));

		NeqResidue widened = neq.project(Arrays.asList(varOf(x)), true);
		assertThat(widened.getRecords()).containsExactly(
				HashMap.<Integer, Term<?>> of(0, lval(5)));

		assertThatThrownBy(() -> neq.project(Arrays.asList(varOf(x)), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("escapes");
	}

	@Test
	public void projectingTheEmptyListIsTop() {
		Unifiable<Integer> x = lvar();
		NeqConstraints neq = store(record(varOf(x), lval(5)));
		assertThat(neq.project(Collections.<LVar<?>> emptyList(), true).getRecords()).isEmpty();
	}

	@Test
	public void leqIsRecordContainment() {
		Unifiable<Integer> x = lvar();
		LVar<?> v = varOf(x);
		NeqResidue narrow = store(record(v, lval(5)), record(v, lval(6)))
				.project(Arrays.asList(v), true);
		NeqResidue wide = store(record(v, lval(5)))
				.project(Arrays.asList(v), true);

		// more disequalities = smaller region = entails
		assertThat(narrow.leq(wide)).isTrue();
		assertThat(wide.leq(narrow)).isFalse();
		assertThat(narrow.leq(narrow)).isTrue();
	}

	@Test
	public void restateReimposesTheDisequality() {
		Unifiable<Integer> x = lvar();
		NeqResidue residue = store(record(varOf(x), lval(5)))
				.project(Arrays.asList(varOf(x)), true);

		Unifiable<Integer> fresh = lvar();
		assertThat(residue.restate(Arrays.<Unifiable<?>> asList(fresh))
				.and(Constraints.unify(fresh, lval(5)))
				.solve(fresh)
				.count()).isEqualTo(0);
		Unifiable<Integer> fresh2 = lvar();
		assertThat(residue.restate(Arrays.<Unifiable<?>> asList(fresh2))
				.and(Constraints.unify(fresh2, lval(6)))
				.solve(fresh2)
				.count()).isEqualTo(1);
	}

	@Test
	public void restateCouplesTheProjectedSlots() {
		// x≠y transcribed over [x,y] replays between the TARGET pair
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		NeqResidue residue = store(record(varOf(x), y))
				.project(Arrays.asList(varOf(x), varOf(y)), true);

		Unifiable<Integer> f = lvar();
		Unifiable<Integer> g = lvar();
		assertThat(residue.restate(Arrays.<Unifiable<?>> asList(f, g))
				.and(Constraints.unify(f, lval(3)))
				.and(Constraints.unify(g, lval(3)))
				.solve(f)
				.count()).isEqualTo(0);
		Unifiable<Integer> f2 = lvar();
		Unifiable<Integer> g2 = lvar();
		assertThat(residue.restate(Arrays.<Unifiable<?>> asList(f2, g2))
				.and(Constraints.unify(f2, lval(3)))
				.and(Constraints.unify(g2, lval(4)))
				.solve(f2)
				.count()).isEqualTo(1);
	}

	@Test
	public void aCompoundRecordCarriesWhenAllItsVarsAreSupplied() {
		// x ≠ (y, 5): expressible over [x, y], escapes [x] alone
		Unifiable<Integer> y = lvar();
		Unifiable<Object> x = lvar();
		NeqConstraints neq = store(record(varOf(x), lval(Tuple.of(y, lval(5)))));

		NeqResidue both = neq.project(Arrays.asList(varOf(x), varOf(y)), true);
		assertThat(both.getRecords()).hasSize(1);

		assertThat(neq.project(Arrays.asList(varOf(x)), true).getRecords()).isEmpty();
		assertThatThrownBy(() -> neq.project(Arrays.asList(varOf(x)), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("escapes");
	}
}
