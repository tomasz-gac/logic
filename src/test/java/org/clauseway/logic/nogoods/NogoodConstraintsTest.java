package org.clauseway.logic.nogoods;

// ABOUTME: The store faces over the verification core: the four moves through the
// ABOUTME: real propagation pipeline — statement, revise on bindings, the wall.

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import static org.clauseway.logic.finitedomain.FiniteDomain.dom;

import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.unification.terms.Unifiable;
import java.util.stream.Collectors;
import org.junit.Test;

public class NogoodConstraintsTest {

	private static Goal held(Posting... literals) {
		return Exclusion.exclude(literals);
	}

	@Test
	public void nestedConjunctsFlattenAtTheEnvelope() {
		// ∧ is associative: ¬(a ∧ (b ∧ c)) IS ¬(a ∧ b ∧ c) — the envelope
		// normalizes, so structural equality matches semantic equality and
		// nested binds keep the fast-path partition
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();
		Posting pa = Posting.bind(a, lval(1));
		Posting pb = Posting.bind(b, lval(2));
		Posting pc = Posting.bind(c, lval(3));

		Nogood nested = Nogood.of(Posting.all(pa, Posting.all(pb, pc)));
		Nogood flat = Nogood.of(Posting.all(pa, pb, pc));

		assertThat(nested).isEqualTo(flat);
	}

	@Test
	public void aViolatedNogoodFailsTheBranch() {
		Unifiable<Integer> x = lvar();

		Goal g = held(Posting.bind(x, lval(3))).and(x.unifies(3));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void aSatisfiedNogoodDischarges() {
		Unifiable<Integer> x = lvar();

		Goal g = held(Posting.bind(x, lval(3))).and(x.unifies(5));

		assertThat(g.solve(x, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(5);
	}

	@Test
	public void theSurvivorVetoesAfterACrossOff() {
		// y = 2 crosses its literal off; the survivor is ¬(x = 1). The
		// intermediate assertion proves the branch is alive after the
		// cross-off, so the zero can only come from the survivor's veto
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal afterCrossOff = held(Posting.bind(x, lval(1)), Posting.bind(y, lval(2)))
				.and(y.unifies(2));
		assertThat(afterCrossOff.solve(y, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(2);

		assertThat(afterCrossOff.and(x.unifies(1))
				.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void theSurvivorAdmitsTheEscape() {
		// the escape path of the same pair as above
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal g = held(Posting.bind(x, lval(1)), Posting.bind(y, lval(2)))
				.and(y.unifies(2))
				.and(x.unifies(5));

		assertThat(g.solve(x, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(5);
	}

	@Test
	public void aNogoodBornViolatedFailsAtPosting() {
		// after the statement there is no further trigger, so the zero can
		// only come from first examination — the wall cannot produce it
		// (x is ground: no live name renders, so reify stays silent). The
		// sibling statement proves first examination discriminates: a nogood
		// born SATISFIED discards and the branch delivers
		Unifiable<Integer> x = lvar();

		Goal violated = x.unifies(3).and(held(Posting.bind(x, lval(3))));
		assertThat(violated.solve(x, TestSchedulers.factory()).count()).isZero();

		Unifiable<Integer> z = lvar();
		Goal discarded = z.unifies(3).and(held(Posting.bind(z, lval(4))));
		assertThat(discarded.solve(z, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(3);
	}

	@Test
	public void aLiveNogoodAboutARenderedTermAttachesAsAResidual() {
		// both literals stay owed; the condition rides the answer VISIBLY
		// through the carrier — expressed, never dropped (the old wall's
		// refusal, upgraded to rendering). y is unrendered: its literal
		// prunes from the display, Neq's purify convention
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal g = held(Posting.bind(x, lval(1)), Posting.bind(y, lval(2)));

		java.util.List<String> answers = g.solve(x, TestSchedulers.factory())
				.map(Object::toString)
				.collect(java.util.stream.Collectors.toList());
		assertThat(answers).containsExactly("_.0 : ¬(_.0 ≡ {1})");
	}

	@Test
	public void aBindNogoodFiltersAtLabellingOnly() {
		// the nogood stays undecided until enforce-time labelling
		// equality-binds the anchor — the ground floor through the binding
		// seam, per labelled point
		Unifiable<Long> x = lvar();

		Goal g = dom(x, Longs.range(0, 5))
				.and(held(Posting.bind(x, lval(3L))));

		java.util.List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 4L);
	}

	@Test
	public void aDisjointDomainDischargesTheNogoodThroughTheStore() {
		// the constraint-failing path: the imposition fails via the FD meet
		// emptying in the scratch, not via unification — refuted, discarded
		Unifiable<Long> x = lvar();

		Goal g = dom(x, Longs.range(0, 4))
				.and(held(dom(x, Longs.range(5, 8))));

		java.util.List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 3L);
	}

	@Test
	public void theNogoodCarvesTheBoxOutOfALabelledDomain() {
		// pre-labelling the nogood narrows nothing (the imposition would narrow
		// the scratch, so the literal stays owed); each labelled point inside
		// the box reads entailed at the ground floor and dies
		Unifiable<Long> x = lvar();

		Goal g = dom(x, Longs.range(0, 10))
				.and(held(dom(x, Longs.range(3, 6))));

		java.util.List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void aDomainInsideTheBoxDiesAtPosting() {
		// the resident domain sits inside the forbidden box: the imposition
		// meets to no change, the single literal reads entailed — the veto,
		// through the store's own factor rather than a binding
		Unifiable<Long> x = lvar();

		Goal g = dom(x, Longs.range(3, 6))
				.and(held(dom(x, Longs.range(0, 10))));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}
}
