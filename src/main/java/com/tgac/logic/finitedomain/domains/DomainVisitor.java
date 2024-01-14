package com.tgac.logic.finitedomain.domains;
public interface DomainVisitor<T, R> {
	R visit(Empty<T> instance);

	R visit(Singleton<T> instance);

	R visit(SimpleInterval<T> instance);

	R visit(MultiInterval<T> instance);

	R visit(EnumeratedInterval<T> instance);
}
