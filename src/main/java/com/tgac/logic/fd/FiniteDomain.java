package com.tgac.logic.fd;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.LVar;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.Domain;
import com.tgac.logic.cKanren.PackageAccessor;
import io.vavr.Predicates;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;

import java.util.function.Predicate;

import static com.tgac.logic.LVal.lval;
import static com.tgac.logic.cKanren.CKanren.runConstraints;

public abstract class FiniteDomain<T extends Comparable<T>> implements Domain {
	protected abstract T min();

	protected abstract T max();

	protected abstract FiniteDomain<T> dropBefore(Predicate<T> p);

	protected abstract Domain copyBefore(Predicate<T> value);

	public abstract boolean contains(T v);

	protected abstract FiniteDomain<T> intersect(FiniteDomain<T> other);

	protected abstract Option<T> getSingletonElement();

	@Override
	public PackageAccessor processDom(Unifiable<?> x) {
		return a -> x.asVar()
				.map(this::updateVarDomain)
				.map(op -> op.apply(a))
				.orElse(() -> x.asVal()
						.flatMap(v -> Types.<T> castAs(v, Object.class))
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
	private PackageAccessor updateVarDomain(LVar<?> x) {
		return s -> s.getDomain(x)
				.flatMap(this::asFiniteDomain)
				.map(previousDomain -> Option.of(previousDomain.intersect(this))
						.filter(Predicates.not(FiniteDomain::isEmpty))
						.map(i -> i.resolveStorableDom(x).apply(s))
						.getOrElse(Option::none))
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
				.map(a1 -> assignDomainValueWithCheck(x, a.getConstraints())
						.apply(a1))
				.getOrElse(() -> Option.of(extendD(x, this, a)));
	}

	private static PackageAccessor assignDomainValueWithCheck(LVar<?> x, List<Constraint> c) {
		return runConstraints(x, c);
	}

	private static Package extendD(LVar<?> x, FiniteDomain<?> xd, Package a) {
		return Package.of(a.getSubstitutions(),
				a.getSConstraints(),
				a.getDomains().put(x, xd),
				a.getConstraints());
	}

	private Option<FiniteDomain<T>> asFiniteDomain(Domain xd) {
		return Types.castAs(xd, FiniteDomain.class);
	}

	@Override
	public String toString() {
		return "[" + min() + ", " + max() + "]";
	}
}
