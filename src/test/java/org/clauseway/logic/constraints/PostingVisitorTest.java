package org.clauseway.logic.constraints;

// ABOUTME: The visitor seam over the closed rows: each row dispatches to its
// ABOUTME: visit overload, and Named unwraps by default — labels are presentation.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.finitedomain.FiniteDomain;
import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.Prefix;
import org.clauseway.logic.unification.terms.Unifiable;
import org.junit.Test;

public class PostingVisitorTest {

	private static class RowName implements Posting.Visitor<String> {
		@Override
		public String visit(Unification<?> unification) {
			return "unification";
		}

		@Override
		public String visit(Posting.Resolution resolution) {
			return "resolution";
		}

		@Override
		public String visit(Posting.Activation activation) {
			return "activation";
		}

		@Override
		public String visit(Posting.Absorption absorption) {
			return "absorption";
		}

		@Override
		public String visit(Posting.AllOf all) {
			return "all";
		}
	}

	@Test
	public void everyRowDispatchesToItsOverload() {
		Unifiable<Integer> x = lvar();
		Unifiable<Long> y = lvar();

		Posting unification = x.unifies(3);
		Posting resolution = Propagation.resolve(Prefix.binding(
				Package.empty().substitution(), (LVar<?>) x.asVar().get(), lval(3)).get());
		Posting activation = FiniteDomain.dom(y, Longs.range(0, 5));
		Posting all = Posting.all(x.unifies(3), x.unifies(4));

		RowName visitor = new RowName();
		assertThat(unification.accept(visitor)).isEqualTo("unification");
		assertThat(resolution.accept(visitor)).isEqualTo("resolution");
		assertThat(activation.accept(visitor)).isEqualTo("activation");
		assertThat(all.accept(visitor)).isEqualTo("all");
	}

	@Test
	public void namedUnwrapsByDefaultAndAnOverrideSeesIt() {
		// a BARE unification wrapped once — x.unifies(3) itself is already
		// Named (the ≡ trace label), which the row test above sees through
		Unifiable<Integer> x = lvar();
		Posting labelled = Unification.of(x, lval(3), false).named("the bind");

		assertThat(labelled.accept(new RowName())).isEqualTo("unification");

		Posting.Visitor<String> caring = new RowName() {
			@Override
			public String visit(Posting.Named named) {
				return "named:" + named.getInner().accept(this);
			}
		};
		assertThat(labelled.accept(caring)).isEqualTo("named:unification");
	}
}
