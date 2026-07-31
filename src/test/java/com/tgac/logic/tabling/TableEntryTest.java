package com.tgac.logic.tabling;

// ABOUTME: One entry's cache semantics under produce/emit: master selection is
// ABOUTME: the claim CAS, deltas dedup by entailment, the fold absorbs duplicates.

import static com.tgac.logic.unification.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.AwaitResult;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple;
import java.util.ArrayList;
import java.util.Arrays;
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

	/** The entry's whole production as one claimed workforce. */
	@SafeVarargs
	private static Fiber<Nothing> production(TableEntry<Boolean> entry, AnswerKey... answers) {
		return Fiber.produce(entry.channel(), emit -> {
			Fiber<Nothing> tree = Fiber.done(Nothing.nothing());
			for (AnswerKey key : answers) {
				Fiber<Nothing> emitted = emit.emit(entry.answerDelta(key, true));
				tree = tree.flatMap(__ -> emitted);
			}
			return tree;
		});
	}

	@SafeVarargs
	private static void produced(TableEntry<Boolean> entry, AnswerKey... answers) {
		production(entry, answers).get();
	}

	/** A consumer past {@code cursor}, recording the completion it is handed. */
	private static Fiber<Nothing> consuming(TableEntry<Boolean> entry, int cursor,
			List<AwaitResult<Answers<Boolean>>> completions) {
		return Fiber.await(entry.channel(), v -> v.ground().size() > cursor)
				.flatMap(r -> {
					completions.add(r);
					return Fiber.done(Nothing.nothing());
				});
	}

	@Test
	public void testMasterSelectionIsTheClaimCas() {
		TableEntry<Boolean> entry = entry();
		List<String> ran = new ArrayList<>();

		// racing claimants are welcome: the CAS runs at the step, the first
		// spawn wins, and the loser's body is never built
		Fiber.produce(entry.channel(), emit -> {
			ran.add("first");
			return Fiber.done(Nothing.nothing());
		}).get();
		Fiber.produce(entry.channel(), emit -> {
			ran.add("second");
			return Fiber.done(Nothing.nothing());
		}).get();

		assertThat(ran).containsExactly("first");
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
		List<AwaitResult<Answers<Boolean>>> completions = new ArrayList<>();

		// no answers past the cursor, no master, no seal: the consumer parks,
		// and a drive out of work refuses to end with it stranded
		assertThatThrownBy(() -> consuming(entry, 0, completions).get())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("blocked")
				// the entry's channel is named by its call, so the strand names it
				.hasMessageContaining("alice");
		assertThat(completions).isEmpty();
	}

	@Test
	public void testACompletedEntryAnswersWithEof() {
		TableEntry<Boolean> entry = entry();

		produced(entry, answer(Tuple.of("charlie", "dave")));

		// the workforce drained, so the entry is complete: a late consumer
		// gets the terminal EOF with the final fold - nothing is lost
		List<AwaitResult<Answers<Boolean>>> completions = new ArrayList<>();
		consuming(entry, 0, completions).get();
		assertThat(completions).hasSize(1);
		assertThat(completions.get(0).getValue().ground().size()).isEqualTo(1);
		assertThat(completions.get(0).isSealed()).isTrue();
		assertThat(entry.isComplete()).isTrue();
	}

	@Test
	public void testGrowthWakesEveryHeldConsumer() {
		TableEntry<Boolean> entry = entry();
		List<AwaitResult<Answers<Boolean>>> completions = new ArrayList<>();

		Fiber.fork(Arrays.asList(
						consuming(entry, 0, completions),
						consuming(entry, 0, completions),
						consuming(entry, 0, completions)))
				.flatMap(__ -> production(entry, answer(Tuple.of("charlie", "dave"))))
				.get();

		assertThat(completions).hasSize(3);
		assertThat(completions.get(0).getValue().ground().size()).isEqualTo(1);
	}

	@Test
	public void testDuplicateAnswerDoesNotWakeAsGrowth() {
		TableEntry<Boolean> entry = entry();
		List<AwaitResult<Answers<Boolean>>> completions = new ArrayList<>();

		// a consumer past the cache end waits for a SECOND answer; the
		// duplicate is an inert join, so only the seal ever completes it
		Fiber.detach(consuming(entry, 1, completions))
				.flatMap(__ -> production(entry,
						answer(Tuple.of("charlie", "dave")),
						answer(Tuple.of("charlie", "dave"))))
				.get();

		assertThat(completions).hasSize(1);
		assertThat(completions.get(0).isSealed()).isTrue();
		assertThat(completions.get(0).getValue().ground().size()).isEqualTo(1);
	}
}
