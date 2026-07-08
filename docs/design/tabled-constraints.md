# Tabled constraints — the price list for merging the two fixpoint engines

**Status: DESIGN SKETCH (July 2026, from a design conversation with Tom). Not
implemented, not scheduled. This is the successor to `fixpoint-machine.md`'s
"don't merge the engines prematurely" caveat: it prices the merge so the
decision can be made deliberately when a use case pays for it. Until then, the
wall stands (see §2).**

Prerequisite reading: `fixpoint-machine.md` (the two-fixpoint mental model),
`minimal-constraint-vocabulary.md` (the store boundary this extends),
`tabling/Tabling.java`'s class javadoc (the master/slave protocol).

---

## 1. The two machines, in lattice terms

The engine runs one monotone-fixpoint machine in two directions:

- **Constraints descend.** A domain is an element of a lattice ordered by
  information ("smaller set = knows more"); propagators are monotone
  contracting operators; the agenda drain iterates them to a fixpoint; failure
  is ⊥, collapse is reaching an atom, the equal-domain guard is "descend
  strictly or stop". Finite descending chains give termination; monotonicity
  gives confluence (agenda order cannot change the answer set).
- **Tabling ascends.** A table's answer set only grows; new answers join in;
  the fixpoint is "no new answers this round". The ascending thing is the
  TABLE, not the search — search is the labor that feeds the ascent.

They meet in the middle at the `Package`. This doc is about what happens when
a tabled call executes under constraint knowledge, or produces answers that
carry it.

## 2. Today: the wall

Two guard pins keep the machines apart
(`TabledTest.shouldRejectTabledCallsUnderActiveConstraints`,
`shouldRejectConstrainedAnswers`): a tabled call under an active constraint
store throws, and a tabled answer whose variables carry residue throws. The
wall is loud, cheap and sound. It exists because the naive merge is SILENTLY
WRONG in three distinct ways (§3) — do not weaken the guards without
implementing this design.

## 3. Why the naive merge is unsound: three coupling points

Under constraints, both the things tabling equates stop being terms and
become REGIONS — a term plus the descending knowledge around it.

1. **Call time (completeness hazard).** Tabling asks "have I seen this call?"
   and answers by alpha-equivalence of the reified call. But
   `reachable(x), x ∈ {1..10}` and `reachable(x), x ∈ {1..3}` are alpha-equal
   terms and DIFFERENT regions. Reusing a table is sound only when the cached
   call's region CONTAINS the new call's region. A master that ran tight and
   a slave that arrives loose = the slave silently misses answers it is
   entitled to. Variant equality is correct exactly when every region is ⊤ —
   the condition the wall enforces.
2. **Answer time (termination hazard).** Answers become regions too
   ("x, provided x ≠ 3"). Deduplication by alpha-equivalence must become
   deduplication by SUBSUMPTION (`x ∈ {1..5}` makes `x ∈ {2,3}` redundant but
   they are structurally distinct). Worse: tabling's termination argument is
   "finitely many distinct answers", and the ascending chain of answer-regions
   need not be finite — even with perfect subsumption-dedup, an infinite
   ANTICHAIN (infinitely many pairwise-incomparable regions, e.g. Neq minting
   `x≠1`, `x≠2`, …) ascends forever.
3. **Consumption time (the easy one).** Replaying a cached answer into a
   consumer is the MEET of the consumer's state with the answer's region —
   which is what `resolve` already does. Soundness at consumption is nearly
   free, PROVIDED the answer's residue is actually re-imposed (§5.3); the
   silent-generalization failure the second guard test pins is exactly what
   happens when it isn't.

## 4. The key insight: the order decomposes per store

Everything hard above reduces to one operation: ENTAILMENT — `region A ⊑
region B`. And the `Package` being a product lattice means the order is
POINTWISE: A entails B iff every factor of A entails its counterpart in B.
So no cross-domain vocabulary is needed. Comparison is intra-store business;
the driver-side fold ANDs opaque per-store verdicts — the same custody
principle as the store boundary itself (stores understand their own state;
the framework combines answers it does not inspect).

