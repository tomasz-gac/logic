package org.clauseway.logic.constraints;

// ABOUTME: Factor-aware unification as a data goal: order 1 — it can only
// ABOUTME: prune or pass, never branch — so ordering passes sort it first.

import static org.clauseway.functional.category.Nothing.nothing;

import org.clauseway.functional.category.Nothing;
import org.clauseway.functional.monad.Cont;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.NamedGoal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.MiniKanren;
import org.clauseway.logic.unification.Substitutions;
import org.clauseway.logic.unification.Term;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public class UnifyGoal<T> implements Posting {
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
	 * Dynamic order: RUN the unification against the pricing substitutions —
	 * one pass, and it prices partially-ground contradictions the groundness
	 * gate would miss. Sound as a bound because unification failure is
	 * monotone under binding growth: a 0 priced now stays 0 at any later
	 * execution state; a success prices 1, an upper bound regardless of what
	 * stores or later bindings veto at runtime.
	 */
	@Override
	public long answers(Substitutions s) {
		return MiniKanren.unifyPrefix(s, u, v).ground().isDefined() ? 1 : 0;
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
