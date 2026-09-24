package org.clauseway.logic.unification.terms;

import static org.clauseway.logic.unification.terms.LVal.lval;

import org.clauseway.logic.constraints.Constraints;
import org.clauseway.logic.constraints.Posting;

/**
 * A term that may enter a solver: goals are built by unifying these.
 *
 * @author TGa
 */
public interface Unifiable<T> extends Term<T> {

	default Posting unifies(Unifiable<T> rhs) {
		return Constraints.unify(this, rhs);
	}

	default Posting unifies(T value) {
		return Constraints.unify(this, lval(value));
	}

	default Posting unifiesNc(Unifiable<T> rhs) {
		return Constraints.unifyNc(this, rhs)
				.named("unifyNc");
	}

	default Posting unifiesNc(T value) {
		return unifiesNc(lval(value));
	}

	@SuppressWarnings("unchecked")
	default Unifiable<Object> getObjectUnifiable() {
		return (Unifiable<Object>) this;
	}
}
