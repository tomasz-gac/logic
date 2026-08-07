# The bindings factor should speak the crossing language

- **status**: TITLE CLAIM KILLED (Aug 6) — see compression-of-what.md.
  What survives on unknown-names: the three Residues doors (about/all/
  restate) in the pair form, and the polarity argument (keys may widen,
  answers may not drop). The bindings factor does not join the factor
  map; it renders as the reified anchor.
- **evidence held**: derivation; API surface; tabling rewrite sketch; one
  counterexample (the x<z<y coupling) pinning the two-method polarity
- **imports**: Projectable, Absorbable, Renaming, the crossings, Hole,
  chokepoint; answers-as-diffs (shelved). Names adopted in conversation,
  glossary entries owed on graduation: Crossing, about/all.
- **obligations**:
  1. DONE for stores (Crossing extracted, Projectable extends it).
     BLOCKED for Substitutions: `Hole implements Reified`, not `LVar`, so a
     canonically-renamed binding map is not a `Substitutions` — the type is
     not CLOSED under `rename`, and `Crossing<S>` requires closure. The
     literal instance waits on Term-keyed bindings (the
     substitutions-migration direction). The doors were built without it:
     the bindings factor's crossing internals remain reifyWithHoles /
     instantiateWithHoles, now called ONLY from inside Residues.
  2. DONE: `Residues.about/all/restate` over the term anchor, in the PAIR
     form (image, factors) — the fallback this note itself sanctions while
     obligation 4 is open. `ofRelevant`/`ofAll` went private; Tabling
     mentions no Renaming, no reify/instantiate, no unifyArgs. Zero behavior
     change: 561 green, stress 400.
  3. OPEN: re-key `Call` and `SubsumptionMap` off the key's bindings image,
     retiring the term-beside-the-residues shape — sensible only after
     obligation 4 decides whether the image joins the value.
  4. Decide the ⊗ gap: `Residues.meet` over two bindings factors is
     unification, and a clash has no ⊤ value in Substitutions — absorbing
     FALSE Residues, or partial meet.
  5. Naming: is the encompassing value still "Residues" once it holds the
     bindings factor? It is now the whole conditional answer.
- **links**: docs/reference/condition.md §0, docs/shelved/answers-as-diffs.md,
  docs/design/note-store.md, docs/design/domain-layer.md (conditional answers),
  #88 (weight capture rides the complete crossing), #90 (locals' frame),
  #111 (Query front door)

## The claim

The package's oldest factor has private crossing doors under private names:
`reifyWithHoles` is "project the bindings factor onto this anchor, in slot
names" and `instantiateWithHoles` is its minting restate — while every
constraint store crosses through the named family. Give the bindings factor
the DEPARTURE capability (`Crossing`: split + rename) and the four operations
now stranded in Tabling collapse into two, living where they belong:

```java
Residues.about(world, anchor)   // IN, narrow: JUST what world knows about the
                                // anchor's vars — remainder split away; the key citizen
Residues.all(world, anchor)     // IN, complete: EVERYTHING world knows — locals
                                // ride under their own names as ∃ witnesses
residues.restate(anchor)        // OUT: slots land on the target anchor by
                                // unification-and-absorption; locals mint fresh (∃)
```

The anchor is a term. On the way in its free vars (first-occurrence order)
become the slots — the knowledge's PORTS; on the way out it is the receiving
structure, so partially ground targets come free through unification. In
`about` the anchor both selects and names; in `all` it only names — extraction
is total, and the anchor declares which names reconnect versus which are
∃-bound. (`all(world, ∅)` is legal and nearly useless: a fully closed
sentence, restatable only as a disconnected constraint block.)

## The wall, located

July rejected Substitutions-as-a-STORE, and the hierarchy shows exactly why
the rejection stands and exactly what it does not block:
`Projectable extends Absorbable extends ConstraintStore`. The bindings factor
must not be a ConstraintStore (it IS the state stores normalize against), and
must not gain an arrival path through `Propagation.absorb` — the chokepoint
contract says all bindings growth passes `resolve`. Both objections are about
ARRIVAL and RESIDENCE. Departure — split and rename — touches neither. The
`Crossing` cut takes only what the crossings use.

