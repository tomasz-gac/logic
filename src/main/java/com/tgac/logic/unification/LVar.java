package com.tgac.logic.unification;

import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author TGa
 */

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class LVar<T> implements Unifiable<T> {
	private static final AtomicLong VARIABLE_COUNTER = new AtomicLong(0L);
	private final String name;

	LVar() {
		name = "_." + VARIABLE_COUNTER.getAndIncrement(); //System.identityHashCode(this);
	}

	public static <T> Unifiable<T> lvar() {
		return new LVar<>();
	}

	public static <T> Unifiable<T> lvar(String name) {
		return new LVar<>(name);
	}

	@Override
	public Option<LVar<T>> asVar() {
		return Option.of(this);
	}

	@Override
	public String toString() {
		return "<" + name + ">";
	}
}
