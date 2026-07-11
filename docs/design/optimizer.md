# The goal optimizer — rewriting search structure before it runs

**Status: SHIPPED on branch `optimizer` — the seam (Optimizer/
CascadingOptimizer), the ambient delivery (OptimizerStore, solve seeding,
the defer hook), the ordering layer (Bounded, OrderingOptimizer, pipeline)
and the single boundary type `Barrier` (Guard and Optimized converged and
are gone — see ambient-optimizer.md §5).
The pldb planner (`pldb/docs/design/query-planning.md`) is the first customer
and its doc holds the database-specific half (LookupGoal, estimate, phases,
benchmark). This doc is the engine-side theory and catalog.**

---

## 1. The seam (as shipped)

- **`Optimizer`** — a visitor over the goal combinators (`Conjunction`,
  `Conde`, `NamedGoal`, `Barrier`) with a generic `visit(Goal)`
  fallback. Dispatch via `Goal.accept(Optimizer)`; opaque lambdas and
  committed choice land in the fallback and are barriers by construction.
  The combinators are a CLOSED set → visitor overloads. Leaf capabilities
  are an OPEN set → capability interfaces + `instanceof` in the fallback
  (§4). Two mechanisms for two axes of extension.
- **`CascadingOptimizer`** — the base pass: one bottom-up structural
  recursion that flattens nested conjunctions and disjunctions. No fixpoint
  anywhere: pass-level iteration is only needed when a rewrite creates
  redexes it cannot see in its own traversal, and flatten cannot. (The old
  parameterless `Goal.optimize()` was a dangling seam — never called from
  the solve path — and its dead `Conde` override had an or→and flattening
  bug. Both died with the seam.)
- **`Barrier`** — the one explicit leaf (Guard and Optimized both
  converged into it once delivery went ambient): optimize outside and
  inside, never across. NamedGoal is transparent (tracing must not disable
  optimization), so wrapping alone protects nothing; Barrier says "this
  order is deliberate" and makes the contract testable.
- **Composition is sequencing, never merging**: `Optimizer.pipeline(a, b)`
  — every visit = `n.accept(a).flatMap(g -> g.accept(b))`; entry is always
  at the root, each pass runs its full traversal. Subclassing is ONLY for
  borrowing the recursion scheme (same pass, different leaf behavior) —
  normalize-then-sort is sequencing, so it is a pipeline, not an override.

## 2. The contract (correctness, not politeness)

**A pass must preserve the binding environment at every goal it does not
own.** Reordering is legal only within runs of owned goals between
barriers. A barrier is a PARTITION POINT, not a frozen neighborhood: it
holds its position, nothing crosses it in either direction, and the pure
runs on each side sort freely (`A∧B∧C∧imp∧D∧E → C∧A∧B∧imp∧E∧D` is legal;
moving D before `imp` is not). Invariant: for every barrier, the SET of
goals on each side is preserved and barriers keep their relative order — a
pure conjunction accumulates the same substitution under any permutation,
so the barrier runs under the source program's bindings.

Why it is correctness and not caution: tabling keys entries on reified
call arguments. A binder crossing ahead of a tabled call turns one general
entry (one master, N slaves) into one entry PER VALUE — duplicated
fixpoints, unbounded tables under unbounded generators. Committed choice
and `project` are the impurity half of the same rule.

## 3. The taxonomy: narrowing and widening

Two kinds of goals, distinguished by one number:

**`order(g, s)` = an upper bound on the answer states g EMITS when
applied in s** — its population multiplier. Denotational over
goals-as-state-transformers: `dom(x, 1..5)` and labelling constrain the
same RELATION but are different GOALS — dom emits one continuation (a
richer store; the store defers materialization to reification, after all
knowledge, so knowledge injection is FREE and never penalized), labelling
emits the domain width (it SPENDS the store; live width when available,
construction-time width as the sound loose bound). Implementable by any
library from its own semantics — "do I enumerate or post" — with no
engine knowledge: unify 1; every constraint post 1; labelling |dom|;
`conde` Σ clauses; a lookup its bucket. Emitted states are what multiply
downstream population, so this is exactly the number the sort needs:
posts always sort early, constrain-first in full.

