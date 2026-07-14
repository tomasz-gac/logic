# Semiring-weighted logic programming — design sketch

**Status:** design sketch, NOT implemented. Nothing here is needed for the library to work
today. It describes how to generalize the engine from "find solutions" to "compute a
weighted value over all proofs" — which yields counting, probability (Bayesian inference),
shortest-path, most-probable-proof (MAP), provenance, and gradient-based learning, all as
instances of one small abstraction. Read this top to bottom before starting; do the phases
in order; do NOT skip to the hard parts.

---

## 1. The one idea

A logic engine already has the two operations a **semiring** needs:

- **⊗ (times)** combines *along a proof* — this is conjunction (AND of subgoals).
- **⊕ (plus)** combines *across proofs* — this is disjunction (the alternative derivations
  the search enumerates).
- **0** = failure, **1** = trivial success.

So if you thread a semiring value through the search, the *same logic program* computes
different things depending on which semiring you plug in. The engine enumerates the proofs
(it already does this); the semiring turns that enumeration into a value.

| Semiring `S` | ⊕ / ⊗ | `solve` computes | Notes |
|---|---|---|---|
| Boolean | ∨ / ∧ | is there a proof? (today's behaviour) | clean |
| Counting (ℕ) | + / × | number of proofs | clean (this is `aggregate.count`) |
| Probability [0,1] | + / × | marginal probability (Bayesian) | **the only tricky one — see §6** |
| Viterbi | max / × | most-probable proof (MAP) | clean (idempotent ⊕) |
| Tropical | min / + | shortest / cheapest proof | clean (idempotent ⊕) |
| Provenance | ∪ / ∧ | which facts explain it | clean (idempotent ⊕) |
| Expectation | pair ops (§8) | derivative → learning weights | advanced; §8 |

**Key fact to internalise:** every semiring above is clean and free of the hard problem in
§6 EXCEPT probability. The framework is not risky; one specific instance is.

---

## 2. The `Semiring<S>` abstraction (SHIPPED, as capability types)

Java 8. This is the whole abstraction — a few methods. The work lives in the instances,
and the instances are each a few lines.

```java
public interface Semiring<S> {
    S zero();                 // ⊕ identity (failure)
    S one();                  // ⊗ identity (trivial success)
    S plus(S a, S b);         // ⊕ : combine across proofs (disjunction)
    S times(S a, S b);        // ⊗ : combine along a proof (conjunction)

}
```

As shipped, the optional capabilities are TYPES, not flags or throwing
defaults: `ClosedSemiring` declares `star` (so "no closure" is
unrepresentable), `IdempotentSemiring` carries ⊕-idempotence,
`SuperiorSemiring extends IdempotentSemiring` (the subtyping is a theorem).
Kit signatures demand the capability; the coverage gate audits every
implementor.

Instances to write (each is trivial):
- `BooleanSemiring` : `false/true`, `||`, `&&`, `star = true`.
- `CountingSemiring` : `0/1` (long/BigInteger), `+`, `×`, no star on cycles (diverges).
- `ViterbiSemiring` : `0/1` (double), `max`, `×`, `star = 1`.
- `TropicalSemiring` : `+∞/0` (double), `min`, `+`, `star = 0`.
- `ProbabilitySemiring` : `0/1` (double), `+`, `×`, star only under §6 assumptions.

Which engine machinery each plug may keep is not a per-plug design
decision: it derives from the plug's checked law predicates
(`isIdempotentPlus`/`isClosed`/`isSuperior`) via the plug taxonomy —
lattice.md §5a's compatibility matrix (dedup at ⊕-idempotence, commitment
at superiority, native store discipline at ⊗-idempotence, reordering and
refutation universal). Feature gates justified by verified laws, not
per-instance reasoning.

---

## 3. First, refactor `aggregate` onto it (SHIPPED: sum/max/min fold through Monoid witnesses; count untouched)

