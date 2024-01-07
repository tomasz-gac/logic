package com.tgac.logic.fd.domains;
import com.tgac.logic.LVar;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.PackageAccessor;
import io.vavr.Predicates;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;

import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.tgac.logic.LVal.lval;
import static com.tgac.logic.cKanren.CKanren.runConstraints;

public abstract class FiniteDomain<T> {

	public abstract boolean contains(T value);

	public abstract Stream<T> stream();

	public abstract boolean isEmpty();

	public abstract T min();

	public abstract T max();

	public abstract FiniteDomain<T> dropBefore(Predicate<T> p);

	public abstract FiniteDomain<T> copyBefore(Predicate<T> value);

	protected abstract FiniteDomain<T> intersect(FiniteDomain<T> other);

	protected abstract Option<T> getSingletonElement();

	public abstract boolean isDisjoint(FiniteDomain<T> other);

	public abstract FiniteDomain<T> difference(FiniteDomain<T> other);

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
	public PackageAccessor processDom(Unifiable<T> x) {
		return a -> x.asVar()
				.map(this::updateVarDomain)
				.map(op -> op.apply(a))
				.orElse(() -> x.asVal()
						.filter(this::contains)
						.map(v -> Option.of(a)))
				.getOrElse(Option::none);
	}
	/**
	 * <pre>
	 *    Updates variable's domain by computing the intersection between this domain, and it's previously assigned domain.
	 * 	Attempts to unify variable if the resulting domain contains only a single element.
	 * </pre>
	 *
	 * @param x
	 * 		updated variable, must already been walked
	 * @return package operator
	 */
	private PackageAccessor updateVarDomain(LVar<T> x) {
		return s -> s.getDomain(x)
				.map(previousDomain -> Option.of(previousDomain.intersect(this))
						.filter(Predicates.not(com.tgac.logic.fd.domains.FiniteDomain::isEmpty))
						.map(i -> i.resolveStorableDom(x).apply(s))
						// intersection is empty
						.getOrElse(Option::none))
				// x has no domain
				.getOrElse(() -> this.resolveStorableDom(x).apply(s));
	}

	/**
	 * <pre>
	 *    Returned function assigns value to x while preserving constraints if x's domain is a single element.
	 * 	Extends x's domain otherwise
	 * </pre>
	 *
	 * @param x
	 * 		variable to resolve
	 * @return package operator
	 */
	private PackageAccessor resolveStorableDom(LVar<?> x) {
		return a -> getSingletonElement()
				.map(n -> a.extendS(HashMap.of(x, lval(n))))
				.map(a1 -> runConstraints(x, a.getConstraints())
						.apply(a1))
				.getOrElse(() -> Option.of(extendD(x, this, a)));
	}

	private static Package extendD(LVar<?> x, FiniteDomain<?> xd, Package a) {
		return Package.of(a.getSubstitutions(),
				a.getSConstraints(),
				a.getDomains().put(x, xd),
				a.getConstraints());
	}
}
