# Weighted TCLP

**Status: DESIGN (Aug 2026).** Nothing is built; every stage is gated on
a customer and carries its receipts. The load-bearing claim:

> Every weighted TCLP mode has ONE SEMANTIC pipeline: uncompress the
> anonymized answer package, fold the semiring values, group, compress,
> emit as level-set constraints. The modes are fold schedules — WHEN the
> fold may run and which carrier admits it — and a schedule may fuse or
> bypass physical stages only under a receipted carrier law proving the
> shortcut observationally equivalent to that pipeline.

**The uncompression invariant.** A Condition is a compressed branch, and
the whole design leans on the compression being LOSSLESS: for every
captured answer package, enforcing its complete anonymized delta
(`Residues.all`) yields exactly the ground tuples obtained by grounding
the original branch first and projecting to the same boundary —

    ground(project(branch))  ==  enforce(anonymize(project(branch)))

The WHOLE package is the unit: grounding stores separately and
recombining them loses the correlations between them. If projection
ever drops a correlation, every downstream fold is lawful and wrong.
The receipt is executable, and its test family sweeps the couplings:
FD × disequality, FD × tuple relations, relations sharing variables,
existential join witnesses, aliases introduced late, recursive
consumed-answer witnesses. Every optimized schedule below depends on
this invariant; everything else is bookkeeping around it.

## 1. The two views of aggregation (read this first)

Aggregation has two semantically different readings, with different
termination behavior; keeping them named and separate dissolves most of
this design's apparent difficulty.

**Answer-set aggregation (solve-side).** The aggregate is a function of
the ANSWER SET of a sub-query: count of distinct answers, or sum/min/max
over a field the answer term EXPOSES. Two structural commitments define
this view: the payload rides the answer term — the fold reads solution
tuples and nothing else — and multiplicity is exactly once per distinct
solution — a solution derived five ways counts once. The sub-solve runs
under PLAIN tabling — presence semantics, idempotent, so cycles collapse
and the entry seals. The boundary then runs the pipeline: uncompress,
fold once per distinct solution, emit the scalar. Terminates wherever
plain tabling terminates, cycles included. Needs NO weighted cells; its
build path is Aggregate-onto-Semiring plus the uncompress-and-fold
boundary.

This is the engine's SET-SEMANTICS aggregate: **fold once per distinct
SOLUTION TUPLE, never once per distinct aggregate payload.** The payload
is not the solution identity — the aggregate carries a grouping shape
(solutionKey → payload), and distinct solutions with equal payloads stay
distinct until the fold. Receipt:

    product(a, 10)  product(b, 10)
    sum(price over distinct product solutions) == 20      // never 10

If the sub-query exposed only the price, presence dedup would collapse
the two solutions to {10} before the fold — the identity must ride to
the boundary. (SQL-style bag aggregation is a different quotient, not
this operation.)

**Derivation weighting (semiring-side).** The weight is a property of
the DERIVATIONS: number of proofs, probability of support, cost of the
best route. Its operational signature is the opposite of both
commitments above: a factor rides the PACKAGE, not the answer term —
dropped anywhere in a body, accumulated by ⊗ along each derivation,
read off the result separately from the answer — and one solution
counts once per derivation, weighted by body-only information the
answer never exposes. Answer-set aggregation can express none of that,
and simulating it by threading the accumulated value into the answer
term changes the answer set itself: every distinct accumulated value
becomes its own solution, dedup preserves them all, and under cycles
the value set can be unbounded where the in-cell fold (tropical min on
the bounded rail) converges. This view is the rest of this document.
Its bounded instances work today; everything else is gated below.

Distinct quantities get distinct names — from the worked example in §6:
countDistinctAnswers = 8, symbolic pieces = 3, region-point incidences =
11, derivation-ground instances = 16. None of these may be called just
"count".

## 2. The carrier: WeightedCondition

