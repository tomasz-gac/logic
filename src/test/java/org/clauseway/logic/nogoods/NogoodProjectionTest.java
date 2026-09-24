package org.clauseway.logic.nogoods;

// ABOUTME: NogoodConstraints' boundary faces: split keeps wholly-named nogoods, rename
// ABOUTME: transcribes literals wrapped, and nogoods cross tabled calls whole.

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.constraints.store.Renaming;
import org.clauseway.logic.constraints.store.Theory;
import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.tabling.Tabled;
import org.clauseway.logic.tabling.Tabling;
import org.clauseway.logic.unification.terms.Any;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.logic.unification.terms.Name;
import io.vavr.collection.LinkedHashSet;
import java.util.Collections;
import java.util.stream.Collectors;
import org.clauseway.logic.unification.terms.LVal;
import org.junit.Test;

public class NogoodProjectionTest {

	private static Theory<NogoodConstraints> store(Nogood... nogoods) {
		return Theory.of(LinkedHashSet.of(nogoods));
	}

	private static Nogood over(org.clauseway.logic.constraints.Posting... literals) {
		return Nogood.of(literals.length == 1 ?
				literals[0] :
				org.clauseway.logic.constraints.Posting.all(literals));
	}

	private static Renaming toHole(Unifiable<?> var, int slot) {
		return Renaming.of(Collections.<Name<?>, Term<?>> singletonMap(
				var.asVar().get(), Any.of(slot)));
	}

	@Test
	public void splitKeepsWhollyNamedNogoodsAndMeetRestores() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Nogood aboutX = over(x.unifies(3));
		Nogood aboutXY = over(x.unifies(1), y.unifies(2));
		Theory<NogoodConstraints> whole = store(aboutX, aboutXY);

		io.vavr.Tuple2<Theory<NogoodConstraints>, Theory<NogoodConstraints>> parts = whole.split(
				Collections.<LVar<?>> singletonList((LVar<?>) x.asVar().get()));

		assertThat(parts._1.atoms()).containsExactly(aboutX);
		assertThat(parts._2.atoms()).containsExactly(aboutXY);
		assertThat(parts._1.meet(parts._2)).isEqualTo(whole);
	}

	@Test
	public void renamedBindLiteralsCompareAcrossLineages() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> z = lvar();

		Theory<NogoodConstraints> a = store(over(x.unifies(3))).rename(toHole(x, 0)).ground();
		Theory<NogoodConstraints> b = store(over(z.unifies(3))).rename(toHole(z, 0)).ground();

		assertThat(a).isEqualTo(b);
	}

	@Test
	public void renamedImpositionLiteralsCompareAcrossLineages() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> z = lvar();

		Theory<NogoodConstraints> a = store(over(dom(x, Longs.range(0, 5))))
				.rename(toHole(x, 0)).ground();
		Theory<NogoodConstraints> b = store(over(dom(z, Longs.range(0, 5))))
				.rename(toHole(z, 0)).ground();

		assertThat(a).isEqualTo(b);
	}

	@Test
	public void aResolutionLiteralCrossesAsItsBinds() {
		// the checked mint is lineage-local; unification is its portable spelling
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> z = lvar();

		org.clauseway.logic.constraints.Posting resolved = org.clauseway.logic.constraints.Propagation.resolve(
				org.clauseway.logic.unification.Prefix.binding(
								org.clauseway.logic.goals.Package.empty().substitution(),
								(LVar<?>) x.asVar().get(),
								LVal.lval(3))
						.get());

		Theory<NogoodConstraints> viaResolution = store(over(resolved)).rename(toHole(x, 0)).ground();
		Theory<NogoodConstraints> viaBind = store(over(z.unifies(3))).rename(toHole(z, 0)).ground();

		assertThat(viaResolution).isEqualTo(viaBind);
	}

	@Test
	public void aNogoodCrossesTheTabledCallAndReplaysAtTheCaller() {
		Tabled<Unifiable<Integer>> notThree = Tabling.define(x ->
				exclude(x.unifies(3)));

		Unifiable<Integer> y = lvar();
		Goal violating = notThree.apply(y).and(y.unifies(3));
		assertThat(violating.solve(y, TestSchedulers.factory()).count()).isZero();

		Unifiable<Integer> z = lvar();
		Goal escaping = notThree.apply(z).and(z.unifies(5));
		assertThat(escaping.solve(z, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(5);
	}

	@Test
	public void aCrossedNogoodFiltersAtTheCallersLabelling() {
		Tabled<Unifiable<Long>> constrained = Tabling.define(x ->
				dom(x, Longs.range(0, 5))
						.and(exclude(x.unifies(3L))));

		Unifiable<Long> y = lvar();
		java.util.List<Long> answers = constrained.apply(y)
				.solve(y, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());

		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 4L);
	}
}
