# The table subsumes the database: one container behind the relation face, constraints crossing both ways

The engine has four boundary idioms that turned out to be one object
viewed from four sides: a solve's answers fed to the next solve, a tabled
call's entries, an external backend's rows, and an imperative algorithm's
output slotted back in. The unification (the human's, Aug 2026, built up
over the pushdown arc's close): **the TABLE is the one container — a
region-keyed memo of sealed answer cells — and `FactSource` is its one
consumer face.** A goal wrapped in a table and exposed through the face
is a derived relation: probed by region, produced to completion, served
by entry-level subsumption (wide serves narrow), memoized for the
table's lifetime. The in-memory database is the DEGENERATE CORNER of the
same container — every entry ground, every seal instant — and so is the
SQL adapter's pool: a `Fact` is an answer whose residues are TRUE.

The face is CONSTRAINT-TRANSPARENT in both directions, and this is the
load-bearing generalization: pushdown already made the engine's
knowledge flow INTO a source (the region parameter narrowing the
fetch); this note's direction makes a source's knowledge flow OUT (an
answer crosses with its whole normalized delta — residues, existential
witnesses — consumed by replay = rename ∘ absorb, the crossing tabling
already ships). Answers must NOT be reified at the boundary: a solve's
ground output is the compression EXPANDED (one constrained entry can be
a million rows, or nonterminating), so the face serves entries, not
labellings. Ground consumers get the degenerate read; constrained
consumers absorb.

The seal is the one consumption law, inherited unchanged: monotone
consumers may stream (the tabled call, unchanged, remains the
within-fixpoint face); non-monotone consumers — folds, negation,
imperative algorithms, the next stratum, external readers — wait for the
seal. Seal authority varies by producer: instant (stored/ground),
declared (a backend's pin), external (a future's completion —
Fiber.external), computed (completion detection). Strata are seals at
solve granularity; the stratified solve→fold→seed loop becomes plain
composition through the face.

Persistence rides the same stack: storing a tabled call is marshalling
its entries, and entries speak the store language — POSTINGS are the
wire format (the marshal round-trip obligation already on file in
postings-are-the-store-language.md becomes load-bearing for the memo
store). Store constraints, replay constraints: the persistence line and
this line are one line.

- **status**: argued (three rulings in conversation, Aug 2026: the
  table-backed source over a per-probe fresh solve — reification at the
  boundary kills compression; the table subsumes the database's READ
  face; constrained answers are the fundamental face shape, ground the
  degenerate corner). No code; the pushdown arc built the face's inward
  half.
- **evidence held**: derivation over shipped machinery — the entry
  already stores answers as whole normalized deltas
  (tabled-constraints, single-sorted keys); replay = rename ∘ absorb is
  receipted; entry-level subsumption (pointwise-⊑, entailment matching)
  is sharper than the pushdown cache's pattern+leq ledger and comes
  free; CachingFactSource demonstrated the memo-decorator shape and the
  probe-as-call-key identity at the data boundary. Deflation absorbed:
  the per-probe fresh-solve constructor (derived) was built as a
  candidate on paper and torn down — it expands compression at the
  boundary and shares nothing.
- **imports**: EDB/IDB ⋯import (Datalog: extensional vs intensional
  relations — sources are EDB, tabled goals IDB; receipt owed when the
  note graduates); completed tables enable non-monotone consumption
  ⋯import (SLG/XSB: sound negation requires completed tables — our
  seal-before-fold law is this result at three granularities; receipt
  owed).
- **obligations**: (1) PREREQUISITE — the canonicalization alignment:
  the face's probe must BE a Call key, so Regions' positional numbering
  yields to tabling's occurrence convention (resolver decodes
  position→slot from the reified image); decided, unbuilt. (2) The
  answer carrier at the face: (term, residues) — relation-shaped Fact
  plus its delta vs the engine's Constrained; pick with the first
  consumer. (3) The Table's lifetime and pin discipline: per-solve today,
  the source OWNS a persistent table over pinned bases — converges with
  #75 (memo store, pin stamps); the marshal round-trip (postings note
  obligation 3) is its wire format. (4) posted/GAC over CONSTRAINED
  candidates is open research — v1 keeps the posted path on the ground
  corner (expand or refuse), only exists-style replay consumes
  constrained answers; park with the trigger: a workload whose derived
  relations are meaningfully compressed. (5) Async production rides
  Fiber.external (#64): the entry's seal by external completion; scope
  billing so no solve seals past an in-flight fetch. (6) The degenerate
  receipt: the SQL adapter and ImmutableDatabase re-read as ground
  tables — no code moves until a consumer needs them moved (the
  database's mutation/trigger face and Support's ground indexes are NOT
  subsumed).
- **links**: tabled-constraints.md (entries, subsumption, replay),
  condition.md (finality, the founding sentence this cashes),
  domain-layer.md §4/§6 (the face's inward half as shipped; conditional
  answers as the outward half's carrier), postings-are-the-store-language
  (the wire format), sealed-table-zip.md (compression residences),
  negation-over-finite-goals.md (#118 — a sealed entry IS the finite
  goal), finite-goal-tier.md (#120 — which regions seal at all), #64,
  #75, #111 (the front door likely wears this face).
