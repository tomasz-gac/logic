package com.tgac.logic.notes;

// ABOUTME: The verification step: impose a note's postings on a scratch package,
// ABOUTME: read each imposition three ways, route the four moves. Neq generalized.

import static com.tgac.functional.category.Nothing.nothing;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Neq's {@code verificationStep} with postings for pairs and the scratch for
 * the trial: impose the note's postings sequentially on a scratch copy of the
 * state and read each imposition three ways —
 *
 * <ul>
 * <li>the imposition FAILS → the forbidden conjunction is refuted, the note is
 * subsumed fully by the state → discard;</li>
 * <li>the imposition changes NOTHING → that posting already holds (given the
 * state and the postings before it) → crossed off, lawful by monotonicity:
 * knowledge only grows, so an entailed posting stays entailed;</li>
 * <li>the imposition brings NEW knowledge → the posting is still owed → it
 * survives, as its ORIGINAL self — nothing is ever read back out of the
 * scratch.</li>
 * </ul>
 *
 * No survivors → every posting already holds → the forbidden conjunction is
 * entailed → the branch fails. One survivor left IS the plain negative
 * constraint on that posting, same representation.
 *
 * <p>Sequential imposition threads bindings across postings sharing variables
 * — the same jointness as Neq's whole-record trial unification.
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
	 * none = every posting already holds — the forbidden conjunction is
	 * entailed, FAIL the branch; some(none) = some posting refuted — the note
	 * is subsumed fully, DISCARD; some(note) = keep, entailed postings
	 * crossed off.
	 */
	public static Fiber<Option<Option<Note>>> verificationStep(Note note, Package state) {
		return step(note.getPostings(), List.empty(), state);
	}

	private static Fiber<Option<Option<Note>>> step(
			List<Posting> pending,
			List<Posting> survivors,
			Package scratch) {
		if (pending.isEmpty()) {
			return Fiber.done(survivors.isEmpty() ?
					Option.none() :
					Option.of(Option.of(Note.of(survivors))));
		}
		Posting posting = pending.head();
		return imposed(posting, scratch).flatMap(outcome -> {
			if (!outcome.isDefined()) {
				return Fiber.done(Option.of(Option.none()));
			}
			Package grown = outcome.get();
			return unchanged(scratch, grown) ?
					step(pending.tail(), survivors, scratch) :
					step(pending.tail(), survivors.append(posting), grown);
		});
	}

	/** none = the imposition failed (the run stayed silent); some = the package it delivered. */
	static Fiber<Option<Package>> imposed(Posting posting, Package scratch) {
		AtomicReference<Package> delivered = new AtomicReference<>();
		return posting.impose().apply(scratch)
				.apply(pkg -> {
					delivered.set(pkg);
					return Fiber.done(nothing());
				})
				.map(done -> Option.of(delivered.get()));
	}

	private static boolean unchanged(Package before, Package after) {
		return before == after || before.equals(after);
	}
}
