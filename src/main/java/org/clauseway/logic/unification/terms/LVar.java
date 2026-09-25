package org.clauseway.logic.unification.terms;

import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import java.util.Optional;

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
	public Optional<Name<T>> asName() {
		return Optional.of(this);
	}

	@Override
	public Optional<LVar<T>> asVar() {
		return Optional.of(this);
	}

	@Override
	public String toString() {
		return "<" + name + ">";
	}
}
