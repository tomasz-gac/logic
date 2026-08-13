package com.tgac.logic.constraints;

// ABOUTME: Constraint-aware unification as a data goal: order 1 — it can only
// ABOUTME: prune or pass, never branch — so ordering passes sort it first.

import static com.tgac.functional.category.Nothing.nothing;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.NamedGoal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
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
		return MiniKanren.unifyPrefix(s, u, v).get().isDefined() ? 1 : 0;
	}

	@Override
	public boolean doomed(Package p) {
		return answers(p.substitution()) == 0;
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
