package com.tgac.logic.nogoods;

// ABOUTME: The verification core against Neq's own semantics: refuted discards,
// ABOUTME: entailed fails, survivors keep their original literals, bindings thread.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.constraints.Posting;

import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.finitedomain.FiniteDomain;
import java.util.stream.Stream;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

public class VerificationTest {

	private static Package given(Posting... literals) {
		Package state = Package.empty();
		for (Posting literal : literals) {
			state = new BreadthFirstScheduler<>(Trial.imposed(literal, state)).get().head();
		}
		return state;
	}

	private static Option<List<Nogood>> verified(Package state, Posting... literals) {
		return new BreadthFirstScheduler<>(Verification.verify(Stream.of(Nogood.of(literals.length == 1 ?
				literals[0] : Posting.all(literals))), state)).get();
	}

	@Test
	public void subsumptionClaimsNothingWhenTheTrialIsNotDone() {
		// a store-shaped forbidden answers its trial with a real fiber, not
		// Fiber.done — pruning must claim nothing rather than ground the
		// trial on a side engine: the duplicate survives, wider never wrong
		Unifiable<Long> x = lvar();
		Nogood first = Nogood.of(FiniteDomain.dom(x, EnumeratedDomain.range(0L, 6L)));
		Nogood second = Nogood.of(FiniteDomain.dom(x, EnumeratedDomain.range(0L, 6L)));

		List<Nogood> kept = Verification.pruneSubsumed(List.of(first, second), Package.empty());
		assertThat(kept).containsExactly(first, second);
	}

	@Test
	public void aRefutedPostingSubsumesTheNogoodFully() {
		// x is 5, so x = 3 can never hold: the forbidden conjunction is
		// refuted, the nogood discards
		Unifiable<Integer> x = lvar();
		Package state = given(Posting.bind(x, lval(5)));

		Option<List<Nogood>> verdict = verified(state, Posting.bind(x, lval(3)));

		assertThat(verdict.get()).isEmpty();
	}

	@Test
	public void anEntailedConjunctionFailsTheBranch() {
		// x is already 3: the forbidden thing holds — the veto
		Unifiable<Integer> x = lvar();
		Package state = given(Posting.bind(x, lval(3)));

		Option<List<Nogood>> verdict = verified(state, Posting.bind(x, lval(3)));

		assertThat(verdict.isDefined()).isFalse();
	}

	@Test
	public void anEntailedLiteralIsCrossedOffAndSurvivorsSimplifyToRemainders() {
		// y already holds its half; the x half survives SIMPLIFIED — the
		// residual prefix as a Resolution literal (Neq's remainder rewrite,
		// the human's inheritance ruling, Aug 2026)
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package state = given(Posting.bind(y, lval(2)));

		Option<List<Nogood>> verdict = verified(state,
				Posting.bind(x, lval(1)), Posting.bind(y, lval(2)));

		Nogood survivor = verdict.get().head();
		assertThat(survivor.conjunct()).isInstanceOf(Posting.Resolution.class);
		assertThat(survivor.conjunct().terms()
				.anyMatch(t -> t == x.asVar().get())).isTrue();
	}

	@Test
	public void anUndecidedNogoodSurvivesWithBothRemainders() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Option<List<Nogood>> verdict = verified(Package.empty(),
				Posting.bind(x, lval(1)), Posting.bind(y, lval(2)));

		Posting forbidden = verdict.get().head().conjunct();
		assertThat(forbidden).isInstanceOf(Posting.AllOf.class);
		assertThat(((Posting.AllOf) forbidden).getParts())
				.allMatch(l -> l instanceof Posting.Resolution);
	}

	@Test
	public void aPostingPostingRefusesWhenItsStoreIsAbsent() {
		// Package.withStored silently no-ops on an unregistered store; a
		// dropped statement would read "unchanged" — the false cross-off
		// direction, which can veto a satisfiable branch. Residence is
		// asserted after literal instead
		Atom<NogoodConstraints> orphan = new Atom<NogoodConstraints>() {
			@Override
			public Fiber<Atom<NogoodConstraints>> rename(Renaming renaming) {
				return done(this);
			}

			@Override
			public Posting posting() {
				throw new UnsupportedOperationException("orphan test atom");
			}

			@Override
			public Class<? extends NogoodConstraints> getFactorClass() {
				return NogoodConstraints.class;
			}

			@Override
			public String name() {
				return "orphan";
			}

			@Override
			public io.vavr.collection.Traversable<Term<?>> watched() {
				return io.vavr.collection.HashSet.empty();
			}
		};

		assertThatThrownBy(() -> new BreadthFirstScheduler<>(Trial.imposed(
				Propagation.activate(orphan), Package.empty())).get())
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void statedItemsCompareByTheirOwnIdentity() {
		// one named schema over the same terms, two bodies: identity lives on
		// the item (the named-schema contract), and the statement follows it
		Term<?> x = lvar();
		Posting first = Propagation.activate(
				Propagator.of(NogoodConstraints.EMPTY, "same-schema", Array.of(x), (watched, pkg) -> null));
		Posting second = Propagation.activate(
				Propagator.of(NogoodConstraints.EMPTY, "same-schema", Array.of(x), (watched, pkg) -> null));

		assertThat(first).isEqualTo(second);
		assertThat(first.terms()).containsExactly(x);
	}

	@Test
	public void aMixedConjunctCrossesOffItsEntailedBindPart() {
		// x = 3 already holds, so the bind part crosses off and the survivor
		// is the dom part ALONE — per-part granularity through the package
		// trial, not the whole conjunct kept as a blob
		Unifiable<Integer> x = lvar();
		Unifiable<Long> y = lvar();
		Package state = given(Posting.bind(x, lval(3)));

		Option<List<Nogood>> verdict = verified(state,
				Posting.bind(x, lval(3)),
				com.tgac.logic.finitedomain.FiniteDomain.dom(y,
						com.tgac.logic.finitedomain.domains.EnumeratedDomain.range(2L, 5L)));

		Posting survivor = verdict.get().head().conjunct();
		assertThat(survivor).isNotInstanceOf(Posting.AllOf.class);
		assertThat(survivor.terms().anyMatch(t -> t == y.asVar().get())).isTrue();
	}

	@Test
	public void bindingsThreadAcrossPostingsSharingVariables() {
		// x is 2; imposing x = y binds y to 2, so y = 2 is then entailed and
		// crosses off — the jointness of Neq's whole-record trial
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package state = given(Posting.bind(x, lval(2)));
		Posting alias = Posting.bind(x, y);

		Option<List<Nogood>> verdict = verified(state, alias, Posting.bind(y, lval(2)));

		// one survivor: the alias simplified to its residual (y's binding);
		// the y = 2 literal read entailed THROUGH the threading and crossed off
		assertThat(verdict.get().head().conjunct())
				.isInstanceOf(Posting.Resolution.class);
	}
}
