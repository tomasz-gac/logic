package com.tgac.logic.unification;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.ckanren.CKanren;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;

import java.util.function.Supplier;

import static com.tgac.logic.unification.LVal.lval;

/**
 * @author TGa
 */
public interface Unifiable<T> extends Supplier<T> {

	default Option<T> asVal() {
		return Option.none();
	}

	default boolean isVal() {
		return false;
	}

	default Option<LVar<T>> asVar() {
		return Option.none();
	}

	default List<HashMap<LVar<?>, Unifiable<?>>> getConstraints() {
		return List.empty();
	}

	@Override
	default T get() {
		return ((LVal<T>) this).getValue();
	}

	default LVar<T> getVar() {
		return (LVar<T>) this;
	}

	default Goal unifies(Unifiable<T> rhs) {
		return CKanren.unify(this, rhs);
	}

	default Goal unifies(T value) {
		return CKanren.unify(this, lval(value));
	}

	default Goal unifiesNc(Unifiable<T> rhs) {
		return CKanren.unifyNc(this, rhs)
				.named("unifyNc");
	}

	default Goal unifiesNc(T value) {
		return unifiesNc(lval(value));
	}

	@SuppressWarnings("unchecked")
	default Unifiable<Object> getObjectUnifiable() {
		return (Unifiable<Object>) this;
	}
}
