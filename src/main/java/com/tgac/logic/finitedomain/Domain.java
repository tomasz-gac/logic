package com.tgac.logic.finitedomain;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.DomainVisitor;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Predicates;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import lombok.EqualsAndHashCode;

import java.util.stream.Stream;

import static com.tgac.logic.ckanren.CKanren.runConstraints;
import static com.tgac.logic.unification.LVal.lval;

@EqualsAndHashCode
public abstract class Domain<T> {

	public abstract <R> R accept(DomainVisitor<T, R> v);

	public abstract boolean contains(T value);

	public abstract Stream<T> stream();

	public abstract boolean isEmpty();

	public abstract Arithmetic<T> min();

	public abstract Arithmetic<T> max();

	public abstract Domain<T> dropBefore(Arithmetic<T> value);

	public abstract Domain<T> copyBefore(Arithmetic<T> value);

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
		return s -> FiniteDomainConstraints.getDom(s, x)
				.map(previousDomain -> Option.of(previousDomain.intersect(this))
						.filter(Predicates.not(Domain::isEmpty))
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
		return a -> Option.of(this)
				.flatMap(Types.<Singleton<T>> castAs(Singleton.class))
				.map(Singleton::getValue)
				.map(n -> a.extendS(HashMap.of(x, lval(n.getValue()))))
				.map(a1 -> runConstraints(x, FiniteDomainConstraints.getFDStore(a).getConstraints())
						.apply(a1))
				.getOrElse(() -> Option.of(extendD(x, this, a)));
	}

	private static Package extendD(LVar<?> x, Domain<?> xd, Package a) {
		return a.<FiniteDomainConstraints> updateC(cs -> cs.withDomain(x, xd));
	}
}