`Condition × semiring` is the wrong name — it suggests one condition and
one global weight. The carrier is a finite representation of a FUNCTION:

    WeightedCondition<W>  =  finite representation of  Assignments → W

    value at θ  =  ⊕ of every weight whose region contains θ

    [ (X ∈ 1..5 : 2), (X ∈ 3..8 : 1) ]   denotes   1..2↦2, 3..5↦3, 6..8↦1

Lifted operations (a pure value type, no engine contact):

- ZERO = no pieces; ONE = (TRUE : 1_W)
- P ⊕ Q = union of pieces; merge equal regions by base ⊕; drop zero pieces
- P ⊗ Q = cross pieces; meet regions; multiply weights; normalize

Target laws: (P ⊕ Q)(θ) = P(θ) ⊕ Q(θ), likewise ⊗. The master receipt for
everything in this document: **folding the compressed form must equal
uncompress-then-fold** (foldEarly == foldLate), as a property test
against grounding. No symbolic `star` on this carrier is required by any
schedule below.

## 3. The compressed-fold license

Folding WITHOUT uncompressing is a shortcut, lawful only under carrier
laws. The local rules:

    merge:  (R, a), (R, b)  →  (R, a ⊕ b)          always sound
    drop:   (B, w_B) under (A, w_A)                 sound iff
            B ⊑ A  and  w_A ⊕ w_B = w_A

The drop condition is pairwise and semiring-agnostic; the semiring only
determines how often it fires. Presence (1 ⊕ 1 = 1): always — the rule
collapses to pure region leq, which IS today's Condition absorption;
plain TCLP is this design at the trivial weight, where the shortcut is
always lawful. Counting: never except zero — equality-merge only.
Idempotent optimization semirings (min/max): true product-order
antichains.

These rules do NOT identify all equivalent representations
([(X∈{1,2} : 1)] vs [(X=1 : 1), (X=2 : 1)] denote the same function and
no rule merges them). No canonical-form claim is made. Where the license
does not apply, the fold waits for uncompression — that is the entire
content of the schedule split in §4.

## 4. The fold schedules

The pipeline is fixed; the schedules differ in when the fold runs and
which carrier admits it. The engine's public surface today: solveBounded
(BoundedSemiring, direct streaming), solveClosed (ClosedSemiring,
presence exploration + graph + StarSolve), plain solve/solveEach
(arbitrary Semiring, tabling refused). Capture-at-produce — each
derivation once, producer-side; consumers replay, never re-capture — is
the exact-once contribution identity every schedule relies on, present
by construction.

**The oracles — the semantics.** Two, one per §1 view; each is the
reference its schedules are property-tested against, not a fallback.
The ANSWER-SET oracle: evaluate the distinct answer set under plain
tabling, uncompress each conditional solution, fold once per solution
identity. The DERIVATION oracle: enumerate derivations WITHOUT
proof-collapsing tabling — plain tabling deliberately erases duplicate
proofs, the very multiplicity being measured — uncompress each branch,
fold late per ground answer. The derivation oracle is executable only
where that enumeration terminates; where the stream needs tabling to
close (left recursion), it does not exist, and the cyclic receipts
ground the whole program instead.

**Streaming fold (bounded rail).** The fold runs DURING the solve, on
the compressed form, licensed by §3 plus the carrier's top law.
Admission is the current `BoundedSemiring` capability, EXACTLY: the
cell's early-finality logic (isTop ⟺ value = one) assumes 1 ⊕ a = 1,
stronger than generic idempotence. Implementation:
`JoinMap<Term, WeightedCondition<W>>` with the §3 rules; presence
reproduces plain TCLP exactly (regression proof: plain suites green
under the unified rule). Receipt: every admitted carrier satisfies the
full capability the implementation uses, including the top law. An
idempotent-but-unbounded carrier that waits for seal instead of using
top-finality is separate future work.

