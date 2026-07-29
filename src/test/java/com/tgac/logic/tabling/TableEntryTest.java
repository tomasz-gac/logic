package com.tgac.logic.tabling;

// ABOUTME: One entry's cache semantics under produceTo/emit: master selection is
// ABOUTME: the plant CAS, deltas dedup by entailment, the fold absorbs duplicates.

import static com.tgac.logic.unification.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Await;
import com.tgac.functional.fibers.Fiber;
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

	/** Run the entry's whole production as one planted workforce. */
	@SafeVarargs
	private static void produced(TableEntry<Boolean> entry, AnswerKey... answers) {
		Fiber.produceTo(entry.source(), emit -> {
			Fiber<Nothing> tree = Fiber.done(Nothing.nothing());
			for (AnswerKey key : answers) {
				Fiber<Nothing> emitted = entry.answerDelta(key, true)
						.map(emit::emit)
						.getOrElse(Fiber.done(Nothing.nothing()));
				tree = tree.flatMap(__ -> emitted);
			}
			return tree;
		}).get();
	}

	/** A consumer's waiter, recording the answers each completion hands it. */
	private static Await.Waiter<JoinMap<AnswerKey, Boolean>> recording(
			List<Await.Result<JoinMap<AnswerKey, Boolean>>> completions) {
		return completions::add;
	}

	@Test
	public void testMasterSelectionIsThePlantCas() {
		TableEntry<Boolean> entry = entry();

		// First caller wins the plant; later callers consume
		assertThat(Fiber.tryProduceTo(entry.source(), emit -> Fiber.done(Nothing.nothing()))
				.isDefined()).isTrue();
		assertThat(Fiber.tryProduceTo(entry.source(), emit -> Fiber.done(Nothing.nothing()))
				.isDefined()).isFalse();
	}

	@Test
	public void testAnswerCache() {
		TableEntry<Boolean> entry = entry();

		assertThat(entry.getAnswerCount()).isEqualTo(0);

		AnswerKey ans1 = answer(Tuple.of("alice", "bob"));
		AnswerKey ans2 = answer(Tuple.of("charlie", "dave"));
		produced(entry, ans1, ans2);

		assertThat(entry.getAnswerCount()).isEqualTo(2);
		assertThat(entry.getAnswerAt(0)._1).isEqualTo(ans1);
		assertThat(entry.getAnswerAt(1)._1).isEqualTo(ans2);
		assertThat(entry.getAnswerAt(2)).isNull();
	}

	@Test
	public void testDuplicateAnswerIsAnInertJoin() {
		TableEntry<Boolean> entry = entry();

		produced(entry,
				answer(Tuple.of("alice", "bob")),
				answer(Tuple.of("alice", "bob")));

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testAlphaEquivalentAnswerIsAnInertJoin() {
		TableEntry<Boolean> entry = entry();

		// Reified answers carry canonical hole names, so terms that
		// differ only in token objects are the same answer
		produced(entry,
				answer(Tuple.of(Hole.of(0), lval("bob"))),
				answer(Tuple.of(Hole.of(0), lval("bob"))));

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testConsumerIsHeldAtCacheEnd() {
		TableEntry<Boolean> entry = entry();
		List<Await.Result<JoinMap<AnswerKey, Boolean>>> completions = new ArrayList<>();

		// no answers past the cursor, no seal: the suspend holds the waiter
		entry.source().suspend(v -> v.size() > 0, recording(completions));
		assertThat(completions).isEmpty();
	}

	@Test
	public void testACompletedEntryAnswersWithEof() {
		TableEntry<Boolean> entry = entry();

		produced(entry, answer(Tuple.of("charlie", "dave")));

		// the workforce drained, so the entry is complete: a late consumer
		// gets the terminal EOF with the final fold - nothing is lost
		List<Await.Result<JoinMap<AnswerKey, Boolean>>> completions = new ArrayList<>();
		entry.source().suspend(v -> v.size() > 0, recording(completions));
		assertThat(completions).hasSize(1);
		assertThat(completions.get(0).getValue().size()).isEqualTo(1);
		assertThat(completions.get(0).isSealed()).isTrue();
		assertThat(entry.isComplete()).isTrue();
	}

	@Test
	public void testGrowthWakesEveryHeldConsumer() {
		TableEntry<Boolean> entry = entry();
		List<Await.Result<JoinMap<AnswerKey, Boolean>>> completions = new ArrayList<>();

		entry.source().suspend(v -> v.size() > 0, recording(completions));
		entry.source().suspend(v -> v.size() > 0, recording(completions));
		entry.source().suspend(v -> v.size() > 0, recording(completions));
		assertThat(completions).isEmpty();

		produced(entry, answer(Tuple.of("charlie", "dave")));

		assertThat(completions).hasSize(3);
		assertThat(completions.get(0).getValue().size()).isEqualTo(1);
	}

	@Test
	public void testDuplicateAnswerDoesNotWakeAsGrowth() {
		TableEntry<Boolean> entry = entry();
		List<Await.Result<JoinMap<AnswerKey, Boolean>>> completions = new ArrayList<>();

		// a consumer past the cache end waits for a SECOND answer; the
		// duplicate is an inert join, so only the seal ever completes it
		entry.source().suspend(v -> v.size() > 1, recording(completions));

		produced(entry,
				answer(Tuple.of("charlie", "dave")),
				answer(Tuple.of("charlie", "dave")));

		assertThat(completions).hasSize(1);
		assertThat(completions.get(0).isSealed()).isTrue();
		assertThat(completions.get(0).getValue().size()).isEqualTo(1);
	}
}
