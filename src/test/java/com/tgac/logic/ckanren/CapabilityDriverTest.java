package com.tgac.logic.ckanren;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.ckanren.store.ConstraintStore;
import com.tgac.logic.ckanren.store.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.tabling.Table;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.Test;

/**
 * Pins the driver's revision-routing guarantees
 * (docs/design/minimal-constraint-vocabulary.md §2.2): contradictory inferred
 * bindings fail the branch instead of silently keeping the first, agreeing
 * bindings apply once, narrowed payloads broadcast to every store, runs splice
 * only after quiescence, and the agenda never leaks into answers.
 */
public class CapabilityDriverTest {

	/** A test-only constraint domain that emits configured inferences on every prefix. */
	private static abstract class EmittingStore implements ConstraintStore {
		final BiFunction<Prefix, Package, Revision> reaction;

		EmittingStore(BiFunction<Prefix, Package, Revision> reaction) {
			this.reaction = reaction;
		}

		@Override
		public Revision revise(Prefix prefix, Package state) {
			return reaction.apply(prefix, state);
		}

		@Override
		public <T> Goal enforce(Term<T> x) {
			return Goal.success();
		}

		@Override
		public <A> Term<A> reify(Term<A> unifiable, Package renameSubstitutions, Package p) {
			return unifiable;
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Store remove(Stored c) {
			return this;
		}

		@Override
		public Store prepend(Stored c) {
			return this;
		}

		@Override
		public boolean contains(Stored c) {
			return false;
		}
	}

	// two distinct classes: the store map is keyed by class
	private static final class StoreA extends EmittingStore {
		StoreA(BiFunction<Prefix, Package, Revision> r) {
			super(r);
		}
	}

	private static class StoreB extends EmittingStore {
		StoreB(BiFunction<Prefix, Package, Revision> r) {
			super(r);
		}
	}

	private static Package root(Store... stores) {
		Package p = Package.empty().withStore(Table.empty());
		for (Store s : stores) {
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
	public void contradictoryInferredBindingsFailTheBranch() {
		LVar<Long> q = LVar.<Long> lvar().asVar().get();

		Package root = root(
				new StoreA((prefix, state) -> Revision.updated(new StoreA((pf, st) -> Revision.unchanged()))
						.withInferred(Prefix.binding(state, q, lval(1L)).get())),
				new StoreB((prefix, state) -> Revision.updated(new StoreB((pf, st) -> Revision.unchanged()))
						.withInferred(Prefix.binding(state, q, lval(2L)).get())));

		// two stores infer q=1 and q=2 in one pass: the branch is inconsistent and
		// must DIE — the silent keep-first would instead emit a wrong answer
		assertThat(solutions(root)).isEqualTo(0);
	}

	@Test(timeout = 5000)
	public void agreeingInferredBindingsApplyOnce() {
		LVar<Long> q = LVar.<Long> lvar().asVar().get();

		Package root = root(
				new StoreA((prefix, state) -> Revision.updated(new StoreA((pf, st) -> Revision.unchanged()))
						.withInferred(Prefix.binding(state, q, lval(1L)).get())),
				new StoreB((prefix, state) -> Revision.updated(new StoreB((pf, st) -> Revision.unchanged()))
						.withInferred(Prefix.binding(state, q, lval(1L)).get())));

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
				new StoreA((prefix, state) -> Revision.updated(new StoreA((pf, st) -> Revision.unchanged()))
						.withInferred(Prefix.binding(state, q, lval(1L)).get())));

		Unifiable<Long> x = lvar();
		long count = x.unifies(0L)
				.and(probe)
				.solveFrom(root, x, BreadthFirstScheduler::new)
				.count();

		assertThat(count).isEqualTo(1);
		// quiescence removes the agenda; a leaked store would ride every
		// subsequent package of the branch
		assertThat(answer[0].getConstraints().keySet().toJavaStream()
				.anyMatch(c -> c.getSimpleName().equals("Agenda")))
				.as("the agenda must be removed at quiescence")
				.isFalse();
	}

	@Test(timeout = 5000)
	public void narrowedPayloadBroadcastsToEveryStore() {
		AtomicInteger examinations = new AtomicInteger();
		Term<Long> t = lvar();

		StoreB listening = new StoreB((prefix, state) -> Revision.unchanged()) {
			@Override
			public Revision narrowed(Term<?> x, Package state) {
				if (x == t) {
					examinations.incrementAndGet();
				}
				return Revision.unchanged();
			}
		};
		Package root = root(
				new StoreA((prefix, state) ->
						Revision.updated(new StoreA((pf, st) -> Revision.unchanged()))
								.withNarrowed(t)),
				listening);

		assertThat(solutions(root)).isEqualTo(1);
		assertThat(examinations.get())
				.as("one narrowed payload: every store re-examines exactly once")
				.isEqualTo(1);
	}

	@Test(timeout = 5000)
	public void runPayloadSplicesAfterQuiescence() {
		Package[] seen = new Package[1];
		Goal probe = s -> {
			seen[0] = s;
			return Cont.just(s);
		};

		Package root = root(
				new StoreA((prefix, state) ->
						Revision.updated(new StoreA((pf, st) -> Revision.unchanged()))
								.withRun(probe)));

		assertThat(solutions(root)).isEqualTo(1);
		assertThat(seen[0]).as("the run goal must execute").isNotNull();
		// runs splice only after the drain quiesces and the agenda is removed
		assertThat(seen[0].getConstraints().keySet().toJavaStream()
				.anyMatch(c -> c.getSimpleName().equals("Agenda")))
				.as("a spliced run sees no agenda")
				.isFalse();
	}
}
