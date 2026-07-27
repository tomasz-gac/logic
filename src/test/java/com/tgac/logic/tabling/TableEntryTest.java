package com.tgac.logic.tabling;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple;
import java.util.ArrayList;
import org.junit.Test;

public class TableEntryTest {

	private static TableEntry<Boolean> entry() {
		return entry(new ArrayList<>());
	}

	private static TableEntry<Boolean> entry(java.util.List<Registration> fed) {
		Tabled<Object> relation = Tabling.define(args -> Goal.success());
		return new TableEntry<>(
				Call.of(relation, (Reified<?>) lval(Tuple.of("alice", "bob"))),
				Semirings.BOOLEAN,
				(e, r, answers) -> {
					fed.add(r);
					return Fiber.done(Nothing.nothing());
				});
	}

	private static AnswerKey answer(Object value) {
		return AnswerKey.of((Reified<?>) lval(value));
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

		AnswerKey ans1 = answer(Tuple.of("alice", "bob"));
		AnswerKey ans2 = answer(Tuple.of("charlie", "dave"));

		entry.addAnswer(ans1, true).get();
		entry.addAnswer(ans2, true).get();

		assertThat(entry.getAnswerCount()).isEqualTo(2);
		assertThat(entry.getAnswerAt(0)._1).isEqualTo(ans1);
		assertThat(entry.getAnswerAt(1)._1).isEqualTo(ans2);
		assertThat(entry.getAnswerAt(2)).isNull();
	}

	@Test
	public void testDuplicateAnswerIsRejected() {
		TableEntry<Boolean> entry = entry();

		entry.addAnswer(answer(Tuple.of("alice", "bob")), true).get();
		entry.addAnswer(answer(Tuple.of("alice", "bob")), true).get();

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testAlphaEquivalentAnswerIsRejected() {
		TableEntry<Boolean> entry = entry();

		// Reified answers carry canonical hole names, so terms that
		// differ only in token objects are the same answer
		entry.addAnswer(answer(Tuple.of(Hole.of(0), lval("bob"))), true).get();
		entry.addAnswer(answer(Tuple.of(Hole.of(0), lval("bob"))), true).get();

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testRegistrationParksAtCacheEnd() {
		TableEntry<Boolean> entry = entry();

		assertThat(entry.parkFrom(registrationAt(0)).isRight()).isTrue();
		assertThat(entry.parkedCount()).isEqualTo(1);
	}

	@Test
	public void testRegistrationRefusedWhenAnswersAvailable() {
		TableEntry<Boolean> entry = entry();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")), true);

		// The consumer has not seen answer 0 yet — it must keep consuming
		assertThat(entry.parkFrom(registrationAt(0)).isLeft()).isTrue();
		assertThat(entry.parkedCount()).isEqualTo(0);
	}

	@Test
	public void testGrowthFeedsEveryParkedRegistration() {
		java.util.List<Registration> fed = new ArrayList<>();
		TableEntry<Boolean> entry = entry(fed);

		assertThat(entry.parkFrom(registrationAt(0)).isRight()).isTrue();
		assertThat(entry.parkFrom(registrationAt(0)).isRight()).isTrue();
		assertThat(entry.parkFrom(registrationAt(0)).isRight()).isTrue();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")), true).get();

		assertThat(fed).hasSize(3);
		assertThat(entry.parkedCount()).isEqualTo(0);
	}

	@Test
	public void testDuplicateAnswerDoesNotDrainRegistrations() {
		TableEntry<Boolean> entry = entry();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")), true);

		assertThat(entry.parkFrom(registrationAt(1)).isRight()).isTrue();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")), true).get();
		assertThat(entry.parkedCount()).isEqualTo(1);
	}
}
