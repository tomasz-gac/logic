# Couplings are syntactic citizens of every order; a difference store would naturalize them

- **status**: observation over shipped code (Aug 2026, the human's
  transitivity probe during the fast-path discussion); the fix is a
  trigger-gated future store, not scheduled.
- **evidence held**: code — `LatticeStore.leq` is pointwise value-leq
  AND `propagators.containsAll(...)`, with `Propagator` equality
  declared over (storeClass, name, watchedTerms): name + args, body
  never consulted. The human's witness: resident `a<b` and `b<c`
  entail `a<c`, and no order in the engine can see it — the entailed
  coupling is a different (name, args) atom, incomparable. Measured
  corollary: the scheduling benchmark's losing pole
  (disjunction-store-pays-in-products § the verdict) — coupling
  literals cannot classify through the store order, forcing the
  scratch-imposition trial.
- **imports**: none for the observation; ⋯import if the fix is built:
  difference constraints / simple temporal networks (Dechter–Meiri–
  Pearl STN; entailment = shortest-path/transitive-closure queries) —
  receipt owed then.
- **obligations**: none live — the observation commits to nothing.
  If the difference store is built: entries are `a − b ≤ k` edges,
  leq/entailment = reachability, refutation = a negative cycle; enters
  the store family by the uniform boundary like every store, laws
  first (#116 pattern).
- **links**: lattice-three-way.md (the trial-verdicts-are-order-theory
  observation this bounds), tabled-constraints.md (the deliberate
  floor: "entailment matching with named value-equal couplings"),
  disjunction-store-pays-in-products.md (the measured pole),
  list-constraints.md (the sibling trigger-gated store note —
  scheduling wants both).

## The observation

The engine's orders see VALUES semantically and COUPLINGS
syntactically. A domain entry compares by content (`D₁ ⊆ D₂`); a
propagator compares as a (name, args) atom — the same coupling is
recognized, an entailed-but-different coupling is invisible. One
ceiling, three seats:

1. **The comparison fast path** (impose-on-empty, the human's
   capability-free trick): classifies value-shaped literals perfectly
   through the order; cannot classify coupling literals at all —
   their factor-store is an atom the resident never contains. The
   repair inside the frame: the coupling's own propagator already
   computes a Verdict against resident domains — run it without the
   update. Store-internal, custody-clean, but per-theory code, not
   order structure.
2. **Condition's absorption**: `Residues.leq` folds factor-leq, so
   the antichain is minimal only up to coupling syntax — a region
   carrying `a<c` is not absorbed by one carrying `a<b ∧ b<c`.
3. **TCLP entailment matching**: entries share only between calls
   whose couplings match name-and-value-equal — the tabled-constraints
   doc chose this floor deliberately; this note records that it is a
   floor, not a ceiling proof.

The direction of every miss is CONSERVATIVE: a redundant DNF region
(cost, never wrong answers), a lost cache share, an owed that stays
owed until the ground floor. The engine under-shares and
under-compresses on couplings; it never lies about them.

## The fix, when a trigger fires

Theory reasoning over ordering couplings IS a store: the difference
store (STN shape). Entries `a − b ≤ k`; order and entailment are graph
reachability (the human's `a<b<c ⟹ a<c` is literally transitive
closure); refutation is a negative cycle; bounds propagation and
edge-finding — what scheduling's contested clauses actually need —
are its native operations. One store lifts all three seats at once:
coupling literals become value-like citizens of leq, Condition's
absorption tightens, TCLP matching shares across syntactically
different but entailing temporal constraints.

TRIGGER: the comparison-fast-path arc reaching coupling
classification with the benchmark's losing pole as its pin; or a real
scheduling workload needing bounds reasoning (list-constraints.md's
trigger family); or TCLP sharing measurably lost to coupling syntax.
