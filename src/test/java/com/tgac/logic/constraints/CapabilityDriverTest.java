package com.tgac.logic.constraints;

import com.tgac.logic.constraints.store.Atom;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Suspension;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.tabling.Table;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.Test;

/**
 * Pins the driver's revision-routing guarantees
 * (docs/reference/constraint-kernel.md): contradictory inferred
 * bindings fail the branch instead of silently keeping the first, agreeing
 * bindings apply once, narrowed payloads broadcast to every store, runs splice
 * only after quiescence, and the agenda never leaks into answers.
 */
public class CapabilityDriverTest {

	/** A test-only constraint domain that emits configured inferences on every prefix. */
	private static abstract class EmittingFactor implements Factor<EmittingFactor> {
		final BiFunction<Prefix, Package, Revision> reaction;

		EmittingFactor(BiFunction<Prefix, Package, Revision> reaction) {
			this.reaction = reaction;
		}

		@Override
		public Fiber<Revision> normalize(Prefix prefix, Package state) {
			return Fiber.done(reaction.apply(prefix, state));
		}

		@Override
		public <T> Goal enforce(Term<T> x) {
			return Goal.success();
		}

		@Override
		public <A> Term<A> reify(Term<A> unifiable, Renaming renaming, Package p) {
			return unifiable;
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Theory<EmittingFactor> theory() {
			throw new UnsupportedOperationException("driver fixtures have no theory");
		}

		@Override
		public EmittingFactor meet(Atom<EmittingFactor> c) {
			return this;
		}

		@Override
		public EmittingFactor absorb(Theory<EmittingFactor> incoming) {
			return this;
		}

		@Override
		public boolean contains(Atom<EmittingFactor> c) {
			return false;
		}

		@Override
		public Tuple2<EmittingFactor, EmittingFactor> split(List<LVar<?>> vars) {
			throw new UnsupportedOperationException("driver fixtures do not split");
		}

		@Override
		public Fiber<EmittingFactor> rename(Renaming renaming) {
			throw new UnsupportedOperationException("driver fixtures do not rename");
		}

		@Override
		public EmittingFactor meet(EmittingFactor other) {
			throw new UnsupportedOperationException("driver fixtures do not meet");
		}

		@Override
		public Fiber<Revision> normalize(Package state) {
			return null;
		}
	}

	// two distinct classes: the store map is keyed by class
	private static final class FactorA extends EmittingFactor {
		FactorA(BiFunction<Prefix, Package, Revision> r) {
			super(r);
		}
	}

	private static class FactorB extends EmittingFactor {
		FactorB(BiFunction<Prefix, Package, Revision> r) {
			super(r);
		}
	}

	private static Package root(Packaged... stores) {
		Package p = Package.empty().withStore(Table.empty());
		for (Packaged s : stores) {
			p = p.putStore(s);
		}
		return p;
	}

	private static long solutions(Package root) {
		Unifiable<Long> x = lvar();
		return x.unifies(0L)
				.solveFrom(root, x, BreadthFirstScheduler::new)
				.count();
	}

	@Test(timeout = 5000)
	public void aRevisionMayOnlyReplaceItsOwnFactor() {
		// FactorA answers revise with a FactorB replacement: a
		// cross-family swap the driver must refuse by name - putStore would
		// otherwise silently overwrite ANOTHER family's factor
		Package root = root(
				new FactorA((prefix, state) -> Revision.updated(
						new FactorB((pf, st) -> Revision.unchanged()))));

		assertThatThrownBy(() -> solutions(root))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("FactorA")
				.hasMessageContaining("FactorB");
	}

	@Test(timeout = 5000)
	public void contradictoryInferredBindingsFailTheBranch() {
		LVar<Long> q = LVar.<Long> lvar().asVar().get();

		Package root = root(
				new FactorA((prefix, state) -> Revision.updated(new FactorA((pf, st) -> Revision.unchanged()))
						.withInferred(Prefix.binding(state.substitution(), q, lval(1L)).get())),
				new FactorB((prefix, state) -> Revision.updated(new FactorB((pf, st) -> Revision.unchanged()))
						.withInferred(Prefix.binding(state.substitution(), q, lval(2L)).get())));

		// two stores infer q=1 and q=2 in one pass: the branch is inconsistent and
		// must DIE — the silent keep-first would instead emit a wrong answer
		assertThat(solutions(root)).isEqualTo(0);
	}

	@Test(timeout = 5000)
	public void agreeingInferredBindingsApplyOnce() {
		LVar<Long> q = LVar.<Long> lvar().asVar().get();

		Package root = root(
				new FactorA((prefix, state) -> Revision.updated(new FactorA((pf, st) -> Revision.unchanged()))
						.withInferred(Prefix.binding(state.substitution(), q, lval(1L)).get())),
				new FactorB((prefix, state) -> Revision.updated(new FactorB((pf, st) -> Revision.unchanged()))
						.withInferred(Prefix.binding(state.substitution(), q, lval(1L)).get())));

		assertThat(solutions(root)).isEqualTo(1);
	}

	@Test(timeout = 5000)
	public void agendaNeverLeaksIntoAnswers() {
		LVar<Long> q = LVar.<Long> lvar().asVar().get();
		Package[] answer = new Package[1];
		Goal probe = s -> {
			answer[0] = s;
			return Cont.just(s);
		};

		Package root = root(
				new FactorA((prefix, state) -> Revision.updated(new FactorA((pf, st) -> Revision.unchanged()))
						.withInferred(Prefix.binding(state.substitution(), q, lval(1L)).get())));

		Unifiable<Long> x = lvar();
		long count = x.unifies(0L)
				.and(probe)
				.solveFrom(root, x, BreadthFirstScheduler::new)
				.count();

		assertThat(count).isEqualTo(1);
		// quiescence removes the agenda; a leaked store would ride every
		// subsequent package of the branch
		assertThat(answer[0].getStores().keySet().toJavaStream()
				.anyMatch(c -> c.getSimpleName().equals("Agenda")))
				.as("the agenda must be removed at quiescence")
				.isFalse();
	}

	@Test(timeout = 5000)
	public void runPayloadSplicesAfterQuiescence() {
		Package[] seen = new Package[1];
		Goal probe = s -> {
			seen[0] = s;
			return Cont.just(s);
		};

		Package root = root(
				new FactorA((prefix, state) ->
						Revision.updated(new FactorA((pf, st) -> Revision.unchanged()))
								.withSuspend(Suspension.of(
										Collections.emptyList(), st -> true, probe))));

		assertThat(solutions(root)).isEqualTo(1);
		assertThat(seen[0]).as("the run goal must execute").isNotNull();
		// runs splice only after the drain quiesces and the agenda is removed
		assertThat(seen[0].getStores().keySet().toJavaStream()
				.anyMatch(c -> c.getSimpleName().equals("Agenda")))
				.as("a spliced run sees no agenda")
				.isFalse();
	}
}
