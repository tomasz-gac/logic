package com.tgac.logic.constraints;

// ABOUTME: The one-method supertype of everything that can stand as a nogood
// ABOUTME: literal: it answers with its Posting; a Posting answers with itself.

/**
 * A value with a {@link Posting} reading. Doors that consume literals
 * ({@code exclude}) accept this instead of {@link Posting} so that values
 * whose BARE reading is something else — a relation literal that enumerates
 * when stood in a conjunction — can still be negated without ceremony: under
 * negation only the posting reading is meaningful, so the door converts.
 * A {@link Posting} is its own {@code Postable}.
 */
@FunctionalInterface
public interface Postable {

	/** This value's imposition reading. */
	Posting posted();
}
