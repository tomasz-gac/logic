# Constraint propagation redesign — design sketch

**Status: Phases 1 AND 2 IMPLEMENTED (July 2026, branch `propagation`); Phase 3 not
(worklist optimisation — only if measured).** Phase 0 (the multi-domain guard) was
deliberately skipped — obsolete once Phase 1 landed. Implemented: collapse-bindings
route through the chokepoint (1a); the chokepoint applies the prefix once with stores
purely reactive (1b); cross-store wake via `ConstraintStore.pendingConstraints` (2a);
wake-on-narrowing with the equal-domain termination guard in `updateVarDomain` (2b).
All gated by `PropagationPinTest` (every pin verified red-before/green-after). The
Phase 2 idempotence audit earned its keep: wake-on-narrowing exposed a latent
soundness bug in `mulIntervals` (quotient trims with a zero-spanning divisor interval
produced garbage bounds — fixed by sign-guarding). Multi-domain FD+Neq queries
(`shouldMixMultipleConstraintSystems`) now produce complete correct answer sets.

**Also implemented: the Neq→FD bridge** (cKanren's FD/=/= integration, gated by
`NeqFdBridgeTest`): a ground arithmetic disequality on a domained variable becomes a
domain exclusion (discharging the record) at record-creation time — pruning before
labelling, inferring bindings on collapse, failing on empty. `FiniteDomain
.excludeFromDomain` is the public seam; a disequality stated BEFORE its domain keeps
its record (correct via verification; converting that order would need a
domain-assignment hook — do it only if it ever matters).

**Still open:** Phase 3 — now specified as `capability-constraint-api.md`: the
explicit worklist driver arrived at from type safety (Prefix/Verdict/Reaction/
Inference), not from performance. If Phase 3 ever happens, it happens THAT way; a
bolt-on queue without the capability types forfeits the point.

En route, the pin work exposed and fixed an unrelated FD soundness bug: `leq` lost
boundary solutions because `copyBefore`/`dropBefore` disagreed about inclusivity
across domain types (strictly-before on Interval/Enumerated, inclusive on Singleton)
and the Enumerated miss-case did not narrow at all. Replaced by the inclusive
`atMost`/`atLeast` (see `DomainNarrowingTest`). Lesson recorded: the pre-existing
e2e tests were soundness-only (`allMatch`), blind to LOST solutions — new constraint
tests must also assert completeness.

---

## 1. The problem this solves

Today, reacting to a unification lives in `StoreSupport.processPrefix`
(`com.tgac.logic.ckanren`). It composes every `ConstraintStore` with `Goal::and`,
runs each **once**, in a fixed order, and each store applies the new substitutions
with `Package.withSubstitutions(...)`, which **replaces** the substitution map.

That has two defects that make combining constraint domains unsound:

1. **Replace-clobbering.** A store can bind a variable during propagation (a finite
   domain narrowing to a singleton binds the variable). A later store in the chain
   then does `s.withSubstitutions(newSubstitutions)` and overwrites the whole
   substitution map, dropping the first store's binding.

2. **Incomplete wake-up.** Independent domains trigger each other: an FD narrowing
   can bind a variable, which should re-fire a disequality check, which can bind
   another variable, which should re-fire FD narrowing. The cross-domain cascade is
   incomplete (see §1.1 for exactly where — it is NOT entirely absent).

Evidence: `com.tgac.logic.ckanren.NeqFiniteDomainTest` reproduced the starvation
symptom (`x ∈ {0,1,2} ∧ x ≠ 1` returned `{0,1,2}` instead of `{0,2}`). The patch
(passing the original package to each store so it diffs/verifies against the right
base) fixed *that* case. It did **not** fix the two defects above.

See `StoreSupport#processPrefix` javadoc for the in-code statement of the limitation.

## 1.1 What ALREADY exists — cKanren's `runConstraints` (read before designing)

Do not design from scratch: the wake-up half of AC-3 is already implemented in
`CKanren.runConstraints`, and it maps 1:1 onto §3.2's propagator concept:

| §3.2 concept | existing code |
|---|---|
| propagator | `Constraint` (`constraintOp` = the propagate function, `args` = watched vars) |
| watched-variable check | `CKanren.anyRelevantVar(x, c)` — does constraint c watch changed var x |
| wake on binding | `CKanren.runConstraints(x, constraints)` — called after x gets bound |
| run = remove + re-run | `CKanren.remRun(c)` — take c out of the store, re-run its goal |
| re-suspend if undecided | `Constraint.addTo` — the goal re-adds itself while args remain unbound |

