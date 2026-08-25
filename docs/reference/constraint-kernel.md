# The constraint kernel

**Status: AUTHORITATIVE (August 2026). This describes the engine as shipped
at the end of the Constraint-pair migration (#137: knowledge outside the
factor) and the one-door arc's kept half (#138: the `Met` row, the focused
trigger, verifier-last) — the value plane (Theory, Atom), the execution
plane (Factor), and the doors between them. The arc's other half —
rename-on-bind and the watchers index — was built, measured, and REVERTED;
§7's lineage records the refutation. The superseded design docs
(constraint-propagation, capability-constraint-api,
minimal-constraint-vocabulary, suspensions) were deleted;
`docs/notes/constraint-pairs-theory-with-factor.md` graduated (its subject
shipped as #137). Their reasoning survives in §7, the commit history, and
the still-live companions: `fixpoint-machine.md` (the two-fixpoint model),
`tabled-constraints.md` (the tabling merge, priced), and
`substitutions-migration.md` (the remaining step).**

---

## 1. State: the package is a product

A `Package` is the immutable solver state, a product of factors:

- **`Substitutions`** — the SHARED factor: the monotonically growing log of
  bindings. It has a read-only view type (`unification.Substitutions`) with no
  route to any store; code typed against it is structurally scoped to shared
  knowledge.
- **Constraint store entries** — one `Constraint` pair per family
  (`FiniteDomainConstraints`, `NogoodConstraints`): the **Theory** is the
  knowledge — the set of atoms that state it, held as a value — and the
  **Factor** is the family's execution behavior (§3). Identity is the
  theory ALONE; a factor never holds the knowledge, but it MAY carry
  private state of its own — a memo, reconstructible from the theory by
  invariant, which is what makes it droppable.
- **Driver-owned plumbing** — the transient `Agenda` (its presence marks a
  drain in flight; never survives to answers) and the persistent `Suspensions`
  store (parked search effects). Plain stores like `Table` and `DebugStore`
  ride alongside, invisible to constraint processing.

Everything is persistent; backtracking is free — each branch keeps its own
package. In lattice terms: the substitution only grows, every factor only
narrows, propagators are monotone contracting operators, and the drain is
chaotic iteration to a fixpoint — which is why termination is finite descent
and why agenda order and scheduler choice can never change the answer set.
Finite lattice height is a FAMILY OBLIGATION (§6): the descent argument is
only as good as the well-foundedness the family brings.

## 2. The driver: `Propagation`

Public surface — three mode-oblivious imposition doors plus the parking
facade (drain in flight → work appends; otherwise the call is the outermost
trigger and drains to quiescence). The doors CONSTRUCT the statement
vocabulary: each returns a `Posting` — the chokepoint's vocabulary lifted
to `Goal`, where `apply` IS the imposition (the goal-shaped bodies are
package-private):

```java
Propagation.resolve(Prefix)      // these bindings hold        → Posting
Propagation.activate(Atom)       // this item is stated        → Posting
Propagation.absorb(Theory)       // this knowledge arrives     → Posting
Propagation.suspend(watched, ripe, body)       // run body once ripe
```

`Posting` is the one public imposition API and the kernel's capability
line: a raw `Goal` can do anything to a `Package`; a `Posting` can only
talk to the chokepoint. The same value is a conjunct in a program, a
literal in a nogood, and a store front door's return type (`dom`, `leq`,
`addo`, `x.unifies` — `UnifyGoal` is `resolve`'s single-unification face:
mint the prefix, resolve it). Postings are `Bounded` by taxonomy —
order 1 by construction, `doomed(Package)` the optional eager 0 — compose
under ∧ (`Posting.all`), and survive naming (`named` keeps the face,
labels outside identity).

The two statement doors are GENERIC — nothing rides the call site:

- **`activate(Atom)`** — registration seeds an absent resident from the
  atom's own `empty()` (the family identity's constructive face; its
  nominal face is `getFactorClass()`), and doom is read through the
  declared `Doomed` capability — an atom without it claims nothing and
  prices 1. The body is the DOOR MEET: the atom meets into the resident
  theory, and the guard is identity — meet is identity-preserving, so a
  covered statement returns the SAME theory object and the package rides
  through untouched. Otherwise one queued `Met(family, {atom})`.
- **`absorb(Theory)`** — the wholesale entry: family read from the atoms,
  an absent resident seeded the same way, then the COVERING GUARD
  (`resident.theory().leq(incoming)` → the package rides through
  untouched — no meet, no re-normalization, no trials) or one meet and
  one queued `Met(family, incoming.atoms())`. The empty theory is
  success. The absorber is terminal: a ⊥ resident refuses knowledge
  rather than resurrecting.
