# The constraint kernel

**Status: AUTHORITATIVE (August 2026). This describes the engine as shipped
at the end of the Factor/Theory/Atom migration (branch `constraint`) — the
value plane (Theory, Atom), the execution plane (Factor), and the doors
between them. The superseded design docs (constraint-propagation,
capability-constraint-api, minimal-constraint-vocabulary, suspensions) were
deleted; their reasoning survives in §7's lineage, the commit history, and
the still-live companions: `fixpoint-machine.md` (the two-fixpoint model),
`tabled-constraints.md` (the tabling merge, priced),
`substitutions-migration.md` (the remaining step), and
`docs/notes/constraint-pairs-theory-with-factor.md` (the ratified NEXT
shape: knowledge outside the factor — not yet built).**

---

## 1. State: the package is a product

A `Package` is the immutable solver state, a product of factors:

- **`Substitutions`** — the SHARED factor: the monotonically growing log of
  bindings. It has a read-only view type (`unification.Substitutions`) with no
  route to any store; code typed against it is structurally scoped to shared
  knowledge.
- **Constraint store factors** — each `Factor` owns one slice
  (`FiniteDomainConstraints`, `NogoodConstraints`), private to its family.
  A factor's knowledge is its **Theory** — the set of atoms that state it,
  held as a value (§3).
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
  prices 1.
- **`absorb(Theory)`** — the wholesale entry: family read from the atoms,
  an absent resident seeded the same way, then the COVERING GUARD
  (`resident.theory().leq(incoming)` → the package rides through
  untouched — no meet, no re-normalization, no trials) or one meet and
  one queued normalize. The empty theory is success. The absorber is
  terminal: a ⊥ resident refuses knowledge rather than resurrecting.
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
- The **agenda** holds three item kinds — `Bind(Prefix)`, `Stated(Atom)`,
  `Absorbed(family)` — popped one per deferred step (the fairness quantum
  between branches). A Bind revalidates its prefix against the live package
  (open → bind the walked representative, agreeing → drop, contradicting →
  the branch dies), extends once, folds every store's delta-normalize, then
  ripens suspensions touched by the bound variables. Run-lane goals splice
  after quiescence, when the agenda is removed.
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
union, fuse, prune), `leq` (the COVERING order: every atom of the wider
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

**`Factor<S>`** is the execution face and carries no algebra of its own:

```java
Fiber<Revision> normalize(Prefix, Package);  // bindings arrived — the delta trigger
Fiber<Revision> stated(Atom, Package);       // your item was stated — owner only
Fiber<Revision> normalize(Package);          // a theory was met in — wholesale
// plus lifecycle: enforce (commit before reify), reify (render residue)
// plus: meet(Atom) — the statement park; theory(); absorb(Theory); rename; isEmpty
```

A store's reaction is COMPLETE: custody checks, re-examining its own watchers
of the newly bound variables, and chasing its own cascade. The fiber return is
the scheduling contract: cheap reactions are `Fiber.done(...)`; expensive ones
defer between steps (`functional`'s `Worklist`). The hard laws are custody: a
`Revision` can express at most the store's OWN replaced factor plus payloads.
The lattice family (`LatticeFactor`) additionally declares
`Semilattice & Absorbing` FAMILY-INTERNALLY — not on the interface — because
its cascade drains the store as a lattice point and `MonotoneDrain`'s
termination theorem types that premise; the kernel requires no algebra of a
factor, the family whose fixpoint leans on the theorem declares it. (The
ratified next step moves the drained state to the theory itself —
`constraint-pairs-theory-with-factor.md`.)

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

One known door asymmetry, deliberate until ruled otherwise: the STATED
door's update routing collapses a singleton meet to its binding eagerly;
the ABSORB door's wholesale normalize skips live-var entries, so a
singleton stays resident as a domain until something touches its variable.

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
it; the shed also surfaced and fixed absorb's missing terminal-⊥ guard).
Deliberately foreclosed (do not resurrect without new evidence): the
data-shaped Neq→FD bridge as store coupling (its legitimate successor is a
cross-theory rewriter at the solve seam — pair-note territory), cross-store
narrowing vocabulary, engine-level fixpoint unification
(`fixpoint-machine.md` §4/§9), suspensions as store or theory citizens (the
driver treats them specially for agenda-fixpoint safety — ruled August
2026). Still deferred: representation swaps (`substitutions-migration.md`
§5, benchmark-gated), the Constraint pair
(`constraint-pairs-theory-with-factor.md`, the next arc's charter).

The composition model in two rules, kept from the capability design: (B) domains
couple to shared concepts, never to each other by name; (C) custody transfer,
never information loss — a store may drop its record only when the information
is preserved elsewhere (which, post-bridge-drop, means: in the substitution).