**Sealed fold (acyclic, a small NEW structural mode).** For carriers
with no compressed-fold license and no top law (plain ℕ counting), the
fold can run only on the closed answer set: capture structure under
presence exploration; seal; uncompress; if the sealed graph is a DAG,
fold it topologically with the ordinary Semiring; if a cycle is
discovered, refuse unless a ClosedSemiring was selected. Acyclicity is
generally known only after exploration, which is why this is new code.
The default for derivation-valued queries remains the oracle wherever
plain enumeration completes; this schedule exists for programs that
REQUIRE tabling to close the stream (left recursion), where presence
dedup would falsify the multiplicity — the sharing win on deep DAGs is
a side benefit, not the motivation. Receipt: for a finite DAG, sealed
fold == full derivation enumeration + late fold; a discovered cycle
without star capability refuses.

**Closed fold (cyclic).** Uncompress at the seal; what remains is a
ground dependency graph indistinguishable from the one today's closed
solve builds when answers arrive ground; run the EXISTING StarSolve on
it, unchanged. Because the answer is the whole anonymized package, the
edge's delta is one package — join witnesses included by construction —
and enforcing it whole pairs each coefficient with the consumed answer
it actually multiplied. (Enforcing the pieces separately would break
that pairing; the package form makes the mistake unrepresentable.) The
edge payload is therefore the captured delta rather than a ground term —
a representation change at capture, not new machinery. The bill is
enforcement: the ground graph can be much larger than the symbolic one,
paid once per edge at seal — the same bill labelling always pays.
Receipt: symbolic constrained capture → uncompress at seal → StarSolve
must equal fully grounding the program first → scalar closed solve.
The current ℕ counting carrier is not closed and remains refused on
cyclic graphs; an extended carrier such as ℕ∞ is a separate future
semantic choice. Grounding coarser than per-point (level-set pieces of
the join, symbolic region difference) is a receipted future
optimization, lawful where the weight is constant per piece.

## 5. Delivery: group, compress, emit as level sets

The pipeline's output leg. After a seal-time solve (and in general
whenever weights vary within an original region), delivery restates
regions at the granularity of the solved weight function's LEVEL SETS —
the coarsest partition on which "one region, one weight" is truthful.
The pieces are never produced by splitting regions in constraint
language; they are built ground-up:

1. uncompress (enforce at seal — internal only, never the delivery
   format);
2. group cells by solved weight — pure data work, store-agnostic. The
   SEMANTIC baseline is one extensional piece per solved ground cell;
   equal-weight grouping is optional COMPRESSION, applied only where
   weight equality is lawful (exact values yes; approximate/floating
   values only under a declared equality). Cells whose solved value is
   semiring zero are omitted — the implicit zero region;
3. deliver each group as an EXTENSIONAL tuple-set factor with its scalar
   weight — the generic carrier, always available because the tuples are
   in hand; GAC propagates the set without forking per row;
4. optionally recompress — a store capability "express this ground set
   in your language, or decline" — with a narrow honest scope: it
   applies to UNARY structure only (per-variable, independent-product
   level sets, where FD merges adjacent equal-weight cells into
   intervals). Carried couplings make level sets non-product by nature
   (x ∈ 1..5, y ∈ 1..5, x+y=z: the extension lies on a plane no box
   product expresses — 25 tuples in a 250-point box), so for coupled
   anchors the extensional carrier is the NORM, and the compression
   opportunity lives INSIDE it: the tuple store's own sharing, and a
   decision-diagram representation as a future receipted rung.
   Correctness never depends on any of this.

Receipt: one-piece-per-cell delivery is observationally equivalent to
level-set-grouped delivery under arbitrary caller narrowing. Per-answer
replay is only the degenerate case where every cell is its own level
set. Caller constraints meet each piece BEFORE any further expansion, as
with every conditional answer.

**Layering:** the generic carrier is a logic-core ABSTRACTION to be
extracted, not pldb's implementation relocated. Logic owns a generic
finite tuple relation — immutable finite tuples, projection/support
lookup, binding intersection, enforce, project/rename, optional support
indexing. One REPRESENTATION backs three views — enumeration (a goal),
support (a GAC constraint factor), delivery (pieces) — as separate view
surfaces over the same data, not one behavioral interface doing all
three jobs. pldb adapts Database/Relation/Fact and its
index re-querying to that abstraction. The same structure is wanted by
the FactSource seam's in-memory reference and by the row-set constraint
store; the FactSource work builds it for its own reasons, and this
design inherits it.

