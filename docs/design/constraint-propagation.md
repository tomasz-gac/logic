# Constraint propagation redesign — design sketch

**Status:** design sketch, NOT implemented. The current constraint composition is
patched (stores no longer starve each other of the prefix) but is not a general
constraint solver. This document specifies the target design in enough detail to
implement it later. Nothing here needs to be done to keep the library working for
single-domain queries.

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

2. **Single pass, no fixpoint.** Independent domains trigger each other: an FD
   narrowing can bind a variable, which should re-fire a disequality check, which
   can bind another variable, which should re-fire FD narrowing. A single ordered
   `and`-pass never loops, so cascades are missed.

Evidence: `com.tgac.logic.ckanren.NeqFiniteDomainTest` reproduced the starvation
symptom (`x ∈ {0,1,2} ∧ x ≠ 1` returned `{0,1,2}` instead of `{0,2}`). The patch
(passing the original package to each store so it diffs/verifies against the right
base) fixed *that* case. It did **not** fix the two defects above. Known still-broken
combinations to pin with failing tests before starting:

- an FD variable narrowed to a singleton **and** a disequality on the same variable
  (replace-clobbering);
- two FD constraints that only converge after several mutual narrowings (needs the
  fixpoint), combined so ordering matters.

See `StoreSupport#processPrefix` javadoc for the in-code statement of the limitation.

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

- **Phase 0 (interim, cheap, do regardless):** in `StoreSupport`, detect more than one
  *substitution-mutating* `ConstraintStore` active in a package and fail loudly (or log)
  rather than returning a silently wrong answer. Whitelist the combinations already
  verified. Documents the real contract in code.
- **Phase 1 (correctness):** monotonic substitution (ban `withSubstitutions`-replace in
  the constraint path; use `extendS`) + naive `propagateToFixpoint` replacing the
  `and`-chain in `StoreSupport.processPrefix`. Re-validate every FD/Neq/Projection test.
- **Phase 2 (cross-domain):** add propagators that read one factor and narrow another
  (disequality removing a value from an FD domain). This is the first behaviour the old
  design could not produce; add tests that require it.
- **Phase 3 (performance):** AC-3 worklist + dependency index. No result changes.

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

1. The known-broken compositions, as *failing* tests first (they bound the work):
   - FD var narrowed to a singleton **+** a disequality on it — assert the binding
     survives and the disequality is honoured.
   - two mutually narrowing FD constraints whose fixpoint needs ≥2 sweeps — assert the
     result is independent of the order the constraints are stated.
2. Cross-domain: `x ∈ {1..10} ∧ x ≠ 5` — assert `5` is removed from the domain before
   labelling (Phase 2), not merely filtered during enumeration.
3. Regression: the entire existing constraint suite (`FiniteDomainTest`, `SummationTest`,
   `MultiplicationTest`, `OrderConstraintsTest`, `SeparateTest`, `ParametersTest`,
   `NeqFiniteDomainTest`) stays green at every phase.
