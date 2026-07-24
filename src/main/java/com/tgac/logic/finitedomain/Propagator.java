package com.tgac.logic.finitedomain;

// ABOUTME: A parked constraint body that reports a Verdict — the framework owns the
// ABOUTME: parked lifecycle; watch matching resolves against the live state.

import com.tgac.logic.constraints.store.Watches;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.collection.Array;
import java.util.function.BiFunction;
import lombok.EqualsAndHashCode;

/**
 * The parked unit of the wake machinery (docs/design/constraint-kernel.md* §2.2). Extends {@link Stored} so park/remove route to the owning store without a
 * wrapper. Watch matching walks the watched terms against the LIVE state, so
 * aliasing (x bound to y) re-targets the watch structurally, where the old
 * Constraint protocol relied on the re-park-with-freshly-walked-args side effect of
 * remove-and-rerun.
 */
@EqualsAndHashCode(of = {"storeClass", "name", "watchedTerms"})
public final class Propagator implements Stored {

	private final Class<? extends Store> storeClass;
	private final String name;
	private final Array<? extends Term<?>> watchedTerms;
	private final BiFunction<Array<? extends Term<?>>, Package, Verdict> body;

	private Propagator(Class<? extends Store> storeClass,
			String name,
			Array<? extends Term<?>> watchedTerms,
			BiFunction<Array<? extends Term<?>>, Package, Verdict> body) {
		this.storeClass = storeClass;
		this.name = name;
		this.watchedTerms = watchedTerms;
		this.body = body;
	}

	/**
	 * A propagator from its owning store, name, watched terms and body. The
	 * body is POSITIONAL — it reads its variables through the watched array it
	 * is handed, never through lexical capture — so the constraint can be
	 * re-instantiated over different terms ({@link #watching}).
	 *
	 * <p>A propagator IS its name over its terms: equality is (store, name,
	 * watched), the body excluded. THE CONTRACT THAT LICENSES IT: the name
	 * must uniquely determine the body's semantics — two builders sharing a
	 * name with different verdict logic would merge distinct knowledge,
	 * silently and unsoundly. Under that contract, two independent posts of
	 * one relation on the same terms are the same knowledge stated twice
	 * (idempotent re-posting — the store dedups them), and renamed instances
	 * of one post compare equal wherever the renaming agrees.
	 */
	public static Propagator of(
			Class<? extends Store> storeClass,
			String name,
			Iterable<? extends Term<?>> watchedTerms,
			BiFunction<Array<? extends Term<?>>, Package, Verdict> body) {
		return new Propagator(storeClass, name, Array.ofAll(watchedTerms), body);
	}

	/** The terms whose variables this propagator watches — as stated, un-walked. */
	public Array<? extends Term<?>> watchedTerms() {
		return watchedTerms;
	}

	/**
	 * The same constraint over different terms, positions one-to-one — a fresh
	 * instance of the schema this propagator's body denotes. How a carried
	 * coupling replays onto a consumption's fresh variables.
	 */
	public Propagator watching(Array<? extends Term<?>> terms) {
		return new Propagator(storeClass, name, terms, body);
	}

	/** Re-examine against the current state. Reads anything, mutates nothing. */
	public Verdict propagate(Package state) {
		return body.apply(watchedTerms, state);
	}

	/**
	 * Does a change to {@code changed} re-run this propagator? Chain-inclusive:
	 * see {@link Watches}. Watched terms are VARIABLES in practice — a composite
	 * watched term does not trigger on its members' bindings (suspensions use
	 * the structural variant; no FD constraint watches composites).
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
		return name + watchedTerms;
	}
}