## 6. The boundary walked: uncompress, then fold

Enforce is the uncompression door — the ONE operation that reliably
materializes a region's points, with cross-store pruning (FD ∧ Neq ∧
tuple sets) handled by construction. Walked example, counting, cell as
in §2: two branches (2, 1) → uncompress → eleven ground emissions →
fold per term: 1..2↦2, 3..5↦3, 6..8↦1. countDistinctAnswers = 8;
derivation-ground instances = 16. No inclusion-exclusion: after
uncompression all contributions share a ground basis and ⊕-per-term is
sound. Spans are NEVER computed at emission — a per-region total cannot
narrow against a caller and double-counts across overlaps, irreparably.

## 7. Termination

Per schedule, since the fold's timing changes what must be finite.

**Oracles:** finite answer/derivation enumeration, and finite
enforceability of every residual region.

**Streaming fold:** finitely many call keys; finitely many answer
terms; no infinite strict ascent in any cell — BOTH halves, regional
cover and weight, because weights are not the only delta driver:
regions can grow forever (X=1, X=2, … — integer singletons, shifting
intervals, growing disequality sets, infinite chains in custom stores)
with the weight constant. Slot count does not bound region count;
idempotence only makes EXACT duplicate rediscovery inert. Propagation
and projection must terminate. Fair scheduling is the operational
condition for DISCOVERING the fixed point, not a mathematical condition
for its existence.

**Sealed fold:** finite captured graph; acyclicity (a discovered cycle
without star capability refuses); finite uncompression of the captured
packages — the topological fold is then finite by construction.

**Closed fold:** finite captured symbolic graph; FINITE uncompression
to the ground graph — finite-but-huge grounding is a cost problem,
infinite grounding is a refusal boundary; a defined StarSolve result
under the closed carrier's laws.

Bounded ascent is the program author's responsibility, as it already is
for plain tabling.

## 8. Probability stays separate

Two different overlaps. Constraint-region overlap (one assignment in
several regions) is solved by the pipeline — uncompression puts all
contributions on a common ground basis. Event overlap (two derivations
sharing a random fact) is NOT — it exists identically without
constraints, and uncompressing the answer term does not reveal the
dependence: ⊕-as-plus overcounts inside a term. Count, sum, min, max do
not have this problem under derivation-multiset semantics; an operation
wanting distinct underlying EVENTS is another identity question, decided
like §1's. Probability-like semirings choose a tier before any wiring:
exclusiveness as a documented program obligation (the engine does
nothing), or support formulas in the value with disjointed evaluation at
the boundary. Independently gated.

## 9. Literature and the novelty claim

Component by component. Each mapping LOCATES the design; none is a
leaned-on import — nothing here carries a theorem-import receipt, and
the recollections have not been verified by a focused review. The
moment a schedule's admission or a carrier choice leans on one of these
results, its receipt is owed first.

- **The pipeline** is the stated semantics of two fields. Semiring
  provenance for Datalog (Green–Karvounarakis–Tannen) DEFINES the
  annotated answer as the fold over the fully ground program — the
  closed-fold receipt restates that definition as a test. Algebraic
  model counting (Kimmig–Van den Broeck–De Raedt) is the same shape
  propositionally: ground to a circuit, then fold; their
  compiled-circuit properties (decomposability, determinism) play the
  compressed-fold license's role — structural laws under which folding
  the compressed form equals folding the extension.
