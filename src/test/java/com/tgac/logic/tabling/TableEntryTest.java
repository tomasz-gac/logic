package com.tgac.logic.tabling;

// ABOUTME: One entry's cache semantics under produce/emit: master selection is
// ABOUTME: the claim CAS, deltas dedup by the cell's fold, duplicates are inert.

import static com.tgac.logic.unification.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	private static TableEntry<Condition> entry() {
		Tabled<Object> relation = Tabling.define(args -> Goal.success());
		return new TableEntry<>(
				Call.of(relation, (Reified<?>) lval(Tuple.of("alice", "bob"))),
				Condition.RING);
	}

	private static Reified<?> answer(Object value) {
		return (Reified<?>) lval(value);
	}

	/** The entry's whole production as one claimed workforce. */
	private static Fiber<Nothing> production(TableEntry<Condition> entry, Reified<?>... answers) {
		return Fiber.produce(entry.channel(), emit -> {
			Fiber<Nothing> tree = Fiber.done(Nothing.nothing());
			for (Reified<?> term : answers) {
				Fiber<Nothing> emitted = emit.emit(entry.answerDelta(term, Condition.ONE));
				tree = tree.flatMap(__ -> emitted);
			}
			return tree;
		});
	}

	private static void produced(TableEntry<Condition> entry, Reified<?>... answers) {
		production(entry, answers).ground();
	}

	/** A consumer past {@code cursor}, recording the completion it is handed. */
	private static Fiber<Nothing> consuming(TableEntry<Condition> entry, int cursor,
			List<AwaitResult<JoinMap<Reified<?>, Condition>>> completions) {
		return Fiber.await(entry.channel(), v -> v.logSize() > cursor)
				.flatMap(r -> {
					completions.add(r);
					return Fiber.done(Nothing.nothing());
				});
	}

	@Test
	public void testMasterSelectionIsTheClaimCas() {
		TableEntry<Condition> entry = entry();
		List<String> ran = new ArrayList<>();

		// racing claimants are welcome: the CAS runs at the step, the first
		// spawn wins, and the loser's body is never built
		Fiber.produce(entry.channel(), emit -> {
			ran.add("first");
			return Fiber.done(Nothing.nothing());
		}).ground();
		Fiber.produce(entry.channel(), emit -> {
			ran.add("second");
			return Fiber.done(Nothing.nothing());
		}).ground();

		assertThat(ran).containsExactly("first");
	}

	@Test
	public void testAnswerCache() {
		TableEntry<Condition> entry = entry();

		assertThat(entry.getAnswerCount()).isEqualTo(0);

		Reified<?> ans1 = answer(Tuple.of("alice", "bob"));
		Reified<?> ans2 = answer(Tuple.of("charlie", "dave"));
		produced(entry, ans1, ans2);

		assertThat(entry.getAnswerCount()).isEqualTo(2);
		assertThat(entry.answerTerms()).containsExactly(ans1, ans2);
	}

	@Test
	public void testDuplicateAnswerIsAnInertJoin() {
		TableEntry<Condition> entry = entry();

		produced(entry,
				answer(Tuple.of("alice", "bob")),
				answer(Tuple.of("alice", "bob")));

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testAlphaEquivalentAnswerIsAnInertJoin() {
		TableEntry<Condition> entry = entry();

		// Reified answers carry canonical hole names, so terms that
		// differ only in token objects are the same answer
		produced(entry,
				answer(Tuple.of(Hole.of(0), lval("bob"))),
				answer(Tuple.of(Hole.of(0), lval("bob"))));

		assertThat(entry.getAnswerCount()).isEqualTo(1);
	}

	@Test
	public void testConsumerIsHeldAtCacheEnd() {
		TableEntry<Condition> entry = entry();
		List<AwaitResult<JoinMap<Reified<?>, Condition>>> completions = new ArrayList<>();

		// no answers past the cursor, no master, no seal: the consumer parks,
		// and a drive out of work refuses to end with it stranded
		assertThatThrownBy(() -> consuming(entry, 0, completions).ground())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("blocked")
				// the entry's channel is named by its call, so the strand names it
				.hasMessageContaining("alice");
		assertThat(completions).isEmpty();
	}

	@Test
	public void testACompletedEntryAnswersWithEof() {
		TableEntry<Condition> entry = entry();

		produced(entry, answer(Tuple.of("charlie", "dave")));

		// the workforce drained, so the entry is complete: a late consumer
		// gets the terminal EOF with the final fold - nothing is lost
		List<AwaitResult<JoinMap<Reified<?>, Condition>>> completions = new ArrayList<>();
		consuming(entry, 0, completions).ground();
		assertThat(completions).hasSize(1);
		assertThat(completions.get(0).getValue().size()).isEqualTo(1);
		assertThat(completions.get(0).isSealed()).isTrue();
		assertThat(entry.isComplete()).isTrue();
	}

	@Test
	public void testGrowthWakesEveryHeldConsumer() {
		TableEntry<Condition> entry = entry();
		List<AwaitResult<JoinMap<Reified<?>, Condition>>> completions = new ArrayList<>();

		Fiber.fork(Arrays.asList(
						consuming(entry, 0, completions),
						consuming(entry, 0, completions),
						consuming(entry, 0, completions)))
				.flatMap(__ -> production(entry, answer(Tuple.of("charlie", "dave"))))
				.ground();

		assertThat(completions).hasSize(3);
		assertThat(completions.get(0).getValue().size()).isEqualTo(1);
	}

	@Test
	public void testDuplicateAnswerDoesNotWakeAsGrowth() {
		TableEntry<Condition> entry = entry();
		List<AwaitResult<JoinMap<Reified<?>, Condition>>> completions = new ArrayList<>();

		// a consumer past the cache end waits for a SECOND ascent; the
		// duplicate is an inert join, so only the seal ever completes it
		Fiber.detach(consuming(entry, 1, completions))
				.flatMap(__ -> production(entry,
						answer(Tuple.of("charlie", "dave")),
						answer(Tuple.of("charlie", "dave"))))
				.ground();

		assertThat(completions).hasSize(1);
		assertThat(completions.get(0).isSealed()).isTrue();
		assertThat(completions.get(0).getValue().size()).isEqualTo(1);
	}
}
