package com.tgac.logic.notes;

// ABOUTME: The negative front door: notin carves one box out of one variable,
// ABOUTME: exclude forbids a whole box combination — at least one escape must hold.

import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Exclusion {

	/** {@code x ∉ box}. */
	public static <T> Goal notin(Unifiable<T> x, Domain<T> box) {
		return exclude(lit(x, box));
	}

	/**
	 * Forbids the combination: {@code ¬(x₁∈B₁ ∧ … ∧ xₙ∈Bₙ)} — at least one
	 * variable must escape its box. No branch exists at posting; the note
	 * lives by the four moves as knowledge grows.
	 */
	public static Goal exclude(Tuple2<Term<?>, Domain<?>>... escapes) {
		Note note = Note.of(List.of(escapes));
		return pkg -> Propagation.activate(note).apply(NoteStore.register(pkg));
	}

	/** One escape: {@code x ∉ box}, one literal of an {@link #exclude}. */
	public static <T> Tuple2<Term<?>, Domain<?>> lit(Unifiable<T> x, Domain<T> box) {
		return Tuple.of(x, box);
	}
}
