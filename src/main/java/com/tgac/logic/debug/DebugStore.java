package com.tgac.logic.debug;

// ABOUTME: Carries the active tracer through the package's constraint-store map so every
// ABOUTME: derived state can report box-model ports. A plain Store, inert to constraint solving.

import com.tgac.logic.debug.Trace.Tracer;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Transport for the active {@link Tracer}. Like {@link com.tgac.logic.tabling.Table}
 * it is a plain {@link Store} — constraint processing ignores it; the store map is
 * only its carrier, so every state derived during a solve shares the tracer.
 */
@Value
@RequiredArgsConstructor(staticName = "of")
public class DebugStore implements Store {

	Tracer tracer;

	public static Option<DebugStore> from(Package pkg) {
		return pkg.getConstraints().get(DebugStore.class).map(DebugStore.class::cast);
	}

	@Override
	public Store remove(Stored c) {
		return this;
	}

	@Override
	public Store prepend(Stored c) {
		return this;
	}

	@Override
	public boolean contains(Stored c) {
		return false;
	}
}
