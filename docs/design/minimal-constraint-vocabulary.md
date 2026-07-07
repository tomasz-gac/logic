# The minimal constraint vocabulary — removing Narrowing and Inference

**Status: PLANNED (July 2026, designed with Tom). Not implemented. Supersedes
§2.4 of `capability-constraint-api.md` (the `Inference` vocabulary) and
FORECLOSES the data-shaped Neq→FD bridge idea deferred there — see §6 for the
reasoning, recorded so it is not resurrected.**

Prerequisite reading: `capability-constraint-api.md` (the implemented Steps 1–3.5:
Propagator/Verdict, ConstraintStore/Revision, Prefix, the agenda drain).

---

## 1. Why: the shared narrow vocabulary carries no cross-domain traffic

`Inference.narrow(Term, Narrowing)` was designed as cross-factor vocabulary —
rule B of the composition model: domains shrink, so "shrink this term" is a
shared concept. The audit (July 2026) says otherwise. Every `Inference.narrow`
emission in the codebase:

- FD propagator verdicts: `leq`, `addo`, `mulo`, `separateFDC` (via
  `FiniteDomain.updates`);
- the FD store's own `revise` on var–var aliasing (domain copy/intersect).

Every one is FD talking to FD. The round-trip through the driver's shared
vocabulary exists only to arrive back home: the emitter's owning store is the
consumer. The one real piece of cross-domain deduction in the system — the
Neq→FD bridge — bypasses `Narrowing` entirely (it is call-shaped:
`Disequality.separate` → `FiniteDomain.excludeFromDomain`).

The literature agrees. Single-solver systems (Gecode, Choco, CLP(FD) where
`#\=` is a native propagator) have no cross-domain problem. Systems with
genuinely separate constraint domains keep them siloed and let them interact
through the substitution only (SWI's `dif/2` and clpfd do not prune each
other). cKanren's `=/=`-to-FD exclusion — our bridge's ancestor — is a direct
call there too. We know of no system with a shared narrowing vocabulary between
independent constraint domains.

So `Narrowing` is speculative generality (YAGNI), and it is not free:

- its `Goal`-shape is the escape hatch — applying a narrow hands control to
  `Domain`, which makes driver decisions (collapse vs narrow vs no-op) inside
  an opaque goal and calls the engine's enqueues itself;
- it forces the driver to execute code it cannot inspect mid-drain;
- as cross-domain vocabulary it would *invite* constraint implementors to
  hardcode dependencies on other domains for speed — the thing rule B exists
  to prevent — in exchange for deduction that is optimization-only (the record
  verification already guarantees the same answer set).

## 2. The target vocabulary

The cross-boundary language shrinks to the two values the driver genuinely
owns and understands:

- **`Prefix`** — bindings (substitutions grow);
- **`Term`** — a changed-term announcement (wake its watchers).

Propagators speak `Verdict`; stores speak `Revision`; both carry only those
two values across the boundary. Domain updates never cross the boundary at
all — they are a private conversation between a propagator and its own store.

### 2.1 `Verdict` (revised set)

```java
Verdict.fail()                 // violated — the branch dies
Verdict.keep()                 // undecided — stay parked
Verdict.subsumed()             // entailed — forget me
Verdict.update(f)              // stay parked; apply f to MY OWN store's factor
Verdict.run(Goal)              // unchanged (the suspension feature)
```

`Verdict.narrowed(List<Inference>)` is REPLACED by `Verdict.update(f)` where
`f : (Package, Store) -> Revision` — the driver fetches the propagator's own
factor (it knows it: `getStoreClass()`), applies `f` to it against the live
package, and routes the returned `Revision`. Touching another store's factor
remains unrepresentable: the driver only ever hands a propagator its own.

### 2.2 `Revision` (grows the payload `Inference` used to carry)

```java
Revision.fail()
Revision.unchanged()
Revision.updated(Store myFactor)
Revision.updated(Store myFactor, List<Term> changed)          // strict narrowings
Revision.updated(Store myFactor, Prefix inferred, List<Term> changed)  // + collapses
```

The driver's routing, in one place: swap the factor, queue a Bind for the
prefix, queue a Wake per changed term. `Revision` becomes the single answer
shape for BOTH triggers — a store revising against a prefix and a store
absorbing its own propagator's update.

### 2.3 What dissolves

- **`Narrowing`** — deleted. `Domain` stops implementing anything from
  `ckanren` and stops calling the engine; it becomes pure domain lattice plus
  statement-goal helpers.
- **`Inference`** — deleted. Its bind arm becomes `Revision`'s prefix payload;
  its narrow arm becomes `Verdict.update`'s private function.
- **The public enqueues** — `enqueueBind`/`enqueueWake` go private. The
  public surface of `Propagation` becomes exactly three goal-shaped,
  mode-oblivious entries:

```java
Propagation.resolve(Prefix)    // statement: these unify / this was inferred
Propagation.activate(Propagator)  // statement: this constraint holds
Propagation.changed(Term)      // announcement: x changed, re-examine watchers
```

  (`changed` is required by the statement-position survivors, §4. A spurious
  call is harmless: watchers re-run and answer `keep`.)

