# The package holds Class → Constraint{Theory, Factor}: knowledge outside, behavior beside it

- **status**: argued (the human's ruling, Aug 2026, from the cascade
  evidence; execution is the next arc's charter, not this one's)
- **evidence held**: demonstration — deleting Semilattice from Factor
  broke `MonotoneDrain.drainUnsafe`'s bound at the cascade call site:
  the drain's termination theorem (contraction on a finite-height
  lattice) types its premise, and the THEORY is the lawful citizen the
  factor was satisfying it with. Plus two hot-loop `theory()` calls
  (cascade containment, value reads) each assuming held-wholesale O(1)
  — the implicit contract this makes structural. NogoodConstraints'
  `rename` is already pure delegation to theory rename — dead weight
  witnessing the same fact.
- **imports**: none new; Constraint (the pair) is a NEW coinage owed a
  glossary entry at ratification — it collides with the retired
  ckanren-era "Constraint" and the ruling must retire-or-distinguish,
  never alias.
- **obligations**: (1) the Revision protocol cut: Revision returns the
  updated THEORY, not a replacement factor; the memo (family-private,
  reconstructible, cache = f(theory)) rides the Factor half of the
  pair, swapped copy-on-write as today; (2) Theory implements Absorbing
  by a digest-time capability read (atom instanceof Absorbing —
  Imposition declares it by its value's own absorption): the ⊥-scan
  LatticeFactor.absorb already runs, moved to digestion, cached; (3)
  the cascade drains the theory under its own lawful bound — the
  MonotoneDrain signature is NOT touched; (4) crossings become pure
  theory renames end to end and `rename` dies as a factor method; the
  spent-drop (entries whose name resolved to a value) relocates to the
  consumer's wholesale normalize, which already verifies ground-keyed
  entries — CANARIES: answer-dedup and key pins, because answers carry
  ground-keyed atoms until consumption where they used to shed them at
  capture; fallback if the pins move: a family-owned settle pass at
  capture, honestly named, still not rename; (5) the family signature
  sweep: normalize×2, stated, enforce, reify all take the theory;
  meet(Atom), isEmpty, rename, theory() deleted from Factor.
- **links**: execution-plane.md (this REVISES its factor story — the
  doors stay, the state moves), propagator-index.md (the memo slot's
  first paying customer: a var→watchers index that must survive between
  normalize calls), watched-revise.md (the custody trap any memo index
  inherits).

The package's constraint entry becomes a two-item wrapper:
Constraint{Theory, Factor}. The Theory is the knowledge — the lattice
citizen with the laws, the thing the cascade steps, the thing crossings
rename, the thing keys and answers carry. The Factor is the family's
execution behavior plus its private memo, and it no longer HOLDS the
knowledge: normalize, stated, enforce and reify receive the theory as
an argument and return its successor through Revision. Factors keep
their own state and are separated from the state that matters.

What forced it, in one sentence each: the drain's termination proof is
a lattice theorem and the theory is the lattice, so stepping the
factor was stepping a proxy; the hot paths already treat theory() as a
free read, which is an undeclared invariant this design declares; and
the factor surface left after F3 and G-lite — meet(Atom), isEmpty,
rename, theory() — consists entirely of methods that either delegate
to the theory or exist to reach it.

What it does NOT change: the three doors and the chokepoint (resolve /
activate / absorb speak exactly as they do — absorb already takes a
theory); the capability doctrine (Doomed, Semilattice-fusion, sharp
leq all stay atom-declared, kernel-read); suspensions stay the
driver's citizens, untouched; the stated-vs-absorb singleton
divergence is unaffected and still awaits its own ruling.

Cheapest kill: a family whose normalize cannot be expressed over
(theory in, theory out) — state that is knowledge but cannot be atoms.
The pldb TablePropagator repair is the standing acid test: if the
table family's polled supports and source handle fit as (theory,
memo), the separation holds on the least lattice-like family we have.