`aggregate`/`findall` (in `com.tgac.logic.aggregate`) already folds solution values —
`count` is `+` with `1` per solution, `sum`/`max`/`min` fold a projected value. These are
exactly ⊕ with ⊗ trivial (weight `one()` per solution). Refactor them to go through
`Semiring<S>`:
- `count` = `CountingSemiring`;
- `max`/`min` = `Viterbi`/`Tropical`-style ⊕ over the projected value.

This does not change behaviour; it proves the abstraction unifies what exists, and it is the
safe on-ramp. **Do this and stop; get it reviewed before Phase 2.**

## 3a. The source side is checked too (SHIPPED July 2026)

The table in §1 needs both ends lawful: the plugs (§2) and the goal
operations the plugs interpret. `GoalSemirings` (in `logic`) witnesses the
goal algebra — ⊕ = disjunction, ⊗ = conjunction, 0 = failure, 1 = success —
and its laws run through Eq-PARAMETERIZED law kits (goal equality is
semantic: solve both sides, compare answers). The Eq is the quotient, and
the quotient decides the capabilities:

- `DERIVATIONS` — answer-MULTISET equality: every derivation contributes
  its answer once. Plain semiring; the quotient counting and probability
  ride. Not idempotent — `g ∨ g` doubles every derivation.
- `ANSWERS` — answer-SET equality: duplicate derivations collapse, ⊕ is
  idempotent — the dedup license. Boolean queries live here; counts do not
  survive this quotient.

Same operations, two law suites, distinguished only by the comparator —
lattice.md's quotient tower as two green tests. The suite's first catch:
the instance combinators (`Conde.or`, `Conjunction.and`) are ACCRETIVE —
they mutate an already-composite receiver — so the witnesses build fresh
nodes; `plus(a, a)` through the mutating path builds a self-referential
goal that never terminates.

---

## 4. Weighted goals — the injection side (Phase 2)

Weights go on the *uncertain choices* (probabilistic facts, rule strengths), not on ordinary
goals. Ordinary goals (unify, recurse, arithmetic) carry weight `one()` and just thread it.

Add two goal factories (near `Goal.success`):
```java
// succeed once, multiplying the derivation's accumulated weight by w
static <S> Goal factor(S w);
// weighted(w, g) = factor(w).and(g)
static <S> Goal weighted(S w, Goal g);
```

**Threading mechanism — copy the DebugStore pattern exactly.** The debugger already threads
a value through the immutable `Package` via a plain `Store` in the constraint-store map
(`com.tgac.logic.debug.DebugStore`, riding `Package.getConstraints()`). Do the same:
- a `WeightStore<S>` holding the running ⊗-product for the current derivation, seeded to
  `semiring.one()`;
- `factor(w)` reads it, `times(current, w)`, writes it back into a new package (persistent,
  so branches keep their own copy — this is why backtracking is free), and succeeds;
- because `Package` is immutable, disjunction branches automatically carry independent
  weights — do NOT try to share mutable weight state.

`solve` under a semiring folds the solutions' weights with ⊕:
```java
static <T, S> S solve(Unifiable<T> out, Semiring<S> semiring);
// or, keeping answers: Stream<Tuple2<Reified<T>, S>>
```
Seed a `WeightStore` (like `solve(out, tracer)` seeds a `DebugStore`), run the search, and
⊕-fold the per-solution weights. `factor(0.5)` + `ProbabilitySemiring` gives the dice/mutex
examples in §9. This is the whole of Phase 2 for **non-recursive** programs.

Refinements settled (July 2026), pinning where each operation lives:

- **No `Goal<V>`.** The weight travels in the package (the store above),
  never in the goal's type. Goals stay labels-free (Green–Tannen): the
  program says only WHERE uncertainty enters (`factor`), the plug says
  what it means. Tabling's plug requirements (idempotence for cycles,
  §7a) are demands on the WITNESS at a call site, not annotations on
  goals.
- **⊗ is state threading.** No combinator call site ever multiplies:
  conjunction just runs both goals against the threaded package, and
  `factor` is the only place `times` is called. Package immutability is
  what makes branch weights independent for free.
