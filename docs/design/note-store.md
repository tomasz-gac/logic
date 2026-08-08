# The note store — one mechanism for "not this" and "either-or"

**STATUS: DESIGN, PASS RUN (August 2026). The design pass's rulings are
folded in below: the scratch-copy check is capped at depth one (§4),
compound is the crossing representation with distribution bounded to
positive notes (§5), and the chassis survived its cheapest kill — Neq's
`verificationStep` is the four moves computed in one trial-unification
pass, with enforce-the-last degenerate for `Prefix` cargo. Every name —
`NoteStore`, `Exclusion`, `Disjunction`, `lit`, `inputs` — remains a
PLACEHOLDER for the human's naming call, which gates the first line of
stage-one code. This doc
supersedes lattice-store.md's co-store pitch by restating it in the
vocabulary that survived the comprehension veto, and extends it to the
positive case. Companions: `condition.md` (§0 the homes picture, §8.3 the
ring-side story and the imposition spectrum, §§8.5–8.6 the features that
land on this substrate), `lattice-store.md` (the value-store family this
one is parameterized by).**

---

## 1. The mechanism, in plain words

Neq already ships the whole trick. `separate(Tuple.of(x,y), Tuple.of(1,2))`
stores a note: **"x≠1, or y≠2 — at least one escape must hold."** The
note's life has exactly four moves:

- an escape becomes impossible (x bound to 1) → **cross it off**;
- one escape left → **enforce it** (the note becomes a plain constraint
  on y, watching it);
- no escapes left → **fail the branch**;
- an escape comes true on its own (x bound to 3) → **discard the note**,
  it is satisfied.

Cross off / enforce the last / fail on empty / discard when satisfied.
(The literature calls the note a clause, the escapes literals, and the
lifecycle unit propagation — the names matter less than the four moves.)

**The whole design is: keep the note-keeping, let the escapes be bigger
things.** One store class, parameterized by what an escape is. To run
the four moves, the store asks an escape only two questions:

1. did you come true? (→ discard the note)
2. did you become impossible? (→ cross you off)

