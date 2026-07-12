package com.tgac.logic.finitedomain;

// ABOUTME: Pins the cascade's contraction contract: a propagator that emits
// ABOUTME: re-examination without narrowing is a bug and must throw, not loop.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Unifiable;
import java.util.Collections;
import org.junit.Test;

public class CascadeContractTest {

	@Test(timeout = 5000)
	public void reexaminationWithoutNarrowingThrowsInsteadOfLooping() {
		Unifiable<Long> x = lvar();
		Propagator rogue = Propagator.of(FiniteDomainConstraints.class,
				Collections.singletonList(x),
				state -> Verdict.update((live, factor) ->
						Update.applied(factor).withReexamine(x)));
		FiniteDomainConstraints store =
				(FiniteDomainConstraints) FiniteDomainConstraints.empty().prepend(rogue);
		Package p = Package.empty().withStore(store);

		assertThatThrownBy(() -> store.stated(rogue, p))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("strict descent");
	}
}