- **⊕ is boundary merging.** `plus` fires only where alternatives MEET a
  boundary: `solve`'s terminal fold now, table cells at answer arrival
  later (§7a). Inside the search there is no ⊕ — branches never combine.

---

## 5. Sampling inference — the robust escape for probability (Phase 3)

For probability with *correlated* proofs, exact sum-product is wrong (see §6). The simple,
robust answer is Monte Carlo, and it fits the search engine perfectly:
- interpret each `factor(p)` as a biased coin: with probability `p` succeed, else fail;
- run the deterministic search once → one sampled possible world; record whether the query
  held;
- repeat N times; estimate `P(query) ≈ (# worlds where it held) / N`.

This sidesteps the hard problem entirely (you sample whole consistent worlds, so correlation
is handled correctly), at the cost of variance instead of exactness. Implement it as a
`SamplingSolver` that drives the existing search with a seeded RNG (pass the seed via `args`;
`Math.random`/`Date.now` are unavailable in this environment's scripts, but tests run on the
JVM where `java.util.Random(seed)` is fine). This is the recommended path to general
probabilistic queries and the truest "Bayesian" mode.

---

## 6. The one hard problem (READ THIS — it is narrow and avoidable)

Only the **probability** semiring has it. `P(proof1 ∨ proof2) = P(proof1) + P(proof2)` is
correct ONLY when the proofs are mutually exclusive. If two proofs share a probabilistic
fact, they overlap, and summing over-counts. This is the *disjoint-sum problem*, and exact
inference for it is #P-complete — a fundamental hardness in all of AI, not a flaw here.

Every other semiring is immune: counting genuinely wants to add distinct proofs; min/max
(tropical/Viterbi) are idempotent so shared structure is fine; boolean/provenance likewise.

