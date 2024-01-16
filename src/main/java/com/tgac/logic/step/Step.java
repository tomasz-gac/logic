package com.tgac.logic.step;

import com.tgac.functional.recursion.Recur;
import io.vavr.collection.Array;
import io.vavr.control.Option;

import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
public interface Step<A> {

	<R> R accept(Visitor<A, R> visitor);

	boolean isEmpty();

	<B> Step<B> flatMap(Function<A, Step<B>> f);

	<B> Step<B> map(Function<A, B> f);

	Step<A> append(Step<A> rhs);

	Recur<Step<A>> interleave(Array<Step<A>> rest);

	default Stream<A> stream() {
		return StreamSupport.stream(
				Spliterators.spliteratorUnknownSize(
						new StepIterator<>(this),
						Spliterator.NONNULL & Spliterator.ORDERED),
				false);
	}

	static <A> Empty<A> empty() {
		return Empty.instance();
	}

	static <A> Single<A> single(A v) {
		return Single.of(v);
	}

	static <A> Incomplete<A> incomplete(Supplier<Recur<Step<A>>> s) {
		return Incomplete.of(s);
	}

	static <A> Cons<A> cons(A head, Step<A> tail) {
		return Cons.of(head, tail);
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	static <A> Step<A> of(Optional<A> opt) {
		return opt.<Step<A>> map(Step::single).orElseGet(Step::empty);
	}

	static <A> Step<A> of(Option<A> opt) {
		return opt.<Step<A>> map(Step::single).getOrElse(Step::empty);
	}

	interface Visitor<A, R> {
		R visit(Empty<A> empty);

		R visit(Incomplete<A> inc);

		R visit(Single<A> single);

		R visit(Cons<A> cons);
	}
}
