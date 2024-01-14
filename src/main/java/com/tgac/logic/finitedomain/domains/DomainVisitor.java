package com.tgac.logic.finitedomain.domains;
public interface DomainVisitor<T, R> {
	R visit(Empty<T> domain);

	R visit(Singleton<T> domain);

	R visit(Interval<T> domain);

	R visit(Union<T> domain);

	R visit(EnumeratedDomain<T> domain);
}
