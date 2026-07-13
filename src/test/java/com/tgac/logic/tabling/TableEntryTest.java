package com.tgac.logic.tabling;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.tabling.TableEntry.Registration;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.ReifiedVar;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

public class TableEntryTest {

	private static TableEntry entry() {
		Tabled<Object> relation = Tabling.define(args -> Goal.success());
		return new TableEntry(Call.of(relation, (Reified<?>) lval(Tuple.of("alice", "bob"))));
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
		TableEntry entry = entry();

		// First caller becomes master
		assertThat(entry.tryBecomeMaster()).isTrue();

		// Subsequent callers cannot become master
		assertThat(entry.tryBecomeMaster()).isFalse();
	}

	@Test
	public void testAnswerCache() {
		TableEntry entry = entry();

		assertThat(entry.getAnswerCount()).isEqualTo(0);

		Reified<?> ans1 = answer(Tuple.of("alice", "bob"));
		Reified<?> ans2 = answer(Tuple.of("charlie", "dave"));

		assertThat(entry.addAnswer(ans1).isDefined()).isTrue();
		assertThat(entry.addAnswer(ans2).isDefined()).isTrue();

		assertThat(entry.getAnswerCount()).isEqualTo(2);
		assertThat(entry.getAnswerAt(0)).isEqualTo(ans1);
		assertThat(entry.getAnswerAt(1)).isEqualTo(ans2);
		assertThat(entry.getAnswerAt(2)).isNull();
	}

	@Test
	public void testDuplicateAnswerIsRejected() {
		TableEntry entry = entry();

		assertThat(entry.addAnswer(answer(Tuple.of("alice", "bob"))).isDefined()).isTrue();
		assertThat(entry.addAnswer(answer(Tuple.of("alice", "bob"))).isDefined()).isFalse();

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testAlphaEquivalentAnswerIsRejected() {
		TableEntry entry = entry();

		// Reified answers carry canonical hole names, so terms that
		// differ only in token objects are the same answer
		assertThat(entry.addAnswer(answer(Tuple.of(ReifiedVar.of("_.0"), lval("bob")))).isDefined()).isTrue();
		assertThat(entry.addAnswer(answer(Tuple.of(ReifiedVar.of("_.0"), lval("bob")))).isDefined()).isFalse();

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testRegistrationParksAtCacheEnd() {
		TableEntry entry = entry();

		assertThat(entry.register(registrationAt(0))).isTrue();
		assertThat(entry.getRegistrationCount()).isEqualTo(1);
	}

	@Test
	public void testRegistrationRefusedWhenAnswersAvailable() {
		TableEntry entry = entry();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")));

		// The consumer has not seen answer 0 yet — it must keep consuming
		assertThat(entry.register(registrationAt(0))).isFalse();
		assertThat(entry.getRegistrationCount()).isEqualTo(0);
	}

	@Test
	public void testAddAnswerDrainsRegistrations() {
		TableEntry entry = entry();

		assertThat(entry.register(registrationAt(0))).isTrue();
		assertThat(entry.register(registrationAt(0))).isTrue();
		assertThat(entry.register(registrationAt(0))).isTrue();

		Option<List<Registration>> drained = entry.addAnswer(answer(Tuple.of("charlie", "dave")));

		assertThat(drained.isDefined()).isTrue();
		assertThat(drained.get()).hasSize(3);
		assertThat(entry.getRegistrationCount()).isEqualTo(0);
	}

	@Test
	public void testDuplicateAnswerDoesNotDrainRegistrations() {
		TableEntry entry = entry();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")));

		assertThat(entry.register(registrationAt(1))).isTrue();

		assertThat(entry.addAnswer(answer(Tuple.of("charlie", "dave"))).isDefined()).isFalse();
		assertThat(entry.getRegistrationCount()).isEqualTo(1);
	}
}
