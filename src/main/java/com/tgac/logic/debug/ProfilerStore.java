package com.tgac.logic.debug;

// ABOUTME: Marks a profiling solve through the package's store map: NamedGoal
// ABOUTME: emits Named extents, labeled per name or per call site through the cache.

import com.tgac.logic.goals.NamedGoal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import io.vavr.control.Option;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Transport for the profiling mode, {@link DebugStore}'s twin: its presence
 * makes every {@link NamedGoal} wrap its body in a named extent. The cache
 * keys extents by the goal's static name when it has one (the name is the
 * label), else by its label lambda's class — one bucket per {@code .named()}
 * call site, so recursive unfolds share — labeled by the goal's own label
 * rendered once against the empty package. Origins play no part: a goal
 * profile never changes with the capture flag.
 */
public final class ProfilerStore implements Packaged {

	private final ConcurrentHashMap<Object, String> labels = new ConcurrentHashMap<>();

	private ProfilerStore() {
	}

	public static ProfilerStore of() {
		return new ProfilerStore();
	}

	public static Option<ProfilerStore> from(Package pkg) {
		return pkg.getStores().get(ProfilerStore.class).map(ProfilerStore.class::cast);
	}

	public String label(Object key, Supplier<String> rendered) {
		return labels.computeIfAbsent(key, k -> {
			try {
				return rendered.get();
			} catch (RuntimeException e) {
				return String.valueOf(k);
			}
		});
	}
}
