package com.tgac.logic.step;

import com.tgac.functional.recursion.Recur;
import io.vavr.collection.Array;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.function.Function;

@Value
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Empty<A> implements Step<A> {
	@SuppressWarnings("rawtypes")
	private static final Empty INSTANCE = new Empty();

	@SuppressWarnings("unchecked")
	public static <A> Empty<A> instance() {
		return INSTANCE;
	}

	@Override
	public <R> R accept(Visitor<A, R> visitor) {
		return visitor.visit(this);
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public <B> Step<B> flatMap(Function<A, Step<B>> f) {
		return Empty.instance();
	}

	@Override
	public <B> Step<B> map(Function<A, B> f) {
		return instance();
	}

	@Override
	public Step<A> append(Step<A> rhs) {
		return rhs;
	}
	@Override
	public Recur<Step<A>> interleave(Array<Step<A>> rest) {
		if (rest.isEmpty()) {
			return Recur.done(this);
		} else {
			return Recur.recur(() -> rest.head().interleave(rest.tail()));
		}
	}
}