The asymmetry does not vanish; it moves into one place. `restate` dispatches
by factor kind: private factors arrive by `Propagation.absorb`, the shared
factor arrives by UNIFICATION through the chokepoint — which is what
delivery's unify-then-restate pair has been doing all along, spelled as two
unrelated steps. How a factor arrives is a fact about the factor; Residues
owning that dispatch is the encapsulation, not a leak in it.

## Why two entry methods (the polarity)

`about` and `all` are the two sound directions of approximation, not two
conveniences. Keys may WIDEN: a too-general entry is a coarser filter,
re-verified at consumption — so `about` may split knowledge away. Answers may
not drop anything: an answer is a claim, and dropping over-claims. The merge
("`about` + prune the rest to minimal form") is existential quantifier
elimination, and the store languages are not closed under it: capture
`x < z, z < y, z ∈ 1..10` about {x, y} and the true projection contains
`x < y − 1`, which FD boxes cannot express. Pruning to the expressible part
admits x=7, y=3 — a pair with NO witness — served from cache as a wrong
answer. Carrying the witness costs comparability (locals are conservatively
incomparable, #90); dropping it costs soundness. That is the whole trade.

Sound minimization ON `all` (future work, not blocking): spent entries drop
already; unary islands (a local with only its own non-empty domain) are
trivially satisfiable and droppable; multi-var islands are droppable iff
satisfiable — a capture-time labelling, paying search once to shrink every
future comparison; in-language elimination where a store CAN express a
local's projection is an optional capability rung, never a Crossing promise.

## Tabling as the first customer

```java
Residues.about(callerPkg, anchor).flatMap(key -> {
    Call callKey = Call.of(relation, key);            // term dissolved into the key
    ...
    Goal seeded = Conjunction.of(key.restate(anchor), body.get());
    ...
})
// produce:   Residues.all(answerPkg, anchor) → cell key = answer.image()
// delivery:  answer.restate(reader.getArgsTerm())   — ONE imposition
```

Dissolved from Tabling: both `reifyWithHoles` calls, the var↦hole map and its
hand inversion, `instantiateWithHoles`, `unifyArgs` (it IS the bindings
factor's imposition), `replayMint`, and every mention of `Renaming`. What
remains is genuinely tabling: Table, entries, channels, pricing, suspension
guards, the strip, the consume/seal protocol. The file stops knowing HOW
knowledge crosses and only knows WHEN.

Flags carried into the build:
- Seeding's `key.restate(anchor)` is uniform but slightly redundant: the body
  package inherits caller substitutions, so the bindings half re-unifies
  already-bound args (idempotent; one fresh-var alias per unbound arg). Kept
  uniform — an asymmetric skip-the-shared-factor restate would poke a hole in
  the abstraction to save pennies. The radical alternative (a bindings-clean
  body world built entirely by the key — "the body runs from the key" made
  literal) changes package derivation and the Table-transport canary; it is a
  separate decision, not part of this claim.
- `isTrue()` fast paths re-read as "store factors empty" internally.

## What falls out

The cell's key/value split becomes a theorem instead of a choice: the JoinMap
keys by the SHARED factor's image (the reified term), and the private factors
ride the value. And everything above the Table layer — Query front door
(#111), findall over cold solves (#91's constrained aggregation), persistence
(#75: the comparable form is the marshallable unit), weight capture (#88) —
gets `about`/`all`/`restate` with no tabling in sight. This is the keeper
half of answers-as-diffs arriving from the other direction, with none of the
four walls: nothing here deduces call args.

## Built so far (Aug 5)

`Crossing` exists (split + rename, departure only); `Projectable extends
Crossing, Absorbable`. `Residues.about(world, anchor)` and `.all(world,
anchor)` return the pair `(image, factors)`; `Residues.restate(image,
factors, anchor)` is the one leaving crossing — instantiate the image, meet
the anchor by unification through the chokepoint, restate the factors onto
the same slots by shared minting. Tabling's four crossings are these three
calls; delivery and master seeding are the SAME restate (the seeding
redundancy flag held: one idempotent re-unification, kept for uniformity).
The Substitutions instance is NOT built — the key-type wall above — so the
bindings factor speaks the crossing language through Residues' internals
rather than in its own name. If Term-keyed bindings ever land, the instance
becomes honest and obligation 3 follows.

Cheapest kill: obligation 4 — if the bindings factor cannot join the
⊗-monoid without breaking the ring's algebra (no lawful FALSE), the
encapsulation stops at "Residues plus a term on the side," and this note
records why the term must stay a separate citizen.
