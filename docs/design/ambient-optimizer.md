# The ambient optimizer, from the ground up

**Status: DECIDED TARGET ARCHITECTURE (the human's call, July 2026) — build
directly, skipping a separate static phase: the tiers are configurations of
this one machine (§6), so the cost argument constrains configurations, not
the architecture. Build milestones: (1) store + solve seeding + root
rewrite (subsumes the static tier, unblocks pldb's LookupGoal); (2) the
Goal.defer hook, with a PINNED zero-cost-when-absent property; (3) memos
when live answers(s) ships. The pldb benchmark becomes a tuning input; the
one remaining XOR (dynamic ordering vs deferred lookups) is decided later,
inside this machine. Companion to `optimizer.md` (theory + pass catalog);
this doc rebuilds the architecture from first principles.**

---

## 1. What optimization is here

A goal is a value: `Package → Cont<Package, Nothing>`. Combinators build
TREES of goals (`Conjunction` holds its clause list, `Conde` its
alternatives) before anything runs. Optimization = rewriting that tree into
a semantically equal one that searches faster. "Semantically equal" is
guaranteed by one theorem used three ways: for pure goals, answers are
order-independent (the kernel's confluence — chaotic iteration over
monotone operators). So a rewrite can change only the PATH to the answers,
never the answers, provided it respects barriers (below).

Two facts about the tree matter for everything that follows:

- **The tree is data** — walking and rebuilding it is a pure, cheap,
  plan-time operation (post-order DFS, linear in tree size). No goal
  executes during a rewrite; no substitution is produced.
- **The tree is INCOMPLETE at construction time** — recursion hides behind
  `defer(Supplier<Goal>)`. A defer is a pointer to CODE, not to a
  rewritable definition. The body of layer n+1 does not exist until layer
  n runs. This single fact generates the whole design problem.

## 2. The wrapper architecture and its hole

The first shipped design (since superseded — `Optimized` is gone, §5):
`goal.optimize(opt)` wrapped the tree in an `Optimized` goal that reran
the visitor at APPLY time and executed the rewritten tree. Apply-time
matters because of §1's second fact: construction time sees a defer
wall, so applying per-layer is the only chance to rewrite unfoldings.

But the wrapper has a delivery hole: it rewrites what it WRAPS. When a
bare defer inside the tree forces its supplier mid-search, the fresh body
materializes OUTSIDE any wrapper — unoptimized — unless whoever constructed
that defer cooperated by pre-wrapping the body. Optimization by convention:
every recursive combinator, every library, every user has to remember.

## 3. The ambient move

The codebase already solved this exact problem once, for tracing. The
tracer is not delivered by wrapping: `DebugStore` rides the `Package` (the
solver state), and `NamedGoal.apply` CHECKS for it — instrumenting when
present, free when absent. Cross-cutting concern, delivered through state.

The ambient optimizer is the same move: an **`OptimizerStore`** — a plain
store on the Package (Table/DebugStore pattern) carrying the optimizer
pipeline. State flows everywhere execution goes, including through defer
walls: when a defer forces its supplier, the store is ALREADY THERE, in
the package the defer is being applied to. Nobody needs to wrap anything;
the optimizer is waiting on the far side of every wall.

## 4. The machinery — three pieces

1. **Seeding.** `solve` (or an explicit entry) puts the store in the
   initial package and rewrites the ROOT tree once against the initial
   substitution. This root rewrite is exactly the static planner: with a
   near-empty substitution, selectivity comes from constants in the query
   text (`lval("Tomek")` is ground by construction — no substitution
   needed to see it).
2. **One hook, at the wall.** `Goal.defer`'s forcing consults the store
   and rewrites the materialized body before applying it. That is the ONLY
   hook. Not conjunctions, not conde — hooking every combinator would make
   children re-plan what their parent just planned. The hook sits exactly
   where the wrapper architecture was blind, and nowhere else.
3. **Branch-scoped pass state.** The store carries not just the pipeline
   but its memos: the adornment plan-cache (plans keyed by boundness
   pattern, since the plan depends on the substitution only through which
   args are bound) and the positive ground-walk cache ("walks to ground"
   is upward-closed — substitutions only grow — so positive results never
   invalidate; negative ones are never cached). Package persistence makes
   all of it branch-correct for free: each branch owns its package, hence
   its cache.

## 5. Boundaries: one contract, one type

The correctness rule (optimizer.md §2): **a pass must preserve the binding
environment at every goal it does not own**. Unrecognised goals — opaque
lambdas, committed choice, tabled calls — are implicit barriers: partition
points that hold position while the pure runs on either side sort freely.
Nothing crosses, in either direction (for tabled calls this is what keeps
table keys general — the variant-explosion argument).

In the ambient world the explicit markers collapse into ONE type,
**`Barrier`**: "optimize outside and inside, never across." The trick is
that inside-optimization is OPERATIONAL, not traversal: the rewriter treats
a Barrier as a leaf (never enters — so a hand-ordered conjunction inside is
never re-sorted, since no hook fires at conjunction nodes), yet interior
defer forcings still consult the store — so structure that UNFOLDS inside
the Barrier still gets planned. Protect what was written; optimize what
unfolds. `Guard` (interior-blocking) and foreign-`Optimized` (ownership
claim) were both artifacts of wrapper delivery and are GONE — `Barrier`
shipped as the one type; explicit and implicit barriers have literally the
same contract. Scoped opt-out = removing the store for a subtree.

## 6. The tiers are configurations, not architectures

One machine, three knobs — which passes are in the pipeline, whether the
defer hook is on, whether `answers` reads the substitution:

| configuration | what it is |
|---|---|
| root rewrite only, substitution-free passes | phase 1 static (normalize, algebra, unroll, constants-only ordering) |
| root rewrite + memoized ordering | static-with-adornments — dynamic where boundness varies, free where it doesn't (cache hit = plan reuse) |
| hook on, `answers(s)` live | phase 3 dynamic — re-plans each unfolding against real bindings |

Static rewriting is fully recoverable: call `accept` directly at
construction, or run only substitution-free passes, or let the memo always
hit. And ambient makes the static passes BETTER than phase 1 could:
normalize/algebra also run on each unfolded recursion body — trees phase 1
never saw.

Bindings visible at each point, for the ordering pass: constants (always,
by construction); real upstream bindings (at any staging point — a
conjunction applies its later clauses to the packages its earlier clauses
produced, so anything downstream of a binder sees it, INCLUDING bindings
made by opaque goals that no static analysis could read); hypothetical
bindings (only the chain-aware static tier needs them, and only it pays
the variable-disclosure tax — see optimizer.md §4).

## 7. Interactions

- **Tabling**: a tabled body runs under the master's package, so it
  self-optimizes per VARIANT — and the table entry memoizes the plan for
  every consumer (variants ARE boundness patterns; the table is already
  the adornment cache). Benign oddity: the first caller's store plans for
  all consumers; cost is first-caller-dependent, answers confluence-safe.
  Keyed widening remains a barrier regardless — the store never justifies
  moving binders across a tabled call.
- **Tracing**: composes trivially — two independent stores on the same
  package; NamedGoal transparency means an optimized tree traces normally
  (labels survive rewriting).
- **Suspensions/constraints**: parked bodies are state, not tree — the
  rewriter never reaches them; but a parked body containing defers will
  consult the store when IT runs. Constrain-first ordering synergizes with
  suspensions: early-posted constraints park and prune as generators bind.
- **Semiring** (the recurring rhyme): store-carried algebra reinterpreting
  the search is the same shape as weights (run time) and provenance/trace
  (run time); the order function is the counting semiring at plan time.
  Recorded, deliberately not unified — fixpoint-machine.md §9 discipline;
  the `Semiring` abstraction earns its place at Phase 1 of that plan.

## 8. Costs, honestly

- Root rewrite: once, linear in tree size — noise.
- Per-defer-forcing: one store lookup + (on adornment-cache miss) one
  segment sort. Recursion forces a defer per layer, so the cache is the
  load-bearing part; without it, deep derivations pay a sort per layer.
- The store itself: one map entry per branch, structurally shared.
- Nothing here is measured yet. The pldb Phase 2 benchmark decides the
  fork (this design XOR chain-aware static XOR deferred lookups — the
  latter two solve overlapping gaps; build at most one runtime-binding
  mechanism).

## 8a. Pricing-rebuilding mechanics (the way up)

The public `Optimizer` contract stays `Fiber<Goal>`; pricing is the ordering
pass's PRIVATE recursion returning `Priced = (rewritten goal, order)` pairs:
children first, then the parent sorts its segments using the children's
already-computed orders and prices itself with one saturating fold (× over
∧, + over ∨, leaves declare via `answers(bound)`, everything unrecognised
∞). Each node is visited, priced and rebuilt exactly once — linear; the
pairs die with the traversal (no side-table, no staleness). Saturating ops
clamp at `Long.MAX_VALUE`; `0 · ∞ = 0` deliberately (a failing conjunct
bounds the conjunction). `bound` is the substitution the rewrite was
triggered with — empty at seed, live at a defer hook — constant per walk:
the pass is HALF-BLIND (blind to midway bindings within a layer, refreshed
at every layer boundary; unifications sort first anyway, and constraint
posts prune via suspensions regardless). Tier-2 escalation edits exactly
one line: `answers(bound, assumedSoFar)` threaded through the greedy sort.

## 9. What must be true for this to be sound (checklist)

1. Passes preserve answer sets on pure segments (confluence + purity).
2. Barriers partition: set-before/set-after preserved, barriers keep
   relative order, nothing crosses — implicit and explicit alike.
3. The only ambient hook is defer forcing (no re-planning of existing
   nodes → deliberate clause orders inside Barriers survive).
4. Ripeness-style monotonicity for caches: only upward-closed facts
   (groundness) cached across extensions; plans keyed by full adornment.
5. Passes are deterministic given (tree, adornment) — else memoization
   changes behavior between hit and miss.
