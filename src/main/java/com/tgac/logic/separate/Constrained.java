package com.tgac.logic.separate;

import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class Constrained<T> implements Reified<T> {
	Term<T> that;
	@Getter
	List<HashMap<Term<?>, Term<?>>> constraints;

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
	public Option<Hole<T>> asReified() {
		return that.asReified();
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
