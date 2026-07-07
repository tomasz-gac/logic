package com.tgac.logic.unification;

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.goals.Goal;

/**
 * A term that may enter a solver: goals are built by unifying these.
 *
 * @author TGa
 */
public interface Unifiable<T> extends Term<T> {

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
