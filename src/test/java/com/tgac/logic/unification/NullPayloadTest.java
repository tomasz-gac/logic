package com.tgac.logic.unification;

// ABOUTME: A null payload is a VALUE: lval(null) equals itself, unifies with
// ABOUTME: nothing else, binds free variables, and reifies — never reads as unbound.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import org.junit.Test;

public class NullPayloadTest {

	@Test
	public void aNullValueIsBoundNotFree() {
		Unifiable<String> nul = lval((String) null);
		assertThat(nul.isVal()).isTrue();
		assertThat(nul.asVal().isDefined())
				.describedAs("a null payload must not read as unbound")
				.isTrue();
	}

	@Test
	public void nullUnifiesWithNullOnly() {
		Unifiable<String> x = lvar();
		assertThat(x.unifies(lval((String) null))
				.and(x.unifies(lval((String) null)))
				.solve(x)
				.map(Object::toString)
				.collect(Collectors.toList())).containsExactly("{null}");

		Unifiable<String> y = lvar();
		assertThat(y.unifies(lval((String) null))
				.and(y.unifies("Ada"))
				.solve(y)
				.collect(Collectors.toList())).isEmpty();
	}

	@Test
	public void aFreeVariableBindsToNullAndReifies() {
		Unifiable<String> x = lvar();
		assertThat(x.unifies(lval((String) null)).solve(x)
				.map(Object::toString)
				.collect(Collectors.toList())).containsExactly("{null}");
	}
}
