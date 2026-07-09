package com.tgac.logic.debug;

// ABOUTME: Carries the active tracer through the package's constraint-store map so every
// ABOUTME: derived state can report box-model ports. A plain Store, inert to constraint solving.

import com.tgac.logic.debug.Trace.Tracer;
import com.tgac.logic.tabling.Table;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Transport for the active {@link Tracer} and the current goal spine. Like
 * {@link Table} it is a plain {@link Store} — constraint processing ignores
 * it; the store map is only its carrier, so every state
 * derived during a solve shares the tracer, and each branch carries its own
 * spine (the path of open boxes from the root query to the current goal).
 */
@Value
@RequiredArgsConstructor(staticName = "of")
public class DebugStore implements Store {

	Tracer tracer;
	List<String> spine;

	public static DebugStore of(Tracer tracer) {
		return new DebugStore(tracer, List.empty());
	}

	public static Option<DebugStore> from(Package pkg) {
		return pkg.getStores().get(DebugStore.class).map(DebugStore.class::cast);
	}

	public DebugStore push(String box) {
		return new DebugStore(tracer, spine.prepend(box));
	}

	public int depth() {
		return spine.size();
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
