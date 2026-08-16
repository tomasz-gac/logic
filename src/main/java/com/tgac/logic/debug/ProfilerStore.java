package com.tgac.logic.debug;

// ABOUTME: Marks a profiling solve through the package's store map: NamedGoal
// ABOUTME: emits Named extents labeled name @ mint-site, origins as refinement.

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
 * makes every {@link NamedGoal} wrap its body in a named extent. Labels
 * compose the goal's two dimensions: the NAME says what the goal is — the
 * static name when {@code named(String)} minted it, else the label rendered
 * once per call site against the empty package — and the MINT SITE says who
 * created this one, derived from the goal's construction origin when
 * capture was on. Origins REFINE: without them a bucket is the plain name;
 * with them it splits per site ({@code success @ Constraints.enforce(...)}),
 * so the coarse profile is always the fine one with sites folded together.
 */
public final class ProfilerStore implements Packaged {

	private final ConcurrentHashMap<Object, String> rendered = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Throwable, String> sites = new ConcurrentHashMap<>();

	private ProfilerStore() {
	}

	public static ProfilerStore of() {
		return new ProfilerStore();
	}

	public static Option<ProfilerStore> from(Package pkg) {
		return pkg.getStores().get(ProfilerStore.class).map(ProfilerStore.class::cast);
	}

	public String label(String name, Class<?> site, Throwable origin, Supplier<String> render) {
		String base = name != null ? name
				: rendered.computeIfAbsent(site, key -> safely(render, key));
		if (origin == null) {
			return base;
		}
		return sites.computeIfAbsent(origin, o -> base + " @ " + mintSite(o));
	}

	private static String safely(Supplier<String> render, Object fallback) {
		try {
			return render.get();
		} catch (RuntimeException e) {
			return String.valueOf(fallback);
		}
	}

	private static String mintSite(Throwable origin) {
		for (StackTraceElement frame : origin.getStackTrace()) {
			if (!minting(frame.getClassName())) {
				return frame.toString();
			}
		}
		return "unknown";
	}

	/** The layers that mint named goals on behalf of others. */
	private static boolean minting(String className) {
		return className.startsWith("java.")
				|| className.startsWith("sun.")
				|| className.startsWith("io.vavr.")
				|| className.startsWith("com.tgac.functional.")
				|| className.startsWith(NamedGoal.class.getName())
				|| className.startsWith(Goal.class.getName())
				|| className.startsWith(Posting.class.getName());
	}
}
