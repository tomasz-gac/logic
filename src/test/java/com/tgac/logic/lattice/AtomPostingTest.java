package com.tgac.logic.lattice;

// ABOUTME: The statement capability: an atom knows how to state itself as a
// ABOUTME: Posting — registration and doom travel with it, not with call sites.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.TestSchedulers;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
import com.tgac.logic.nogoods.Nogood;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.Collections;
import org.junit.Test;

public class AtomPostingTest {

	@Test
	public void anImpositionStatesItselfRegistrationIncluded() {
		Unifiable<Integer> x = lvar();
		Imposition<FlatSet, FlatConstraints> imposition =
				new Imposition<>(FlatConstraints.class, x, FlatSet.of(1, 2), FlatConstraints.empty());
		// no store registered beforehand: the posting seeds it
		assertThat(imposition.posting().and(x.unifies(1))
				.solve(x, TestSchedulers.factory()).count()).isEqualTo(1L);
		assertThat(imposition.posting().and(x.unifies(3))
				.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void aNogoodStatesItself() {
		Unifiable<Integer> x = lvar();
		Nogood nogood = Nogood.of(Posting.bind(x, lval(1)));
		assertThat(nogood.posting().and(x.unifies(1))
				.solve(x, TestSchedulers.factory()).count()).isZero();
		assertThat(nogood.posting().and(x.unifies(2))
				.solve(x, TestSchedulers.factory()).count()).isEqualTo(1L);
	}

	@Test
	public void aStatedPropagatorParksAndWakes() {
		Unifiable<Integer> x = lvar();
		Propagator<FlatConstraints> even = Propagator.of(FlatConstraints.class, "even",
						Collections.singletonList(x),
						(watched, state) -> {
							Term<?> w = state.walk(watched.get(0));
							if (!w.isVal()) {
								return Verdict.keep();
							}
							return ((Integer) w.get()) % 2 == 0 ? Verdict.subsumed() : Verdict.fail();
						})
				.stated(FlatConstraints.empty(), p -> false);
		assertThat(FlatConstraints.empty().impose(x, FlatSet.of(1, 2, 3, 4))
				.and(even.posting()).and(x.unifies(4))
				.solve(x, TestSchedulers.factory()).count()).isEqualTo(1L);
		assertThat(FlatConstraints.empty().impose(x, FlatSet.of(1, 2, 3, 4))
				.and(even.posting()).and(x.unifies(3))
				.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void anUnconfiguredPropagatorRefusesToPost() {
		Unifiable<Integer> x = lvar();
		assertThatThrownBy(() -> Propagator.of(FlatConstraints.class, "even",
						Collections.singletonList(x), (watched, state) -> Verdict.keep())
				.posting())
				.isInstanceOf(IllegalStateException.class);
	}
}
