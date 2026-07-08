# The minimal constraint vocabulary — the driver speaks only to stores

**AMENDED (July 2026, §9): the `narrowed` trigger was ABOLISHED — hooks return
`Fiber<Revision>` and stores own their cascades; where the text below describes a
narrowed trigger/broadcast, §9 supersedes it.**

**Status: IMPLEMENTED (July 2026; the uniform store boundary is Tom's call:
"the driver only handles cross-store interactions, never intra-store").
Deviations from the plan, all recorded in place: the FD primitive lives in its
own class (`finitedomain/DomainUpdate` — process-δ as a value); the enqueues
were DELETED outright rather than privatized (zero callers remained once
stores answered with data); `Stated` dispatches by store-class equality;
projection's update arm throws (no projection propagator updates its factor);
the retired dedup pin became two pins on the payload routes (a narrowed
term broadcasts exactly once; runs splice only after quiescence — suite is 315). Supersedes §2.4 of
`capability-constraint-api.md` (the `Inference` vocabulary), DEMOTES the
propagator protocol from driver boundary to store-implementor toolkit, and
FORECLOSES the data-shaped Neq→FD bridge (§6) — recorded so it is not
resurrected. Also supersedes the Step 3.5 "Neq-as-propagators" backlog entry:
under this design Neq's wholesale store is the model citizen, not the legacy
oddball.**

Prerequisite reading: `capability-constraint-api.md` (the implemented Steps
1–3.5: Propagator/Verdict, ConstraintStore/Revision, Prefix, the agenda drain).

---

## 1. Why

### 1.1 The narrow audit: the shared vocabulary carries no cross-domain traffic

`Inference.narrow(Term, Narrowing)` was designed as cross-factor vocabulary.
The audit (July 2026) found every emission is FD talking to FD — propagator
verdicts (`leq`, `addo`, `mulo`, `separateFDC`) and the FD store's aliasing
revise. The emitter's owning store is always the consumer; the round-trip
through the driver exists only to arrive back home. The one real cross-domain
deduction (the Neq→FD bridge) bypasses `Narrowing` entirely — it is
call-shaped. The literature agrees: single-solver systems have no cross-domain
problem, and systems with separate domains (SWI `dif/2` vs clpfd) interact
through the substitution only. No known CLP system has a shared narrowing
vocabulary between independent constraint domains.

### 1.2 The protocol asymmetry: binds are store-level, wakes are propagator-level

When a prefix arrives, the driver asks the STORE (`revise`) and the store
updates its own factor. When a term changes, the driver bypasses the store and
runs individual PROPAGATORS — read-only reporters that must relay factor
updates back through driver vocabulary (`Inference.narrow`, and the escape
hatches `Domain` needs to apply them). The asymmetry is the sole reason the
shared narrow vocabulary — and the relay machinery — exists.

The fix is Tom's principle: **the driver handles cross-store interactions
only, never intra-store.** Wakes become store-level triggers like binds
already are. The store runs its own propagators internally, updates its own
factor directly, and answers with data. Nothing intra-domain ever crosses the
boundary again.

## 2. The target boundary

The driver understands exactly two values — **`Prefix`** (bindings grow) and
**`Term`** (this narrowed; re-examine) — and one answer shape.

### 2.1 `ConstraintStore` — three triggers, one answer

```java
interface ConstraintStore extends Store {
	boolean isEmpty();

	/** Bindings arrived. Revise your factor against them. */
	Revision revise(Prefix prefix, Package state);

	/** A term's knowledge shrank (bound, or domain narrowed). Re-examine watchers. */
	default Revision narrowed(Term<?> x, Package state) { return Revision.unchanged(); }

	/** One of your items was just stated. First examination. */
	default Revision stated(Stored item, Package state) { return Revision.unchanged(); }

	<T> Goal enforce(Term<T> x);      // as today
	<A> Term<A> reify(...);           // as today
}
```

`revise` and `narrowed` broadcast to every store; `stated` goes to the item's
owning store only (`getStoreClass`). `pendingPropagators` is DELETED from the
interface — the cross-store wake union was the driver reaching into store
internals; the broadcast replaces it.

### 2.2 `Revision` — the single answer

```java
Revision.fail()                      // the branch dies
Revision.unchanged()                 // nothing to do
Revision.updated(Store myFactor)     // swap my factor
    // payloads (exact factory shape decided at implementation, §8):
    //   Prefix inferred      — collapses and other inferred bindings
    //   List<Term> narrowed   — strict narrowings: wake the watchers
    //   List<Goal> runs      — suspension bodies (projection), spliced
    //                          after quiescence via the run lane
```

The driver's routing, in ONE place: swap the factor, queue a Bind for the
prefix, queue a wake per narrowed term, append runs to the run lane. It never
executes store code and never inspects a domain.

