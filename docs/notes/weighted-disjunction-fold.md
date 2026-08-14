# The disjunct's weighted fold: sum at decided, absorption as the short-circuit

- **status**: parked design (August 2026) — argued to completion in
  conversation, then discovered UNREACHABLE today: `SemiringStore`
  implements only `Packaged` — not a ConstraintStore, no Propagation
  door touches it — so no expressible program can place a weight on a
  disjunct's alternative. The capability wall ("a Posting can only talk
  to the chokepoint") makes the shipped DisjunctionConstraints
  unconditionally correct in the current engine, and this whole design
  waits, thinking-debt-free, on its trigger.
- **evidence held**: derivation (the distributivity theorem below); one
  negative witness (the scratch-sum counterexample — the boundary this
  design must not cross); the shipped store as the boolean instance.
- **imports**: semiring, absorption (w ⊕ (w ⊗ v) = w — the law the
  boolean store's discharge instantiates; Condition's "dominated drops"
  is the same identity), distributivity; nothing new to ratify until
  built.
- **obligations** (all trigger-gated): (1) build the fold exactly as §
  below, then MEASURE — counting-semiring receipts (summed weights =
  conde derivation counts on decided shapes; weighted-conde parity when
  owed rivals fork) and the scheduling benchmark under counting; (2)
  the human's ruling, August 2026: the upgrades (§ upgrades) are built
  ONLY after the base fold is created and measured; (3) per-alternative
  weight extraction and the store's access to ⊕ are the build's two
  open designs — the second is the first-class-weight decision point.
- **links**: disjunction-store-pays-in-products.md (the boolean store's
  economics), condition.md §8 (weight ⊗ Condition — upgrade 1's home),
  weighted-tclp.md §4 (level sets — upgrade 2's home; the per-piece
  constancy shelf this note's boundary re-derives), semiring-inference.md,
  #116 (the trial-as-doom-oracle observation: both doors' doom checks
  are the trial's two decisive verdicts under the isDone guard —
  refuted-permanent read at the pricing seat), #88 (the trigger's
  likely bearer).

## The fold (one code path, no modes — the semiring decides)

Per alternative, against the branch weight w:

- **refuted → eliminated** (contributes 0; 0 ⊕ x = x everywhere) — as
  shipped.
- **entailed → removed, its weight ⊕-accumulated into a pending
  SUMMAND carried by the disjunct.** License: an entailed alternative's
  imposition is a no-op, so its branch is w ⊗ vᵢ ⊗ C with the SAME
  continuation C as the general branch — distributivity factors the sum
  as (⊕ᵢ vᵢ) ⊗ (w ⊗ C). Exact, computable now, permanent (entailment
  is monotone-stable).
- **owed → stays.** Its imposition narrows, its continuation differs,
  its contribution is not factorable — and not scratch-summable, see
  the boundary.

Per disjunct:

- **all decided** → the summand is the disjunct's whole value, a pure
  weight factor: emit it (an always-ripe suspension absorbing a
  SemiringStore factor — the unit vehicle; Revision.updated cannot
  carry it, the same-store contract binds the factor slot) and
  discharge. One general answer, weight = the sum, zero forks — the
  human's fold-in, in its sound scope.
- **all refuted, empty summand** → fail. **One owed, empty summand** →
  unit imposition (the survivor's own factors ride free).
- **owed remain** → resident WITH the summand (a summand, never a
  factor, while rivals live — folding it early would multiply into the
  owed branches' weights); the ground floor emits one general branch
  weighted by the summand plus one branch per owed alternative. The
  entailed collapse still happened: k entailed alternatives = one
  weighted branch.

Degeneracy check: absorptive semirings collapse the summand to 1 and
additionally license dropping owed rivals (w ⊕ w ⊗ v = w needs no
computing) — early discharge, the shipped store verbatim. Min-plus and
Viterbi absorb; counting and provenance do not.

## The boundary (the scratch-sum counterexample)

Counting semiring: x≡1 ∧ anyOf(x≡1, y≡2) ∧ y≡3. At verification the
first alternative entails, y≡2 is owed; scratch-running it succeeds
against the PRESENT and a sum 1 ⊕ 1 = 2 discharges — then y≡3 kills the
y≡2 derivation and the true count is 1. Owed means compatible-now; the
contribution depends on compatible-later. Scalar weights therefore
force the all-decided rule; discharge with live owed rivals makes the
weight NON-CONSTANT over the general answer's region — exactly
weighted-tclp §4's per-piece constancy shelf, re-derived from the store
side.

## The upgrades (gated behind create-and-measure of the fold above)

1. **Guarded summands** — weight values as small Conditions: the owed
   contribution rides as (v given [its region]); future knowledge
   evaluates guards; discharge eager, weight symbolic. Weight ⊗
   Condition, arrived at from the store side.
2. **Level-set emission** — scalar weights, sum deferred to the ground
   floor where all knowledge is in and the per-piece sum is sound: the
   general answer splits by weight ({y=2 : 2}, {y≠2 : 1} — the nogood
   store carving the pieces). Weighted-tclp's own emit pipeline meeting
   the disjunctive store.

That both upgrades re-derive standing designs from the other doc is
this note's headline: the weighted disjunct and weighted TCLP are one
machine seen from two ends.

## First-class weight (the build's decision point)

The store needs ⊕ and per-alternative weights. Mechanically the
custody-clean route exists (consequences via suspension; capability
query for the semiring), but weight fits no store shape — every store
is name-keyed knowledge, weight is a branch-global scalar — and the
human's standing conception is a substitutions SIBLING at the package
top level. A tabling simplification may also be waiting on it (the
human's hint, unchased). Decide when building the fold, not before.

TRIGGER: weighted TCLP ships the weight↔posting crossing (#88 / weight
⊗ Condition) — the moment weights become expressible where postings
live, the shipped store's unconditional correctness expires and this
design comes off the shelf.

The trigger is LOUD by type, not by memory (checked Aug 14, the
human's guard ruling): a weight-shaped alternative is UNREPRESENTABLE
today — `Stored.getStoreClass()` returns `Class<? extends Store>` and
SemiringStore is not a Store; Absorption holds Absorbable, which
SemiringStore is not either. A runtime guard in anyOf cannot even be
written, let alone reached. The crossing therefore cannot arrive
silently: it must WIDEN A TYPE (SemiringStore becoming a Store, or
weight going first-class in Package), and the FIRST ACT of whoever
widens it is the runtime guard in the disjunction door — refuse
weight-carrying alternatives until this note's fold ships. That
obligation transfers to the crossing's builder by this sentence.
