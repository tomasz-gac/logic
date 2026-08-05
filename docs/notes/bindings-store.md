# The chokepoint is the agenda: substitutions may be a constraint store

- **status**: argued; PARTIALLY BUILT (Aug 5, branch renaming-simplified):
  resolve died into activate(Prefix), Bind.apply routes through the
  bindings factor's own examination (Substitutions.extended — the
  trichotomy and the own-factor extension). NOT built: the ConstraintStore
  interface conformance — DISCOVERED WALL: ConstraintStore lives in
  constraints.store and references Package and Goal, so the base
  unification layer implementing it inverts the layering (a package
  cycle). Options for the human: accept the cycle; a store-shaped view of
  the bindings factor living in constraints.store; or defer conformance to
  the residence decision (obligation 3). The operational reroute needed
  none of them.
- **evidence held**: the receipt that `Agenda.Bind` ALREADY EXISTS —
  `resolve`'s whole body is: empty-prefix no-op, pure-relational fast path,
  `enqueue(new Agenda.Bind(prefix))`. Unification already rides the agenda;
  `resolve` is a courtesy name on the door, not a separate mechanism. The
  July wall's arrival argument ("bindings have a privileged path, therefore
  not a store") was already half-false in the shipped code.
- **imports**: chokepoint, Agenda, Prefix, Revision, ConstraintStore,
  Absorbable, absorbing ⊥ (LatticeStore's dead-store representative)
- **obligations**:
  1. Reroute the drain: express the Bind handler as the bindings store's
     REVISION — apply the prefix as an own-factor swap — with the fan-out to
     other stores and suspension ripening as that revision's consequences.
     Zero behavior change; the chaos suite is the detector for accidental
     ordering drift.
  2. Keep the pure-relational fast path (every unification in a
     constraint-free program hits it) and pin it with a perf guard (#109's
     step counts).
  3. Decide residence separately from capability: Substitutions can
     IMPLEMENT ConstraintStore while remaining the Package field every
     `revise(prefix, state)` reads — capability without tile-ization. Full
     residence (into the store map) reworks `state.substitution()` readers
     and the `withSubstitutions` landmine; it is not required for any buy
     listed below.
  4. Fill in the store surface: `revise` = apply own factor; `normalize` =
     already clean; `enforce` = success; `reify` = term rendering — the
     operation finally living where it always conceptually belonged.
  5. On ratification, revise the July package-layout ruling (and its
     memory): the rejection dies on capability, may stand on residence.
  6. Then unlock substitutions-projectable obligations 3 and 4 — see the
     buys.
- **links**: docs/notes/substitutions-projectable.md (obligations 3, 4),
  docs/notes/unknown-name-type.md (composes: that note gives departure,
  this one gives arrival — neither depends on the other),
  docs/reference/constraint-kernel.md (the trigger family this completes)

## The claim

Unification is statement. `Propagation.activate` parks an item and queues
its first examination; `Propagation.resolve` — read honestly — parks a
Prefix and queues its examination. The only differences are the item type
and the routing breadth. So let the bindings factor be a ConstraintStore
whose statement kind is Prefix: unification enqueues `Agenda.Bind`, the
drain hands it to the bindings store as a revision (the prefix applied as
an OWN-FACTOR swap), and the fan-out to every other store plus suspension
ripening are that revision's consequences — exactly how an FD narrowing
cascades to its watchers. `resolve` dies as a special entry; what survives
of it is one branch (the pure-relational fast path) and one routing rule.

Prefix privilege is untouched — this note moves nothing about WHO may mint
a binding delta (the unifier, `Prefix.binding`), only about how a minted
delta ARRIVES. Arrival becomes uniform; minting stays sacred. (The
companion note argues privilege always belonged to the delta, not the
container.)

## What unifications it buys

1. **The trigger family closes.** Three entries — stated (item), absorbed
   (factor), resolved (bindings) — become one family with one story: a
   piece of knowledge arrives, its owning store examines it, consequences
   cascade through the drain. The last special case in the kernel's
   vocabulary dissolves; "stores may swap only their OWN factor" now
   covers substitution growth itself, because substitution growth IS the
   bindings store's factor swap.
2. **reify finds its home.** Term rendering is the bindings store's
   `reify` — the store-capability reading of reification stops being an
   analogy and becomes the interface.
3. **The lawful FALSE.** As a ConstraintStore, the bindings factor
   inherits the absorbing-⊥ convention (the dead-store representative).
   That is exactly the clash representative substitutions-projectable's
   obligation 4 was missing: `Residues.meet` over two bindings factors
   (unification) gets a total answer, and the bindings factor can join
   the ⊗-monoid lawfully — unblocking the image-into-the-value merge and
   obligation 3's Call re-key behind it.
4. **Full Projectable comes into reach.** With unknown-name-type giving
   departure (`Crossing`: rename closed over the widened key) and this
   note giving arrival + storehood, the bindings factor can carry the
   whole `Projectable` bundle honestly — the substitutions-projectable
   title claim, assembled from two independent halves.
5. **One less lie in the docs.** "Bindings route through resolve, stores
   route through the agenda" becomes "everything routes through the
   agenda" — the sentence the architecture screen has been approximating.

## What stays special, honestly

Routing breadth: Stated and Absorbed route to the owning store; Bind wakes
every store and ripens suspensions. That case does not vanish — it moves
from "bindings are magic" to "the SHARED factor's revisions wake everyone,"
a statement about the factor, not the driver. The drain keeps exactly one
special case either way; this note relocates whose fact it is. The
StoreSupport discipline survives verbatim: the prefix is applied exactly
once, by its owner — which is now a sentence the store contract itself
enforces, instead of a rule about a privileged method.

Cheapest kill: obligation 1's zero-behavior-change bar. If expressing Bind
as a revision observably reorders propagation (chaos seeds disagree), or
the fast path cannot be preserved without keeping resolve-shaped special
casing that leaves the entry family no more uniform than today, the
reframing is cosmetic and the note records that the wall, while thinner
than July claimed, is load-bearing at exactly one point: the fan-out.
