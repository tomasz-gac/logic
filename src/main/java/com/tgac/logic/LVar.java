package com.tgac.logic;

import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author TGa
 */

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class LVar<T> implements Unifiable<T> {
	private final String name;

	LVar() {
		name = "_." + System.identityHashCode(this);
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
