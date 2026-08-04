package com.tgac.logic.constraints.store;

// ABOUTME: Pins Renaming.apply's paths: name-free terms, bare names, compound
// ABOUTME: var replacement, slot instantiation, minting — and deep-term safety.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class RenamingTest {

	private static boolean sameAs(Term<?> l, Term<?> r) {
		return MiniKanren.unify(Substitutions.empty(),
				l.getObjectTerm(), r.getObjectTerm()).get().isDefined();
	}

	@Test
	public void aNameFreeTermPassesUnchanged() {
		Term<?> ground = lval(List.of(lval(1), lval(2)));
		assertThat(Renaming.of(Collections.<Term<?>, Term<?>> emptyMap()).apply(ground).get())
				.isSameAs(ground);
	}

	@Test
	public void aBareNameMapsDirectly() {
		Unifiable<Integer> x = lvar();
		Map<Term<?>, Term<?>> seed = new HashMap<>();
		seed.put(x.getObjectTerm(), lval(9));
		assertThat(Renaming.of(seed).apply(x.getObjectTerm()).get()).isEqualTo(lval(9));
	}

	@Test
	public void compoundVarNamesReplaceDeep() {
		// the path past the bare-name return: a compound term whose var
		// names replace while unlisted ones keep themselves
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Map<Term<?>, Term<?>> seed = new HashMap<>();
		seed.put(x.getObjectTerm(), lval(9));

		Term<?> applied = Renaming.of(seed)
				.apply(lval(List.of(x, lval(2), y)).getObjectTerm()).get();

		assertThat(sameAs(applied, lval(List.of(lval(9), lval(2), y)).getObjectTerm())).isTrue();
		assertThat(sameAs(applied, lval(List.of(lval(8), lval(2), y)).getObjectTerm())).isFalse();
	}

	@Test
	public void compoundSlotNamesInstantiate() {
		// the slot path: holes in a compound term land on their targets
		Term<?> withHoles = lval(List.of(Hole.of(0), lval(2), Hole.of(1)));
		Term<?> applied = Renaming
				.restating(Arrays.<Term<?>> asList(lval(7), lval(8)))
				.apply(withHoles).get();

		assertThat(sameAs(applied, lval(List.of(lval(7), lval(2), lval(8)))))
				.isTrue();
	}

	@Test
	public void mintingAppliesVarsAndSlotsInOnePass() {
		// the one crossing that speaks both namespaces: seeded vars and
		// seeded holes land on their targets in a single application
		Unifiable<Integer> x = lvar();
		Map<Term<?>, Term<?>> seed = new HashMap<>();
		seed.put(x.getObjectTerm(), lval(9));
		seed.put(Hole.of(0), lval(7));

		Term<?> applied = Renaming.minting(seed)
				.apply(lval(List.of(Hole.of(0), x, lval(2))).getObjectTerm()).get();

		assertThat(sameAs(applied, lval(List.of(lval(7), lval(9), lval(2)))))
				.isTrue();
	}

	@Test
	public void aVarRenamingRefusesSlotNames() {
		// slot names cross by restating or minting; a var seed holding a
		// hole is a construction error, refused loudly
		Map<Term<?>, Term<?>> seed = new HashMap<>();
		seed.put(Hole.of(0), lval(7));

		assertThatThrownBy(() -> Renaming.of(seed))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void mintingSharesTheFreshVarAcrossOccurrences() {
		// one unlisted name, two occurrences: the mint is recorded, so both
		// occurrences become the SAME fresh variable (the existential)
		Unifiable<Integer> local = lvar();
		Term<?> applied = Renaming.minting(Collections.<Term<?>, Term<?>> emptyMap())
				.apply(lval(List.of(local, local)).getObjectTerm()).get();

		java.util.List<Term<?>> members = new java.util.ArrayList<>();
		MiniKanren.members(applied.asVal().isDefined() ? applied : applied)
				.forEach(ms -> ms.forEach(members::add));
		assertThat(members).hasSize(2);
		assertThat(members.get(0)).isEqualTo(members.get(1));
		assertThat(members.get(0)).isNotEqualTo(local.getObjectTerm());
	}

	@Test
	public void aDeeplyNestedTermDoesNotBlowTheStack() {
		// nesting, not length: each level is an iterable wrapping the next,
		// so any construction-time recursion in the rebuild pays one stack
		// frame per level
		Unifiable<Integer> x = lvar();
		Term<?> deep = x.getObjectTerm();
		for (int i = 0; i < 10_000; i++) {
			deep = lval(List.of(deep));
		}
		Map<Term<?>, Term<?>> seed = new HashMap<>();
		seed.put(x.getObjectTerm(), lval(1));

		Term<?> applied = Renaming.of(seed).apply(deep).get();

		assertThat(applied).isNotNull();
	}
}
