package com.tgac.logic.separate;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.stream.Collectors;

@Value
@RequiredArgsConstructor(staticName = "of")
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
