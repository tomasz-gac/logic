package com.tgac.logic.finitedomain;

import static com.tgac.logic.unification.LVal.lval;
import static io.vavr.Predicates.not;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.ckanren.StoreSupport;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.DomainVisitor;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.control.Option;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public abstract class Domain<T> {

	public abstract <R> R accept(DomainVisitor<T, R> v);

	public abstract boolean contains(T value);

	public abstract Stream<T> stream();

	public abstract boolean isEmpty();

	public abstract Arithmetic<T> min();

	public abstract Arithmetic<T> max();

	/**
	 * The values of this domain that are ≥ {@code value} (inclusive lower bound).
	 */
	public abstract Domain<T> atLeast(Arithmetic<T> value);

	/**
	 * The values of this domain that are ≤ {@code value} (inclusive upper bound).
	 */
	public abstract Domain<T> atMost(Arithmetic<T> value);

	public abstract Domain<T> intersect(Domain<T> other);

	public abstract boolean isDisjoint(Domain<T> other);

	public abstract Domain<T> difference(Domain<T> other);

	/**
	 * <pre>
	 * processδ takes as arguments a value v and a domain δ.
	 * - 	If v is a domain value in δ, then we return a unchanged.
	 * - 	If v is a variable, we intersect the two domains:
	 * 	the one associated with v in d and this domain.
	 * 	- 	If the intersection is a singleton, we extend the substitution.
	 * 		Otherwise, we extend the domain with the intersection.
	 * 	- 	If the two domains are disjoint, then we return false.
	 *
	 * 	(At this point, we have wrong information in d, but this is fine,
	 * 	since we look up variables in d only when they are not in s.)
	 * </pre>
	 */
	public Goal processDom(Term<T> x) {
		return a -> {
			if (x.isVal()) {
				return this.contains(x.get()) ?
						Cont.just(a) :
						Cont.complete(Nothing.nothing());
			} else {
				return updateVarDomain((LVar<T>) x).apply(a);
			}
		};
	}

	/**
	 * <pre>
	 *    Updates variable's domain by computing the intersection between this domain, and it's previously assigned domain.
	 * 	Attempts to unify variable if the resulting domain contains only a single element.
	 * </pre>
	 *
	 * @param x updated variable, must already been walked
	 * @return package operator
	 */
	private Goal updateVarDomain(LVar<T> x) {
		return s -> FiniteDomainConstraints.getDom(s, x)
				.map(previousDomain -> Option.of(previousDomain.intersect(this))
						.filter(not(Domain::isEmpty))
						// an unchanged domain must not re-wake watchers — this is the
						// termination guard of wake-on-narrowing
						.map(i -> i.equals(previousDomain) ?
								Cont.<Package, Nothing> just(s) :
								i.resolveStorableDom(x).apply(s))
						// intersection is empty
						.getOrElse(Cont.complete(Nothing.nothing())))
				// x has no domain
				.getOrElse(() -> this.resolveStorableDom(x).apply(s));
	}

	/**
	 * <pre>
	 *    Returned function assigns value to x while preserving constraints if x's domain is a single element.
	 * 	Extends x's domain otherwise
	 * </pre>
	 *
	 * @param x variable to resolve
	 * @return package operator
	 */
	private Goal resolveStorableDom(LVar<?> x) {
		return a -> {
			if (this instanceof Singleton) {
				T v = ((Singleton<T>) this).getValue().getValue();
				// an inferred binding goes through the same chokepoint as a unification,
				// so every store hears it — not just the FD store's own constraints
				return StoreSupport.processPrefix(a.getSubstitutions().put(x, lval(v)))
						.apply(a);
			} else {
				// a narrowed (but not collapsed) domain wakes the constraints watching x,
				// so narrowing cascades like x≤y≤z propagate without waiting for a binding
				Package narrowed = extendD(x, this, a);
				return com.tgac.logic.ckanren.CKanren
						.runConstraints(x, StoreSupport.pendingConstraints(narrowed))
						.apply(narrowed);
			}
		};
	}

	private static Package extendD(LVar<?> x, Domain<?> xd, Package a) {
		return StoreSupport.updateC(a, FiniteDomainConstraints.class, cs -> cs.withDomain(x, xd));
	}
}
