package com.tgac.logic.lattice;

// ABOUTME: A parked constraint body that reports a Verdict — the framework owns the
// ABOUTME: parked lifecycle; watch matching resolves against the live state.

import com.tgac.logic.constraints.store.Watches;
import com.tgac.logic.goals.Package;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Transcribable;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.unification.Term;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The parked unit of the wake machinery (docs/reference/constraint-kernel.md* §2.2). Extends {@link Atom} so park/remove route to the owning store without a
 * wrapper. Watch matching walks the watched terms against the LIVE state, so
 * aliasing (x bound to y) re-targets the watch structurally, where the old
 * Factor protocol relied on the re-park-with-freshly-walked-args side effect of
 * remove-and-rerun.
 */
@EqualsAndHashCode(of = {"storeClass", "name", "watchedTerms"})
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Propagator<F extends Factor<F>> implements Atom<F>, Transcribable<Propagator<F>> {

	@Getter
	private final Class<? extends F> storeClass;
	private final String name;
	private final Array<? extends Term<?>> watchedTerms;
	private final BiFunction<Array<? extends Term<?>>, Package, Verdict> body;

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
	public static <F extends Factor<F>> Propagator<F> of(
			Class<? extends F> storeClass,
			String name,
			Iterable<? extends Term<?>> watchedTerms,
			BiFunction<Array<? extends Term<?>>, Package, Verdict> body) {
		return new Propagator<>(storeClass, name, Array.ofAll(watchedTerms), body);
	}

	/** The terms whose variables this propagator watches — as stated, un-walked. */
	public Array<? extends Term<?>> watchedTerms() {
		return watchedTerms;
	}

	@Override
	public Class<? extends F> getFactorClass() {
		return storeClass;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public Stream<Term<?>> watched() {
		return watchedTerms.toJavaStream().map(t -> (Term<?>) t);
	}

	/** The rebuild-by-name schema: the watched terms, re-posted under name(). */
	@Override
	public Object payload() {
		return watchedTerms;
	}

	/**
	 * The body, for the OWNING store to recognize richer capabilities on its
	 * own propagators (labelling, marshalling) — equality never consults it,
	 * and no store may assume anything about a body it did not create.
	 */
	public BiFunction<Array<? extends Term<?>>, Package, Verdict> body() {
		return body;
	}

	/**
	 * The same constraint over different terms, positions one-to-one — a fresh
	 * instance of the schema this propagator's body denotes. How a carried
	 * coupling replays onto a consumption's fresh variables.
	 */
	public Propagator watching(Array<? extends Term<?>> terms) {
		return new Propagator(storeClass, name, terms, body);
	}

	/** The schema re-instantiated over the renamed terms — {@link #watching}. */
	@Override
	public Fiber<Propagator<F>> rename(Renaming renaming) {
		return watchedTerms.foldLeft(
						Fiber.<List<Term<?>>> done(List.empty()),
						(acc, term) -> acc.flatMap(terms ->
								renaming.apply(term).map(terms::append)))
				.map(terms -> watching(Array.ofAll(terms)));
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
	public String toString() {
		return name + watchedTerms;
	}
}
