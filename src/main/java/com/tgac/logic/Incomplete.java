package com.tgac.logic;
import com.tgac.functional.recursion.Recur;
import io.vavr.Predicates;
import io.vavr.collection.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;
import java.util.function.Supplier;

import static com.tgac.functional.recursion.Recur.done;
import static com.tgac.functional.recursion.Recur.recur;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Incomplete<T> implements Stream<T> {
	@NonNull
	private Recur<Stream<T>> rest;

	public static <A> Stream<A> incomplete(Supplier<Recur<Stream<A>>> r) {
		return new Incomplete<>(recur(r));
	}

	@Override
	public boolean isEmpty() {
		return getOrEvaluate().isEmpty();
	}
	@Override
	public T head() {
		return getOrEvaluate().head();
	}
	@Override
	public Stream<T> tail() {
		return getOrEvaluate().tail();
	}

	@Override
	public <U> Stream<U> flatMap(Function<? super T, ? extends Iterable<? extends U>> mapper) {
		return incomplete(() -> rest.map(rst -> rst.flatMap(mapper)));
	}
	private synchronized Stream<T> getOrEvaluate() {
		if (!rest.isDone()) {
			rest = done(eval(rest.get()));
		}
		return rest.get();
	}

	private static <T> Stream<T> eval(Stream<T> stream) {
		if (stream instanceof Incomplete) {
			return java.util.stream.Stream.iterate(((Incomplete<T>) stream).rest.get(),
							Incomplete::eval)
					.filter(Predicates.not(Incomplete.class::isInstance))
					.findFirst()
					.orElseThrow(IllegalStateException::new);
		} else {
			return stream;
		}
	}
}