The definition took three rounds (kept for honesty): out-degree
(rejected for conflating apply-time forking with emission), answers
admitted by the relation (rejected: it penalized knowledge injection —
a wide dom sorted behind branchers, forfeiting propagation kills —
because it priced the RELATION where the sort needs the GOAL), and
emitted answer states (final — both prior intuitions were denotational;
they denoted different things, and the goal's own denotation is the
population multiplier). The relation-admits reading survives as the
correct number for goals that SPEND a store, which is where the two
readings differ at all.

**Forcing is the store's actuator.** Every deferred-branching store has
a spend operation (FD forceAns, tabling consume, deferred-lookup flush)
with default placement at reify. A `Forcing` capability — goal-factory
plus current width — is the uniform hook through which the scheduler
INVOKES data→branch conversion at chosen points (§5a force), rather than
only sorting goals the user wrote. **Widening** = no bound
estimable: bare `defer` (transparent widening — sortable to the back;
fair BFS keeps completeness order-independent) vs tabled calls (KEYED
widening — barriers, immovable in both directions, §2). Committed choice
is outside the taxonomy: not narrowing or widening, just impure.

**The one rule** — subsuming det-first, constrain-first (constrain-and-
generate), selectivity ordering and fail-first: *sort each segment by
order, ascending; ∞ last; barriers hold position.*

Properties that make it sound and cheap:

- **Confluence** (the kernel's own theorem, third appearance): answers are
  order-independent for pure goals, so sorting is legal, changes only the
  path length to the fixpoint/failure — AND the sort key is stable under
  the sort (order is denotational — a property of the goal's own
  semantics; the optimizer changes only scheduling). Computed once,
  never invalidated.
- **Compositional**: orders multiply over ∧ and add over ∨ — leaves
  declare, combinators derive. This is evaluation in the counting semiring
  over upper bounds: when semiring Phase 1 lands, `order` is one more
  semiring instance, not a planner hack.
- **One pass, and it is a REWRITE walk, not execution**: the goal tree
  exists as data before anything runs (Conjunction holds its clauses); the
  optimizer's post-order DFS walks that structure, computing orders on the
  way back up in the same traversal that normalizes and sorts, stopping at
  defer walls (∞, never forced). No goal is applied, no substitution is
  produced, nothing is "computed twice" — plan-time work is linear in tree
  size and noise against search-time.
- **Groundness has two sources, only one of which is the substitution**:
  constants in the query text (`lval("Tomek")`) are ground by construction
  and visible to `answers(empty)` — the static tier's selectivity comes
  from them. What an empty substitution cannot see is bindings from
  earlier picks (tier 2's hypothesis) and bindings from outside the
  conjunction (tier 3, where the defer hook fires mid-search with the
  LIVE package and the substitution is not empty).
- **Saturating arithmetic**: products explode immediately and the sort
  only compares; small saturating longs (or magnitude classes), cap at ∞
  early.
- Constraints synergize with suspensions: an early-posted constraint with
  free vars PARKS and prunes incrementally as generators bind — "narrowing
  first" installs the watchers before the branching starts.

## 4. The capability interface

```java
public interface Bounded {                 // "Narrowing" also acceptable;
	long answers(Substitutions s);     //  Bounded says what it promises
}
```

Leaves implement it (pldb's `LookupGoal` via `estimate` — the whole pldb
planner collapses to this one data type plus the generic ordering pass);
the sorter discovers it by `instanceof` in `visit(Goal)`. The generic
`OrderingOptimizer` therefore lives in LOGIC, not pldb.

Three tiers, by where bindings come from:

1. **Naive static** — one-shot sort by `answers(s)` under real bindings;
   no chain awareness; minimal interface. SHIP THIS FIRST.
2. **Chain-aware static** — the greedy loop assumes picked goals' vars
   bound. Values don't exist at sort time, only boundness is predictable,
   so picked goals must DISCLOSE what they bind (`binds()`), and the
   hypothesis threads to candidates as
   `answers(Substitutions, Set<LVar<?>> assumedBound)` (candidates stay
   self-estimating). Variable disclosure is precisely the price of chain
   effects without running anything.
3. **Dynamic** — the interpreter conjunction: pick cheapest, RUN it,
   re-rank; bindings are real, so `answers(s)` alone is fully chain-aware
   and disclosure evaporates. Substitution reaches the pass as optimizer
   state (`OptimizerStore.rewrite` at the defer hook holds the live
   package and parameterizes the pass per forcing, via `with(...)` — same
   slot as unroll fuel); the visitor signature stays clean.

Tier 2 vs 3 is decided by the pldb Phase 2 benchmark, and is further
constrained by: dynamic planning and deferred lookups are SUBSTITUTES
(both exploit runtime bindings; deferral has more reach and moves
consumers LATER across barriers — the tabling-safe direction). Build at
most one.

Related for free: **tabled bodies self-optimize** — the tabling
combinator wraps its own body (the barrier binds outsiders, not the
owner); the master applies once per variant, so plans are memoized by the
table itself, per boundness pattern.

## 4a. Ambient delivery: the optimizer rides the Package

Tier 3's mechanism question — how does optimization reach bare defer
unfoldings nobody wrapped? — has the same answer as tracing: a plain
store on the Package (the DebugStore pattern). An `OptimizerStore`
travels with the state, so it is waiting on the far side of every defer
wall when the body materializes. Wrapper vs store (the wrapper is the
DEAD design, kept for contrast): `Optimized` was an
EXPLICIT LOCAL claim (placement fixed at construction; cannot reach an
unwrapped defer); the store is AMBIENT (placement follows the search).

Discipline:
- **Hook only at the wall**: `Goal.defer`'s forcing consults the store
  and rewrites the materialized body — nothing else does, or parents'
  plans get redundantly re-planned by their children.
- **Pass state converges here**: the adornment plan-cache and the
  positive ground-walk cache are branch-scoped for the same reason —
  optimizer + plans + memos are one store.
- **Precedence**: an explicit wrapper or Guard overrides the ambient
  store (the foreign-ownership rule, extended to scoping); removing the
  store for a subtree is a scoped opt-out.
- **Tabling oddity (benign)**: a tabled body runs under the master's
  package, so the first caller's optimizer plans for all consumers —
  cost is first-caller-dependent, answers are confluence-safe.
- **Guard and Optimized unified into `Barrier` (SHIPPED)** — "optimize outside
  and inside, never across". Inside-optimization is OPERATIONAL (ambient
  staging at interior defer forcings), not traversal, so a hand-ordered
  conjunction inside a Barrier is never re-sorted (no hook at
  conjunction nodes) while forced recursion bodies still get planned:
  protect what was written, optimize what unfolds. Guard's
  interior-blocking and Optimized's entry-point/ownership roles were
  both artifacts of wrapper-delivered optimization; ambient delivery
  dissolves them, leaving pure boundary semantics — the explicit,
  user-invokable form of the same contract implicit barriers (impure,
  keyed-widening, opaque) already have. A PHASE-3 fact: the static
  tiers keep the two types distinct; Barrier is the convergence target
  if the fork lands ambient.

## 5. Pass catalog (beyond normalize + order)

Buildable today:
- **Success/failure algebra**: `failure()∧g → failure()`, `success()∧g →
  g`, `g∨failure() → g`, singleton collapse, empty conde → failure.
  Contracting and confluent — extends the normalize pass.
- **Per-clause normalization inside Conda/Condu**: clause ORDER is sacred,
  clause BODIES are not.
- **Pure dedup** `g∧g → g` (reference-identical): sound under set
  semantics; dies when weighted inference lands (multiplicity).

Gated on unify-as-data (a `UnifyGoal` type — same move as LookupGoal):
- **Static failure folding**: ground-ground unification decided at rewrite
  time, then the algebra pass kills the conjunction.
- **Clause indexing**: a Conde whose clauses open with `unify(x, cᵢ)` and
  a ground x → dispatch instead of fork (Prolog first-argument indexing;
  the Matche accelerator). Needs the dynamic tier.

Designed, waiting on customers:
- **UnrollingOptimizer(k)**: force bare defers to depth k, splice — the
  window then spans recursion layers and the bound-set simulation handles
  cross-layer dependencies. Fuel-bounded; NEVER through a tabled call
  (tabling IS the commitment not to unfold); forced construction must be
  pure.
- **Factoring/distribution** `(g∧a)∨(g∧b) ↔ g∧(a∨b)`: sound both ways for
  pure goals, mutual inverses — cost-directed, never drained.
- Instrumentation passes (auto-naming for traces): the visitor is a
  general goal-tree rewriter; not every pass optimizes.

Out of scope: magic sets / unfold-fold (need rules-as-data — a `defer`
points at code, not at a rewritable definition); the table-boundary
rewrite `tabled(g)∧g2 ↔ tabled(g∧g2)` (sound only when g2 is pure, its
free vars ⊆ call args, and set semantics suffice — and even then a
cache-granularity trade the local cost model cannot see: manual pattern,
documented in the pldb doc §7).

## 5a. Branch↔data scheduling (the generalized job)

Pending disjunction lives in two forms — tree (branches) and data (a
domain, a table entry, a parked lookup) — and the optimizer's general
job is scheduling the CONVERSIONS. One dial (order under current
knowledge), four moves:

| move | direction | when |
|---|---|---|
| domainify (conde of ground unifications → dom) | branch → data | MANUAL IDIOM ONLY — see below |
| force (dom → early labelling) | data → branch | narrow, coupled to wide things |
| defer a lookup (park) | branch → data | wide, args unbound |
| wake a lookup | data → branch | narrowed by arrived bindings |

Narrow disjunctions spend early (their branches inject ground knowledge);
wide ones defer as data (they RECEIVE knowledge and shrink while
waiting). A domain is deferred branching reified as data carrying its own
order as store state — the FD store is the limit case of the sort:
branch-last with continuous narrowing. Domainification REMOVES tree
rather than reordering it (propagation kills values at zero branches) —
but as an AUTOMATIC pass it is unrealistic (July 2026, the human's call):
the recognition window is razor-thin (every clause a ground unify on one
variable, values store-representable); the benefit presupposes FD
coupling, i.e. an FD-aware author who writes dom anyway; deciding
profitability needs global coupling analysis; and FATALLY, it can turn a
legal program into a throwing one — the tabling wall rejects tabled
calls under active constraint stores, so converting a pure conde near a
tabled call trips the guard. A rewrite that breaks working programs
violates the wrong-is-only-slow license. It survives as a MANUAL
refactoring idiom ("enumerating ground values you post arithmetic on?
write dom, not conde — mind the tabling wall"), completing the taxonomy;
force-early is its inverse and needs a real cost model (width vs
coupling — CP's variable-ordering problem; dumb threshold first,
benchmark-gated) plus Package reading at rewrite time (it acts on store
state — the first landmine-adjacent pass). Both are set-semantics only
(domains dedup where condes replay — dies under the semiring).

## 5b. Tabling ties: the direction of monotonicity prices everything

Tabling is the same branch↔data conversion applied to recursive
enumeration: the table entry is the reified pending-branching, master =
force, slave = suspend (parked continuations wake as answers arrive).
The inversion: a domain SHRINKS (∧-monotone down), a table GROWS
(∨-monotone up) — the two fixpoint machines (fixpoint-machine.md §10).
Pricing soundness follows the direction: a stale domain width is a valid
upper bound (only shrinks); a stale table count is an UNDER-estimate
(only grows) — so incomplete tabled calls price ∞ (keyed widening,
immovable), while a COMPLETED entry is a perfect oracle: the exact
branch count consumers will spawn, better than any estimate. The pricing
ladder: constraint posts (sound stale bounds) → lookups (index
estimates) → completed tabled calls (exact counts) → incomplete tabled
calls (∞). The optimizer generalizes the DECISION layer only — the
stores and the table own their data and its monotone evolution
(correctness, two engines, deliberately unmerged); the optimizer owns
timing (cost, confluence-protected).

**Barrier-ness is a PHASE, and the price transition is the immovability
transition (July 2026).** A tabled call is immovable while its fold is
IN PROGRESS (keyed widening: moving binders forks the fixpoint per
value, and stale counts of a growing ascent are unsound — price ∞). At
COMPLETION the object changes kind: data at rest, a materialized
relation — priced exactly (the oracle) and, in principle, sortable like
a LookupGoal by its answer count. `tabled` is the only annotation whose
price transitions, because completion is the moment the fold finishes.
The enabling precondition for actually reordering completed calls:
**call subsumption** — reordering changes boundness, boundness is the
key, and exact-variant lookup mints a fresh master even though a
completed general entry contains every needed answer. Herbrand
subsumption (term matching on reified args, no TCLP machinery) is the
constraint-free base case, and it is the SubsumptionMap's second
customer alongside the adornment memo. Until it ships, the §2 invariant
stands even for completed entries. Mechanism note: completion is a
runtime event and the Table rides the package, so completed-entry
re-pricing is ambient-tier and needs answers to read the PACKAGE — the
THIRD customer for the answers(Package) widening, after force-early and
live labelling; three customers ends its speculative status.

**The suspension≡consumer triple.** Kernel suspensions and tabling
consumers are one shape: (parked continuation, upward-closed wake
condition, monotonically growing substrate) — ripeness over the
substitution, has-answers-beyond-i over the table; both substrates only
grow, which is the fire-once soundness argument in both places (a slave's
re-park is a fresh suspension at i+1, chained fire-onces). Domains
shrink, which is why domain-shaped ripeness stays store-internal. What
distinguishes the parked mechanisms is their FLUSH POLICY — end-of-search
treatment: FAIL (pending kernel suspensions throw at reify), DIE (a slave
parked on a completed entry silently never resumes), FORCE (deferred
lookups and labelling enumerate the residual at enforce). Park/wake
covers the during; flush covers the end. Unifying the mechanisms
(suspensions watching stores) stays vetoed without a customer — the
shared vocabulary is the payoff. TCLP adds a fifth conversion move to the
§5a table: TRANSFER (table-data → store-data via restate, zero branches —
tabled-constraints.md §3.3), which is why constrained answers consume at
order 1 where ground answers materialize per-answer.

## 5c. Is this FD in a trenchcoat? The capability ladder (July 2026)

Stress-tested against non-FD systems: the model is about LATTICES; FD is
the model organism — the one store implementing every optional tier —
which is why examples kept reaching for it. Two renamings remove the
residual FD flavor, and each imports a witness:

- `answers` stays a COUNT — but distinguish it from the measure (the
  conflation FD hides): ORDER is emitted answer-states, discrete by
  construction, the only number entering the arithmetic (products/sums
  are over state counts — the counting semiring over populations,
  uniform across stores; cross-store products are independent upper
  bounds, sound). A forcing's order is its SPLIT ARITY: FD enumeration
  |d|-way (arity = width, the coincidence that hid the distinction),
  bisection and DPLL case-split arity 2. The MEASURE μ (A ⊑ B → μ(A) ≤
  μ(B): width, interval length, volume) is per-store and ordinal — it
  guides WHICH region to split, wake thresholds, tie-breaks; comparison
  only, no arithmetic, never crosses stores. Min-domain survives both
  ways: finite stores sort forcings by arity (= width); continuous ones
  see uniform 2s and discriminate by μ inside the store.
- Forcing as enumeration → SPLIT: a store-offered complete finite
  branching of a region. Enumeration is the finite case, bisection the
  continuous one.

Stated as "propagate to fixpoint, split the smallest-measure region,
repeat", DPLL (Boolean domains, unit propagation, literal case-split) and
interval branch-and-prune (numerical CSP) are both instances WITHOUT
modification — two algorithm families from different fields, the
strongest evidence the abstraction generalizes. The negative witness:
Neq is correctly EXCLUDED tier by tier, not broken by the model.

The ladder (stores opt in per tier):
1. ORDERED knowledge (meet → entails) — mandatory; the kernel's
   contraction contract already requires it.
2. PRICED (order as count) — enables Bounded/sorting; global arithmetic.
   Neq declines (∞ — forcing a disequality would be insane, and the
   model says so).
2a. MEASURED (μ) — enables split-choice/tie-breaking inside the store;
   local comparison only, never in the arithmetic.
3. SPLITTABLE — enables forcing. STN intervals: yes (bisection); Neq: no.
4. FINITELY-LATTICED — enables TCLP (tabled-constraints.md §5.5 gate).

## 6. Open decisions

- Interface name: `Bounded` vs `Narrowing` (combinators are also
  narrowing but derive rather than declare — Bounded dodges the mismatch).
- Marker for constraint posts: they should implement the capability
  (order 1) when the constraint factories next grow a recognizable type.
- `Optimizer.pipeline` ships with the ordering pass (first real user).