- **`Prefix`** — a delta of bindings, mintable only by the unifier
  (`MiniKanren.unifyPrefix`, a collecting Extender — born valid, O(delta)) or
  the checked `Prefix.binding`. `resolve` is the chokepoint: the ONLY way
  substitutions grow in constraint-aware code. The routing exists for two
  coequal reasons, not one. The **veto**: every store answers the delta
  trigger before the binding stands, and any store may fail the branch (bind
  `x := 1` against a live `x ≠ 1`). The **wake**: routing through `resolve`
  is the only way the other stores hear of the binding at all — watchers
  fire, suspensions ripen, consequences cascade. A bypass skips both and
  fails SILENTLY: the package looks healthy while every other store's
  knowledge goes stale, and the cost surfaces later as wrong answers, never
  as a refusal. An inferred binding is indistinguishable from a unification.
  Pure-relational fast path: no stores and no pending suspensions → apply
  the delta, skip all machinery.
- The **agenda** holds two item kinds — `Bind(Prefix)` and `Met(family,
  atoms)` — popped one per deferred step (the fairness quantum between
  branches). A Bind revalidates its prefix against the live package (open →
  bind the walked representative, agreeing → drop, contradicting → the
  branch dies), extends once, folds every store's delta-normalize, then
  ripens suspensions touched by the bound variables. A Met carries the
  atoms that ARRIVED at a door — the statement's atom, an absorbed
  theory's atoms; the focus is caller-known arithmetic, never a computed
  diff — and folds the OWNING family's focused normalize (every other
  store answers unchanged). The shared store fold visits `Verifier`
  families LAST (§3). Run-lane goals splice after quiescence, when the
  agenda is removed.
- **Suspensions** are `(watched, ripe, body)`: parked persistently, re-examined
  when a watched chain binds, spliced through the run lane once ripe — fired
  once, forever. `ripe` is a `Predicate<Substitutions>` and MUST be monotone:
  once true, true in every derived state — adding bindings never falsifies it
  (upward-closed). Conditions about the presence of knowledge qualify
  ("ground", "both bound", "equality decided"); absence-shaped conditions are
  negation-as-failure and belong to committed choice, not here. Pending
  suspensions disqualify the fast path and block answers (the reify-time
  pend-check; the ratified crossing design for the refusal's safe subset is
  `docs/notes/suspensions-cross-as-schemas.md`).

## 3. The two planes: Theory (value) and Factor (execution)