A **fixpoint-by-recursion** also already exists for binding-driven propagation:
bind → `runConstraints` → constraint goals re-run → they narrow domains → a domain
collapses to a singleton → bind → wake again… and constraint bodies that bind via
`CKanren.unify` re-enter `StoreSupport.processPrefix`, which reaches ALL stores.

The precise gaps (each verified in code, July 2026) — this is what the redesign
actually has to fix, and it is "complete the existing machinery", not "build a
fixpoint engine":

1. **Wake lists are intra-store.** Every `runConstraints` call site passes only its
   own store's constraints (`FiniteDomainConstraints.processPrefix` and
   `Domain.resolveStorableDom` pass the FD store's; `ProjectionConstraints` passes
   projections). A binding never wakes another store's watchers directly.
2. **Singleton-collapse binds bypass the chokepoint.** `Domain.resolveStorableDom`
   binds a collapsed variable via `Package.extendS` directly — NOT via `unify` — so
   `StoreSupport.processPrefix` never runs and other stores do not hear FD's
   inferred bindings mid-search. This is rescued LATE: at reify time,
   `EnforceConstraintsFD` re-runs `StoreSupport.processPrefix` over the full
   substitution map, which is why `separate(x,1) ∧ dom(x,{1})` correctly fails
   today (verified empirically — do not "re-fix" it; note also that a one-element
   domain built by `EnumeratedDomain.range` is NOT a `Singleton`, so it never
   collapse-binds — only domain INTERSECTION normalises to `Singleton` and takes
   the collapse path). Consequences of the lateness:
   wasted search (violated branches are pruned only at the end), and a real
   unsoundness risk under committed choice — `conda`/`condu` can commit on a
   mid-search package that enforcement would later reject.
3. **Domain-only narrowing wakes nobody.** The non-singleton branch (`extendD`)
   just stores the smaller domain; constraints watching that variable are not
   re-run until labelling. No bounds-propagation cascade mid-search.
4. **The base-package bookkeeping bug** — fixed (the `oldPackage` parameter).

Known still-broken/risky combinations to pin with tests before starting:

- committed choice (`conda`/`condu`) over a subgoal whose mid-search package
  carries an FD-inferred binding that violates a Neq constraint (gap 2's
  unsoundness vector);
- two FD constraints that only converge after several mutual narrowings (gap 3),
  combined so ordering matters.

---

## 2. The idea in one paragraph

Every variable carries a **domain**: the set of values it could still take (a bound
variable is a singleton domain; an empty domain is failure). The only legal move is
to make a domain **smaller** — never larger. A **propagator** is a function attached
to one constraint that removes values that constraint proves impossible given the
current domains of the other variables it touches. The engine runs propagators **to a
fixpoint** (until a full sweep shrinks nothing), because one narrowing can enable
another. Reaching a fixpoint means *locally consistent*, not *solved* — you then
**branch** (search / labelling) on a remaining choice and propagate again inside each
branch. Termination is guaranteed for finite domains because domains only shrink and
cannot shrink below empty.

---

## 3. Target design for this codebase

> **How to read this section after §1.1:** §3.1's monotonicity invariant is binding.
> §3.2–3.4 are the CONCEPTUAL model (what a propagator is, why the loop terminates) —
> do NOT implement them as new classes. The concrete implementation path (§4) reuses
> the existing machinery: `Constraint` is already the propagator, `runConstraints` is
> already the wake-up, and recursion through the CPS/`Cont.defer` substrate is already
> the (stack-safe, trampolined) fixpoint loop. Only §3.4's explicit worklist would be
> genuinely new code, and it is deferred until measured (Phase 3).

### 3.1 State = a product lattice carried in `Package`

`Package` already is `substitutions (HashMap<LVar,Term>) × constraints
(LinkedHashMap<Class,Store>)`. Treat the whole thing as one lattice ordered by
"more constrained than", with a distinguished **bottom** = failure. Each kind of
information is a factor:

- substitution binding: unbound ⊒ bound-to-term ⊒ ⊥ (clash / occurs-check);
- finite domain: full-set ⊒ subset ⊒ singleton ⊒ ⊥ (empty);
- disequality obligations: a set that only shrinks (each obligation is discharged) or
  hits ⊥ (all sides equal).

