package com.tgac.logic.nogoods;

// ABOUTME: Every FD relation under exclusion: ground entailment fails the branch,
// ABOUTME: ground refutation discharges, open anchors filter at labelling.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.finitedomain.Longs;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class ExcludedFdRelationsTest {

	private static long count(Goal g, Unifiable<Long> out) {
		return g.solve(out, TestSchedulers.factory()).count();
	}

	private static List<Long> answers(Goal g, Unifiable<Long> out) {
		return g.solve(out, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
	}

	private static Goal ground(Unifiable<Long> a, long va, Unifiable<Long> b, long vb, Unifiable<Long> c, long vc) {
		return a.unifies(va).and(b.unifies(vb)).and(c.unifies(vc));
	}

	@Test
	public void excludedAddoDecidesAtGround() {
		Unifiable<Long> a = lvar();
		Unifiable<Long> b = lvar();
		Unifiable<Long> c = lvar();
		assertThat(count(ground(a, 2, b, 3, c, 5).and(exclude(Longs.addo(a, b, c))), a))
				.isZero();

		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();
		assertThat(count(ground(x, 2, y, 3, z, 9).and(exclude(Longs.addo(x, y, z))), x))
				.isEqualTo(1);
	}

	@Test
	public void excludedAddoFiltersAtLabelling() {
		Unifiable<Long> a = lvar();
		Unifiable<Long> b = lvar();
		Unifiable<Long> c = lvar();

		Goal g = dom(a, Longs.range(0, 5))
				.and(b.unifies(2L)).and(c.unifies(4L))
				.and(exclude(Longs.addo(a, b, c)));

		assertThat(answers(g, a)).containsExactlyInAnyOrder(0L, 1L, 3L, 4L);
	}

	@Test
	public void excludedSubtractoDecidesAtGround() {
		Unifiable<Long> a = lvar();
		Unifiable<Long> b = lvar();
		Unifiable<Long> c = lvar();
		assertThat(count(ground(a, 5, b, 3, c, 2).and(exclude(Longs.subtracto(a, b, c))), a))
				.isZero();

		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();
		assertThat(count(ground(x, 5, y, 3, z, 7).and(exclude(Longs.subtracto(x, y, z))), x))
				.isEqualTo(1);
	}

	@Test
	public void excludedMultoDecidesAtGround() {
		Unifiable<Long> a = lvar();
		Unifiable<Long> b = lvar();
		Unifiable<Long> c = lvar();
		assertThat(count(ground(a, 2, b, 3, c, 6).and(exclude(Longs.multo(a, b, c))), a))
				.isZero();

		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();
		assertThat(count(ground(x, 2, y, 3, z, 7).and(exclude(Longs.multo(x, y, z))), x))
				.isEqualTo(1);
	}

	@Test
	public void excludedMultoFiltersAtLabelling() {
		Unifiable<Long> a = lvar();
		Unifiable<Long> b = lvar();
		Unifiable<Long> c = lvar();

		Goal g = dom(a, Longs.range(1, 5))
				.and(b.unifies(2L)).and(c.unifies(6L))
				.and(exclude(Longs.multo(a, b, c)));

		assertThat(answers(g, a)).containsExactlyInAnyOrder(1L, 2L, 4L);
	}

	@Test
	public void excludedDivoDecidesAtGround() {
		Unifiable<Long> a = lvar();
		Unifiable<Long> b = lvar();
		Unifiable<Long> c = lvar();
		assertThat(count(ground(a, 6, b, 3, c, 2).and(exclude(Longs.divo(a, b, c))), a))
				.isZero();

		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();
		assertThat(count(ground(x, 6, y, 3, z, 5).and(exclude(Longs.divo(x, y, z))), x))
				.isEqualTo(1);
	}

	@Test
	public void excludedLeqDecidesAtGround() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		assertThat(count(x.unifies(1L).and(y.unifies(5L)).and(exclude(Longs.leq(x, y))), x))
				.isZero();

		Unifiable<Long> p = lvar();
		Unifiable<Long> q = lvar();
		assertThat(count(p.unifies(5L).and(q.unifies(1L)).and(exclude(Longs.leq(p, q))), p))
				.isEqualTo(1);
	}

	@Test
	public void excludedLeqFiltersAtLabelling() {
		// ¬(x ≤ 2) over 0..4 is the strict upper half
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();

		Goal g = dom(x, Longs.range(0, 5))
				.and(y.unifies(2L))
				.and(exclude(Longs.leq(x, y)));

		assertThat(answers(g, x)).containsExactlyInAnyOrder(3L, 4L);
	}

	@Test
	public void excludedLssDecidesAtGround() {
		// the composite literal: lss = leq ∧ separate under one exclusion
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		assertThat(count(x.unifies(1L).and(y.unifies(5L)).and(exclude(Longs.lss(x, y))), x))
				.isZero();

		Unifiable<Long> p = lvar();
		Unifiable<Long> q = lvar();
		assertThat(count(p.unifies(5L).and(q.unifies(1L)).and(exclude(Longs.lss(p, q))), p))
				.isEqualTo(1);
	}

	@Test
	public void excludedGtrDecidesAtGround() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		assertThat(count(x.unifies(5L).and(y.unifies(1L)).and(exclude(Longs.gtr(x, y))), x))
				.isZero();

		Unifiable<Long> p = lvar();
		Unifiable<Long> q = lvar();
		assertThat(count(p.unifies(1L).and(q.unifies(5L)).and(exclude(Longs.gtr(p, q))), p))
				.isEqualTo(1);
	}

	@Test
	public void excludedGeqDecidesAtGround() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		assertThat(count(x.unifies(5L).and(y.unifies(5L)).and(exclude(Longs.geq(x, y))), x))
				.isZero();

		Unifiable<Long> p = lvar();
		Unifiable<Long> q = lvar();
		assertThat(count(p.unifies(1L).and(q.unifies(5L)).and(exclude(Longs.geq(p, q))), p))
				.isEqualTo(1);
	}

	@Test
	public void excludedSeparateIsEqualityByLazyVeto() {
		// ¬(x ≠ y) admits only x = y
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		assertThat(count(x.unifies(1L).and(y.unifies(2L)).and(exclude(Longs.separate(x, y))), x))
				.isZero();

		Unifiable<Long> p = lvar();
		Unifiable<Long> q = lvar();
		assertThat(count(p.unifies(1L).and(q.unifies(1L)).and(exclude(Longs.separate(p, q))), p))
				.isEqualTo(1);
	}

	@Test
	public void excludedSeparateFiltersAtLabelling() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();

		Goal g = dom(x, Longs.range(0, 5))
				.and(y.unifies(2L))
				.and(exclude(Longs.separate(x, y)));

		assertThat(answers(g, x)).containsExactly(2L);
	}

	@Test
	public void excludedForbiddenCombinationAcrossRelations() {
		// ¬(a + b = c ∧ c ≤ cap): the pair dies only when BOTH hold
		Unifiable<Long> a = lvar();
		Unifiable<Long> b = lvar();
		Unifiable<Long> c = lvar();
		Unifiable<Long> cap = lvar();
		assertThat(count(ground(a, 1, b, 2, c, 3).and(cap.unifies(4L))
				.and(exclude(Longs.addo(a, b, c), Longs.leq(c, cap))), a))
				.isZero();

		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();
		Unifiable<Long> tight = lvar();
		assertThat(count(ground(x, 1, y, 2, z, 3).and(tight.unifies(2L))
				.and(exclude(Longs.addo(x, y, z), Longs.leq(z, tight))), x))
				.isEqualTo(1);
	}
}
