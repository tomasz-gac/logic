package com.tgac.logic.tabling;

import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.schedulers.BredthFirstScheduler;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

public class CallTest {

	/**
	 * Helper to run a Fiber synchronously and get its result.
	 */
	private <T> T runFiber(Fiber<T> fiber) {
		AtomicReference<T> result = new AtomicReference<>();
		BredthFirstScheduler<Void> scheduler = new BredthFirstScheduler<>(
			fiber.map(val -> {
				result.set(val);
				return null;
			})
		);
		scheduler.run(val -> {});  // Run with no-op consumer
		return result.get();
	}

	@Test
	public void testCallEquality() {
		// Same goal name and arguments should be equal (when ground)
		// Note: Equality checks only work for ground calls
		Call call1 = Call.of("ancestor", lval("alice"), lval("bob"));
		Call call2 = Call.of("ancestor", lval("alice"), lval("bob"));

		assertThat(call1).isEqualTo(call2);
		assertThat(call1.hashCode()).isEqualTo(call2.hashCode());
	}

	@Test
	public void testCallInequality() {
		// Different concrete arguments should not be equal
		Call call1 = Call.of("ancestor", lval("alice"), lval("bob"));
		Call call2 = Call.of("ancestor", lval("charlie"), lval("david"));

		assertThat(call1).isNotEqualTo(call2);
	}

	@Test
	public void testGroundDetection() {
		Package emptyPkg = Package.empty();

		// Unbound variable - contains LVars (not ground)
		LVar x = (LVar) lvar("X");
		Call call1 = Call.of("parent", x, lval("bob"));
		Call walked1 = runFiber(call1.walkFiber(emptyPkg));
		assertThat(runFiber(walked1.containsLVarsFiber())).isTrue();

		// Bound variable - no LVars after walking (ground)
		Package boundPkg = Package.of(
			HashMap.of(x, lval("alice")),
			LinkedHashMap.empty()
		);
		Call walked2 = runFiber(call1.walkFiber(boundPkg));
		assertThat(runFiber(walked2.containsLVarsFiber())).isFalse();

		// All concrete values - no LVars (ground)
		Call call2 = Call.of("parent", lval("alice"), lval("bob"));
		Call walked3 = runFiber(call2.walkFiber(emptyPkg));
		assertThat(runFiber(walked3.containsLVarsFiber())).isFalse();
	}

	@Test
	public void testWalk() {
		LVar x = (LVar) lvar("X");
		LVar y = (LVar) lvar("Y");

		Call call = Call.of("ancestor", x, y);

		// Walk with x bound to "alice"
		Package pkg = Package.of(
			HashMap.of(x, lval("alice")),
			LinkedHashMap.empty()
		);

		Call walked = runFiber(call.walkFiber(pkg));

		// After walking, x should be replaced with "alice", y stays as variable
		assertThat(walked.getArguments().get(0)).isEqualTo(lval("alice"));
		assertThat(walked.getArguments().get(1)).isInstanceOf(LVar.class);
	}

	@Test
	public void testToString() {
		Call call = Call.of("ancestor", lvar("X"), lval("bob"));
		assertThat(call.toString()).contains("ancestor");
		assertThat(call.toString()).contains("bob");
	}
}