**Invariant that must hold:** the substitution map is **monotonic** — only ever
extended (`Package.extendS`, a merge), NEVER replaced (`Package.withSubstitutions`).
Replacing is what causes clobbering. A propagator returns a package that is ⊑ its
input (same-or-more bindings, same-or-smaller domains, same-or-fewer obligations).

### 3.2 The propagator abstraction (generic, written once)

```java
interface Propagator {
    // variables whose change should re-wake this propagator
    Set<LVar<?>> watched();

    // contracting: returns a package ⊑ input (narrower), or fails.
    // Option.none() == bottom (this branch is inconsistent).
    Option<Package> propagate(Package s);
}
```

The engine never looks inside `propagate`. It only relies on:

- **contracting** — output ⊑ input (only narrows), so the loop terminates under DCC;
- **change-detectable** — the caller can tell `propagate(s)` changed `s`. Because
  `Package` is persistent, compare the relevant sub-maps by reference (`==`): if the
  substitution map and every domain map are reference-equal, nothing changed.

### 3.3 The engine — naive fixpoint first

Correctness lives entirely in "loop to quiescence". Start here; it is small.

```
propagateToFixpoint(package, propagators):
    repeat:
        changed = false
        for p in propagators:
            next = p.propagate(package)
            if next is bottom: return FAIL
            if next != package (by sub-map reference):
                package = next
                changed = true
    until not changed
    return package
```

This replaces the body of `StoreSupport.processPrefix`. It is O(passes × propagators)
but always correct. Do NOT add the worklist yet.

### 3.4 The engine — AC-3 worklist (optimization, later)

Once naive works and is tested, make it cheaper: keep a queue of propagators to run
and a map `LVar -> propagators watching it`. When a propagator narrows variable `v`,
enqueue only the propagators that `watch(v)`. Drain the queue.

```
worklist = all propagators
index    = map from each var to the propagators watching it
while worklist not empty:
    p = worklist.remove()
    next = p.propagate(package)
    if next is bottom: return FAIL
    for v in variables whose domain/binding changed between package and next:
        worklist.addAll(index[v])          // re-wake only affected propagators
    package = next
return package
```

Same fixpoint, less redundant work. This is a pure efficiency layer — it changes no
results. Skip it until profiling shows the naive loop is a bottleneck.

### 3.5 Mapping the existing stores to propagators

| current store / operation            | propagator narrowing rule |
|--------------------------------------|---------------------------|
| unification (`CKanren.unify`)        | narrow a variable's substitution factor from any-term to one-term; ⊥ on clash/occurs-check |
| `FiniteDomainConstraints` domain     | intersect a variable's domain with the constraint; singleton ⇒ bind it; empty ⇒ ⊥ |
| FD relations (`addo`/`multo`/`<`, …) | bounds/interval narrowing on the variables they relate (one propagator per relation) |
| `NeqConstraints` (disequality)       | watch the vars; all sides equal ⇒ ⊥; some side differs ⇒ discharge (drop); a bound value vs an FD var ⇒ remove that value from the var's domain (cross-domain!) |
| `ProjectionConstraints`              | run the projected goal once its watched vars are ground |

Note the disequality × finite-domain row: `x ≠ 5` removing `5` from `x`'s domain is
exactly the **cross-domain narrowing** the current `and`-chain cannot express, and is
a concrete win of the redesign.

### 3.6 The always-available generic propagator

Any constraint expressible as a decidable predicate over finite domains can be
propagated with no bespoke code by **arc-consistency by enumeration**:

> for each variable `v` and each value `d` still in `v`'s domain, remove `d` if no
> assignment of the other variables (from their current domains) satisfies the
> predicate with `v = d`.

Correct for any such constraint, but exponential in the number of variables. Use it as
the default; write a specialized propagator (like interval reasoning for `+`) only for
constraints that are hot.

### 3.7 Integration with search

`propagateToFixpoint` runs after each unification and returns a narrowed package or
fails. It does **not** solve — labelling/`enforceConstraints` still branches on a
remaining multi-valued domain (a disjunction in the existing engine), and each branch
re-propagates. Because `Package` is immutable, backtracking is free: the parent branch
still holds its un-narrowed package; there is no trail to undo.

---

## 4. Migration phases

