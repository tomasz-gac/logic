# Watched revise removes the measured waste share of the disjunction store's carrying cost

- **status**: parked (August 2026 — shelved by the human after the
  census; no honing without a payoff in evidence, per
  disjunction-store-pays-in-products' accounting)
- **evidence held**: measurement — the skip census (Aug 2026): 44.3%
  of disjunct folds in the dense race at five, 72.8% in the job shop
  3×3, were variable-disjoint from the triggering prefix; plus the
  profiler decomposition locating verification at ~64–80% of the
  resident lane's steps, 97% of it Bind-triggered.
- **imports**: two watched literals ⋯import (SAT's quiescent-clause
  skip, Chaff 2001; travels with the unit-propagation glossary entry —
  receipt owed if built); revise, Watches (glossary).
- **obligations**: (1) the custody law FIRST, failing against a naive
  watch set, before any implementation — see the trap below; (2) the
  sufficiency law in the imposition-law kit (#116's family): an
  untouched disjunct's verdict cannot change; (3) census fractions as
  benchmark assertions (fold counts drop ≥44%/≥73%) and the step pins
  before/after as the real speedup receipt; (4) chaos-harness
  order-independence — watched wakes change delivery order.
- **links**: disjunction-store-pays-in-products.md (parks this on its
  trigger; holds the 2021 benchmark and the accounting), nogood-store
  §9 (the same mitigation on the negative side),
  negation-over-finite-goals.md (the De Morgan dual — restating
  non-overlap as a nogood is the orthogonal lever: it halves what one
  ask costs, watching changes how often you ask).

The store's `revise` is wholesale by design — "normalize by another
trigger" — so every binding that reaches the store re-folds every live
disjunct, and each fold re-trials each alternative from zero knowledge:
a scratch imposition plus a drain, ~33 steps, to conclude — usually —
"still undecided". The idea: give each disjunct a watch set (its
still-open variable representatives) and fold on revise only the
disjuncts whose watch set intersects the prefix; carry the rest through
with their verdicts standing. A revise touching nothing answers
`Revision.unchanged()` outright, skipping the settle too. The census
measured the ceiling of this filter directly, by var-disjointness at
the Bind item against the pre-extension package: 44% of folds skippable
in the dense race (where discharge already shrinks the store to ~2.4
live disjuncts per revise — the survivors correlate with the active
region), 73% in the sparser job shop, fraction growing with problem
size and constraint sparsity. Expected uninstrumented step cut: ~20–25%
on the race at five, roughly a third on job-shop shapes (three-factor
arithmetic: skip fraction × per-disjunct share of verification (~75%)
× verification share of total (60–80%)). A free rider needing no
watcher machinery at all: `stated` re-folds everything when only the
stated disjunct is new — 82% of statement-time folds skip by narrowing
`stated` to its own item.

What it does NOT buy: the race at five stays conde's — the cut takes
residence from ~4× to ~3× there. The case is the scaling curve and the
store's real workloads (the job-shop pole it already wins; pldb
composition if that materializes), not the small dense benchmark.

The trap, met during the census: watch sets go stale in time. Record
that a disjunct watches representative x; an unrelated unification
merges x under y; a prefix later binds y — a naive set says "not
involved", the wake is missed, a stale verdict survives, and the wrong
answer surfaces far from the cause. This is exactly the custody half of
the revise contract, and the census itself dodged it only by measuring
at Bind with the pre-extension package (post-extension, walking erases
the touched names). The implementation must ride the shared `Watches`
chain matcher — custody re-keys before the intersection is read — which
is FD's existing discipline, adopted, not new machinery. The change is
otherwise confined: one store (the protocol already fits — revise
receives the prefix it currently drops; absorb already declares watched
surfaces), ~60–100 main lines, tests the larger half.

Cheapest kill: a profile of the store's winning workload (job-shop
pole) showing per-disjunct fold work is NOT the dominant verification
term there — then the filter optimizes the losing benchmark only, and
the shelf is permanent.
