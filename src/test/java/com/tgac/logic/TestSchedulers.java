package com.tgac.logic;

// ABOUTME: The one solve seam for tests: every unpinned solve drives a seeded
// ABOUTME: RandomizedScheduler — chaos every run, recorded and replayable.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.Scheduler;
import com.tgac.functional.fibers.schedulers.RandomizedScheduler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Every test solve that is not deliberately pinned to a specific driver runs
 * under a seeded {@link RandomizedScheduler}: order-independence is exercised
 * on every run, per the chaos doctrine (answer SETS are scheduler-invariant;
 * arrival order is not, so order-asserting tests pin their driver instead).
 *
 * <p>REPRODUCTION: one master seed per run — fresh by default, pinned with
 * {@code -Dsolve.seed=N}. Each test method's scheduler seed derives from the
 * master and the test's own identity, so a single test replays identically
 * both in-suite and in isolation. Every minted seed is recorded in
 * {@code target/solve-seeds.txt}; on a red test, look its name up there and
 * re-run with the master pinned.
 */
public final class TestSchedulers {

	private static final Path SEED_FILE = Paths.get("target", "solve-seeds.txt");
	private static final long MASTER = initMaster();
	private static final Set<String> RECORDED = ConcurrentHashMap.newKeySet();

	private TestSchedulers() {
	}

	public static Function<Fiber<Nothing>, Scheduler<Nothing>> factory() {
		String test = testIdentity();
		long seed = MASTER * 1_000_003L + test.hashCode();
		record(test, seed);
		return fiber -> RandomizedScheduler.of(fiber, seed);
	}

	private static long initMaster() {
		Long pinned = Long.getLong("solve.seed");
		long master = pinned != null ? pinned : new Random().nextLong();
		try {
			Files.createDirectories(SEED_FILE.getParent());
			Files.write(SEED_FILE,
					("master=" + master + (pinned != null ? " (pinned)" : "") + "\n")
							.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return master;
	}

	/** The innermost test-class frame — stable per test method, suite or solo. */
	private static String testIdentity() {
		for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
			if (frame.getClassName().endsWith("Test")
					&& !frame.getClassName().equals(TestSchedulers.class.getName())) {
				return frame.getClassName() + "#" + frame.getMethodName();
			}
		}
		return "unknown";
	}

	private static void record(String test, long seed) {
		if (!RECORDED.add(test)) {
			return;
		}
		try {
			Files.write(SEED_FILE, (test + "=" + seed + "\n").getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
