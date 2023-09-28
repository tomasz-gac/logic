package com.tgac.logic;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;

import java.util.function.Supplier;

import static com.tgac.logic.Incomplete.incomplete;
import static com.tgac.logic.LVal.lval;

/**
 * @author TGa
 */
public interface Unifiable<T> extends Supplier<T> {

	default Option<T> asVal() {
		return Option.none();
	}

	default Option<LVar<T>> asVar() {
		return Option.none();
	}

	default List<HashMap<LVar<?>, Unifiable<?>>> getConstraints() {
		return List.empty();
	}

	@Override
	default T get() {
		return asVal().get();
	}

	default LVar<T> getVar() {
		return asVar().get();
	}

	default Goal unify(Unifiable<T> rhs) {
		return Goal.unify(this, rhs);
	}

	default Goal unify(T value) {
		return Goal.unify(this, lval(value));
	}

	default Goal unifyNc(Unifiable<T> rhs) {
		return Goal.goal(s -> incomplete(() -> MiniKanren.unifyUnsafe(s, this, rhs)
						.map(s1 -> Option.of(s1)
								.map(Stream::of)
								.getOrElse(Stream::empty))
						.getOrElse(Stream::empty)))
				.named("unifyNc");
	}

	default Goal unifyNc(T value) {
		return unifyNc(lval(value));
	}

	default Goal separate(Unifiable<T> rhs) {
		return Goal.separate(this, rhs);
	}

	default Goal separate(T value) {
		return separate(lval(value));
	}

	@SuppressWarnings("unchecked")
	default Unifiable<Object> getObjectUnifiable() {
		return asVal()
				.map(LVal::<Object>lval)
				.orElse(() -> asVar().map(v -> (Unifiable<Object>) v))
				.get();
	}
}
