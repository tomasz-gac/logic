package org.clauseway.logic.constraints;

// ABOUTME: Factor-aware unification as a data goal: order 1 — it can only
// ABOUTME: prune or pass, never branch — so ordering passes sort it first.

import static org.clauseway.functional.category.Nothing.nothing;

import org.clauseway.functional.category.Nothing;
import org.clauseway.functional.monad.Cont;
import org.clauseway.logic.goals.NamedGoal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.MiniKanren;
import org.clauseway.logic.unification.Term;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public class Unification<T> implements Posting {
	Term<T> u;
	Term<T> v;
	boolean noCheck;

	@Override
	public Cont<Package, Nothing> apply(Package s) {
		return Cont.defer(() -> (noCheck ?
				MiniKanren.unifyPrefixUnsafe(s.substitution(), u, v) :
				MiniKanren.unifyPrefix(s.substitution(), u, v))
				.map(prefix -> Propagation.resolve(prefix).apply(s))
				.getOrElse(() -> Cont.complete(nothing())));
	}

	/**
	 * RUN the unification against the state's substitution — one pass, and
	 * it sees partially-ground contradictions a groundness gate would miss.
	 * Sound as doom because unification failure is monotone under binding
	 * growth: a clash found now stays a clash at any later execution state.
	 * O(walk), no trial — the price stays 1 either way.
	 */
	@Override
	public boolean doomed(Package p) {
		return !MiniKanren.unifyPrefix(p.substitution(), u, v).ground().isDefined();
	}

	@Override
	public <R> R accept(Posting.Visitor<R> visitor) {
		return visitor.visit(this);
	}

	/** The label pattern {@link NamedGoal} renders with, against the empty state. */
	@Override
	public String toString() {
		Package empty = Package.empty();
		return empty.format(u) + " ≡ " + empty.format(v);
	}

	@Override
	public Stream<Term<?>> terms() {
		return Stream.concat(
				MiniKanren.namesIn(u).map(name -> (Term<?>) name),
				MiniKanren.namesIn(v).map(name -> (Term<?>) name));
	}
}
