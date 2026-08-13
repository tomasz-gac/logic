package com.tgac.logic.nogoods;

// ABOUTME: NogoodConstraints' boundary faces: split keeps wholly-named nogoods, rename
// ABOUTME: transcribes literals wrapped, and nogoods cross tabled calls whole.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import com.tgac.logic.unification.Unknown;
import io.vavr.collection.LinkedHashSet;
import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.Test;

public class NogoodProjectionTest {

	private static NogoodConstraints store(Nogood... nogoods) {
		return NogoodConstraints.of(LinkedHashSet.of(nogoods));
	}

	private static Nogood over(com.tgac.logic.constraints.Posting... literals) {
		return Nogood.of(literals.length == 1 ?
				literals[0] :
				com.tgac.logic.constraints.Posting.all(literals));
	}

	private static Renaming toHole(Unifiable<?> var, int slot) {
		return Renaming.of(Collections.<Unknown<?>, Term<?>> singletonMap(
				var.asVar().get(), Hole.of(slot)));
	}

	@Test
	public void splitKeepsWhollyNamedNogoodsAndMeetRestores() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Nogood aboutX = over(x.unifies(3));
		Nogood aboutXY = over(x.unifies(1), y.unifies(2));
		NogoodConstraints whole = store(aboutX, aboutXY);

		io.vavr.Tuple2<NogoodConstraints, NogoodConstraints> parts = whole.split(
				Collections.<LVar<?>> singletonList((LVar<?>) x.asVar().get()));

		assertThat(parts._1.getNogoods()).containsExactly(aboutX);
		assertThat(parts._2.getNogoods()).containsExactly(aboutXY);
		assertThat(parts._1.meet(parts._2)).isEqualTo(whole);
	}

	@Test
	public void renamedBindLiteralsCompareAcrossLineages() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> z = lvar();

		NogoodConstraints a = store(over(x.unifies(3))).rename(toHole(x, 0)).get();
		NogoodConstraints b = store(over(z.unifies(3))).rename(toHole(z, 0)).get();

		assertThat(a).isEqualTo(b);
	}

	@Test
	public void renamedImpositionLiteralsCompareAcrossLineages() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> z = lvar();

		NogoodConstraints a = store(over(dom(x, EnumeratedDomain.range(0L, 5L))))
				.rename(toHole(x, 0)).get();
		NogoodConstraints b = store(over(dom(z, EnumeratedDomain.range(0L, 5L))))
				.rename(toHole(z, 0)).get();

		assertThat(a).isEqualTo(b);
	}

	@Test
	public void aResolutionLiteralCrossesAsItsBinds() {
		// the checked mint is lineage-local; unification is its portable spelling
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> z = lvar();

		com.tgac.logic.constraints.Posting resolved = com.tgac.logic.constraints.Propagation.resolve(
				com.tgac.logic.unification.Prefix.binding(
								com.tgac.logic.goals.Package.empty().substitution(),
								(LVar<?>) x.asVar().get(),
								com.tgac.logic.unification.LVal.lval(3))
						.get());

		NogoodConstraints viaResolution = store(over(resolved)).rename(toHole(x, 0)).get();
		NogoodConstraints viaBind = store(over(z.unifies(3))).rename(toHole(z, 0)).get();

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
				dom(x, EnumeratedDomain.range(0L, 5L))
						.and(exclude(x.unifies(3L))));

		Unifiable<Long> y = lvar();
		java.util.List<Long> answers = constrained.apply(y)
				.solve(y, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());

		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 4L);
	}
}
