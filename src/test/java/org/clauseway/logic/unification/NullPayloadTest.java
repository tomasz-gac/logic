package org.clauseway.logic.unification;

// ABOUTME: A null payload is a VALUE: lval(null) equals itself, unifies with
// ABOUTME: nothing else, binds free variables, and reifies — never reads as unbound.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Logic;
import org.clauseway.logic.goals.Matche;
import org.clauseway.logic.unification.terms.Unifiable;
import org.junit.Test;

public class NullPayloadTest {

	@Test
	public void aNullValueIsBoundNotFree() {
		Unifiable<String> nul = lval((String) null);
		assertThat(nul.isVal())
				.describedAs("a null payload must not read as unbound")
				.isTrue();
		assertThat(nul.asVal())
				.describedAs("asVal is empty for a NULL payload — bindness is isVal, the payload is get")
				.isEmpty();
		assertThat(nul.get()).isNull();
	}

	@Test
	public void matcheValueHandsTheNullPayloadToTheBody() {
		Unifiable<String> x = lvar();
		assertThat(x.unifies(lval((String) null))
				.and(Matche.matche(x, Matche.value(v -> v == null ? Goal.success() : Goal.failure())))
				.solve(x)
				.map(Object::toString)
				.collect(Collectors.toList())).containsExactly("{null}");
	}

	@Test
	public void groundSucceedsOnANullBoundVariable() {
		Unifiable<String> x = lvar();
		assertThat(x.unifies(lval((String) null))
				.and(Logic.ground(x))
				.solve(x)
				.map(Object::toString)
				.collect(Collectors.toList())).containsExactly("{null}");
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
