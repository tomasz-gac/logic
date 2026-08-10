package com.tgac.logic.nogoods;

// ABOUTME: The verification core against Neq's own semantics: refuted discards,
// ABOUTME: entailed fails, survivors keep their original literals, bindings thread.

import com.tgac.logic.constraints.Statement;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Store;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

public class VerificationTest {

	private static Package given(Statement... literals) {
		Package state = Package.empty();
		for (Statement literal : literals) {
			state = Verification.imposed(literal, state).get().head();
		}
		return state;
	}

	private static Option<List<Nogood>> verified(Package state, Statement... literals) {
		return Verification.verify(List.of(Nogood.of(List.of(literals))), state).get();
	}

	@Test
	public void aRefutedStatementSubsumesTheNogoodFully() {
		// x is 5, so x = 3 can never hold: the forbidden conjunction is
		// refuted, the nogood discards
		Unifiable<Integer> x = lvar();
		Package state = given(Statement.bind(x, lval(5)));

		Option<List<Nogood>> verdict = verified(state, Statement.bind(x, lval(3)));

		assertThat(verdict.get()).isEmpty();
	}

	@Test
	public void anEntailedConjunctionFailsTheBranch() {
		// x is already 3: the forbidden thing holds — the veto
		Unifiable<Integer> x = lvar();
		Package state = given(Statement.bind(x, lval(3)));

		Option<List<Nogood>> verdict = verified(state, Statement.bind(x, lval(3)));

		assertThat(verdict.isDefined()).isFalse();
	}

	@Test
	public void anEntailedStatementIsCrossedOffAndSurvivorsKeepTheirOriginals() {
		// y already holds its half; only the x half is still owed
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package state = given(Statement.bind(y, lval(2)));
		Statement stillOwed = Statement.bind(x, lval(1));

		Option<List<Nogood>> verdict = verified(state, stillOwed, Statement.bind(y, lval(2)));

		Nogood survivor = verdict.get().head();
		assertThat(survivor.getLiterals()).containsExactly(stillOwed);
	}

	@Test
	public void anUndecidedNogoodSurvivesWhole() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Statement first = Statement.bind(x, lval(1));
		Statement second = Statement.bind(y, lval(2));

		Option<List<Nogood>> verdict = verified(Package.empty(), first, second);

		assertThat(verdict.get().head().getLiterals()).containsExactly(first, second);
	}

	@Test
	public void aStatementStatementRefusesWhenItsStoreIsAbsent() {
		// Package.withStored silently no-ops on an unregistered store; a
		// dropped statement would read "unchanged" — the false cross-off
		// direction, which can veto a satisfiable branch. Residence is
		// asserted after literal instead
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

		assertThatThrownBy(() -> Verification.imposed(
				Statement.state(List.empty(), terms -> orphan), Package.empty()).get())
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void statementStatementsCompareByTheirGeneratedItems() {
		// two distinct maker lambdas, one named schema over the same terms:
		// identity lives on the generated item, the maker is excluded
		Term<?> x = lvar();
		Statement first = Statement.state(List.of(x), terms ->
				Propagator.of(Store.class, "same-schema", terms, (watched, pkg) -> null));
		Statement second = Statement.state(List.of(x), terms ->
				Propagator.of(Store.class, "same-schema", terms, (watched, pkg) -> null));

		assertThat(first).isEqualTo(second);
		assertThat(first.terms()).containsExactly(x);
	}

	@Test
	public void bindingsThreadAcrossStatementsSharingVariables() {
		// x is 2; imposing x = y binds y to 2, so y = 2 is then entailed and
		// crosses off — the jointness of Neq's whole-record trial
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Package state = given(Statement.bind(x, lval(2)));
		Statement alias = Statement.bind(x, y);

		Option<List<Nogood>> verdict = verified(state, alias, Statement.bind(y, lval(2)));

		assertThat(verdict.get().head().getLiterals()).containsExactly(alias);
	}
}
