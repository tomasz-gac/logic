package com.tgac.logic.notes;

// ABOUTME: The note store's faces over the verification core: normalize is verify
// ABOUTME: wrapped into Revision, revise is normalize by another trigger.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Absorbable;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.collection.LinkedHashSet;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A bag of notes, held conjunctively — the package's own ∧. The lattice is
 * note union (more notes = more known; the derived leq is containment), and
 * the normal form is wholesale re-verification: {@link Verification#verify}
 * wrapped into {@link Revision} — none → fail, the kept set unchanged →
 * unchanged, otherwise the kept set replaces the factor.
 *
 * <p>Verification imposes on a scratch that NEVER carries this store: notes
 * examined inside a scratch answer conservatively by not being there — the
 * depth-one ruling (note-store.md §5) by construction, and the recursion a
 * resident note store would cause (its own revise re-verifying inside every
 * trial) is unrepresentable.
 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor(staticName = "of")
final class NoteStore implements Absorbable<NoteStore> {
	public static final NoteStore EMPTY = NoteStore.of(LinkedHashSet.empty());
	private final LinkedHashSet<Note> notes;

	public static Package register(Package a) {
		return a.withStore(EMPTY);
	}

	/** More notes = more known: meet is union; wholesale re-verification keeps it exact. */
	@Override
	public NoteStore meet(NoteStore other) {
		return NoteStore.of(notes.addAll(other.notes));
	}

	@Override
	public boolean isEmpty() {
		return notes.isEmpty();
	}

	@Override
	public ConstraintStore remove(Stored c) {
		return NoteStore.of(notes.remove((Note) c));
	}

	@Override
	public ConstraintStore prepend(Stored c) {
		return NoteStore.of(notes.add((Note) c));
	}

	@Override
	public boolean contains(Stored c) {
		return c instanceof Note && notes.contains((Note) c);
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		return Goal.success();
	}

	@Override
	public Fiber<Revision> normalize(Package state) {
		return Verification.verify(notes.toList(), state.withoutStore(NoteStore.class))
				.map(kept -> kept.isDefined() ?
						revisedTo(LinkedHashSet.ofAll(kept.get())) :
						Revision.fail());
	}

	private Revision revisedTo(LinkedHashSet<Note> kept) {
		return kept.equals(notes) ?
				Revision.unchanged() :
				Revision.updated(NoteStore.of(kept));
	}

	@Override
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		// the reaction was always wholesale — revise is normalize by another trigger
		return normalize(state);
	}

	/** First examination is the same wholesale re-verification: a note born violated fails here. */
	@Override
	public Fiber<Revision> stated(Stored item, Package state) {
		return normalize(state);
	}

	/**
	 * Stage wall: a note still live about the rendered term is an expressed
	 * infinity this stage cannot render — refuse loudly rather than deliver
	 * an answer with its condition silently dropped.
	 */
	@Override
	public <A> Term<A> reify(Term<A> unifiable, Substitutions renameSubstitutions, Package s) {
		// renameSubstitutions is the answer's canonical seed: a live name it
		// binds is part of the rendered answer
		for (Note note : notes) {
			note.terms()
					.map(term -> s.substitution().walkAll(term))
					.flatMap(MiniKanren::namesIn)
					.filter(name -> renameSubstitutions.walk((Term<?>) name) != name)
					.findFirst()
					.ifPresent(name -> {
						throw new IllegalStateException(
								"unresolved note " + note + " about rendered " + name
										+ ": rendering conditional answers is not built at this stage");
					});
		}
		return unifiable;
	}
}
