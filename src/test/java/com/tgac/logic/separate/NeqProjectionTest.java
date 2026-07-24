package com.tgac.logic.separate;

// ABOUTME: Pins the Neq store's single-sorted boundary algebra: records as data
// ABOUTME: over names, lossless split, canonical projection, renamed replay.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
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

	private static NeqConstraint record(Object... nameTermPairs) {
		HashMap<Term<?>, Term<?>> separate = HashMap.empty();
		for (int i = 0; i < nameTermPairs.length; i += 2) {
			separate = separate.put((Term<?>) nameTermPairs[i], (Term<?>) nameTermPairs[i + 1]);
		}
		return NeqConstraint.of(separate);
	}

	@Test
	public void projectTranscribesRecordsPositionally() {
		Unifiable<Integer> x = lvar();
		NeqConstraints neq = store(record(varOf(x), lval(5)));

		NeqConstraints keyed = neq.project(Arrays.asList(varOf(x)));
		assertThat(keyed.getConstraints()).containsExactly(
				record(Hole.of(0), lval(5)));

		// a record referencing only OTHER vars is not knowledge about this list
		Unifiable<Integer> z = lvar();
		assertThat(neq.project(Arrays.asList(varOf(z))).isEmpty()).isTrue();
	}

	@Test
	public void transcriptionIsCanonicalAcrossLineages() {
		// x≠y from two unrelated var pairs projects to the SAME key: records
		// are data over names, no lineage in them — independent same-shaped
		// contexts compare equal and share entries
		Unifiable<Integer> x1 = lvar();
		Unifiable<Integer> y1 = lvar();
		Unifiable<Integer> x2 = lvar();
		Unifiable<Integer> y2 = lvar();

		NeqConstraints first = store(record(varOf(x1), y1))
				.project(Arrays.asList(varOf(x1), varOf(y1)));
		NeqConstraints second = store(record(varOf(x2), y2))
				.project(Arrays.asList(varOf(x2), varOf(y2)));

		assertThat(first).isEqualTo(second);
		assertThat(first.getConstraints()).containsExactly(
				record(Hole.of(0), Hole.of(1)));
	}

	@Test
	public void splitFactorsRecordsLosslessly() {
		// a record touching an unsupplied var lands in the remainder;
		// _1 ∧ _2 = this — the caller decides the remainder's fate
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> w = lvar();
		NeqConstraints neq = store(
				record(varOf(x), lval(5)),
				record(varOf(x), w));

		Tuple2<NeqConstraints, NeqConstraints> halves = neq.split(Arrays.asList(varOf(x)));
		assertThat(halves._1.getConstraints()).containsExactly(record(varOf(x), lval(5)));
		assertThat(halves._2.getConstraints()).containsExactly(record(varOf(x), w));
		assertThat(halves._1.meet(halves._2)).isEqualTo(neq);
	}

	@Test
	public void projectingTheEmptyListIsTop() {
		Unifiable<Integer> x = lvar();
		NeqConstraints neq = store(record(varOf(x), lval(5)));
		assertThat(neq.project(Collections.<LVar<?>> emptyList()).isEmpty()).isTrue();
	}

	@Test
	public void leqIsRecordContainment() {
		Unifiable<Integer> x = lvar();
		LVar<?> v = varOf(x);
		NeqConstraints narrow = store(record(v, lval(5)), record(v, lval(6)));
		NeqConstraints wide = store(record(v, lval(5)));

		// more disequalities = smaller region = entails
		assertThat(narrow.leq(wide)).isTrue();
		assertThat(wide.leq(narrow)).isFalse();
		assertThat(narrow.leq(narrow)).isTrue();
	}

	@Test
	public void restateReimposesTheDisequality() {
		Unifiable<Integer> x = lvar();
		NeqConstraints keyed = store(record(varOf(x), lval(5)))
				.project(Arrays.asList(varOf(x)));

		Unifiable<Integer> fresh = lvar();
		assertThat(keyed.rename(Renaming.ofSlots(Arrays.<Term<?>> asList(fresh))).stated()
				.and(Constraints.unify(fresh, lval(5)))
				.solve(fresh)
				.count()).isEqualTo(0);
		Unifiable<Integer> fresh2 = lvar();
		assertThat(keyed.rename(Renaming.ofSlots(Arrays.<Term<?>> asList(fresh2))).stated()
				.and(Constraints.unify(fresh2, lval(6)))
				.solve(fresh2)
				.count()).isEqualTo(1);
	}

	@Test
	public void restateCouplesTheProjectedSlots() {
		// x≠y canonicalized over [x,y] replays between the TARGET pair
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		NeqConstraints keyed = store(record(varOf(x), y))
				.project(Arrays.asList(varOf(x), varOf(y)));

		Unifiable<Integer> f = lvar();
		Unifiable<Integer> g = lvar();
		assertThat(keyed.rename(Renaming.ofSlots(Arrays.<Term<?>> asList(f, g))).stated()
				.and(Constraints.unify(f, lval(3)))
				.and(Constraints.unify(g, lval(3)))
				.solve(f)
				.count()).isEqualTo(0);
		Unifiable<Integer> f2 = lvar();
		Unifiable<Integer> g2 = lvar();
		assertThat(keyed.rename(Renaming.ofSlots(Arrays.<Term<?>> asList(f2, g2))).stated()
				.and(Constraints.unify(f2, lval(3)))
				.and(Constraints.unify(g2, lval(4)))
				.solve(f2)
				.count()).isEqualTo(1);
	}

	@Test
	public void aCompoundRecordSplitsByItsFullSupport() {
		// x ≠ (y, 5): covered over [x, y], remainder over [x] alone
		Unifiable<Integer> y = lvar();
		Unifiable<Object> x = lvar();
		NeqConstraints neq = store(record(varOf(x), lval(Tuple.of(y, lval(5)))));

		assertThat(neq.split(Arrays.asList(varOf(x), varOf(y)))._1.getConstraints()).hasSize(1);
		assertThat(neq.split(Arrays.asList(varOf(x)))._1.isEmpty()).isTrue();
		assertThat(neq.split(Arrays.asList(varOf(x)))._2.getConstraints()).hasSize(1);
	}

	@Test
	public void renamingRecordsFollowsTheSharedRenaming() {
		// a record's vars translate through the given Renaming — seeded vars
		// to their targets, unseeded vars to minted fresh ones shared with
		// any other store applying the same renaming
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> w = lvar();
		Unifiable<Integer> a = lvar();
		NeqConstraints neq = store(record(varOf(x), w));

		java.util.Map<LVar<?>, Term<?>> seed = new java.util.HashMap<>();
		seed.put(varOf(x), a);
		Renaming renaming = Renaming.into(seed);

		NeqConstraints renamed = neq.rename(renaming);
		NeqConstraint only = renamed.getConstraints().head();
		assertThat(only.getSeparate().containsKey(a)).isTrue();
		Term<?> mintedW = only.getSeparate().get(a).get();
		assertThat(mintedW.asVar().isDefined()).isTrue();
		assertThat(mintedW).isNotEqualTo(w);
		assertThat(renaming.apply(w)).isSameAs(mintedW);
	}

	@Test
	public void aStatedStoreReimposesItsRecords() {
		// stated() re-verifies each record against the target state like a
		// freshly posted disequality
		Unifiable<Integer> x = lvar();
		NeqConstraints neq = store(record(varOf(x), lval(5)));

		assertThat(neq.stated()
				.and(Constraints.unify(x, lval(5)))
				.solve(x)
				.count()).isEqualTo(0);
		assertThat(neq.stated()
				.and(Constraints.unify(x, lval(6)))
				.solve(x)
				.count()).isEqualTo(1);
	}
}
