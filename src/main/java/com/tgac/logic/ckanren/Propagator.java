package com.tgac.logic.ckanren;

// ABOUTME: A parked constraint body that reports a Verdict — the framework owns the
// ABOUTME: parked lifecycle; watch matching resolves against the live state.

import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import java.util.function.Function;
import java.util.stream.StreamSupport;

/**
 * The parked unit of the wake machinery (docs/design/capability-constraint-api.md
 * §2.2). Extends {@link Stored} so park/remove route to the owning store without a
 * wrapper. Watch matching walks the watched terms against the LIVE state, so
 * aliasing (x bound to y) re-targets the watch structurally, where the old
 * Constraint protocol relied on the re-park-with-freshly-walked-args side effect of
 * remove-and-rerun.
 */
public interface Propagator extends Stored {

	/** The terms whose variables this propagator watches — as stated, un-walked. */
	Iterable<? extends Term<?>> watchedTerms();

	/** Re-examine against the current state. Reads anything, mutates nothing. */
	Verdict propagate(Package state);

	/**
	 * Does a change to {@code changed} re-run this propagator? {@code changed} is a
	 * newly bound variable during a wake, or an answer term during enforcement
	 * (whose components match element-wise, as the old relevance check did).
	 */
	default boolean watches(Package state, Term<?> changed) {
		for (Term<?> watchedTerm : watchedTerms()) {
			// follow the whole walk chain: the changed variable may be the watched
			// term itself, an alias link in the middle, or the end of the chain —
			// a full walk would step THROUGH a just-bound variable to its value
			// and miss the match
			Term<?> cur = watchedTerm;
			while (true) {
				if (cur.equals(changed)) {
					return true;
				}
				if (changed.isVal() && changedContains(changed, cur)) {
					return true;
				}
				if (!cur.asVar().isDefined()) {
					break;
				}
				Term<?> next = state.getSubstitutions().getOrElse(cur.asVar().get(), null);
				if (next == null) {
					break;
				}
				cur = next;
			}
		}
		return false;
	}

	/** Mirrors the old anyRelevantVar: a ground composite matches element-wise. */
	static boolean changedContains(Term<?> changed, Term<?> live) {
		Object w = changed.get();
		return MiniKanren.asIterable(w)
				.orElse(() -> MiniKanren.tupleAsIterable(w))
				.map(it -> StreamSupport.stream(it.spliterator(), false)
						.map(MiniKanren::wrapTerm)
						.anyMatch(live::equals))
				.getOrElse(() -> MiniKanren.asLList(changed)
						.map(l -> l.stream()
								.anyMatch(e -> e.fold(live::equals, live::equals)))
						.getOrElse(false));
	}

	/** A propagator from its owning store, watched terms and body. */
	static Propagator of(
			Class<? extends Store> storeClass,
			Iterable<? extends Term<?>> watchedTerms,
			Function<Package, Verdict> body) {
		return new Propagator() {
			@Override
			public Iterable<? extends Term<?>> watchedTerms() {
				return watchedTerms;
			}

			@Override
			public Verdict propagate(Package state) {
				return body.apply(state);
			}

			@Override
			public Class<? extends Store> getStoreClass() {
				return storeClass;
			}

			@Override
			public String toString() {
				return "propagator" + watchedTerms;
			}
		};
	}
}
