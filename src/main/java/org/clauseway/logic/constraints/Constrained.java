package org.clauseway.logic.constraints;

// ABOUTME: The canonical escape for infinities: a rendered answer carrying the
// ABOUTME: residual items its stores could not finish — expressed, never dropped.

import org.clauseway.logic.constraints.store.Atom;
import org.clauseway.logic.unification.Any;
import org.clauseway.logic.unification.LVar;
import org.clauseway.logic.unification.Reified;
import org.clauseway.logic.unification.Term;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.Value;

/**
 * An answer with conditions: the term plus the residual items still riding it
 * at rendering, displayed through the items' own {@code toString} (a nogood
 * prints {@code ¬(…)}). {@link #isGround} is false BY MEANING, not accident —
 * a conditional answer denotes a region, so everything demanding values (the
 * aggregation fold) must refuse or enforce first; this carrier is where the
 * compression boundary shows in an answer's type.
 */
@Value(staticConstructor = "of")
public class Constrained<T> implements Reified<T> {
	Term<T> that;
	List<Atom<?>> residuals;

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
	public Option<Any<T>> asReified() {
		return that.asReified();
	}

	@Override
	public boolean isGround() {
		// residuals ride this carrier precisely because the answer is a region
		return false;
	}

	@Override
	public String toString() {
		return that + " : " + residuals.mkString(" ∧ ");
	}
}
