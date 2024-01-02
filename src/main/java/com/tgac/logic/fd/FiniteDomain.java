package com.tgac.logic.fd;
import com.tgac.functional.recursion.MRecur;
import com.tgac.logic.LVar;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.Domain;
import com.tgac.logic.cKanren.PackageOp;
import io.vavr.Predicates;
import io.vavr.collection.List;
import io.vavr.control.Option;

import java.util.function.Predicate;

import static com.tgac.logic.LVal.lval;
import static com.tgac.logic.cKanren.CKanren.runConstraints;

public abstract class FiniteDomain implements Domain {
	protected abstract long min();

	protected abstract long max();

	protected abstract FiniteDomain dropBefore(Predicate<Long> p);

	protected abstract Domain copyBefore(Predicate<Long> value);

	protected abstract boolean contains(Object v);

	protected abstract FiniteDomain intersect(FiniteDomain other);

	protected abstract Option<Long> getSingletonElement();

	@Override
	public PackageOp processDom(Unifiable<?> x) {
		return a -> x.asVar()
				.map(this::updateVarDomain)
				.map(op -> op.apply(a))
				.orElse(() -> x.asVal()
						.filter(this::contains)
						.map(v -> MRecur.mdone(a)))
				.getOrElse(MRecur::none);
	}
	/**
	 * Updates variable's domain by computing the intersection between this domain, and it's previously assigned domain. Attempts to unify variable if the resulting domain contains only a single
	 * element.
	 *
	 * @param x
	 * 		updated variable, must already been walked
	 * @return package operator
	 */
	private PackageOp updateVarDomain(LVar<?> x) {
		return s -> s.getDomain(x)
				.map(FiniteDomain::asFiniteDomain)
				.map(xd -> Option.of(xd.intersect(this))
						.filter(Predicates.not(FiniteDomain::isEmpty))
						.map(i -> i.resolveStorableDom(x).apply(s))
						.getOrElse(MRecur::none))
				.getOrElse(() -> resolveStorableDom(x).apply(s));
	}

	/**
	 * Returned function assigns value to x while preserving constraints if x's domain is a single element. Extends x's domain otherwise
	 *
	 * @param x
	 * 		variable to resolve
	 * @return package operator
	 */
	private PackageOp resolveStorableDom(LVar<?> x) {
		return a -> getSingletonElement()
				.map(n -> extendS(a, x, n))
				.map(a1 -> assignDomainValueWithCheck(x, a.getConstraints())
						.apply(a))
				.getOrElse(() -> MRecur.mdone(extendD(x, this, a)));
	}

	private static PackageOp assignDomainValueWithCheck(LVar<?> x, List<Constraint> c) {
		return a -> MRecur.ofRecur(runConstraints(x, c))
				.flatMap(op -> op.apply(a));
	}

	private static Package extendS(Package a, LVar<?> variableToAssign, long value) {
		return Package.of(
				a.getSubstitutions().put(variableToAssign, lval(value)),
				a.getSConstraints(),
				a.getDomains(),
				a.getConstraints());
	}
	private static Package extendD(LVar<?> x, FiniteDomain xd, Package a) {
		return Package.of(a.getSubstitutions(),
				a.getSConstraints(),
				a.getDomains().put(x, xd),
				a.getConstraints());
	}

	private static FiniteDomain asFiniteDomain(Domain xd) {
		return (FiniteDomain) xd;
	}
}