### 2.3 `Propagation` — the public surface

```java
Propagation.resolve(Prefix)     // statement: these unify / this was inferred
Propagation.activate(Stored)    // statement: park this item + first examination
Propagation.narrowed(Term)      // announcement: x narrowed, re-examine watchers
```

All three are goal-shaped and mode-oblivious (drain in flight → append;
otherwise → install and drain). `activate(item)` = `withStored(item)` + queue
`Stated(item)`. `enqueueBind`/`enqueueWake` go PRIVATE. The agenda's item
kinds become Bind(Prefix) / Narrowed(Term) / Stated(Stored); the
one-item-per-deferred-step fairness invariant and the run-lane phase-2 splice
are unchanged.

### 2.4 What dissolves, what demotes

- **`Narrowing`, `Inference` — deleted.** `Domain` implements nothing from
  `ckanren` and never calls the engine; binds and narrowing announcements ride
  `Revision` payloads.
- **`Propagator`/`Verdict` — demoted, not deleted.** They stop being driver
  citizens and become the `ckanren.propagator` TOOLKIT: a library for stores
  that schedule parked bodies (FD, Projection). The evaporation-safety
  argument (keep-is-default, framework owns parking) protects constraint
  AUTHORS, and authors write against this toolkit — the protection moves with
  them. The store administers its own propagators' verdicts inside
  `narrowed`/`stated` (the logic of today's `Propagation.interpret`/`wake`,
  including chain-inclusive watch matching, moves into the stores). The
  toolkit's `Verdict.narrowed(List<Inference>)` is replaced by a store-local
  update shape (recommended: `Verdict.update(f)` with
  `f : (Package, S) -> Revision`, administered by the owning store — the
  driver never sees it); `fail`/`keep`/`subsumed`/`run` keep their semantics.

## 3. What the uniform boundary buys

- **The escape hatches close.** No store or domain code calls the engine
  mid-drain; the only engine callers left are statement-position goals
  (`dom`, the bridge, enforce hooks) on the three public entries.
- **The staleness class is removed, not guarded.** Today a narrowed verdict's
  inferences apply sequentially — applying one can invalidate the next's
  captured target (the separateFDC raw-target bug, patched by
  walk-at-application). Now a store's whole response to a trigger is one
  `Revision` computed against one state snapshot. The walk-at-application
  invariant retires with `Inference`; CapabilityDriverTest's dedup pin is
  REWORKED (atomic revisions have nothing to dedup), not silently dropped.
- **Termination becomes vocabulary-visible.** The equal-domain guard stays
  FD-side (domain equality is domain semantics) but the driver sees its
  effect as "no narrowed terms in the revision" — the fixpoint argument reads
  off the boundary: substitutions only grow, and stores that report no change
  cause no work.
- **The two-level mirror resolves.** Store-level `Revision` is THE protocol;
  propagators are one store-internal scheduling strategy among possible
  others. Neq (wholesale revise, parks nothing) needs no conversion — it was
  the uniform shape all along.

## 4. Statement-position survivors

Goal-position code that announces work, all on the public API, no hatches:

- **`FiniteDomain.dom`**: update the FD factor (`Package.updateStore`), then
  `narrowed(x)` on strict narrowing or `resolve(prefix)` on collapse.
- **The call-shaped bridge** — DECIDED AND DROPPED (Tom, July 2026):
  optimization-only (identical answer sets via record verification +
  labelling), and it was the last inter-domain named dependency (`separate`
  no longer imports `finitedomain`). `NeqFdTest` pins the answer-set
  equivalence that made the drop safe. Disequality and FD now compose through
  the substitution alone — the way every CLP system we surveyed does (§1.1).
- **The enforce hooks** (labelling's re-run, projection's wake-then-check):
  goal-shaped forever — labelling IS search; `narrowed` is their entry.

## 5. Relationship to the other designs

- `capability-constraint-api.md`: §2.4 superseded; §2.2's propagator protocol
  demotes to toolkit (its safety analysis still applies, one level down); the
  scorecard improves — "woken constraint body makes driver decisions" becomes
  unrepresentable.
- **Neq-as-propagators** (Step 3.5 backlog): SUPERSEDED, inverted — the
  store boundary is the protocol; nothing should be converted TO propagators.
- `suspensions.md`: the feature survives as `Verdict.run` in the toolkit,
  surfacing through `Revision`'s runs payload; semantics (spliced after
  quiescence; inline-at-statement becomes after-a-trivial-drain) essentially
  unchanged.

## 6. Foreclosed: the data-shaped Neq→FD bridge

The idea (emit "x may not be v" as vocabulary) is deliberately dead:

