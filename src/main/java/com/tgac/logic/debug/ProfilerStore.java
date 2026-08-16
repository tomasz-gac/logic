package com.tgac.logic.debug;

// ABOUTME: Marks a profiling solve through the package's store map: NamedGoal
// ABOUTME: emits Named extents, labeled per call site through the cache here.

import com.tgac.logic.constraints.Posting;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.NamedGoal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import io.vavr.control.Option;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Transport for the profiling mode, {@link DebugStore}'s twin: its presence
 * makes every {@link NamedGoal} wrap its body in a named extent, and the
 * label cache here keys those extents per {@code .named()} call site (the
 * label lambda's class), so recursive unfolds share one bucket. A label
 * derives from the goal's construction-site origin when captured, else from
 * the label rendered against the empty package, else from the site class.
 */
public final class ProfilerStore implements Packaged {

	private final ConcurrentHashMap<Class<?>, String> labels = new ConcurrentHashMap<>();

	private ProfilerStore() {
	}

	public static ProfilerStore of() {
		return new ProfilerStore();
	}

	public static Option<ProfilerStore> from(Package pkg) {
		return pkg.getStores().get(ProfilerStore.class).map(ProfilerStore.class::cast);
	}

	public String label(Class<?> site, Throwable origin, Supplier<String> rendered) {
		return labels.computeIfAbsent(site, key -> derive(key, origin, rendered));
	}

	private static String derive(Class<?> site, Throwable origin, Supplier<String> rendered) {
		if (origin != null) {
			for (StackTraceElement frame : origin.getStackTrace()) {
				if (!minting(frame.getClassName())) {
					return frame.toString();
				}
			}
		}
		try {
			return rendered.get();
		} catch (RuntimeException e) {
			return site.getSimpleName();
		}
	}

	/** The layers that mint named goals on behalf of others. */
	private static boolean minting(String className) {
		return className.startsWith("java.")
				|| className.startsWith("sun.")
				|| className.startsWith("com.tgac.functional.")
				|| className.startsWith(NamedGoal.class.getName())
				|| className.startsWith(Goal.class.getName())
				|| className.startsWith(Posting.class.getName());
	}
}
