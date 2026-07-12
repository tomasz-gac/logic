package com.tgac.logic.finitedomain;

// ABOUTME: Pins the toolkit coupling that terminates the unchecked cascade:
// ABOUTME: re-examination only with strict narrowing, collapse infers only.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import org.junit.Test;

public class DomainUpdateContractTest {

	private static final LVar<?> X = (LVar<?>) lvar().asVar().get();

	private static FiniteDomainConstraints store(Domain<Long> dom) {
		return FiniteDomainConstraints.empty().withDomain(X, dom);
	}

	private static String kind(Update step) {
		return step.match(() -> "fail", () -> "unchanged", applied -> "applied");
	}

	private static Update.Applied applied(Update step) {
		Update.Applied result = step.match(() -> null, () -> null, a -> a);
		assertThat(result).isNotNull();
		return result;
	}

	@Test
	public void equalDomainDoesNotReexamine() {
		Update step = DomainUpdate.apply(Package.empty(),
				store(Interval.of(0L, 10L)), X, Interval.of(0L, 10L));
		assertThat(kind(step)).isEqualTo("unchanged");
	}

	@Test
	public void narrowingReexaminesTheNarrowedVariable() {
		Update step = DomainUpdate.apply(Package.empty(),
				store(Interval.of(0L, 10L)), X, Interval.of(3L, 6L));
		assertThat(applied(step).reexamine()).containsExactly(X);
		assertThat(applied(step).inferred()).isEmpty();
	}

	@Test
	public void collapseInfersABindingWithoutReexamination() {
		Update step = DomainUpdate.apply(Package.empty(),
				store(Interval.of(0L, 10L)), X, Interval.of(5L, 5L));
		assertThat(applied(step).reexamine()).isEmpty();
		assertThat(applied(step).inferred()).hasSize(1);
	}

	@Test
	public void emptyIntersectionFails() {
		Update step = DomainUpdate.apply(Package.empty(),
				store(Interval.of(0L, 4L)), X, Interval.of(8L, 12L));
		assertThat(kind(step)).isEqualTo("fail");
	}
}