**Do NOT implement exact correlated inference (knowledge compilation / BDD / SDD).** It is a
large subsystem, worst-case exponential, and out of scope (§10). Handle probability the two
clean ways instead:
1. **Restrict to the disjoint/independent fragment** (PRISM's choice): require that a query's
   proofs are mutually exclusive and shared facts are independent. Then sum-product is exact
   and linear. Covers HMMs, PCFGs, Markov chains, many Bayes nets. If you add this mode,
   *check the assumption at runtime and fail loudly* when violated (same discipline as the
   constraint single-domain guard) — never silently return a wrong probability.
2. **Sampling** (§5) for the general correlated case.

---

## 7. Recursion — semiring-tabling (Phase 4a, hard)

Non-recursive programs are handled by §4. For recursive relations the value is a *fixpoint*:
`reach(a,b)`'s probability = combine over all paths, which requires iterating.

Tabling (`com.tgac.logic.tabling`) is ALREADY a fixpoint engine: it memoizes a tabled call's
answers and iterates until the answer set stops growing (least fixpoint over the set lattice).
Generalize the table's accumulator from **set of answers** to **answer → semiring value**:
when the same answer is derived again, `plus` the values; along a proof, `times`. Iterate to
a fixpoint.

Caveats (do not ignore):
- **Idempotent semirings** (boolean, tropical, Viterbi) converge under "iterate to no change"
  — safe.
- **Non-idempotent semirings** (counting, probability) DIVERGE around cycles (the value keeps
  growing). They need the `star` closure or a bounded/ε-convergence solve. Do NOT ship
  counting/probability over cyclic programs without handling closure; restrict to acyclic, or
  use sampling (§5), or implement `star`.
- This reuses tabling's EXISTING worklist/parking; it does not need a new fixpoint driver. Do
  NOT try to merge this with the constraint AC-3 driver (see §11).

---

## 7a. Semiring tabling — the mechanics settled (July 2026 conversations)

Derived with the human against the shipped tabling; supplements §7.

- **The cell is a map, and always was**: `TableEntry = Map<AnswerTerm, V>`
  — answer terms lattice-compared (alpha-equivalence today, entailment
  under TCLP), values semiring-folded. Set-tabling is the degenerate
  V = "present" (the boolean plug), which is why the value column has
  been invisible. ⊕ fires at ANSWER ARRIVAL (another derivation of the
  same answer reaches the master: V ⊕ contribution); ⊗ fires at
  CONSUMPTION (a replayed answer's value multiplies into the consuming
  derivation's running value). Packages are NEVER combined — each branch
  CARRIES its derivation's running value as a package store (the same
  plain-store pattern as DebugStore/OptimizerStore).
- **The call-boundary cut, both columns**: keys must exclude
  caller-specific bindings (else variant explosion) and values must
  exclude caller-specific weight (else the cell is poisoned for other
  callers) — cell value = fold of derivations FROM the call's inputs TO
  the answer, caller-agnostic; arrival history multiplies in at replay:
  total = callerValue ⊗ cellValue. This is DP's optimal substructure as
  an engine invariant.
- **The units have jobs**: 1 = the weight a fresh branch is born with
  (empty product; success contributes 1). 0 = "no way" — empty cell,
  failed replay unification, seed of every ⊕-fold — and is normally
  REPRESENTED BY ABSENCE: CPS failure is silence (the continuation never
  called), which contributes 0 structurally; 0 materializes only in cell
  initialization and empty-search aggregates (counting: 0; min-plus:
  +∞ — the 0 is a role, not the number). The optimizer's algebra pass IS
  the unit laws: failure∨g=g is 0⊕x=x, success∧g=g is 1⊗x=x,
  failure∧g=failure is 0⊗x=0.
- **Idempotence is tabling's cycle-termination, not a detail**:
  tabling = memoization + idempotence. Sharing (memoization) works for
  ANY plug on acyclic call structures (classical DP). Termination on
  CYCLIC programs requires the ascent to go stationary: a⊕a=a makes
  "no new answers" detectable. Idempotent plugs (set, bool, min, max)
  tabulate freely; non-idempotent (count, probability) tabulate only on
  acyclic queries — or with a **closed semiring** (Kleene star), which
  solves the cycle analytically instead of iterating it (geometric
  series 1/(1−p); min-plus star = Floyd–Warshall). Star support is the
  real Phase-4 frontier for cyclic weighted queries.
- **The algebraic junction**: an idempotent-⊕ semiring IS a
  join-semilattice — the set quotient is literally where the structure
  algebra (semiring/tree) becomes the state algebra (lattice/data).
  Tabling is the shipped branch→data fold; the free cell (provenance
  polynomials — the free commutative semiring, Green–Tannen) is the
  yardstick that prices every quotient, never the runtime choice.
- **The lawfulness certificate is executable**: foldEarly (in cells) ==
  foldLate (enumerate then fold, today's aggregate) on fixtures —
  distributivity as a property test; a failure means "history matters,
  DP will silently lie".

## 7b. Failure provenance — "why did this fail" is the dual plug (sketch)

Two structural facts (July 2026):

1. **Failure today is ABSENCE** (CPS silence — the zeros never
   materialize), so reasons require reifying them — and the seams
   half-exist: `MFiber`'s `none` (a payload-less 0, upgradeable to
   `Either<Reason, A>`), `Revision.fail()` (store vetoes are values with
   room for "domain wiped: had {1..3}, met {5}"), and the tracer's Fail
   port (v0.5 of the feature ships today as `trace(out)`; this upgrades
   log → algebra). Delivery: an opt-in reason-collector store riding the
   package (tracer pattern, zero cost absent) — un-silencing every zero
   unconditionally taxes the hottest path for a debug feature.
2. **The reason algebra is the De Morgan DUAL of the answer algebra**: a
   conjunction fails if ANY conjunct fails (reasons merge ⊕-like); a
   disjunction fails only if ALL branches fail (reasons chain ⊗-like) —
   success's ⊕/⊗ placement, swapped. Hence the honest hardness (the
   literature's "why-not provenance"): a COMPLETE explanation is a
   product over the whole tree, exponential. The practical move is the
   architecture's usual one — a lossy plug: deepest-failure (the Prolog
   debugger heuristic, a max-progress fold), first-k reasons,
   reasons-mentioning-x. Explanation quality is a plug choice, like
   answer aggregation.

