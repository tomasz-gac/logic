# The cascade's per-trigger propagator scan is the engine's measured per-step growth

- **status**: argued (diagnosis measured, fix unbuilt; whether to build
  or park with a trigger is the human's call)
- **evidence held**: measurement — the all-answers benchmark (Aug 2026,
  corelogic-bench/RESULTS.md): per-step cost grows 0.90 → 1.37 → 2.04
  µs/step across n = 4..6 on the packed scheduling fixture, ratios
  ×1.52/×1.49 against C(n,2) ratios ×1.67/×1.50 — the propagator
  population's growth curve, not the answer set's (steps/answer grows
  only 325 → 429 → 550 over the same span). Correlational: no
  differential counter isolating the scan's wall-time share yet.
- **imports**: watch lists ⋯import (CP solvers' per-variable constraint
  indexing; receipt owed if built); revise, Watches (glossary).
- **obligations**: (1) the differential receipt FIRST — a counter of
  `watches` checks per solve showing the O(C(n,2)) sweep and its
  wall-time share, before any index is built; (2) the custody law from
  watched-revise.md, verbatim: an index keyed by watched-root goes stale
  when an unrelated unification merges roots — re-key before the lookup
  is read, failing test against a naive map first; (3) after: µs/step
  flat across n on the same fixture, step pins unchanged (the index must
  not change semantics, only lookup cost), chaos-harness
  order-independence (indexed wakes change delivery order); (4) the
  scaling ceiling documented: where the crossover against core.logic
  moves once the sweep is gone.
- **links**: watched-revise.md (the disjunction store's cousin of this
  idea and the custody trap's first sighting),
  disjunction-store-pays-in-products.md (the accounting discipline this
  answers to: no honing without a payoff in evidence).

`LatticeFactor.cascade` finds a changed term's watchers by scanning ALL
parked propagators and asking each `p.watches(live, next)` — a linear
sweep over the store's whole propagator population per queue item, each
check itself walking substitution chains. On the scheduling fixture the
population is the pairwise constraint set, O(C(n,2)), so every binding
in every branch pays a quadratic-in-n sweep to find the few watchers
that care. This is why the engine loses to core.logic above n ≈ 5:
cKanren keeps per-variable constraint lists, so its binding cost is
O(watchers-of-var) — higher constant (we win small n), flat scaling
(they win large n). The idea: index parked propagators by the root
representative of each watched term, migrate entries when roots merge,
and let revise touch only the indexed bucket.

What it buys: flat per-step cost in the constraint population — the
crossover against per-variable-indexed engines moves out or disappears.
What it does NOT buy: steps/answer (search shape) is untouched, and at
today's fixture sizes the whole tax is ~2.4× against core.logic at
n = 6 while ForkJoin buys back ~6× — this is a scaling lever, not a
present emergency. The surgery is store-internal (`LatticeFactor` owns
the scan; the chokepoint and store protocol don't move) but it is
constraint-core territory all the same.

Cheapest kill: the differential counter shows the sweep is NOT the
dominant per-step term (e.g. the growth is really in package walks or
domain ops) — then the index optimizes a minority share and the note
refutes itself.