## 3. What the atomicity buys

Today a `narrowed` verdict's inferences apply **sequentially**: applying
inference 1 can change the package under inference 2's feet — the staleness
class that produced the separateFDC raw-target rebind bug, patched by
walk-at-application-time. Under `Verdict.update` the whole domain update is
one function producing one `Revision` against one state snapshot: the
staleness class is REMOVED, not guarded against. Consequences:

- the walk-at-application invariant (Inference.Narrow's javadoc) disappears
  with its type;
- the inference `.distinct()` dedup — and CapabilityDriverTest's
  "identical narrowings dedup" pin — retire with it (one atomic revision has
  nothing to dedup); the pin is REPLACED, not deleted silently: its concern
  becomes "one update, one factor swap", asserted on the new shape;
- the termination guard (equal-domain short-circuit) stays FD-side, where
  domain equality belongs, but becomes *visible* to the driver as "no changed
  terms in the revision" — the fixpoint argument reads off the vocabulary.

## 4. Statement-position survivors

Three call sites announce work from goal position (no drain in flight) and
stay on the public API:

- **`FiniteDomain.dom`** (register/tighten a domain as a goal): updates the FD
  factor via `Package.updateStore`, then `changed(x)` on strict narrowing, or
  `resolve(prefix)` on collapse.
- **The call-shaped bridge** (`excludeFromDomain`, reached from `separate`'s
  goal body): same statement-position pattern; it keeps working through the
  public API without any hatch. Whether to keep the bridge at all is a
  SEPARATE, purely behavioral decision (dropping it loses pre-labelling
  pruning and the `x∈{4,5} ∧ x≠5 ⊢ x=4` collapse inference; NeqFdBridgeTest
  pins both; the answer set is identical either way). Tom decides explicitly;
  this design neither requires nor performs it.
- **The enforce hooks** (`rerunConstraints`, projection's wake-then-check):
  goal-shaped forever — labelling IS search — and `changed` is their entry.

## 5. Relationship to the other designs

- `capability-constraint-api.md`: §2.4 (Inference) is superseded by this doc.
  Everything else stands; the scorecard improves (the "woken constraint body
  makes driver decisions" row becomes unrepresentable).
- **Neq-as-propagators** (deferred in Step 3.5): compatible and slightly
  easier after this — records-as-propagators would answer `Verdict.update`
  on their own store like everyone else.
- `suspensions.md`: unaffected; `Verdict.run` is untouched.

## 6. Foreclosed: the data-shaped Neq→FD bridge

Deferred in `constraint-propagation.md`-era notes as "needs a vocabulary-growth
decision per rule B", the idea was to replace the call-shaped bridge with an
emitted "x may not be v" inference. This design forecloses it deliberately:

1. it is optimization-only (record verification already guarantees the same
   answers — pruning earlier, never pruning differently);
2. it requires exactly the cross-domain narrow vocabulary whose only effect is
   to invite inter-domain coupling;
3. no CLP system we know of has such a channel — the substitution is the
   universal inter-domain medium, and we already have it.

If a future need for genuine cross-domain narrowing appears, this section is
the argument it must overcome.

## 7. Migration plan (each step lands green on the full suite)

1. **`Propagation.changed(Term)`** public entry; convert the enforce hooks and
   `Domain`'s statement-position wake to it. (Small; behavior identical.)
2. **`Revision` grows payloads** (changed terms, prefix), additively; the
   driver routes wakes/binds from revisions. Existing `updated(Store)` and
   `updated(Store, List<Inference>)` callers untouched.
3. **`Verdict.update(f)`** added; the driver interprets it (fetch own factor,
   apply, route). Convert FD propagators one at a time (`leq`, `addo`, `mulo`,
   `separateFDC`), then the FD store's aliasing revise. `Verdict.narrowed`
   deleted when the last emitter converts; the dedup pin is reworked here.
4. **Statement-position conversion**: `dom` and the bridge onto
   `resolve`/`changed` + `Package.updateStore`; `Domain` sheds the `Narrowing`
   implementation and every `Propagation` call.
5. **Deletions**: `Narrowing`, `Inference`; `enqueueBind`/`enqueueWake` go
   private. Public surface = `resolve`, `activate`, `changed`.
6. **Sweep**: this doc's status, `capability-constraint-api.md` §2.4 pointer,
   CLAUDE.md, memory.

Sizing: comparable to Step 2 of the capability migration (it touches every FD
propagator body), smaller than the propagation redesign. Risk concentrates in
step 3 (the FD conversions — expect it to surface latent bugs the way Phase 2
surfaced `mulIntervals`); steps 1–2 are additive and safe.

## 8. Open naming decisions (Tom)

- `Verdict.update` vs `Verdict.revise` (symmetry with the store hook) vs
  another verb. `update` recommended: the propagator requests an update of its
  own factor; `revise` would overload the store-hook name for a different
  speaker.
- `Propagation.changed` vs `touched` vs `narrowed`. `changed` recommended:
  it names the fact announced, not the mechanism.
- `Revision.updated(Store, Prefix, List<Term>)` arity vs a small builder.
  Decide at implementation against real call-site shapes.
