package com.tgac.logic.constraints.store;

// ABOUTME: Pins Renaming.apply's paths: name-free terms, bare names, compound
// ABOUTME: var replacement, slot instantiation, minting — and deep-term safety.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.unification.Any;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import com.tgac.logic.unification.Name;
import io.vavr.collection.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class RenamingTest {

	private static Name<?> nameOf(Unifiable<?> u) {
		return u.getObjectTerm().asVar().get();
	}

	private static boolean sameAs(Term<?> l, Term<?> r) {
		return MiniKanren.unify(Substitutions.empty(),
				l.getObjectTerm(), r.getObjectTerm()).ground().isDefined();
	}

	@Test
	public void aNameFreeTermPassesUnchanged() {
		Term<?> ground = lval(List.of(lval(1), lval(2)));
		assertThat(Renaming.of(Collections.<Name<?>, Term<?>> emptyMap()).apply(ground).ground())
				.isSameAs(ground);
	}

	@Test
	public void aBareNameMapsDirectly() {
		Unifiable<Integer> x = lvar();
		Map<Name<?>, Term<?>> seed = new HashMap<>();
		seed.put(nameOf(x), lval(9));
		assertThat(Renaming.of(seed).apply(x.getObjectTerm()).ground()).isEqualTo(lval(9));
	}

	@Test
	public void compoundVarNamesReplaceDeep() {
		// the path past the bare-name return: a compound term whose var
		// names replace while unlisted ones keep themselves
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Map<Name<?>, Term<?>> seed = new HashMap<>();
		seed.put(nameOf(x), lval(9));

		Term<?> applied = Renaming.of(seed)
				.apply(lval(List.of(x, lval(2), y)).getObjectTerm()).ground();

		assertThat(sameAs(applied, lval(List.of(lval(9), lval(2), y)).getObjectTerm())).isTrue();
		assertThat(sameAs(applied, lval(List.of(lval(8), lval(2), y)).getObjectTerm())).isFalse();
	}

	@Test
	public void compoundSlotNamesInstantiate() {
		// the slot path: anys in a compound term land on their targets
		Term<?> withHoles = lval(List.of(Any.of(0), lval(2), Any.of(1)));
		Map<Any<?>, Term<?>> slotTargets = new HashMap<>();
		slotTargets.put(Any.of(0), lval(7));
		slotTargets.put(Any.of(1), lval(8));
		Term<?> applied = Renaming.restating(slotTargets)
				.apply(withHoles).ground();

		assertThat(sameAs(applied, lval(List.of(lval(7), lval(2), lval(8)))))
				.isTrue();
	}

	@Test
	public void mintingAppliesVarsAndSlotsInOnePass() {
		// the one crossing that speaks both namespaces: seeded vars and
		// seeded anys land on their targets in a single application
		Unifiable<Integer> x = lvar();
		Map<Name<?>, Term<?>> seed = new HashMap<>();
		seed.put(nameOf(x), lval(9));
		seed.put(Any.of(0), lval(7));

		Term<?> applied = Renaming.minting(seed)
				.apply(lval(List.of(Any.of(0), x, lval(2))).getObjectTerm()).ground();

		assertThat(sameAs(applied, lval(List.of(lval(7), lval(9), lval(2)))))
				.isTrue();
	}

	@Test
	public void aSeedMayMixVarsAndSlots() {
		// one engine, one map: live vars and anys are both names, so a
		// plain seed carries both namespaces in one application
		Unifiable<Integer> x = lvar();
		Map<Name<?>, Term<?>> seed = new HashMap<>();
		seed.put(nameOf(x), lval(9));
		seed.put(Any.of(0), lval(7));

		Term<?> applied = Renaming.of(seed)
				.apply(lval(List.of(Any.of(0), x, lval(2))).getObjectTerm()).ground();

		assertThat(sameAs(applied, lval(List.of(lval(7), lval(9), lval(2)))))
				.isTrue();
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void aRenamingRefusesValueKeys() {
		// the type proves seed keys are names — nobody wants an LVal → LVal
		// mapping — and raw-typed abuse dies loudly on the erased cast
		Map raw = new HashMap();
		raw.put(lval(7), lval(8));

		assertThatThrownBy(() -> Renaming.of((Map<Name<?>, Term<?>>) raw))
				.isInstanceOf(ClassCastException.class);
	}

	@Test
	public void mintingSharesTheFreshVarAcrossOccurrences() {
		// one unlisted name, two occurrences: the mint is recorded, so both
		// occurrences become the SAME fresh variable (the existential)
		Unifiable<Integer> local = lvar();
		Term<?> applied = Renaming.minting(Collections.<Name<?>, Term<?>> emptyMap())
				.apply(lval(List.of(local, local)).getObjectTerm()).ground();

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
		Map<Name<?>, Term<?>> seed = new HashMap<>();
		seed.put(nameOf(x), lval(1));

		Term<?> applied = Renaming.of(seed).apply(deep).ground();

		assertThat(applied).isNotNull();
	}
}
