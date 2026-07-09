package com.tgac.logic.finitedomain;

// ABOUTME: A parked constraint body that reports a Verdict — the framework owns the
// ABOUTME: parked lifecycle; watch matching resolves against the live state.

import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Store;
import com.tgac.logic.ckanren.store.Watches;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import java.util.function.Function;

/**
 * The parked unit of the wake machinery (docs/design/capability-constraint-api.md
 * §2.2). Extends {@link Stored} so park/remove route to the owning store without a
 * wrapper. Watch matching walks the watched terms against the LIVE state, so
 * aliasing (x bound to y) re-targets the watch structurally, where the old
 * Constraint protocol relied on the re-park-with-freshly-walked-args side effect of
 * remove-and-rerun.
 */
public final class Propagator implements Stored {

	private final Class<? extends Store> storeClass;
	private final Iterable<? extends Term<?>> watchedTerms;
	private final Function<Package, Verdict> body;

	private Propagator(Class<? extends Store> storeClass,
			Iterable<? extends Term<?>> watchedTerms,
			Function<Package, Verdict> body) {
		this.storeClass = storeClass;
		this.watchedTerms = watchedTerms;
		this.body = body;
	}

	/** A propagator from its owning store, watched terms and body. */
	public static Propagator of(
			Class<? extends Store> storeClass,
			Iterable<? extends Term<?>> watchedTerms,
			Function<Package, Verdict> body) {
		return new Propagator(storeClass, watchedTerms, body);
	}

	/** The terms whose variables this propagator watches — as stated, un-walked. */
	public Iterable<? extends Term<?>> watchedTerms() {
		return watchedTerms;
	}

	/** Re-examine against the current state. Reads anything, mutates nothing. */
	public Verdict propagate(Package state) {
		return body.apply(state);
	}

	/**
	 * Does a change to {@code changed} re-run this propagator? Chain-inclusive:
	 * see {@link Watches}.
	 */
	public boolean watches(Package state, Term<?> changed) {
		for (Term<?> watchedTerm : watchedTerms) {
			if (Watches.matches(state.substitution(), watchedTerm, changed)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Class<? extends Store> getStoreClass() {
		return storeClass;
	}

	@Override
	public String toString() {
		return "propagator" + watchedTerms;
	}
}