Anything that can answer those two questions can be an escape — and the
admission ticket is a TYPE (the human's call, August 2026):

    NoteStore<V extends Semilattice<V> & PartialOrder<V>>

The bound is the PAIR, and not `Semilattice` alone, for a reason the
Semilattice javadoc states: the interface is deliberately
DIRECTION-UNNAMED — `absorbedBy` induces AN order but withholds which
way entailment reads it (meet-carriers flip: `Residues.absorbedBy` is
literally `other.leq(this)`; join-carriers don't: `Condition`'s
containment is `absorbedBy` unflipped — same formula, opposite readings).
`PartialOrder` is the cargo's DIRECTION COMMITMENT, so "narrower entails
wider" means one thing across all cargoes. The store needs that order,
not just the operation: escape dedup within a note and note-subsumption
between notes (a note whose escapes are all dominated by another's is
redundant — clause subsumption) are `leq` questions; the two lifecycle
questions stay cargo-delegated. Every intended cargo already qualifies or nearly does:
`Domain` ✓, `Residues` ✓, `Condition` has the Semilattice face and its
`absorbedBy` IS the order (declaring `leq` is three lines), and `Prefix`
declares the pair as part of the re-seat's transcription — a lawful
instance with its laws test, per the adoption rule. Direction-neutrality
does real work: `Domain`/`Residues` accumulate by meet, `Condition` by
join, and the house `Semilattice` is deliberately direction-unnamed — so
`NoteStore<Condition>` is WELL-TYPED: nested notes, escapes that are
themselves disjunctions, the re-homing/distributivity story visible in
the signature. Any future lattice (pldb's Support, #75's epoch lattices)
is admissible cargo the day it declares the pair.

## 2. The instances — one store, three cargoes

| escapes are… | answers the two questions via | user front door | status |
|---|---|---|---|
| unifications (`Prefix`) | walk-and-compare; trial unification — word-for-word Neq's verification today | `Disequality.separate` (unchanged) | Neq SHIPPED; re-seat = hygiene, do last |
| lattice boxes (`Domain`) | leq; meet-then-⊥-check — shipped on every `Domain<L>` | `Exclusion.notin` / `exclude` (new) | design |
| whole packs (`Residues`) | entailment per factor; the scratch-copy check (§4) | `Disjunction.either` (new) | design |

The `Prefix` row is the human's "store of unifications" observation made
respectable: unification is a value algebra (unify = meet, failure = ⊥,
walk = the entailment check), and the engine already has its data form —
`Prefix`, which is exactly what Neq's records hold. The old rejection of
Substitutions-as-a-Store (the capability wall) does not bite here:
**literals are cargo, not residents** — the note store borrows the
substitution lattice's three operations without asking it to be a store.

The polarity of an escape ("avoid this" vs "be in this") is a flag, not
a machine: the two questions swap answers —

- positive `x∈R`: true when `current ⊑ R`; impossible when
  `current ∧ R = ⊥`;
- negative `¬(x∈B)`: true when `current ∧ B = ⊥`; impossible when
  `current ⊑ B`.

Same two lattice operations, swapped.

## 3. The grid — and its fourth cell already shipped, twice

| an escape is… | points | boxes/regions |
|---|---|---|
| negative ("avoid") | Neq — SHIPPED | `Exclusion` — design |
| positive ("be in") | FD domains (unary: `x∈{1,2}` IS "x=1 ∨ x=2" compressed); pldb's row-set store (n-ary, #61) — SHIPPED | `Disjunction` — design |

A table constraint is a disjunction of positive point-tuples, and **its
GAC propagator is the agreement move (§4) for point cargo**: prune
unsupported rows, project survivors per variable, tell the domains. The
"new" deduction below has a shipped precedent.

## 4. The two extras that only fat escapes need

**The scratch-copy check** (is a whole pack still possible?): add the
pack to a scratch copy of the current state and watch whether propagation
fails. Scratch copies are free — immutable Packages. Failure is observed
by the drained machinery aggregate/conda already ride. Verdicts TIER at
the run-lane boundary (condition.md §8.1): at drain-quiescence the check
is cheap and sound-but-incomplete (a merely-parked suspension in the
scratch proves nothing); the full alternation is complete but terminates
only as the program does.

**RULED (the pass): the check is capped at depth one.** A scratch-copy
check never opens another — a `Residues`-cargo escape examined INSIDE a
scratch answers "still possible" without proof. Soundness rests on one
asymmetry: crossing off requires a WITNESSED failure, keeping requires
nothing. Every failure a scratch can witness is exact — a meet
emptying, a propagator's verdict, enforce-the-last imposing its
survivor's pack (real propagation), or fail-on-empty, which needs every
escape crossed off and under the cap assembles only from exact
cross-offs. Conservatism is closed under itself: keeping an escape can
only make a scratch fail LESS, which keeps more. What is kept too long
dies at real imposition — narrowing makes the impossibility visible to
the exact operations, or labelling at enforce imposes the pack for real
— so the cap costs search time on doomed alternatives, never answers.
Recursing would not buy completeness anyway: mutual waking of
store-resident notes forces an in-progress cut whose cycle answer is
the same conservative "possible", the cost per wake is exponential in
depth on the chokepoint's hot path, and the question deep scratches
answer — "would this alternative survive full exploration?" — already
has a fair, billed, chaos-tested home: the search itself. Depth is a
tier with a measurable trigger (branches routinely dying at labelling
that a deeper check would have killed early), not a principle.

**The agreement move** (lift): whatever ALL surviving escapes agree on
holds now, unconditionally — from `(x∈1..3) ∨ (x∈7..9)` the store may
tell the world `x ∈ {1..3} ∪ {7..9}` before anything is decided, so
`x = 5` dies without a choice ever being made. The forked encoding can
NEVER make this deduction — each branch knows only its own half; no
place holds "either way." Needs: a JOIN on the escape's lattice, as an
opt-in projection per the `Lattices.Mask` precedent ("meet inherited,
join exposed as a projection"); hull joins are sound (a wider statement
is still entailed by every world — exactness buys sharpness, never
correctness); no join → the store degrades to the four moves, still
correct. Herbrand's join (anti-unification) tiers: all-escapes-bind-
equally (an equality check) first, msg later.

## 5. The products — API sketches (names = the human's call)

```java
// Product 1 — negative constraints, inexpressible today:
Exclusion.notin(x, Interval.of(1L, 3L));               // x ∉ 1..3
Exclusion.exclude(                                     // ¬(x∈1..3 ∧ y∈5..9)
        lit(x, Interval.of(1L, 3L)),
        lit(y, Interval.of(5L, 9L)));
// blackout windows, forbidden assignments, "this row-pattern never occurs"

// Product 2 — `or` between constraints, without forking:
Goal taxRate = Disjunction.either(
        dom(income, Interval.of(0L, 85_000L)).and(rate.unifies(12L)),
        dom(income, Interval.of(85_001L, 190_000L)).and(rate.unifies(24L)),
        dom(income, Interval.of(190_001L, MAX)).and(rate.unifies(35L)));
// no branch exists at posting; propagation crosses off wrong brackets as
// income narrows, enforces the survivor; leftovers label at enforce.
// CONTRACT (a mid-solve pin): packs must be pure constraint postings —
// a pack that generates or forks refuses loudly at statement.

// Product 3 — Neq re-seated: separate() keeps its signature; internal only.

// The free rider — notes are constraints, so they cross boundaries like
// any factor (Projectable): cached answers read back conditionally —
//   {route = 42  GIVEN  income ∈ 0..85000 ∨ income ∈ 85001..190000}
```

**RULED (the pass): compound at the crossings; distribution is bounded
and per-boundary.** When project lifts a note into a conditional
answer, the answer's Condition holds it as ONE conjunct whose Residues
carries the note whole as a NoteStore factor — never eagerly
distributed into one conjunct per escape. Three reasons, in force
order:

- **Distribution un-builds the feature downstream.** Delivery streams
  per conjunct, and each delivered conjunct restates into the consumer
  as its own branch: a distributed disjunction replays as forks — the
  exact forks `either` exists to avoid — while a compound note replays
  as one delivery whose restate re-imposes the note.
  Or-without-forking survives the table only under compound.
- **Distribution splits the ⊕, never the wrapper — polarity never
  dissolves.** A negative note distributes by De Morgan
  (¬(A ∧ B) = ¬A ∨ ¬B → one conjunct per literal), but each piece is a
  UNIT NOTE — a single-escape exclusion, still a NoteStore factor —
  because a bare `Domain` factor asserts membership and cannot say
  "not" (a box's complement is not a box). Unwrapping into a resident
  positive factor is available only to positive escapes; a carrier
  that can genuinely express a complement may recompress a unit
  exclusion as an opt-in capability. An exclusion never explores under
  EITHER representation — negative boxes denote infinite regions, so
  they never label; their finite exit is parasitic on a positive
  generator (FD enumerates, each binding wakes the note, excluded
  points die — veto, not generation), and absent one they ride out as
  residuals, non-ground. Distribution's cost for negatives is therefore
  a pure DELIVERY artifact: the De Morgan conjuncts overlap (¬A and ¬B
  share the both-escape worlds), neither absorbs the other, so the same
  answer delivers twice and the consumer's downstream search runs
  duplicated under overlapping filters — paid for an answer that never
  had alternatives to explore — against one gain, exact antitone
  subsumption on unit boxes. De Morgan is also the general law behind
  §8.5's negation home: ¬Condition De-Morgans conjunct-wise into
  notes — the store IS the CNF side of the house, and the ruling is
  precisely "hold that side compound instead of paying the crossing."
- **The costs are asymmetric.** Compound errs toward fat-but-correct:
  subsumption against a disjunctive factor is conservative, so the
  table may keep an entry it cannot prove covered — missed reuse,
  never a wrong answer. Distribution errs toward exponential (k^n
  conjuncts for n notes of k escapes), and spends the two properties
  the gated tail needs whole: negation's blowup-free home (¬Condition
  stays notes, never DNF) and the agreement move's grip on the note.

A boundary may still distribute deliberately, with a receipt; the
sanctioned case is the fold's uncompression — enforcement is expansion
by definition.

**Split transcribes, wrapped, polarity intact.** `NoteStore.split`
extracts the notes mentioning the split variables AS NoteStore factors
— canonically renamed, never simplified into another store's
representation (a unary exclusion handed to FD as a Domain factor
would flip polarity silently; structure's one owner). Two obligations
ride this: subsumption on excluded boxes is ANTITONE (excluding a
bigger box denotes a smaller region, so note-level leq reverses the
box-level leq — the polarity swap surfacing in the order), and
enforcement of a negative is SUBTRACTION against the resident positive
domain — an exclusion alone denotes a cofinite region, which is why an
answer whose only knowledge of a variable is an exclusion reads as
non-ground (`Reified.isGround` false) and refuses the fold, consistent
with the aggregation ruling.

## 6. The build, staged — each stage green with a customer

1. **Chassis + `Domain` escapes, negative** → ships `notin`/`exclude`
   end-to-end. Smallest slice; no scratch-copy machinery needed (single
   escapes answer the two questions by direct lattice ops); chaos-tested.
2. **`Projectable` on the store** → notes ride keys and answers — TCLP
   learns "given x ∉ …"; the re-homing cycle (condition.md §0) closes
   for ∨.
3. **Scratch-copy check + `Residues` escapes + the agreement move +
   labelling** (`enforce` → `Conde` of restates) → `Disjunction.either`,
   the piecewise/optionality/scheduling workloads.
4. **Gated tail**: the ¬ operator (condition.md §8.5 — this store is its
   blowup-free resident home: ¬Condition = notes, never distributed to
   DNF), clause learning tier 1 (§8.6 — learned nogoods ARE negative
   notes; the clause DB is this store behind the Table transport
   pattern), polarity mixing, the Neq re-seat (last; hygiene).

New code, all small: the store class (bag of notes, watched-two-escapes
bookkeeping, the four moves, `ConstraintStore` + `Projectable` faces);
the scratch-copy seam (compose absorb + drained); the join projection
per opting-in `Domain`; the labelling hook. Reused whole: `Watches`,
`LatticeStore.getValue` (reads in revise are legal — custody restricts
writes), `Residues.restate`/rename for crossings, narrowing wakes, GAC
as the agreement-move precedent, `Prefix` + trial unification as the
first cargo.

## 7. Risks, named

Watch fan-out on fat notes (watched-two-escapes is the mitigation; real
bookkeeping); the CNF/DNF representation dial — RULED in §5: compound
at the crossings, distribution positive-only and per-boundary with a
receipt; scratch-run re-entrancy — RULED in §4: depth one; and the
standing doctrine — every stage prices against the suite before it
merges.

## 8. Why this one build compounds

condition.md §8.3 (the disjunctive imposition tier), §8.5 (negation's
resident home), and §8.6 (the clause DB) all land on this substrate.
One chassis converts three research items from "design with unknown
carrier" into "wiring over a shipped store" — and the engine's negative
and either-or vocabulary stops being three bespoke mechanisms and
becomes one store with three cargoes.