Given §1.1, the shape of the work is **completing the existing `runConstraints`
machinery**, not building a new engine. The whole target fits in one invariant:

> **One chokepoint through which every binding flows; stores may only REACT
> (narrow domains, verify, fail) — never extend or replace the substitution
> themselves; wake-ups reach all stores' watchers; recursion is the fixpoint.**

Conceptually the chokepoint is:

```
bind(pkg, newBindings):                      // the ONLY way substitutions grow
    pkg' = pkg.extendS(newBindings)          // monotonic; applied ONCE, centrally
    for each newly bound var x:
        runConstraints(x, union of ALL stores' pending constraints)   // cross-store wake
    run store-level reactions (Neq verify, FD domain-membership check)
    // reactions that infer bindings call bind() again → recursion = the fixpoint,
    // stack-safe because everything is Cont.defer'd on the fiber substrate
```

`StoreSupport.processPrefix` essentially IS this function already — it just is not
the only door (gap 2), does not extend centrally (clobber), and wakes intra-store
only (gap 1).

- **Phase 0 (interim, cheap, do regardless):** in `StoreSupport`, detect more than one
  *substitution-mutating* `ConstraintStore` active in a package and fail loudly (or log)
  rather than returning a silently wrong answer. Whitelist the combinations already
  verified. Documents the real contract in code. (Becomes obsolete after Phase 1.)

- **Phase 1 (close gap 2 + kill the clobber class).** Two concrete moves:
  1. `Domain.resolveStorableDom`, singleton branch — route the inferred binding
     through the chokepoint. Today:
     ```java
     runConstraints(x, FiniteDomainConstraints.getFDStore(a).getConstraints())
             .apply(a.extendS(HashMap.of(x, lval(v))));
     ```
     Target (note: `processPrefix` takes the FULL new substitution map — CKanren.unify
     passes `s1.getSubstitutions()` — not just the delta; passing only the delta would
     lose every other binding):
     ```java
     StoreSupport.processPrefix(a.getSubstitutions().put(x, lval(v))).apply(a);
     ```
     An FD-inferred binding becomes indistinguishable from a unification: Neq
     verifies immediately, projections fire, FD's own wake-up happens inside its
     `processPrefix`. The reify-time rescue (`EnforceConstraintsFD` re-running
     processPrefix) becomes a redundant safety net — KEEP it initially and add a
     test asserting it discovers nothing new.
  2. **Stores stop touching substitutions.** The chokepoint applies
     `extendS(prefix)` once; every store's `processPrefix(prefix, oldPackage)`
     becomes purely reactive — it receives the already-extended package plus the
     prefix as data, and may narrow domains / verify / fail, but never calls
     `withSubstitutions`. Mechanical sweep across `FiniteDomainConstraints`,
     `NeqConstraints` (its `verifyUnify` already takes `(newPackage, oldPackage)`
     — it just stops building `newPackage` itself), and `ProjectionConstraints`.

  Validation: full suite green; `SummationTest`, `MultiplicationTest` and
  `OrderConstraintsTest` are the most likely to catch semantic drift. Days of work,
  small diffs, big re-validation burden. Removes the committed-choice unsoundness
  vector (mid-search states become consistent).

