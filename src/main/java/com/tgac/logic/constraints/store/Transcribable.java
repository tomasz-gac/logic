package com.tgac.logic.constraints.store;

// ABOUTME: A stored item that can change variable namespaces: rename actuals,
// ABOUTME: re-instantiate — the item half of posting transcription.

import com.tgac.functional.fibers.Fiber;

/**
 * The item-side crossing capability: this item's content under changed names —
 * terms rename through the {@link Renaming}, ground data rides unchanged, the
 * instance rebuilds over the renamed terms (the named-schema contract keeps
 * identity lawful). An item without this capability cannot ride a posting
 * across a boundary; {@code Posting.rename} refuses loudly on it rather than
 * crossing knowledge silently.
 */
public interface Transcribable {

	Fiber<Atom> rename(Renaming renaming);
}