**`Atom<F>`** is one constraint item — the unit a family accumulates and
the unit theory. Its required surface is small and lattice-free:
`getFactorClass()` (the family's nominal face), `empty()` (its constructive
face — the registration seed), `name()`, `watched()` (the held collection —
its equality is the kind's identity granularity), `rename(Renaming)`.
Everything else is DECLARED CAPABILITY, read by the kernel, never assumed:

- `Semilattice` — the atom kind knows how to digest its own slot-mates
  (same family, name, and watched surface): domains meet, same-surface
  nogood conjuncts union. A kind that cannot combine yet collides has
  broken single occupancy — it must hold the collection inside the atom.
- `Doomed` — a cheap own-semantics born-violated check, monotone under
  binding growth; the pricing layer's eager 0.
- sharp `leq` overrides — single-atom entailment beyond structural
  equality (domain containment, nogood subsumption); sound only over
  walked terms.

**`Theory<F>`** is a factor's knowledge as SYNTAX: a family-homogeneous
atom set in normal form. Every door digests: slot-mates fuse through the
Semilattice capability, then SUBSUMPTION DELETION drops every atom
strictly dominated by another. Its surface: `meet` (an index merge —
union, fuse, prune; IDENTITY-PRESERVING: when nothing moves the receiver
returns itself, so a door's no-op guard is reference equality), `leq`
(the COVERING order: every atom of the wider
theory entailed by some atom of this one — grade two of the leq tower,
`structural ⊂ covering ⊂ family-semantic`, sharp exactly as far as the
atom classes' own overrides reach, blind to conjunctive entailment by
construction), `split(vars)` (the name cut: covered ⊗ remainder = this),
`rename` (the crossing, re-digesting), the slot read (`atom(family, name,
surface)`, one hop) and the kind read (`kind(Class)`, buckets matched by
assignability). Theories are what CROSSES: keys, answers, replay and
Residues all carry theories, and the key form is
`theory.split(vars)._1.rename(canonical)` — hole-named, structurally
comparable across packages.

**`Constraint<S>`** is one family's entry in the package: the Theory paired
with its Factor. Identity is the theory ALONE (`@EqualsAndHashCode(of =
"theory")`) — a factor may keep private state, but it must be a MEMO:
reconstructible from the theory by invariant (droppability — marshal never
carries it), so two entries with one theory are one constraint regardless
of their interpreters' state. `Constraint.in` is the residence read;
`register` seats an absent family with empty knowledge.

**`Factor<S>`** is the execution face. It never HOLDS the knowledge —
every trigger is handed the theory it interprets, and a `Revision` may
swap the whole pair, factor included (today's factors happen to be
stateless singletons; that is a fact, not a requirement):

```java
Fiber<Revision> normalize(Theory<S>, Prefix, Package);   // bindings arrived — the delta trigger
Fiber<Revision> normalize(Theory<S>, LinkedHashSet<Atom<S>>, Package);
                                    // knowledge arrived — the focused trigger
// plus lifecycle: enforce (commit before reify), reify (render residue)
```

The focused trigger's contract is ONE LAW: `normalize(T, F, P) ≡
normalize(T, T.atoms(), P)` whenever the focus contains the true change —
a family may skip only what the focus cannot have touched; doing more is
always sound (the nogood family ignores the focus and verifies wholesale
by right). A store's reaction is COMPLETE: custody checks, re-examining
its own watchers of the newly bound variables (the family WALKS its terms
at examination — atoms stay as authored, and a rebound name follows its
representative through the substitution at read time; see §7 for the
measured refutation of the eager alternative), and chasing its own
cascade. The fiber return is the scheduling contract: cheap reactions are
`Fiber.done(...)`; expensive ones defer between steps (`functional`'s
`Worklist`). The hard laws are custody: a `Revision` can express at most
the store's OWN replaced pair plus payloads.

**`Verifier`** is a marker for a family that verifies its claims by TRIAL
against the rest of the package's knowledge (nogoods: sequential scratch
imposition read three ways — fail = refuted, unchanged = crossed off, new
= owed). The crossed-off reading is package EQUALITY, so a trial
presupposes a base where every value family has finished reacting to the
current trigger; the driver honors this structurally — the store fold
visits marked families after every unmarked one — and the trial base only
strips the drain machinery (`Propagation.scratch`). Queued work needs no
settling: verdicts against current knowledge are monotone, and a late
cross-off re-verifies when the queued work lands as its own trigger.
Verifiers are mutually unordered; one exists today.

The lattice family (`LatticeFactor`) declares no algebra of its own —
its cascade drains the THEORY as the lattice point, and `Theory`'s own
`Semilattice`, `PartialOrder` and `Absorbing` declarations type
`MonotoneDrain`'s termination premise. The kernel requires no algebra of
a factor; the value the fixpoint contracts over carries it.

**The 2×2 vocabulary** — two effect kinds, two speaking positions, and nothing
else crosses any boundary:

| effect | from goal position | from store position |
|---|---|---|
| **bind** | `Propagation.resolve(prefix)` | `Revision.withInferred(prefix)` |
| **search** | `Propagation.suspend(w, ripe, body)` | `Revision.withSuspend(suspension)` |
| **knowledge** | `Propagation.absorb(theory)` | *(none — meet is the driver's)* |

A store-emitted run is the degenerate always-ripe suspension. Cross-store
interaction remains one thing: bindings, through the substitution — the
blackboard. Everything else is intra-store or store↔driver scheduling.

Propagators are NAMED and VALUE-EQUAL — (family, name, watched terms),
body excluded; the name must uniquely determine the semantics, duplicate
posts merge (idempotent re-posting made structural), and renamed instances
compare equal, which is what recursion's entry-sharing rides on.
Participation in tabled calls requires nothing beyond the theory: every
resident family crosses, because every atom renames.

## 4. The lattice family's toolkit

`lattice` owns the propagator toolkit — the driver never sees it:

- **`Propagator`** — an abstract class, postable by construction: the only
  instance state is the watched terms; identity is final (family, name,
  watched); the body reads its variables POSITIONALLY through the watched
  terms, never through lexical capture, so the schema re-instantiates over
  other terms (`watching`) — how a carried coupling replays. FD's
  relations are schema classes (`LeqO`, `AddO`, `MulO`, `SeparateO`), each
  carrying its own doom; pldb's posted lookup is one more
  (`TablePropagator` — the relation and its database ride the name).
  Replay is a RENAMING, never an aliasing: alias-replay was variable
  capture and is retired.
- **`Verdict`** — the body's lifecycle ruling: `fail | keep | subsumed |
  update(f)`. `keep` is the default-safe case (forgetting to re-park is
  unwritable). `update`'s `f : (Package, Store) → Update` is applied by the
  OWNING store to its own factor.
- **`Update`** — the intra-store step algebra: `fail | unchanged |
  applied(factor) + withInferred + withReexamine + withSuspend-shaped runs`.
  `withReexamine` terms feed the owner's worklist and NEVER reach the driver —
  the type makes leaking unrepresentable. `DomainUpdate` is cKanren's
  process-δ as `Update` steps (membership / intersect / the equal-domain
  guard — the termination guard of wake-on-narrowing / singleton collapse as
  an inferred prefix).
- The cascade: pop a term, run its watchers, `consume` each step (factor into
  a local, payloads accumulated, reexamine onto the queue), until dry or dead.

Suspension conditions in a store's own language (domain-shaped ripeness —
adaptive labelling, guarded statement, prune-to-enumerate handovers) are
propagators whose updates emit suspensions: private trigger, same lane.

The doors are symmetric: both statement and absorption queue the same
`Met` item, the family's routing is one code path, and a singleton meet
collapses to its binding eagerly at either door (the earlier stated/absorb
asymmetry dissolved with the doors' merge — ruled out August 2026).

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
touches the JVM stack. The planned `substitutions-migration.md` step C gave
unify and `members` a shared kind-tagged `decompose`.

## 6. Contracts (the ones types cannot say)

1. **Contraction**: store updates only shrink knowledge; `DomainUpdate`
   guarantees it for domains. This is the drain's termination argument.
2. **Finite height**: the descent terminates only over a well-founded
   lattice; a family whose values admit infinite descending chains (string
   domains are the known future case) must bound them or accept budgeted,
   fixpoint-free propagation.
3. **Ripeness monotonicity**: see §2 — spelled out on `Suspension`.
4. **Facts immutable per solve**: no assert/retract; tabling and any future
   constrain-mode queries assume snapshots (monotonicity engine-wide).
5. **Capability honesty**: `Doomed` may never claim a failure later
   knowledge could lift; sharp `leq` overrides answer false over open
   terms; a declared Semilattice fusion is the kind's own normal form.
6. Everything else IS types: prefix validity, custody, reexamine locality,
   substitution scoping.

## 7. Lineage (why it is this way)

The shape was reached by subtraction, each step killing a live bug class or a
channel with zero users: rule-enforced chokepoint → `Prefix` born valid;
store hooks that could clobber the world → `Revision` custody; the propagator
protocol at the driver boundary → demoted to the family toolkit when the
narrow audit found zero cross-domain traffic; the `narrowed` broadcast →
abolished when its two event kinds separated; store-emitted runs → subsumed
by suspensions before ever shipping a user; Neq → subsumed by
`NogoodConstraints` (disequality is the one-literal nogood, #121); per-kind
`posting()` and the registration lambdas → collapsed into the one generic
`activate(Atom)` when `empty()` and `Doomed` moved onto atoms; per-atom
restate → rejected for wholesale absorb when the ring-closure pin priced it
(+26%); the Factor interface's algebra → shed when the census found its only
live consumer was the lattice family's own cascade (the family now declares
it; the shed also surfaced and fixed absorb's missing terminal-⊥ guard);
the stateful factor → the Constraint pair (#137: knowledge outside the
factor, identity the theory alone, factor state demoted to reconstructible
memo); the three trigger rows → two (#138's kept half: `Stated` and
`Absorbed` merged into `Met` with the focused trigger and its one law,
`stated()` died, the verifier fold moved structurally last — retiring the
trial's settled() call — and the stated/absorb collapse asymmetry
dissolved).
Deliberately foreclosed (do not resurrect without new evidence): the
data-shaped Neq→FD bridge as store coupling (its legitimate successor is a
cross-theory rewriter at the solve seam — pair-note territory), cross-store
narrowing vocabulary, engine-level fixpoint unification
(`fixpoint-machine.md` §4/§9), suspensions as store or theory citizens (the
driver treats them specially for agenda-fixpoint safety — ruled August
2026), and **rename-on-bind** (#138's reverted half, August 2026): keeping
theory atoms textually renamed to representatives at every bind — so
families could trust keys without walking — was built through review and
REVERTED on measurement. Rewriting every watcher of a bound var into
persistent maps at each bind cost ~2.5× wall-clock on the genesis fixture
(corelogic-bench packed lane, all answers, steps identical), where walking
at read was already correct for the dead-alias case the rename was
scaffolding for: names only die at var-var aliasing, and every family
examination resolves its terms through the substitution anyway. The
watchers index the rename needed cost a further ~20% in per-digestion
maintenance with no other reader, and died with it. Both are archived on
branch `one-door-rename-lens` with their receipts; if watcher-wake cost
ever matters at population scale, the shape that composes with
walk-at-read is the migrating id-bucket index
(`docs/notes/propagator-index.md`), not this one. Still deferred:
representation swaps (`substitutions-migration.md` §5, benchmark-gated).

The composition model in two rules, kept from the capability design: (B) domains
couple to shared concepts, never to each other by name; (C) custody transfer,
never information loss — a store may drop its record only when the information
is preserved elsewhere (which, post-bridge-drop, means: in the substitution).