- **Phase 2 (close gaps 1 and 3 — where genuinely NEW behaviour appears).**

  *Two constraint protocols coexist and BOTH stay.* FD and Projection use suspended
  `Constraint` goals (woken per-variable by `runConstraints`, re-suspended by
  `addTo`). Neq is Byrd's `=/=` design: NO `Constraint` objects — it stores prefix
  maps (`NeqConstraint`) and re-verifies them wholesale in its `processPrefix`
  (`verifyUnify`: re-attempt the stored unification; no-extension = violated →
  fail; can't-unify = discharged → drop; extension = simplified → keep). **Do NOT
  rewrite Neq into `Constraint` objects** — it participates in every wake through
  its store-level `processPrefix`, which the chokepoint already calls. Neq is a
  pure filter (never binds), so the reactive contract is trivially satisfied; its
  only change in this redesign is Phase 1's bookkeeping (stop building
  `withSubstitutions` itself).

  1. **Cross-store wake (gap 1):** the chokepoint wakes, per newly bound var, the
     union of the `Constraint`-protocol stores' pending constraints (FD,
     Projection, future pldb parked lookups). `runConstraints`/`remRun` are
     already store-agnostic (each `Constraint` carries its `storeClass`) — only
     the call sites' constraint lists widen. Wholesale-verify stores (Neq) are
     woken by having their `processPrefix` run, as today.
  2. **Wake on narrowing (gap 3):** `Domain.extendD` (non-singleton shrink)
     additionally wakes the watchers of the narrowed variable. This is what turns
     `x<y ∧ y<z` interval chains into immediate propagation and gives cross-domain
     hooks (a Neq watcher reacting to a domain shrink). Termination is safe —
     domains only shrink (DCC) — but **audit every FD constraint body for
     idempotence under narrowing-wakes**: they were written assuming re-runs happen
     only on binding. This audit is the real design risk of the whole redesign.

  Acceptance: `x ∈ {1..10} ∧ x ≠ 5` prunes 5 from the domain BEFORE labelling;
  `FiniteDomainTest#shouldMixMultipleConstraintSystems` gains a real assertion.
  The pruning is a small bridging reaction (a singleton-prefix Neq record consults
  the FD store and excludes its value from the domain) — this is exactly the
  cKanren paper's own FD/=/= integration, so follow the paper, don't invent. It is
  an optimisation, not a prerequisite: Neq is correct without it, just later.
  Side effect: pldb's deferred lookups (`pldb/docs/design/deferred-lookups.md`)
  unblock with zero extra machinery — a parked lookup is just a `Constraint` in its
  own store, woken by the cross-store registry, flushed by `enforceConstraints`.

- **Phase 3 — specified in `capability-constraint-api.md`.** The explicit worklist
  driver, motivated by type safety (making the documented breakage modes
  unrepresentable: Prefix/Verdict/Reaction/Inference) with the AC-3 worklist and
  watcher index as consequences rather than goals. No result changes. Do NOT build
  a bolt-on queue without those types.

**What deliberately does not change:** `Package` immutability (it is what makes
reactions confluent and backtracking free), `MiniKanren.unify` itself, the
search/scheduler substrate, the reify/labelling structure, and the inert stores
(`Table`, `DebugStore`). Blast radius: `StoreSupport`, the three stores'
`processPrefix` bodies, and `Domain.resolveStorableDom`/`extendD`.

Phases 1–3 touch the constraint core that FD, Neq, Projection (and, transitively,
aggregate) sit on. Treat each as its own reviewed change with the full suite green.

---

## 5. Caveats

- **Infinite domains break free termination.** DCC is automatic for finite domains. For
  unbounded integers/reals, narrowing can descend forever. Options: require bounded
  domains, or add *widening* (jump to a coarser fixed bound after k narrowings, trading
  precision for termination). A finite-domain library can require bounds and ignore this.
- **You still hand-write each narrowing rule.** The engine is generic; a constraint's
  meaning *is* its pruning, so each relation supplies its own `propagate` (with
  enumeration as the generic fallback).
- **Keep the persistent `Package`.** The immutability is what makes backtracking free and
  change-detection cheap (reference equality on persistent sub-maps). Do not introduce
  mutable domain state.

---

## 6. Acceptance tests to add

1. The known-broken/risky compositions, as *failing* tests first (they bound the work).
   NOTE: `separate(x,1) ∧ dom(x,{1})` already fails correctly TODAY via the reify-time
   rescue (verified July 2026) — do not use it as the failing case. The genuinely
   unpinned cases are:
   - committed choice over an inconsistent mid-search state: `conda`/`condu` whose
     first clause produces a package carrying an FD-collapse binding that violates a
     Neq constraint — assert the commit does not select the doomed branch (this fails
     before Phase 1, passes after);
   - two mutually narrowing FD constraints whose fixpoint needs ≥2 sweeps — assert the
     result is independent of the order the constraints are stated (fails before
     Phase 2's wake-on-narrowing).
2. After Phase 1: assert the reify-time rescue is a no-op — `EnforceConstraintsFD`'s
   re-run of processPrefix discovers no new violations, because the chokepoint already
   caught them mid-search.
3. Cross-domain: `x ∈ {1..10} ∧ x ≠ 5` — assert `5` is removed from the domain before
   labelling (Phase 2), not merely filtered during enumeration.
4. Regression: the entire existing constraint suite (`FiniteDomainTest`, `SummationTest`,
   `MultiplicationTest`, `OrderConstraintsTest`, `SeparateTest`, `ParametersTest`,
   `NeqFiniteDomainTest`) stays green at every phase.
