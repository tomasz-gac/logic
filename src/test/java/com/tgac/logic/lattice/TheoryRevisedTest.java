package com.tgac.logic.lattice;

// ABOUTME: The reporting faces of Theory: metReporting/renamedReporting answer the
// ABOUTME: result plus exactly the changed atoms - what the doors read to skip or wake.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revised;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
import com.tgac.logic.nogoods.Nogood;
import com.tgac.logic.nogoods.NogoodConstraints;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Name;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.LinkedHashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class TheoryRevisedTest {

	private static Propagator<FlatConstraints> even(Unifiable<?> x) {
		return Propagator.of(FlatConstraints.empty(), "even",
				Collections.<Term<?>> singletonList(x), (watched, state) -> Verdict.keep());
	}

	private static Imposition<FlatSet, FlatConstraints> imp(Term<?> target, Object... values) {
		return new Imposition<>(FlatConstraints.class, target, FlatSet.of(values),
				FlatConstraints.empty());
	}

	private static Renaming to(Unifiable<?> from, Term<?> target) {
		return Renaming.of(Collections.<Name<?>, Term<?>> singletonMap(
				from.asVar().get(), target));
	}

	// ---- metReporting ----

	@Test
	public void metReportingAgreesWithMeetAndReportsTheMovedAtoms() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Theory<FlatConstraints> resident = LatticeFactorTest.valued((Term<?>) x, 1, 2)
				.with(even(y));
		Theory<FlatConstraints> incoming = LatticeFactorTest.valued((Term<?>) x, 2, 3)
				.meet(LatticeFactorTest.valued((Term<?>) z, 5));

		Revised<FlatConstraints> revised = resident.metReporting(incoming);

		assertThat(revised.getTheory()).isEqualTo(resident.meet(incoming));
		// the fused slot and the fresh insert moved; the untouched propagator did not
		assertThat(revised.getChanged())
				.containsExactlyInAnyOrder(imp((Term<?>) x, 2), imp((Term<?>) z, 5));
	}

	@Test
	public void coveredIncomingReportsNoChangeAndRidesByIdentity() {
		Unifiable<Integer> x = lvar();
		Theory<FlatConstraints> resident = LatticeFactorTest.valued((Term<?>) x, 1, 2);
		Theory<FlatConstraints> wider = LatticeFactorTest.valued((Term<?>) x, 1, 2, 3);

		Revised<FlatConstraints> revised = resident.metReporting(wider);

		assertThat(revised.getChanged().isEmpty()).isTrue();
		assertThat(revised.getTheory()).isSameAs(resident);
	}

	@Test
	public void aDominatedIncomingAtomIsAbsentFromChanged() {
		// cross-slot domination (nogood subsumption): the dominated incoming
		// atom is covered knowledge - the report must not wake anyone on it
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Nogood xApart = Nogood.of(Posting.bind(x, lval(1)));
		Nogood notBoth = Nogood.of(Posting.all(
				Posting.bind(x, lval(1)), Posting.bind(y, lval(2))));
		Theory<NogoodConstraints> resident = Theory.of(
				LinkedHashSet.of(xApart));

		Revised<NogoodConstraints> revised = resident.metReporting(
				Theory.of(LinkedHashSet.of(notBoth)));

		assertThat(revised.getChanged().isEmpty()).isTrue();
		assertThat(revised.getTheory()).isSameAs(resident);
	}

	@Test
	public void aDominatingIncomingAtomReportsItselfAndDropsTheResident() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Nogood xApart = Nogood.of(Posting.bind(x, lval(1)));
		Nogood notBoth = Nogood.of(Posting.all(
				Posting.bind(x, lval(1)), Posting.bind(y, lval(2))));
		Theory<NogoodConstraints> resident = Theory.of(
				LinkedHashSet.of(notBoth));

		Revised<NogoodConstraints> revised = resident.metReporting(
				Theory.of(LinkedHashSet.of(xApart)));

		assertThat(revised.getChanged()).containsExactly(xApart);
		assertThat(revised.getTheory().atoms()).containsExactly(xApart);
	}

	// ---- renamedReporting ----

	@Test
	public void renamedReportingAgreesWithRenameAndReportsTheRewrittenAtoms() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Theory<FlatConstraints> theory = LatticeFactorTest.valued((Term<?>) x, 1, 2)
				.meet(LatticeFactorTest.valued((Term<?>) y, 3, 4))
				.with(even(x));

		Revised<FlatConstraints> revised = theory.renamedReporting(to(x, (Term<?>) z)).ground();

		assertThat(revised.getTheory()).isEqualTo(theory.rename(to(x, (Term<?>) z)).ground());
		assertThat(revised.getChanged())
				.containsExactlyInAnyOrder(imp((Term<?>) z, 1, 2), even(z));
	}

	@Test
	public void aRenamingOutsideTheTheoryIsIdentity() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> w = lvar();
		Unifiable<Integer> z = lvar();
		Theory<FlatConstraints> theory = LatticeFactorTest.valued((Term<?>) x, 1, 2);

		Revised<FlatConstraints> revised = theory.renamedReporting(to(w, (Term<?>) z)).ground();

		assertThat(revised.getChanged().isEmpty()).isTrue();
		assertThat(revised.getTheory()).isSameAs(theory);
	}

	@Test
	public void aRenameCollisionFusesAndReportsTheFusion() {
		// x → y where y holds its own entry: the crossing re-digests and the
		// fusion is the changed atom - aliasing as slot re-digestion
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Theory<FlatConstraints> theory = LatticeFactorTest.valued((Term<?>) x, 1, 2)
				.meet(LatticeFactorTest.valued((Term<?>) y, 2, 3));

		Revised<FlatConstraints> revised = theory.renamedReporting(to(x, (Term<?>) y)).ground();

		assertThat(revised.getChanged()).containsExactly(imp((Term<?>) y, 2));
		assertThat(revised.getTheory().atoms()).containsExactly(imp((Term<?>) y, 2));
	}

	@Test
	public void aRenameThatDeduplicatesAgainstAResidentReportsNothingNew() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Theory<FlatConstraints> theory = LatticeFactorTest.valued((Term<?>) x, 1, 2)
				.meet(LatticeFactorTest.valued((Term<?>) y, 1, 2));

		Revised<FlatConstraints> revised = theory.renamedReporting(to(x, (Term<?>) y)).ground();

		// x's entry renamed onto y's identical entry: same knowledge, no wake
		assertThat(revised.getChanged().isEmpty()).isTrue();
		assertThat(revised.getTheory().atoms()).containsExactly(imp((Term<?>) y, 1, 2));
	}

	@Test
	public void renamingToAValueKeepsTheValKeyedEntry() {
		// the faithful crossing: a bound name's entry re-keys to its value
		// and stays for the consumer's verification
		Unifiable<Integer> x = lvar();
		Theory<FlatConstraints> theory = LatticeFactorTest.valued((Term<?>) x, 1, 2);

		Revised<FlatConstraints> revised = theory.renamedReporting(to(x, lval(1))).ground();

		assertThat(revised.getChanged()).containsExactly(imp(lval(1), 1, 2));
	}

	// ---- the watchers index ----

	@Test
	public void theWatchersIndexMatchesABruteScanThroughEveryDoor() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Theory<FlatConstraints> theory = LatticeFactorTest.valued((Term<?>) x, 1, 2)
				.with(even(x))
				.meet(LatticeFactorTest.valued((Term<?>) y, 3, 4))
				.with(even(z));
		assertIndexed(theory);

		Theory<FlatConstraints> shrunk = theory.without(even(z));
		assertIndexed(shrunk);

		Theory<FlatConstraints> renamed = theory.renamedReporting(to(x, (Term<?>) z))
				.ground().getTheory();
		assertIndexed(renamed);
	}

	private static <F extends Factor<F>> void assertIndexed(Theory<F> theory) {
		Map<Name<?>, Set<Atom<F>>> expected = new LinkedHashMap<>();
		for (Atom<F> atom : theory.atoms()) {
			for (Term<?> term : atom.watched()) {
				MiniKanren.namesIn(term).forEach(name ->
						expected.computeIfAbsent(name, n -> new java.util.LinkedHashSet<>()).add(atom));
			}
		}
		for (Map.Entry<Name<?>, Set<Atom<F>>> entry : expected.entrySet()) {
			assertThat(theory.watchers(entry.getKey()).toJavaSet())
					.describedAs("watchers of %s", entry.getKey())
					.isEqualTo(entry.getValue());
		}
	}
}
