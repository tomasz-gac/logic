package com.tgac.logic.finitedomain;

import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.monad.Cont;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.separate.Disequality;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * The Neq→FD bridge (cKanren's FD/≠ integration): a disequality against a ground
 * value, on a variable with a finite domain, is expressed by excluding the value
 * from the domain and discharging the record — pruning before labelling instead
 * of generate-and-reject.
 */
public class NeqFdBridgeTest {

	@Test(timeout = 5000)
	public void groundDisequalityIsExcludedFromTheDomainBeforeLabelling() {
		Unifiable<Long> x = lvar();
		Package[] beforeLabelling = new Package[1];
		Goal probe = s -> {
			beforeLabelling[0] = s;
			return Cont.just(s);
		};

		long count = dom(x, EnumeratedDomain.range(1L, 11L))       // {1..10}
				.and(Disequality.separate(x, lval(5L)))
				.and(probe)
				.solve(x)
				.count();

		assertThat(count).isEqualTo(9);
		assertThat(FiniteDomainConstraints.getDom(beforeLabelling[0], x.asVar().get())
				.get()
				.contains(5L))
				.as("5 should be excluded from x's domain before labelling")
				.isFalse();
	}

	@Test(timeout = 5000)
	public void exclusionCollapsingTheDomainInfersTheBinding() {
		Unifiable<Long> x = lvar();
		Package[] beforeLabelling = new Package[1];
		Goal probe = s -> {
			beforeLabelling[0] = s;
			return Cont.just(s);
		};

		long count = dom(x, EnumeratedDomain.range(4L, 6L))        // {4,5}
				.and(Disequality.separate(x, lval(5L)))
				.and(probe)
				.solve(x)
				.count();

		assertThat(count).isEqualTo(1);
		// {4,5} minus {5} collapses to {4}: x is BOUND by inference, not by search
		assertThat(MiniKanren.walk(beforeLabelling[0], x).isVal())
				.as("x should be bound to 4 before labelling")
				.isTrue();
	}

	@Test(timeout = 5000)
	public void exclusionEmptyingTheDomainFails() {
		Unifiable<Long> x = lvar();

		long count = dom(x, EnumeratedDomain.range(5L, 6L))        // {5} exactly
				.and(Disequality.separate(x, lval(5L)))
				.solve(x)
				.count();

		assertThat(count).isEqualTo(0);
	}

	@Test(timeout = 5000)
	public void disequalityStatedBeforeTheDomainStaysCorrect() {
		// the record path (no conversion for this order) — answers must be right anyway
		Unifiable<Long> x = lvar();

		assertThat(Disequality.separate(x, lval(5L))
				.and(dom(x, EnumeratedDomain.range(1L, 11L)))
				.solve(x)
				.map(Term::get)
				.collect(Collectors.toList()))
				.doesNotContain(5L)
				.hasSize(9);
	}

	@Test(timeout = 5000)
	public void nonArithmeticDisequalityKeepsItsRecord() {
		// a String disequality has no domain to bridge into; the Byrd record must
		// keep working exactly as before
		Unifiable<String> s = lvar();

		assertThat(Disequality.separate(s, lval("no"))
				.and(CKanren.unify(s, lval("yes")))
				.solve(s)
				.map(Term::get)
				.collect(Collectors.toList()))
				.containsExactly("yes");
	}
}
