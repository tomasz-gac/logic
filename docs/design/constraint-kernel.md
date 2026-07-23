# The constraint kernel

**Status: AUTHORITATIVE (July 2026). This describes the engine as shipped on
the `worklist` branch — the end state of the capability / store-boundary /
suspension redesigns. The superseded design docs (constraint-propagation,
capability-constraint-api, minimal-constraint-vocabulary, suspensions) were
deleted; their reasoning survives in §7's lineage, the commit history, and the
still-live companions: `fixpoint-machine.md` (the two-fixpoint model),
`tabled-constraints.md` (the tabling merge, priced), and
`substitutions-migration.md` (the next planned step).**

---

## 1. State: the package is a product

A `Package` is the immutable solver state, a product of factors:

- **`Substitutions`** — the SHARED factor: the monotonically growing log of
  bindings. It has a read-only view type (`unification.Substitutions`) with no
  route to any store; code typed against it is structurally scoped to shared
  knowledge.
- **Constraint store factors** — each `ConstraintStore` owns one factor
  (`FiniteDomainConstraints`, `NeqConstraints`), private to its domain.
- **Driver-owned plumbing** — the transient `Agenda` (its presence marks a
  drain in flight; never survives to answers) and the persistent `Suspensions`
  store (parked search effects). Plain stores like `Table` and `DebugStore`
  ride alongside, invisible to constraint processing.

Everything is persistent; backtracking is free — each branch keeps its own
package. In lattice terms: the substitution only grows, every factor only
narrows, propagators are monotone contracting operators, and the drain is
chaotic iteration to a fixpoint — which is why termination is finite descent
and why agenda order and scheduler choice can never change the answer set.

## 2. The driver: `Propagation`

Public surface — three goal-shaped, mode-oblivious entries (drain in flight →
work appends; otherwise the call is the outermost trigger and drains to
quiescence):

```java
Propagation.resolve(Prefix)                    // these bindings hold
Propagation.activate(Stored)                   // this store item was stated
Propagation.suspend(watched, ripe, body)       // run body once ripe
```

- **`Prefix`** — a delta of bindings, mintable only by the unifier
  (`MiniKanren.unifyPrefix`, a collecting Extender — born valid, O(delta)) or
  the checked `Prefix.binding`. `resolve` is the chokepoint: the ONLY way
  substitutions grow in constraint-aware code. An inferred binding is
  indistinguishable from a unification. Pure-relational fast path: no stores
  and no pending suspensions → apply the delta, skip all machinery.
- The **agenda** holds two item kinds — `Bind(Prefix)` and `Stated(Stored)` —
  popped one per deferred step (the fairness quantum between branches). A Bind
  revalidates its prefix against the live package (open → bind the walked
  representative, agreeing → drop, contradicting → the branch dies), extends
  once, folds every store's `revise`, then ripens suspensions touched by the
  bound variables. Run-lane goals splice after quiescence, when the agenda is
  removed.
- **Suspensions** are `(watched, ripe, body)`: parked persistently, re-examined
  when a watched chain binds, spliced through the run lane once ripe — fired
  once, forever. `ripe` is a `Predicate<Substitutions>` and MUST be monotone:
  once true, true in every derived state — adding bindings never falsifies it
  (upward-closed). Conditions about the presence of knowledge qualify
  ("ground", "both bound", "equality decided"); absence-shaped conditions are
  negation-as-failure and belong to committed choice, not here. Pending
  suspensions disqualify the fast path and block answers (the reify-time
  pend-check).

## 3. The store protocol

The driver speaks to stores through exactly two triggers, each answered by a
`Fiber<Revision>`:

```java
Fiber<Revision> revise(Prefix, Package);   // bindings arrived — broadcast
Fiber<Revision> stated(Stored, Package);   // your item was stated — owner only
// plus lifecycle: enforce (commit before reify), reify (render residue)
// optional capability: Projectable (project/restate/discharged — see below)
```

