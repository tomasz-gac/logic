package com.tgac.logic.fd;
import com.tgac.functional.Exceptions;
import io.vavr.Predicates;
import io.vavr.collection.Set;
import io.vavr.collection.Traversable;
import io.vavr.control.Option;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.function.Predicate;

@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(staticName = "of")
public class EnumeratedInterval extends FiniteDomain {
	Set<Long> elements;

	@Override
	public java.util.stream.Stream<Object> stream() {
		return elements.toJavaStream()
				.map(Object.class::cast);
	}
	@Override
	public boolean isEmpty() {
		return elements.isEmpty();
	}

	@Override
	public FiniteDomain dropBefore(Predicate<Long> pred) {
		return EnumeratedInterval.of(elements.filter(pred));
	}
	@Override
	public FiniteDomain copyBefore(Predicate<Long> pred) {
		return EnumeratedInterval.of(elements.filter(e -> !pred.test(e)));
	}
	@Override
	protected long min() {
		return elements.min()
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "Cannot call min on empty domain"));
	}
	@Override
	protected long max() {
		return elements.max()
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "Cannot call min on empty domain"));
	}
	@Override
	protected boolean contains(Object v) {
		return v instanceof Long && elements.contains((long) v);
	}
	@Override
	protected FiniteDomain intersect(FiniteDomain other) {
		return Option.of(elements.retainAll(((EnumeratedInterval) other).elements))
				.filter(Predicates.not(Set::isEmpty))
				.map(s -> s.size() == 1 ?
						new SingletonFD(s.get()) :
						EnumeratedInterval.of(s))
				.getOrElse(EmptyDomain::new);
	}
	@Override
	protected Option<Long> getSingletonElement() {
		return Option.of(elements)
				.filter(s -> s.size() == 1)
				.map(Traversable::get);
	}
}