- **The carrier.** A weighted conditional answer is a row of a
  semiring-ANNOTATED c-table (Imieliński–Lipski conditional tables,
  combined with annotations in the provenance line). The denotation
  Assignments → W with ⊕ is the c-semiring soft-constraint formalism
  (Bistarelli–Montanari–Rossi), with one deliberate divergence:
  c-semirings project a variable away by ⊕-summing over it — exactly
  the early span this design forbids. Here projection keeps witnesses
  and defers every ⊕ to the ground basis.
- **The uncompression invariant** is the representation condition of
  constraint databases (Kanellakis–Kuper–Revesz) — evaluation commutes
  with the represented extension — specialized to answer tables.
  Level-set delivery resembles their post-elimination output form; as a
  NAMED delivery operation in tabled LP no precedent is known (nearest:
  unnamed equal-probability grouping in probabilistic databases).
- **The fold schedules** follow Mohri's semiring shortest-distance
  program: which algorithm a carrier admits is read off its laws. Per
  schedule: streaming ≈ XSB partial-order answer subsumption, whose
  documented in-table unsafety for sum/count matches the bounded-rail
  admission; sealed ≈ semiring parsing (Goodman) — topological fold of
  a packed derivation forest, acyclicity required; closed ≈ Lehmann's
  algebraic closure, where StarSolve already lives. ℕ∞ has a canonical
  home — ω-continuous/complete semirings (Kuich, Ésik) — and the
  frontier past linear equations is Newtonian program analysis
  (Esparza–Kiefer–Luttenberger).
- **The two views** are the database world's stratified-aggregation vs
  annotated-semantics split (Soufflé and Flix sit on the stratified
  side; Dyna erases the split by weighting everything). Set-semantics
  aggregation as a fold over the exact-once replay of a presence-sealed
  table is an XSB idiom (tabling + findall at completion) that no known
  system states as a theorem. Mod TCLP (Arias–Carro, Ciao) anchors the
  conditional-answer tabling itself — entailment-checked constrained
  answers, no weights.
- **Probability tiers**: the probabilistic tabling systems (PITA,
  ProbLog) own the event-overlap problem and its solutions; §8's tier
  choice selects between their obligations, it does not compete with
  them.

Safe wording of the novelty claim: every piece has a home; the
unclaimed junction is conditional answers as the semiring cell's VALUE
over this engine's fiber-computed completion substrate, seal-time
grounding licensed by an explicit losslessness invariant, and delivery
re-entering constraint language through level sets. Novelty has not
been established by a focused literature review.

## 10. Build plan (gated, each step with receipts)

1. **Aggregate onto Semiring** — answer-set aggregation (§1): plain TCLP
   + boundary uncompress-and-fold, with SOLUTION IDENTITY distinct from
   aggregate payload (the §1 receipt), named quotients, and the
   uncompression-invariant test family over the store couplings — the
   boundary is the first consumer that leans on it. Serves the
   aggregations currently on the board — all set-functions of exposed
   answer fields; nothing below starts before a per-derivation or
   package-resident quantity demands it.
2. **WeightedCondition<W> as a pure value** — denotation, lifted ops,
   §3 license; property-tested against grounding (foldEarly ==
   foldLate).
3. **Streaming fold** — the BoundedSemiring rail, reusing JoinMap;
   presence reproduces plain TCLP as the regression proof; the
   capability receipt per carrier.
4. **Sealed fold** — the new acyclic structural mode, gated on a
   tabling-requiring derivation-valued customer; its enumeration receipt
   and its cycle refusal.
5. **Closed fold** — only against a real cyclic weighted-constrained
   workload: uncompress at seal, existing StarSolve over the demanded
   closure, level-set delivery on the extensional carrier; the grounding
   receipt guards the capture representation.
6. **Probability** — independently gated on its tier decision.

## 11. Kill criterion

If real aggregation over constrained tabled queries stays within
answer-set aggregation — which everything currently on the board does —
then steps 2–6 never build and nothing on the board is lost. What stays
unserved is exactly the derivation-weighted territory: package-resident
quantities beyond the bounded rail — sealed folds where the stream needs
tabling to close, cyclic closures over conditional answers. This
document then remains the map of what was deferred and why.