1. optimization-only — record verification already guarantees the same
   answer set; the bridge prunes earlier, never differently;
2. it requires exactly the cross-domain narrow vocabulary whose only effect
   is to invite inter-domain coupling (rule B's failure mode);
3. no CLP system we know of has such a channel — the substitution is the
   universal inter-domain medium, and we have it.

A future need for genuine cross-domain narrowing must overcome this section.

## 7. Migration plan (each step lands green on the full suite)

1. **`Revision` grows payloads** (prefix, narrowed, runs) additively; the
   driver routes them wherever revisions are already folded. Existing callers
   untouched.
2. **Store-level wakes.** Add `ConstraintStore.changed` (default unchanged);
   move the interpret/wake/watch machinery from `Propagation` into
   `FiniteDomainConstraints.narrowed` and `ProjectionConstraints.narrowed`;
   Wake items fold `narrowed` over stores; DELETE `pendingPropagators` and the
   driver's propagator handling. The watches lesson applies verbatim: the
   chain-inclusive matching must move intact (a plain live walk steps THROUGH
   a just-bound variable and misses the primary match).
3. **Store-level statements.** `Stated` items + `ConstraintStore.stated`;
   `activate(Stored)` reworked as park+queue; convert `fdConstraint` and
   `project`.
4. **FD internals.** Toolkit `Verdict` update-shape replaces
   `narrowed(List<Inference>)`; convert `leq`, `addo`, `mulo`, `separateFDC`,
   the aliasing revise; `dom` and the bridge onto `resolve`/`narrowed` +
   `updateStore`; `Domain` sheds `Narrowing`.
5. **Deletions.** `Narrowing`, `Inference`; enqueues private;
   `ckanren.propagator` package-info rewritten as the toolkit ("used BY
   stores, unknown to the driver").
6. **Sweep.** This doc's status, capability doc pointers, CLAUDE.md, memory.

Sizing: propagation-redesign-shaped — steps 2 and 4 touch every FD propagator
body and the wake machinery; expect them to surface latent bugs the way
Phase 2 surfaced `mulIntervals`. Steps 1 and 3 are additive. Do not start
without failing-pin discipline: identify the behavior-sensitive tests per
step before moving code.

## 8. Open decisions (Tom)

- Names: `narrowed` vs `touched` (trigger + public entry); `stated` vs
  `examined`; the `Revision` payload factories (arities vs builder) — decide
  at implementation against real call-site shapes.
- Whether `Stated` is a distinct agenda item kind (recommended: yes — exact
  statement semantics, single-store dispatch) or faked via `narrowed` on
  watched terms (rejected: multi-wake, wakes on ground values, inexact).
- The bridge keep/drop decision (§4) — independent of this design.

## 9. The scheduling layer (July 2026 amendment — implemented)

The `narrowed` trigger (né `changed`) conflated two events: bound variables
(shared content — the substitution) and domain narrowings (private content —
only the owner can act). The resolution, decided with Tom:

- **`narrowed` is ABOLISHED** as a trigger, an agenda item, and a public entry.
  The driver's items are Bind and Stated; the public surface is `resolve` and
  `activate`; the store hooks are `revise`, `stated`, `enforce`, `reify`.
- **A store's reaction is COMPLETE**: `revise` does custody, re-examines the
  store's own watchers of the newly bound variables, and chases the resulting
  cascade. Statement-position re-examination (`dom` narrowing, labelling's
  catch-up, projection's enforce flush) is self-service by the owning domain's
  own code.
- **Fair interleaving moved to the correct layer**: hooks return
  `Fiber<Revision>`, mirroring the unifier (`MiniKanren.unifyPrefix` is an
  `MFiber`). Cheap stores answer `Fiber.done(...)` — today's FD cascade is a
  plain synchronous loop, since every propagator is cheap; a store hosting
  expensive propagators defers between steps via `functional`'s `Worklist`
  (extracted under fixpoint-machine.md §4's bottom-up rule: one item per
  deferred step, so any scheduler interleaves fairly). Granularity is the
  store author's choice — it was never enforceable anyway; the framework's
  hard laws are only the custody rules.
- **The intra-store note has its own type**: `Verdict.update` answers the
  toolkit's `Update` (factor + inferred + runs + `withReexamine` terms feeding
  the owner's worklist), and `Revision` carries only what the driver routes
  (factor + inferred + runs). Leaking a re-examination note to the driver is
  UNREPRESENTABLE — the runtime guard and its pin were retired for the type.
- **Termination** is each store's contraction obligation (`DomainUpdate` only
  shrinks); a non-contracting store now spins fairly as fiber steps rather
  than being preempted per agenda item.

Cross-store interaction is therefore exactly one thing: bindings, through the
substitution. Everything else is intra-store or store↔driver scheduling.
