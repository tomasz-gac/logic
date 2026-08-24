package com.tgac.logic.lattice;

// ABOUTME: The watchers index and the door arithmetic it powers: touched selection
// ABOUTME: by name, and the subset-rename decomposition the Bind door stands on.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
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

public class TheoryWatchersTest {

	private static Propagator<FlatConstraints> even(Unifiable<?> x) {
		return Propagator.of(FlatConstraints.empty(), "even",
				Collections.<Term<?>> singletonList(x), (watched, state) -> Verdict.keep());
	}

	private static Renaming to(Unifiable<?> from, Term<?> target) {
		return Renaming.of(Collections.<Name<?>, Term<?>> singletonMap(
				from.asVar().get(), target));
	}

	@Test
	public void theIndexMatchesABruteScanThroughEveryDoor() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Theory<FlatConstraints> theory = LatticeFactorTest.valued((Term<?>) x, 1, 2)
				.with(even(x))
				.meet(LatticeFactorTest.valued((Term<?>) y, 3, 4))
				.with(even(z));
		assertIndexed(theory);
		assertIndexed(theory.without(even(z)));
		assertIndexed(theory.rename(to(x, (Term<?>) z)).ground());
	}

	@Test
	public void aForeignNameHasNoWatchers() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> w = lvar();
		Theory<FlatConstraints> theory = LatticeFactorTest.valued((Term<?>) x, 1, 2);
		assertThat(theory.watchers((Name<?>) w.asVar().get()).isEmpty()).isTrue();
	}

	@Test
	public void subsetRenameDecomposesTheWholeRename() {
		// the Bind door's arithmetic: touched off the index, the touched
		// subset renamed as its own theory (its atoms ARE the delta), and the
		// resident recomposed by meet — equal to the whole-theory rename
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Theory<FlatConstraints> theory = LatticeFactorTest.valued((Term<?>) x, 1, 2)
				.meet(LatticeFactorTest.valued((Term<?>) y, 3, 4))
				.with(even(x));

		assertDecomposes(theory, x, to(x, (Term<?>) z));   // plain re-key
		assertDecomposes(theory, x, to(x, (Term<?>) y));   // alias: collision fuses in the meet
		assertDecomposes(theory, x, to(x, lval(1)));       // bound: the faithful val-keyed crossing
	}

	private static void assertDecomposes(Theory<FlatConstraints> theory,
			Unifiable<?> renamed, Renaming renaming) {
		LinkedHashSet<Atom<FlatConstraints>> touched =
				theory.watchers((Name<?>) renamed.asVar().get());
		Theory<FlatConstraints> stripped = touched.foldLeft(theory, Theory::without);
		Theory<FlatConstraints> rewritten = Theory.of(touched).rename(renaming).ground();
		assertThat(stripped.meet(rewritten))
				.isEqualTo(theory.rename(renaming).ground());
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
