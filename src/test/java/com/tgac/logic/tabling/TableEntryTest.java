package com.tgac.logic.tabling;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.tabling.TableEntry.Registration;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.ReifiedVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

public class TableEntryTest {

	private static Registration registrationAt(int index) {
		return new Registration(
				p -> Fiber.done(Nothing.nothing()),
				Package.empty(),
				List.of(lvar().getObjectUnifiable()),
				index);
	}

	@Test
	public void testMasterSelection() {
		Call call = Call.of("parent", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		// First caller becomes master
		assertThat(entry.tryBecomeMaster()).isTrue();

		// Subsequent callers cannot become master
		assertThat(entry.tryBecomeMaster()).isFalse();
	}

	@Test
	public void testAnswerCache() {
		Call call = Call.of("parent", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		assertThat(entry.getAnswerCount()).isEqualTo(0);

		// Add some answers (answer terms are lists of reified arguments)
		List<Term<?>> ans1 = List.of(lval("alice"), lval("bob"));
		List<Term<?>> ans2 = List.of(lval("charlie"), lval("dave"));

		assertThat(entry.addAnswer(ans1).isDefined()).isTrue();
		assertThat(entry.addAnswer(ans2).isDefined()).isTrue();

		assertThat(entry.getAnswerCount()).isEqualTo(2);
		assertThat(entry.getAnswerAt(0)).isEqualTo(ans1);
		assertThat(entry.getAnswerAt(1)).isEqualTo(ans2);
		assertThat(entry.getAnswerAt(2)).isNull();
	}

	@Test
	public void testDuplicateAnswerIsRejected() {
		Call call = Call.of("parent", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		assertThat(entry.addAnswer(List.of(lval("alice"), lval("bob"))).isDefined()).isTrue();
		assertThat(entry.addAnswer(List.of(lval("alice"), lval("bob"))).isDefined()).isFalse();

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testAlphaEquivalentAnswerIsRejected() {
		Call call = Call.of("parent", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		// Reified answer terms carry canonical hole names, so terms that
		// differ only in token objects are the same answer
		List<Term<?>> first = List.of(ReifiedVar.of("_.0"), lval("bob"));
		List<Term<?>> second = List.of(ReifiedVar.of("_.0"), lval("bob"));

		assertThat(entry.addAnswer(first).isDefined()).isTrue();
		assertThat(entry.addAnswer(second).isDefined()).isFalse();

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testRegistrationParksAtCacheEnd() {
		Call call = Call.of("ancestor", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		assertThat(entry.register(registrationAt(0))).isTrue();
		assertThat(entry.getRegistrationCount()).isEqualTo(1);
	}

	@Test
	public void testRegistrationRefusedWhenAnswersAvailable() {
		Call call = Call.of("ancestor", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		entry.addAnswer(List.of(lval("charlie"), lval("dave")));

		// The consumer has not seen answer 0 yet — it must keep consuming
		assertThat(entry.register(registrationAt(0))).isFalse();
		assertThat(entry.getRegistrationCount()).isEqualTo(0);
	}

	@Test
	public void testAddAnswerDrainsRegistrations() {
		Call call = Call.of("ancestor", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		assertThat(entry.register(registrationAt(0))).isTrue();
		assertThat(entry.register(registrationAt(0))).isTrue();
		assertThat(entry.register(registrationAt(0))).isTrue();

		Option<List<Registration>> drained = entry.addAnswer(List.of(lval("charlie"), lval("dave")));

		assertThat(drained.isDefined()).isTrue();
		assertThat(drained.get()).hasSize(3);
		assertThat(entry.getRegistrationCount()).isEqualTo(0);
	}

	@Test
	public void testDuplicateAnswerDoesNotDrainRegistrations() {
		Call call = Call.of("ancestor", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		List<Term<?>> ans = List.of(lval("charlie"), lval("dave"));
		entry.addAnswer(ans);

		assertThat(entry.register(registrationAt(1))).isTrue();

		assertThat(entry.addAnswer(List.of(lval("charlie"), lval("dave"))).isDefined()).isFalse();
		assertThat(entry.getRegistrationCount()).isEqualTo(1);
	}
}
