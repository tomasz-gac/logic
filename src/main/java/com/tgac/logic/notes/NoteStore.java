package com.tgac.logic.notes;

// ABOUTME: The Domain-cargo negative instance of the note chassis: escapes are
// ABOUTME: (anchor, box) literals read anchor ∉ box, examined by lattice ops.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.lattice.LatticeStore;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * An escape {@code anchor ∉ box} is TRUE when the anchor's knowledge is
 * disjoint from the box (bound outside it, or its domain misses it entirely)
 * and IMPOSSIBLE when the knowledge sits inside the box (bound into it, or
 * its domain contained by it). Anchors' domains are read from whatever
 * {@link LatticeStore} resides in the package — reads in revise are legal;
 * custody restricts writes.
 */
@Getter
@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor(staticName = "of")
final class NoteStore extends Notes<Note, NoteStore> {
	public static final NoteStore EMPTY = NoteStore.of(LinkedHashSet.empty());
	private final LinkedHashSet<Note> notes;

	public static Package register(Package a) {
		return a.withStore(EMPTY);
	}

	@Override
	protected LinkedHashSet<Note> records() {
		return notes;
	}

	@Override
	protected NoteStore make(LinkedHashSet<Note> records) {
		return NoteStore.of(records);
	}

	@Override
	protected Class<Note> recordClass() {
		return Note.class;
	}

	/**
	 * The two questions per escape, then the note's verdict: any escape true
	 * → satisfied, discard; impossible escapes cross off; none left → the
	 * branch fails; one survivor IS the plain exclusion watching its anchor.
	 */
	@Override
	protected Option<Option<Note>> verify(Note note, Package state) {
		List<Tuple2<Term<?>, Domain<?>>> surviving = List.empty();
		for (Tuple2<Term<?>, Domain<?>> escape : note.getEscapes()) {
			switch (examine(escape, state)) {
				case TRUE:
					return Option.of(Option.none());
				case IMPOSSIBLE:
					break;
				case UNDECIDED:
					surviving = surviving.append(escape);
					break;
			}
		}
		return surviving.isEmpty() ?
				Option.none() :
				Option.of(Option.of(Note.of(surviving)));
	}

	private enum Answer {
		TRUE, IMPOSSIBLE, UNDECIDED
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Answer examine(Tuple2<Term<?>, Domain<?>> escape, Package state) {
		Term<?> walked = state.walk(escape._1);
		Domain box = escape._2;
		if (walked.asVal().isDefined()) {
			return box.admits(walked.get()) ? Answer.IMPOSSIBLE : Answer.TRUE;
		}
		return latticeValue(state, walked)
				.map(current -> current.isDisjoint(box) ? Answer.TRUE
						: current.leq(box) ? Answer.IMPOSSIBLE
						: Answer.UNDECIDED)
				.getOrElse(Answer.UNDECIDED);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Option<Domain> latticeValue(Package state, Term<?> anchor) {
		for (Packaged store : state.getStores().values()) {
			if (store instanceof LatticeStore) {
				Option<?> value = ((LatticeStore) store).getValue(anchor);
				if (value.isDefined() && value.get() instanceof Domain) {
					return Option.of((Domain) value.get());
				}
			}
		}
		return Option.none();
	}

	@Override
	protected boolean fits(Note note, Set<Term<?>> covered) {
		return note.getEscapes().forAll(escape -> covered.contains(escape._1));
	}

	/** Anchors translate through the renaming; boxes are ground values and ride unchanged. */
	@Override
	protected Fiber<Note> renamed(Note note, Renaming renaming) {
		return note.getEscapes().foldLeft(
						Fiber.<List<Tuple2<Term<?>, Domain<?>>>> done(List.empty()),
						(acc, escape) -> acc.flatMap(escapes -> renaming.apply(escape._1)
								.map(anchor -> escapes.append(Tuple.of(anchor, escape._2)))))
				.map(Note::of);
	}

	/**
	 * Stage-one wall: a note still live about the rendered term is an
	 * expressed infinity this stage cannot render — refuse loudly rather
	 * than deliver an answer with its condition silently dropped.
	 */
	@Override
	public <A> Term<A> reify(Term<A> unifiable, Substitutions renameSubstitutions, Package s) {
		Set<Term<?>> rendered = new HashSet<>();
		MiniKanren.namesIn(s.substitution().walkAll(unifiable)).forEach(rendered::add);
		for (Note note : notes) {
			for (Tuple2<Term<?>, Domain<?>> escape : note.getEscapes()) {
				if (rendered.contains(s.substitution().walk(escape._1))) {
					throw new IllegalStateException(
							"unresolved exclusion about " + escape._1
									+ ": rendering conditional answers is not built at this stage");
				}
			}
		}
		return unifiable;
	}
}
