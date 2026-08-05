# Names get one type; privilege belongs to Prefix

- **status**: argued — derivation in conversation (Aug 5); nothing built
- **evidence held**: derivation; the seed inventory below (every crossing
  operation as one walk under one map shape); the reifyS receipt (the
  var→hole map already exists inside reification, legal today only because
  holes sit on the value side)
- **imports**: Substitutions, Prefix, chokepoint, Renaming, the crossings,
  Hole, LVar; substitutions-migration Steps A–C (kind-tagged decompose).
  The interface name is a PLACEHOLDER pending adoption: candidates Unknown /
  Var / Symbol; this note writes `Unknown`.
- **obligations**:
  1. Adopt the name (glossary entry on ratification).
  2. The asVar audit: classify every `asVar()` call site as "live var
     required" (unifier extend, occurs check, Prefix minting) or "any name"
     (walk's key chase, the renaming scans, isGround's leaf test). The audit
     IS the migration plan — and its cheapest kill.
  3. Widen the key: `Substitutions` over `Unknown<?>`, walk chasing
     `asUnknown()`; `Prefix` stays LVar-keyed. Pins: the unify guard
     (Reified never re-enters unification), the pure-relational fast path,
     the chaos suite.
  4. Collapse Renaming back onto the one engine: every crossing = walkAll
     under a seed (+ mint policy); the positional instantiate engine and the
     two-stage minting composition retire.
  5. Then reopen substitutions-projectable obligation 1: `rename` is closed
     over `Substitutions`, so `Crossing<Substitutions>` becomes honest.
- **links**: docs/notes/substitutions-projectable.md (obligation 1 is gated
  on exactly this), docs/reference/substitutions-migration.md (this is
  Step D arriving as key WIDENING for uniformity, not representation swap
  for speed), the agenda/Bind idea (separate note if commissioned —
  composes with this one, neither depends on the other)

## The claim

`LVar` and `Hole` are the same kind of thing — a NAME for something not yet
determined — distinguished only by which world names it: live (identity) or
canonical (position). Give them one supertype, `Unknown<T> extends Term<T>`,
and key `Substitutions` by it. Two consequences:

**One engine.** Every crossing operation becomes walkAll under an
`Unknown→Term` seed:

    canonical    seed: live var → hole      (reifyS already BUILDS this map)
    restating    seed: hole → target        (today needs the second engine)
    resolution   seed: var → walked meaning (already is one)
    minting      the same walk, miss policy: record a fresh var

The two-engine braid — vars ride walkAll because Substitutions can key
them, holes ride positional instantiate because it cannot — was never a
fact about renaming; it was a fact about the key type. Widen the key and
SlotRenaming's engine dies (with its padding workaround — unlisted names
simply do not walk), Minting's two-stage composition becomes one pass,
VarRenaming's identity-entry filter dies, and `reifyWithHoles` /
`instantiateWithHoles` become seeds-plus-walk instead of bespoke
traversals. Renaming ends where it was ruled to be: a dumb map — literally
the map type the engine already walks — plus one flag.

**Privilege moves to where it belonged.** The July wall degrades to this:
what is privileged is not the substitution CONTAINER but the binding
DELTA. `Prefix` stays LVar-keyed — a live-world delta, holes cannot appear
in one — and stays mintable only by the unifier. The chokepoint theorem
restates cleanly: substitution growth = Prefix arrival; Prefixes are
minted only by unification. Privilege attaches to who may CLAIM new
knowledge, not to where knowledge sits. Substitutions itself becomes an
ordinary map over names — which is what the crossings needed it to be.

## What must NOT change (the pins)

The unifier binds only live vars: `extend` keeps its LVar signature, the
"Reified terms cannot re-enter unification" guard stays, `occursCheck`
stays live-world. `Unknown` makes holes WALKABLE, never UNIFIABLE — a hole
reaching unify in a live package is still the namespace-mixing bug it is
today. `resolve`'s pure-relational fast path is untouched; walk's hot loop
swaps `asVar()` for `asUnknown()` at the key-chase sites only — same
dispatch shape.

## Cost inventory

`Substitutions`' API surface (extend, binding, walk, bindings(), isGround)
and its test surface; `Term.asUnknown()` (default none, two overrides);
the asVar audit (obligation 2 — the one place a conceptual error could
hide); and one discipline worth a javadoc sentence: Hole equality is by
number, so seeds from different slot namespaces must never mix in one map
— the same per-crossing rule that exists today, now stated where the keys
live.

## What it does NOT buy

Arrival. This note makes the bindings factor CROSSABLE in its own name; it
says nothing about how bindings arrive (the agenda/Bind reframing is its
own idea: the Bind handler as the bindings store's revision, resolve
dissolving into the uniform entries). The two compose — key widening gives
Crossing, agenda-residence gives Absorbable-hood and the absorbing ⊥ that
substitutions-projectable's obligation 4 wants — but neither needs the
other, and each dies on its own evidence.

Cheapest kill: the asVar audit (obligation 2) finds a site where LVar-only
keying is load-bearing — a place that MEANS "live var" but would silently
accept a hole once the types allow it, with no guard expressible. Second
kill: walk-hot-path regression on the perf pins (#109's step counts).
