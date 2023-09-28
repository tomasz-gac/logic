package com.tgac.logic;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.stream.Collectors;

@Value
@RequiredArgsConstructor(access = AccessLevel.MODULE, staticName = "of")
public class Constrained<T> implements Unifiable<T> {
	Unifiable<T> that;
	@Getter
	List<HashMap<LVar<?>, Unifiable<?>>> constraints;

	@Override
	public Option<T> asVal() {
		return that.asVal();
	}
	@Override
	public Option<LVar<T>> asVar() {
		return that.asVar();
	}
	@Override
	public T get() {
		return that.get();
	}
	@Override
	public LVar<T> getVar() {
		return that.getVar();
	}
	@Override
	public Goal unify(Unifiable<T> rhs) {
		return that.unify(rhs);
	}
	@Override
	public Goal unify(T value) {
		return that.unify(value);
	}
	@Override
	public Goal unifyNc(Unifiable<T> rhs) {
		return that.unifyNc(rhs);
	}
	@Override
	public Goal unifyNc(T value) {
		return that.unifyNc(value);
	}
	@Override
	public Unifiable<Object> getObjectUnifiable() {
		return that.getObjectUnifiable();
	}

	@Override
	public String toString() {
		return that.toString() + " : " +
				constraints.toJavaStream()
						.map(c -> "(" + c.toJavaStream()
								.map(e -> e._1 + " ≠ " + e._2)
								.collect(Collectors.joining(" && ")) + ")")
						.collect(Collectors.joining(" || "));
	}
}
