package com.tgac.logic.unification;

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.constraints.Statement;
import com.tgac.logic.goals.Goal;

/**
 * A term that may enter a solver: goals are built by unifying these.
 *
 * @author TGa
 */
public interface Unifiable<T> extends Term<T> {

	default Statement unifies(Unifiable<T> rhs) {
		return Constraints.unify(this, rhs);
	}

	default Statement unifies(T value) {
		return Constraints.unify(this, lval(value));
	}

	default Statement unifiesNc(Unifiable<T> rhs) {
		return Constraints.unifyNc(this, rhs)
				.named("unifyNc");
	}

	default Statement unifiesNc(T value) {
		return unifiesNc(lval(value));
	}

	@SuppressWarnings("unchecked")
	default Unifiable<Object> getObjectUnifiable() {
		return (Unifiable<Object>) this;
	}
}
