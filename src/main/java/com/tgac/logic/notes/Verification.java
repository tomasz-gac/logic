package com.tgac.logic.notes;

// ABOUTME: The verification step: impose a note's postings on a scratch package,
// ABOUTME: read each imposition three ways, route the four moves. Neq generalized.

import static com.tgac.functional.category.Nothing.nothing;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Exhaustion;
import com.tgac.logic.goals.Package;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Neq's verification with postings for pairs and the scratch for the trial —
 * the same signatures, contract for contract: {@link #verify} is
 * verifyAndSimplify (none = a note is violated, the branch fails; the kept
 * list holds the survivors, discarded notes simply absent), {@link #trial} is
 * unifyConstraints (none = the note is subsumed fully, discard; empty = every
 * posting already holds, violated; survivors = the simplified note's
 * postings). The store slice maps verify onto {@link
 * com.tgac.logic.constraints.store.Revision} in one line: none → fail,
 * kept → updated.
 *
 * <p>A posting is imposed on the scratch and read three ways: the imposition
 * FAILS → the forbidden conjunction is refuted; it changes NOTHING → that
 * posting already holds, crossed off (lawful by monotonicity: knowledge only
 * grows, so an entailed posting stays entailed); it brings NEW knowledge →
 * still owed, the posting survives as its ORIGINAL self — nothing is ever
 * read back out of the scratch. Sequential imposition threads bindings across
 * postings sharing variables — the jointness of Neq's whole-record trial.
 *
 * <p>The change detection is exact for bindings (an entailed unification
 * resolves an empty prefix and returns the package untouched). A stated item
 * always changes the store it enters, so statement postings classify
 * conservatively toward "still owed" — a missed cross-off keeps the note
 * wider, never wrong.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Verification {

	/**
	 * Every note re-verified against the state: none = some note is violated,
	 * the branch fails; otherwise the kept list — survivors simplified,
	 * satisfied notes absent.
	 */
	public static Fiber<Option<List<Note>>> verify(List<Note> notes, Package state) {
		// a scratch starts QUIESCENT: verification runs inside revise, and a
		// mid-drain package carries the outer agenda — impositions would
		// append to it instead of draining, and the stale agenda perturbs
		// change detection
		Package base = Propagation.quiescent(state);
		return notes.foldLeft(
				Fiber.done(Option.of(List.empty())),
				(acc, note) -> acc.flatMap(kept -> kept.isDefined() ?
						verificationStep(base, kept.get(), note) :
						Fiber.done(kept)));
	}

	/** One note against the state: the kept list grown by the note's verdict. */
	private static Fiber<Option<List<Note>>> verificationStep(
			Package state, List<Note> kept, Note note) {
		return trial(note.getPostings(), List.empty(), state)
				.map(delta -> !delta.isDefined() ?
						Option.of(kept) :
						delta.get().isEmpty() ?
								Option.none() :
								Option.of(kept.append(Note.of(delta.get()))));
	}

	/**
	 * The note's postings imposed sequentially on the scratch: none = an
	 * imposition failed, the forbidden conjunction is refuted — the note is
	 * subsumed fully by the state; empty = every posting already holds — the
	 * note is violated; otherwise the surviving postings, entailed ones
	 * crossed off.
	 */
	static Fiber<Option<List<Posting>>> trial(
			List<Posting> pending,
			List<Posting> survivors,
			Package scratch) {
		if (pending.isEmpty()) {
			return Fiber.done(Option.of(survivors));
		}
		Posting posting = pending.head();
		return imposed(posting, scratch).flatMap(worlds -> {
			if (worlds.isEmpty()) {
				return Fiber.done(Option.none());
			}
			if (worlds.size() > 1) {
				// the imposition woke something that forked: no single world
				// to thread. Conservative on both counts — the posting stays
				// owed (never a false cross-off) and later postings verify
				// against the unthreaded scratch (missed jointness only ever
				// keeps more)
				return trial(pending.tail(), survivors.append(posting), scratch);
			}
			Package grown = worlds.head();
			return unchanged(scratch, grown) ?
					trial(pending.tail(), survivors, scratch) :
					trial(pending.tail(), survivors.append(posting), grown);
		});
	}

	/**
	 * The worlds the imposition delivered, under the Exhaustion claim so
	 * completion is honest even when the imposition wakes suspension bodies —
	 * bodies are arbitrary goals and may spawn. Empty = the run stayed
	 * silent: the imposition failed.
	 */
	static Fiber<List<Package>> imposed(Posting posting, Package scratch) {
		Queue<Package> delivered = new ConcurrentLinkedQueue<>();
		return Exhaustion.exhausted(posting.impose().apply(scratch)
						.apply(pkg -> {
							delivered.add(pkg);
							return Fiber.done(nothing());
						}))
				.map(done -> List.ofAll(delivered));
	}

	/**
	 * Sound because every piece of solver knowledge lives IN the package —
	 * substitutions, factors, parked suspensions, tables — so an imposition
	 * that added knowledge necessarily perturbs the structure, and equality
	 * witnesses "nothing new". Errs only toward "changed" (bookkeeping
	 * growth, representation drift), the conservative direction — a missed
	 * entailment only delays: notes re-verify on every revise and the
	 * ground floor decides by answer time. If solver knowledge ever lives
	 * outside the Package, this classifier is where that breaks silently.
	 *
	 * <p>Exactness at the points this runs rests on the imposition law
	 * (idempotent normalization, the ground floor, no silent swallowing —
	 * the logic laws kit's claims): verification runs on post-drain
	 * packages, quiescent hence normalized, so an entailed imposition
	 * cannot drift. Per-factor mutual leq (each store's own Absorbable
	 * order) remains available as a drift-immune refinement — pure
	 * optimization, buying earliness on the delay side.
	 */
	private static boolean unchanged(Package before, Package after) {
		return before == after || before.equals(after);
	}
}