A store's reaction is COMPLETE: custody checks, re-examining its own watchers
of the newly bound variables, and chasing its own cascade. The fiber return is
the scheduling contract: cheap reactions are `Fiber.done(...)` (FD's cascade is
a plain synchronous loop — every propagator is cheap); expensive ones defer
between steps (`functional`'s `Worklist` is that loop, fiber-stepped — same
substrate as the unifier's `MFiber`). Granularity is the author's choice; it
was never enforceable anyway. The hard laws are custody: a `Revision` can
express at most the store's OWN replaced factor plus payloads.

**The 2×2 vocabulary** — two effect kinds, two speaking positions, and nothing
else crosses any boundary:

| effect | from goal position | from store position |
|---|---|---|
| **bind** | `Propagation.resolve(prefix)` | `Revision.withInferred(prefix)` |
| **search** | `Propagation.suspend(w, ripe, body)` | `Revision.withSuspend(suspension)` |

A store-emitted run is the degenerate always-ripe suspension. Cross-store
interaction is therefore one thing: bindings, through the substitution — the
blackboard. Everything else is intra-store or store↔driver scheduling.

**Optional capability (July 2026): `Projectable<R extends Residue<R>>`**
— `project(vars, wideningAllowed)` reports the store's knowledge about a
POSITIONAL var list as a `Residue` (slot i = vars[i], absence = ⊤,
empty-list projection IS ⊤). It TRANSCRIBES everything expressible —
domains and wholly-covered couplings, the latter carried as LIVE propagator
objects with (var → slot) maps; the parameter governs only the
inexpressible remainder (escapes): dropped by permission or thrown when
exactness was demanded. The residue RESTATES ITSELF through the public
posts (`Residue.restate` — carried couplings alias-unify onto the live
vars and re-activate) and carries entailment (`PartialOrder.leq` — all any
consumer asks; matching is containment, tabled-constraints.md §5.4).
`discharged(state)` distinguishes live from spent knowledge (stale domains
under bindings). FD is the prototype (`DomainResidue`). Participation in
tabled calls requires the capability: unprojected knowledge cannot be
keyed, and unkeyed knowledge means wrong reuse.

## 4. Intra-store machinery (FD's, privately)

`finitedomain` owns the propagator toolkit — the driver never sees it:

- **`Propagator`** — `(storeClass, watchedTerms, body)`, a concrete value.
- **`Verdict`** — the body's lifecycle ruling: `fail | keep | subsumed |
  update(f)`. `keep` is the default-safe case (forgetting to re-park is
  unwritable). `update`'s `f : (Package, Store) → Update` is applied by the
  OWNING store to its own factor.
- **`Update`** — the intra-store step algebra: `fail | unchanged |
  applied(factor) + withInferred + withReexamine + withSuspend-shaped runs`.
  `withReexamine` terms feed the owner's worklist and NEVER reach the driver —
  the type makes leaking unrepresentable. `DomainUpdate` is cKanren's
  process-δ as `Update` steps (membership / intersect / equal-domain guard —
  the termination guard of wake-on-narrowing / singleton collapse as an
  inferred prefix, factor deliberately not updated).
- The cascade: pop a term, run its watchers, `consume` each step (factor into
  a local, payloads accumulated, reexamine onto the queue), until dry or dead.

Suspension conditions in a store's own language (domain-shaped ripeness —
adaptive labelling, guarded statement, prune-to-enumerate handovers) are
propagators whose updates emit suspensions: private trigger, same lane.

**Shelved: Neq on the verdict protocol.** Neq's records are propagators in
disguise — `verificationStep`'s three outcomes are, case for case,
`subsumed` (trial unification fails: the record can never be violated,
forget it), `fail` (empty delta: violated), and `update` (non-empty delta:
replace the record with its residual), with `keep` as the degenerate
equal-residual update. `verifyAndSimplify` is a hand-rolled
`examine`/`consume` sweep encoding those verdicts through nested `Option` —
two semantically different `None`s in one channel is the store's one
genuinely cryptic spot. Rewriting Neq on the toolkit would name the
verdicts, unify the propagation vocabulary across both stores, and open
watch-based waking (re-verify only records watching a bound variable,
instead of wholesale on every binding — the only real performance headroom
here). The cost is the reason it is shelved: `Verdict`/`Update` are
FD-private by the lineage decision above (demoted from the driver boundary
when the audit found zero cross-domain traffic), and Neq would be the first
second user — promoting the toolkit to `constraints/store` is a framework
decision, not a store-local edit. Wholesale re-verification is also cheap at
Neq's record counts, so the watch payoff is speculative. Revisit when a
third store wants the toolkit or Neq shows up in a profile.

Related, subsumed by the above: normalize-at-meet (maintain the store as a
canonical antichain — union + subsumption-prune at every entry point instead
of display-time `removeSubsumed`). Assessed and declined on its own: revise
already prunes satisfied records, rewrites survivors to residuals, and
merges equal residuals via set identity; the remaining gap (coexisting
strict-inclusion records) is a rare statement-time artifact. Its one live
payoff — the Smyth-order `leq` ("every record of B has a stronger
representative in A"), the honest `entails` — belongs to TCLP and should be
built with it.

## 5. Structure has one owner

`MiniKanren` defines what structure is. Three verbs, one decomposition:

- **`unify`** — defines it (pairwise, kind-agreeing, arity-checked).
- **`mapStructure`** — rebuilds it (needs the collector registry; rebuild and
  traversal are different operations — a traversal must never require a
  collector).
- **`members`** — reads it: a term's structural members (collections, tuples,
  LList, LTree), read-only. Consumers: `Watches` (chain-inclusive matching —
  every walk-chain node is checked, because a full walk steps THROUGH a
  just-bound variable and misses the match; `matchesStructurally` extends this
  through composites, including members that appear via nested instantiation),
  `Substitutions.isGround`, `reifyS`.

All structural traversals are heap-stacked (explicit deques): term depth never
touches the JVM stack. The planned `substitutions-migration.md` step C gives
unify and `members` a literally shared `decompose`.

## 6. Contracts (the ones types cannot say)

1. **Contraction**: store updates only shrink knowledge; `DomainUpdate`
   guarantees it for domains. This is the drain's termination argument.
2. **Ripeness monotonicity**: see §2 — spelled out on `Suspension`.
3. **Facts immutable per solve**: no assert/retract; tabling and any future
   constrain-mode queries assume snapshots (monotonicity engine-wide).
4. Everything else IS types: prefix validity, custody, reexamine locality,
   substitution scoping.

## 7. Lineage (why it is this way)

The shape was reached by subtraction, each step killing a live bug class or a
channel with zero users: rule-enforced chokepoint → `Prefix` born valid;
store hooks that could clobber the world → `Revision` custody; the propagator
protocol at the driver boundary → demoted to FD's toolkit when the narrow
audit found zero cross-domain traffic; the `narrowed` broadcast → abolished
when its two event kinds separated (shared content rides `revise`; private
content is the owner's business); store-emitted runs → subsumed by
suspensions before ever shipping a user. Deliberately foreclosed (do not
resurrect without new evidence): the data-shaped Neq→FD bridge, cross-store
narrowing vocabulary, engine-level fixpoint unification
(`fixpoint-machine.md` §4/§9). Deliberately deferred, since adopted: `Lattice<L>` shipped July 2026 as the
`functional.algebra` hierarchy (lattice.md §6 records the as-built shape).
Still deferred: representation swaps
(`substitutions-migration.md` §5, benchmark-gated), TCLP
(`tabled-constraints.md`, entailment-gated), Neq on the verdict protocol
(§4 — gated on a third toolkit user or a profile showing Neq).

The composition model in two rules, kept from the capability design: (B) domains
couple to shared concepts, never to each other by name; (C) custody transfer,
never information loss — a store may drop its record only when the information
is preserved elsewhere (which, post-bridge-drop, means: in the substitution).
