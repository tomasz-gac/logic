package com.tgac.logic.tabling;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Hole;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

public class TableEntryTest {

	private static TableEntry<Boolean> entry() {
		Tabled<Object> relation = Tabling.define(args -> Goal.success());
		return new TableEntry<>(
				Call.of(relation, (Reified<?>) lval(Tuple.of("alice", "bob"))),
				Semirings.BOOLEAN);
	}

	private static Reified<?> answer(Object value) {
		return (Reified<?>) lval(value);
	}

	private static Registration registrationAt(int index) {
		return new Registration(
				p -> Fiber.done(Nothing.nothing()),
				Package.empty(),
				lvar().getObjectUnifiable(),
				index,
				null);
	}

	@Test
	public void testMasterSelection() {
		TableEntry<Boolean> entry = entry();

		// First caller becomes master
		assertThat(entry.tryBecomeMaster()).isTrue();

		// Subsequent callers cannot become master
		assertThat(entry.tryBecomeMaster()).isFalse();
	}

	@Test
	public void testAnswerCache() {
		TableEntry<Boolean> entry = entry();

		assertThat(entry.getAnswerCount()).isEqualTo(0);

		Reified<?> ans1 = answer(Tuple.of("alice", "bob"));
		Reified<?> ans2 = answer(Tuple.of("charlie", "dave"));

		assertThat(entry.addAnswer(ans1, true).isDefined()).isTrue();
		assertThat(entry.addAnswer(ans2, true).isDefined()).isTrue();

		assertThat(entry.getAnswerCount()).isEqualTo(2);
		assertThat(entry.getAnswerAt(0)._1).isEqualTo(ans1);
		assertThat(entry.getAnswerAt(1)._1).isEqualTo(ans2);
		assertThat(entry.getAnswerAt(2)).isNull();
	}

	@Test
	public void testDuplicateAnswerIsRejected() {
		TableEntry<Boolean> entry = entry();

		assertThat(entry.addAnswer(answer(Tuple.of("alice", "bob")), true).isDefined()).isTrue();
		assertThat(entry.addAnswer(answer(Tuple.of("alice", "bob")), true).isDefined()).isFalse();

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testAlphaEquivalentAnswerIsRejected() {
		TableEntry<Boolean> entry = entry();

		// Reified answers carry canonical hole names, so terms that
		// differ only in token objects are the same answer
		assertThat(entry.addAnswer(answer(Tuple.of(Hole.of(0), lval("bob"))), true).isDefined()).isTrue();
		assertThat(entry.addAnswer(answer(Tuple.of(Hole.of(0), lval("bob"))), true).isDefined()).isFalse();

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testRegistrationParksAtCacheEnd() {
		TableEntry<Boolean> entry = entry();

		assertThat(entry.park(registrationAt(0))).isTrue();
		assertThat(entry.registrationCount()).isEqualTo(1);
	}

	@Test
	public void testRegistrationRefusedWhenAnswersAvailable() {
		TableEntry<Boolean> entry = entry();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")), true);

		// The consumer has not seen answer 0 yet — it must keep consuming
		assertThat(entry.park(registrationAt(0))).isFalse();
		assertThat(entry.registrationCount()).isEqualTo(0);
	}

	@Test
	public void testAddAnswerDrainsRegistrations() {
		TableEntry<Boolean> entry = entry();

		assertThat(entry.park(registrationAt(0))).isTrue();
		assertThat(entry.park(registrationAt(0))).isTrue();
		assertThat(entry.park(registrationAt(0))).isTrue();

		Option<List<Registration>> drained = entry.addAnswer(answer(Tuple.of("charlie", "dave")), true);

		assertThat(drained.isDefined()).isTrue();
		assertThat(drained.get()).hasSize(3);
		assertThat(entry.registrationCount()).isEqualTo(0);
	}

	@Test
	public void testDuplicateAnswerDoesNotDrainRegistrations() {
		TableEntry<Boolean> entry = entry();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")), true);

		assertThat(entry.park(registrationAt(1))).isTrue();

		assertThat(entry.addAnswer(answer(Tuple.of("charlie", "dave")), true).isDefined()).isFalse();
		assertThat(entry.registrationCount()).isEqualTo(1);
	}
}
