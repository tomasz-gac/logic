package com.tgac.logic.tabling;

import static com.tgac.logic.unification.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.fibers.Await;
import com.tgac.functional.fibers.primitives.JoinMap;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TableEntryTest {

	private static TableEntry<Boolean> entry() {
		Tabled<Object> relation = Tabling.define(args -> Goal.success());
		return new TableEntry<>(
				Call.of(relation, (Reified<?>) lval(Tuple.of("alice", "bob"))),
				Semirings.BOOLEAN);
	}

	private static AnswerKey answer(Object value) {
		return AnswerKey.of((Reified<?>) lval(value));
	}

	/** A consumer's waiter, recording the answers each completion hands it. */
	private static Await.Waiter<JoinMap<AnswerKey, Boolean>> recording(
			List<Await.Result<JoinMap<AnswerKey, Boolean>>> completions) {
		return completions::add;
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
	public void testConsumerIsHeldAtCacheEnd() {
		TableEntry<Boolean> entry = entry();
		List<Await.Result<JoinMap<AnswerKey, Boolean>>> completions = new ArrayList<>();

		// no answers past the cursor: the suspend holds the waiter
		entry.source().suspend(v -> v.size() > 0, recording(completions));
		assertThat(completions).isEmpty();
	}

	@Test
	public void testConsumerIsAnsweredWhenAnswersAvailable() {
		TableEntry<Boolean> entry = entry();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")), true);

		// the consumer has not seen answer 0 yet — the completion arrives at
		// once, possibly synchronously: an await always yields
		List<Await.Result<JoinMap<AnswerKey, Boolean>>> completions = new ArrayList<>();
		entry.source().suspend(v -> v.size() > 0, recording(completions));
		assertThat(completions).hasSize(1);
		assertThat(completions.get(0).getValue().size()).isEqualTo(1);
		assertThat(completions.get(0).isSealed()).isFalse();
	}

	@Test
	public void testGrowthWakesEveryHeldConsumer() {
		TableEntry<Boolean> entry = entry();
		List<Await.Result<JoinMap<AnswerKey, Boolean>>> completions = new ArrayList<>();

		entry.source().suspend(v -> v.size() > 0, recording(completions));
		entry.source().suspend(v -> v.size() > 0, recording(completions));
		entry.source().suspend(v -> v.size() > 0, recording(completions));
		assertThat(completions).isEmpty();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")), true).get();

		assertThat(completions).hasSize(3);
		assertThat(completions.get(0).getValue().size()).isEqualTo(1);
	}

	@Test
	public void testDuplicateAnswerDoesNotWake() {
		TableEntry<Boolean> entry = entry();
		List<Await.Result<JoinMap<AnswerKey, Boolean>>> completions = new ArrayList<>();

		entry.addAnswer(answer(Tuple.of("charlie", "dave")), true);

		// a consumer past the cache end waits for a SECOND answer
		entry.source().suspend(v -> v.size() > 1, recording(completions));

		entry.addAnswer(answer(Tuple.of("charlie", "dave")), true).get();
		assertThat(completions).isEmpty();
	}
}