Bonus from tabling: a COMPLETED entry with zero answers is a cached
"no", and under the reason plug its cell value is the folded explanation
— failure explanations with sharing and exact pricing (the pricing
ladder, dual side). Status: two plugs deep in the future (needs Phase 1
Semiring + the Phase 2 value-riding store); recorded because it shows
the semiring seam is infrastructure, not just an inference feature.

## 8. Learning — the expectation/gradient semiring (Phase 4b, advanced)

To *learn* weights θ from data you need `∂P(query;θ)/∂θ`. The expectation semiring computes it
in the same pass as `P`. Elements are pairs `(p, r)` where `p` is the value and `r` its
derivative:
```
(p1,r1) ⊗ (p2,r2) = (p1·p2,  p1·r2 + p2·r1)   // product rule
(p1,r1) ⊕ (p2,r2) = (p1+p2,  r1 + r2)          // sum rule
zero = (0,0)   one = (1,0)
```
Seed the fact using θ with `(θ, 1)` and every other fact with `(weight, 0)`; run the same
sum-product; the answer's second coordinate is `∂P/∂θ`. Then gradient-ascend the likelihood.
It is just another `Semiring<S>` instance once §2 exists — nearly free to add.

Caveats: this is forward-mode (one parameter per pass — O(#params); the reverse/inside-outside
version is the scalable form and is more work); it inherits §6 (the gradient is only as exact
as `P`); needs log-space care for underflow. Advanced/research — do §2–§5 first.

---

## 9. Acceptance tests (write these as you go)

- **Phase 1:** existing `aggregate` tests still pass after the refactor; `count` equals the
  old count.
- **Phase 2 (exact, disjoint):** two dice — `die(1..6)` each `factor(1/6)`, query `a+b=7`
  under `ProbabilitySemiring` gives `6/36 = 1/6` (proofs are mutually exclusive → exact).
- **Phase 2 (idempotent):** a small weighted graph — `TropicalSemiring` gives the true
  shortest path length; `ViterbiSemiring` gives the most-probable single proof.
- **Phase 3 (sampling):** the dice query estimated by sampling converges to `1/6` within a
  tolerance for large N and a fixed seed.
- **Phase 6-guard:** a query with shared probabilistic facts, under the "restricted-fragment"
  mode, is REJECTED loudly (not silently mis-answered).

---

## 10. Non-goals (explicit)

- **Exact correlated Bayesian inference** (knowledge compilation, BDD/SDD). #P-hard, large,
  out of scope. Use sampling or the disjoint fragment.
- **Scalable reverse-mode learning** (full inside-outside). The forward expectation semiring
  (§8) is the target; the scalable version is a separate research effort.

---

## 11. Relationship to constraint propagation (do NOT over-unify)

Both this and `docs/design/constraint-kernel.md` are "iterate a monotone operator on a
lattice to a fixpoint." That kinship is a useful *mental model* but they are **duals** and
should stay separate code:
- semiring-tabling **grows** answers (least fixpoint, ⊕ accumulates);
- constraint propagation **shrinks** domains (greatest fixpoint, meet narrows).
The shared machinery (a worklist + change detection) is thin; the clients differ in
granularity (running a whole goal vs. a cheap local narrowing), keys (calls vs. variables),
and direction. Build each on its own; extract a shared `Fixpoint` helper ONLY if real
duplication appears after both exist. Do NOT design a unified driver up front.

---

## 12. Order of work (start here)

1. `Semiring<S>` + instances (boolean, counting, viterbi, tropical) — §2.
2. Refactor `aggregate` onto it — §3. **Review before continuing.**
3. `factor`/`weighted` + `solve(out, semiring)` for non-recursive programs — §4. Dice test.
4. `SamplingSolver` for general probability — §5.
5. (Research, separate go-aheads) semiring-tabling — §7; expectation semiring — §8.
