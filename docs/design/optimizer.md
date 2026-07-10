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

**`order(g, s)` = g's out-degree at apply — the maximum number of
continuations it may invoke when it RUNS from state s.** Not the eventual
answer count of the query: the two diverge exactly when a store defers
disjunction as data. `dom(x, 1..10)` represents a 10-way choice but
branches ONCE — the ten-ness is store-data, not tree structure — so it is
order 1 like every constraint post; the branching is cashed in later by
LABELLING, whose order is the then-current domain size (min-domain /
fail-first falls out of the ascending sort). unify: 1 (fail = 0, but 0 is
only knowable when foldable to `failure()`). Pure propagators (≤, +,
≠fd): 1 — they only prune; there is no deferred disjunction in them at
all. `conde` of k clauses: ≤ k. A lookup: ≤ its index bucket. **Widening** = no bound
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
  the sort (order is denotational — a property of the relation; the
  optimizer changes only the operational side). Computed once, never
  invalidated.
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
  conjunction (tier 3, where Optimized.apply holds the LIVE package and
  the substitution is not empty).
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
   state (`Optimized.apply` holds the live Package and parameterizes the
   pass per application — same slot as unroll fuel); the visitor signature
   stays clean.

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
wall when the body materializes. Wrapper vs store: `Optimized` is an
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

## 6. Open decisions

- Interface name: `Bounded` vs `Narrowing` (combinators are also
  narrowing but derive rather than declare — Bounded dodges the mismatch).
- Marker for constraint posts: they should implement the capability
  (order 1) when the constraint factories next grow a recognizable type.
- `Optimizer.pipeline` ships with the ordering pass (first real user).
