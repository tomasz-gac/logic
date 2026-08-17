package com.tgac.logic.unification;

import io.vavr.control.Option;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;

/**
 * @author TGa
 */

@Getter
public class LVar<T> implements Unifiable<T>, Name<T> {
	private static final AtomicLong VARIABLE_COUNTER = new AtomicLong(0L);
	private final long birth;
	private final String name;

	private LVar(String name) {
		this.birth = VARIABLE_COUNTER.getAndIncrement();
		this.name = name;
	}

	LVar() {
		this.birth = VARIABLE_COUNTER.getAndIncrement();
		this.name = "_." + birth;
	}

	/** The birth counter's current value: every variable created from now on satisfies {@code getBirth() >= births()}. */
	public static long births() {
		return VARIABLE_COUNTER.get();
	}

	public static <T> Unifiable<T> lvar() {
		return new LVar<>();
	}

	public static <T> Unifiable<T> lvar(String name) {
		return new LVar<>(name);
	}

	@Override
	public Option<Name<T>> asName() {
		return Option.of(this);
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
