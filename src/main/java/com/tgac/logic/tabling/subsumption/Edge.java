package com.tgac.logic.tabling.subsumption;

// ABOUTME: The closed vocabulary of trie edge labels a stored call pattern
// ABOUTME: serializes to: an equality atom, a composite of n children, or an any.

import com.tgac.logic.unification.Term;
import lombok.Value;

/**
 * One edge of the subsumption trie — what a stored pattern's preorder walk
 * emits per term node. Three kinds, closed:
 * <ul>
 * <li>{@link Atom} — a non-structural term, matched by equality;</li>
 * <li>{@link Branch} — a composite with its child count (kinds are
 * deliberately not distinguished — the {@code subsumes} post-filter is);</li>
 * <li>{@link Any} — a pattern variable; any NAMES are erased, so
 * {@code p(X,X)} and {@code p(X,Y)} share a path and repeated-any
 * consistency is the post-filter's job.</li>
 * </ul>
 */
public interface Edge {

	enum Any implements Edge {
		ANY
	}

	@Value
	class Atom implements Edge {
		Term<?> term;
	}

	@Value
	class Branch implements Edge {
		int arity;
	}
}
