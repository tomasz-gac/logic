package com.tgac.logic.notes;

// ABOUTME: The note store, Domain-cargo negative polarity: notes live by the four
// ABOUTME: moves — cross off, enforce the last, fail on empty, discard when satisfied.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.lattice.LatticeStore;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * A note's escape answers two questions against the live state — did you come
 * true? did you become impossible? — and the store runs the four moves from
 * the answers, wholesale on every trigger, the shape lifted from Neq. An
 * escape {@code anchor ∉ box} is TRUE when the anchor's knowledge is disjoint
 * from the box (bound outside it, or its domain misses it entirely) and
 * IMPOSSIBLE when the knowledge sits inside the box (bound into it, or its
 * domain contained by it). Anchors' domains are read from whatever
 * {@link LatticeStore} resides in the package — reads in revise are legal;
 * custody restricts writes.
 */
@Value
@RequiredArgsConstructor(staticName = "of")
class NoteStore implements Projectable<NoteStore> {
	public static final NoteStore EMPTY = NoteStore.of(LinkedHashSet.empty());
	LinkedHashSet<Note> notes;

	public static Package register(Package a) {
		return a.withStore(EMPTY);
	}

	/** More notes = more known: meet is union; wholesale re-verification keeps it exact. */
	@Override
	public NoteStore meet(NoteStore other) {
		return NoteStore.of(notes.addAll(other.notes));
	}

	@Override
	public boolean leq(NoteStore other) {
		return notes.containsAll(other.notes);
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

	/**
	 * The four moves, wholesale: each note re-examined against the state.
	 * Any escape true → the note is satisfied, discarded. Impossible escapes
	 * cross off. No escapes left → the branch fails. One left → the note IS
	 * the plain exclusion on its survivor, same representation, watching it.
	 */
	@Override
	public Fiber<Revision> normalize(Package state) {
		return Fiber.done(verifyAll(state)
				.map(kept -> (Revision) Revision.updated(NoteStore.of(kept)))
				.getOrElse(Revision::fail));
	}

	@Override
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		return normalize(state);
	}

	private Option<LinkedHashSet<Note>> verifyAll(Package state) {
		LinkedHashSet<Note> kept = LinkedHashSet.empty();
		for (Note note : notes) {
			Option<Option<Note>> verdict = verify(note, state);
			if (!verdict.isDefined()) {
				return Option.none();
			}
			for (Note survivor : verdict.get()) {
				kept = kept.add(survivor);
			}
		}
		return Option.of(kept);
	}

	/** none = the branch fails; some(none) = satisfied, discard; some(note) = keep, simplified. */
	private static Option<Option<Note>> verify(Note note, Package state) {
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

	/** A note goes to the covered half iff every anchor it watches is supplied. */
	@Override
	public Tuple2<NoteStore, NoteStore> split(java.util.List<LVar<?>> vars) {
		Set<Term<?>> covered = new HashSet<>(vars);
		LinkedHashSet<Note> in = LinkedHashSet.empty();
		LinkedHashSet<Note> out = LinkedHashSet.empty();
		for (Note note : notes) {
			if (note.getEscapes().forAll(escape -> covered.contains(escape._1))) {
				in = in.add(note);
			} else {
				out = out.add(note);
			}
		}
		return Tuple.of(NoteStore.of(in), NoteStore.of(out));
	}

	/** Anchors translate through the renaming; boxes are ground values and ride unchanged. */
	@Override
	public Fiber<NoteStore> rename(Renaming renaming) {
		return notes.foldLeft(
						Fiber.<LinkedHashSet<Note>> done(LinkedHashSet.empty()),
						(acc, note) -> acc.flatMap(renamed ->
								renamedNote(note, renaming).map(renamed::add)))
				.map(NoteStore::of);
	}

	private static Fiber<Note> renamedNote(Note note, Renaming renaming) {
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