**Known, accepted incompleteness:** the pointwise product order approximates
semantic entailment from below. Example: A = `x ∈ {1..5}` (FD) ∧ `x ≠ 3`
(Neq) and B = `x ∈ {1,2,4,5}` (FD, Neq empty) denote the SAME region, but
pointwise neither entails the other (A's FD factor is looser, its Neq factor
tighter). Cross-domain reasoning could see through this; the fold cannot. The
cost is only a missed cache hit — recomputation, never wrong answers. That is
the right trade for a decomposed API.

## 5. The design: three intra-domain hooks

`Propagation` does not change at all — tabling does not route through the
driver (`Table` is a plain store). The whole feature is `ConstraintStore`
growing three OPTIONAL hooks, plus logic in `Tabling`:

### 5.1 `project(vars, state, renaming) → Residue`

"My knowledge about these variables, canonically renamed" — an opaque,
store-specific value. Half of this exists: `reify` already renders residue
against a rename substitution; this is its data-shaped sibling. It MUST share
the call key's alpha-renaming (the `reifyS` substitution) so variable
identities line up between the reified arguments and every store's residue.

### 5.2 `entails(mine, other) → boolean`

The intra-domain order — the one genuinely new word. For FD: domain-wise ⊆.
For Neq: record-set implication (hard in general; see §6). Reflexive,
transitive; `entails` need not be complete (a conservative `false` costs
reuse, not soundness).

### 5.3 `restate(residue) → Goal`

Turn my residue back into statements through the normal public entries
(`resolve`/`activate`/`narrowed`), so a consumer replaying a cached answer
re-imposes its guards — the meet-at-consumption. Without this hook, cached
answers silently generalize (the second guard test's scenario).

### 5.4 What Tabling does with them

- **Key** = (reified args, map storeClass → residue), residues projected onto
  the call variables at call time.
- **Call matching, stage 1**: exact residue equality. Sound, complete per
  key, least reuse, trivially terminating in the key space iff residues over
  fixed vars are finitely many.
- **Call matching, stage 2 (optional upgrade)**: pointwise-⊑ subsumption —
  reuse an entry when its call region contains the new call's; the consumer's
  own tighter state filters answers at consumption for free.
- **Answers** = (reified term, map storeClass → residue); replay =
  instantiate + `restate` each residue. Dedup by pointwise entailment.

### 5.5 The termination gate

Entailment enables subsumption; it does not bound ascent. Participation must
be gated per store on a declared property: **"my residues over a fixed
variable set form a finite lattice"** (equivalently: no infinite antichains).

- FD qualifies: residues are domain assignments over a finite universe.
- Neq does NOT: records range over an unbounded value space (`x≠1`, `x≠2`, …
  is an infinite antichain). Neq participation requires a WIDENING (collapse
  record sets above some size to ⊤, trading deduction for termination) —
  a separate design decision, not assumed here.

A store that declines the gate keeps today's wall; the guard tests become
per-store rather than global.

## 6. Staging, if ever implemented

1. Hooks + FD-only, exact-equality keys, unconstrained answers still rejected.
   (Smallest sound opening; already useful: tabled relations under domains.)
2. Constrained ANSWERS for FD (residue storage + `restate` + subsumption
   dedup).
3. Pointwise-⊑ call subsumption.
4. Neq widening — only with a motivating use case; the deduction/termination
   trade needs one to be judged.

Each stage lands green with the guard tests refined, not deleted.

## 7. What this is for (the use case that would pay)

Memoizing pruned subproblems: a tabled recursive relation whose calls carry
domains caches "the answers to this subproblem GIVEN this region" — e.g.
partial schedules keyed by their temporal bounds, reachability under
resource windows, grammar/analysis fixpoints with value constraints. This is
TCLP (tabled constraint logic programming — XSB's TCLP, Mod-TCLP of Arias &
Carro), and the hook set in §5 is deliberately the same shape Mod-TCLP
requires of a pluggable domain: projection + entailment (+ our restate,
which they fold into answer resumption). The lattice reading and the
literature agree on the interface; that convergence is the main evidence
this design is the right shape.

## 8. Non-goals

- No cross-domain semantic entailment (§4's accepted incompleteness).
- No changes to `Propagation`, the agenda, or the store boundary's three
  triggers — this composes beside them.
- No general assert/retract: tabling (and `constrain`-mode queries, see the
  pldb notes) assumes facts and rules are immutable per solve. Dynamic
  programs break monotonicity and are out of scope everywhere in this engine.
