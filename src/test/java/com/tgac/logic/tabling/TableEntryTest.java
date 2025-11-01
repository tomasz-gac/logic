package com.tgac.logic.tabling;

import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

public class TableEntryTest {

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

		// Add some answers
		LVar x = (LVar) lvar("X");
		Package ans1 = Package.of(HashMap.of(x, lval("alice")), LinkedHashMap.empty());
		Package ans2 = Package.of(HashMap.of(x, lval("bob")), LinkedHashMap.empty());

		entry.addAnswer(ans1);
		entry.addAnswer(ans2);

		assertThat(entry.getAnswerCount()).isEqualTo(2);
		assertThat(entry.getAnswerAt(0)).isEqualTo(ans1);
		assertThat(entry.getAnswerAt(1)).isEqualTo(ans2);
		assertThat(entry.getAnswerAt(2)).isNull();
	}

	@Test
	public void testSlaveWaitsForAnswer() throws Exception {
		Call call = Call.of("ancestor", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		// Slave requests answer at index 0 (not yet available)
		CompletableFuture<TableEntry.AnswerStatus> future = entry.waitForAnswerAt(0);
		assertThat(future).isNotCompleted();

		// Master produces answer
		LVar x = (LVar) lvar("X");
		Package ans = Package.of(HashMap.of(x, lval("charlie")), LinkedHashMap.empty());
		entry.addAnswer(ans);

		// Slave's future should now complete
		TableEntry.AnswerStatus status = future.get(100, TimeUnit.MILLISECONDS);
		assertThat(status).isInstanceOf(TableEntry.Answer.class);
		assertThat(((TableEntry.Answer) status).getAnswer()).isEqualTo(ans);
	}

	@Test
	public void testSlaveGetsImmediateAnswerIfAlreadyAvailable() throws Exception {
		Call call = Call.of("ancestor", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		// Master produces answer first
		LVar x = (LVar) lvar("X");
		Package ans = Package.of(HashMap.of(x, lval("charlie")), LinkedHashMap.empty());
		entry.addAnswer(ans);

		// Slave requests answer at index 0 (already available)
		CompletableFuture<TableEntry.AnswerStatus> future = entry.waitForAnswerAt(0);

		// Should complete immediately
		assertThat(future).isCompleted();
		TableEntry.AnswerStatus status = future.get();
		assertThat(status).isInstanceOf(TableEntry.Answer.class);
		assertThat(((TableEntry.Answer) status).getAnswer()).isEqualTo(ans);
	}

	@Test
	public void testCompletion() throws Exception {
		Call call = Call.of("ancestor", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		assertThat(entry.isComplete()).isFalse();

		// Slave waits for answer at index 0
		CompletableFuture<TableEntry.AnswerStatus> future = entry.waitForAnswerAt(0);

		// Master completes without producing any answers
		entry.markComplete();

		assertThat(entry.isComplete()).isTrue();

		// Slave should be notified of completion
		TableEntry.AnswerStatus status = future.get(100, TimeUnit.MILLISECONDS);
		assertThat(status).isInstanceOf(TableEntry.Done.class);
	}

	@Test
	public void testMultipleSlaves() throws Exception {
		Call call = Call.of("ancestor", lval("alice"), lval("bob"));
		TableEntry entry = new TableEntry(call);

		// Three slaves wait for answer at index 0
		CompletableFuture<TableEntry.AnswerStatus> future1 = entry.waitForAnswerAt(0);
		CompletableFuture<TableEntry.AnswerStatus> future2 = entry.waitForAnswerAt(0);
		CompletableFuture<TableEntry.AnswerStatus> future3 = entry.waitForAnswerAt(0);

		assertThat(future1).isNotCompleted();
		assertThat(future2).isNotCompleted();
		assertThat(future3).isNotCompleted();

		// Master produces answer
		LVar x = (LVar) lvar("X");
		Package ans = Package.of(HashMap.of(x, lval("charlie")), LinkedHashMap.empty());
		entry.addAnswer(ans);

		// All slaves should be notified
		assertThat(future1.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TableEntry.Answer.class);
		assertThat(future2.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TableEntry.Answer.class);
		assertThat(future3.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TableEntry.Answer.class);
	}
}
