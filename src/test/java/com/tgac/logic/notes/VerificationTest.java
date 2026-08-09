package com.tgac.logic.notes;

// ABOUTME: The verification core against Neq's own semantics: refuted discards,
// ABOUTME: entailed fails, survivors keep their original postings, bindings thread.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

public class VerificationTest {

	private static Package given(Posting... postings) {
		Package state = Package.empty();
		for (Posting posting : postings) {
			state = Verification.imposed(posting, state).get().head();
		}
		return state;
	}

	private static Option<List<Note>> verified(Package state, Posting... postings) {
		return Verification.verify(List.of(Note.of(List.of(postings))), state).get();
	}

	@Test
	public void aRefutedPostingSubsumesTheNoteFully() {
		// x is 5, so x = 3 can never hold: the forbidden conjunction is
		// refuted, the note discards
		Unifiable<Integer> x = lvar();
		Package state = given(Posting.bind(x, lval(5)));

		Option<List<Note>> verdict = verified(state, Posting.bind(x, lval(3)));

		assertThat(verdict.get()).isEmpty();
	}

	@Test
	public void anEntailedConjunctionFailsTheBranch() {
		// x is already 3: the forbidden thing holds — the veto
		Unifiable<Integer> x = lvar();
		Package state = given(Posting.bind(x, lval(3)));

		Option<List<Note>> verdict = verified(state, Posting.bind(x, lval(3)));

		assertThat(verdict.isDefined()).isFalse();
	}

	@Test
	public void anEntailedPostingIsCrossedOffAndSurvivorsKeepTheirOriginals() {
		// y already holds its half; only the x half is still owed
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package state = given(Posting.bind(y, lval(2)));
		Posting stillOwed = Posting.bind(x, lval(1));

		Option<List<Note>> verdict = verified(state, stillOwed, Posting.bind(y, lval(2)));

		Note survivor = verdict.get().head();
		assertThat(survivor.getPostings()).containsExactly(stillOwed);
	}

	@Test
	public void anUndecidedNoteSurvivesWhole() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Posting first = Posting.bind(x, lval(1));
		Posting second = Posting.bind(y, lval(2));

		Option<List<Note>> verdict = verified(Package.empty(), first, second);

		assertThat(verdict.get().head().getPostings()).containsExactly(first, second);
	}

	@Test
	public void aStatementPostingRefusesWhenItsStoreIsAbsent() {
		// Package.withStored silently no-ops on an unregistered store; a
		// dropped statement would read "unchanged" — the false cross-off
		// direction, which can veto a satisfiable branch. Residence is
		// asserted after posting instead
		Stored orphan = new Stored() {
			@Override
			public Class<? extends Store> getStoreClass() {
				return Store.class;
			}

			@Override
			public java.util.stream.Stream<Term<?>> terms() {
				return java.util.stream.Stream.empty();
			}
		};

		assertThatThrownBy(() -> Verification.imposed(Posting.state(orphan), Package.empty()).get())
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void bindingsThreadAcrossPostingsSharingVariables() {
		// x is 2; imposing x = y binds y to 2, so y = 2 is then entailed and
		// crosses off — the jointness of Neq's whole-record trial
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package state = given(Posting.bind(x, lval(2)));
		Posting alias = Posting.bind(x, y);

		Option<List<Note>> verdict = verified(state, alias, Posting.bind(y, lval(2)));

		assertThat(verdict.get().head().getPostings()).containsExactly(alias);
	}
}
