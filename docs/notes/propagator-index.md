# A watcher index must migrate buckets, not rewrite atoms — and today it isn't worth building at all

The idea, corrected by measurement: finding a changed term's watchers by
scanning all parked propagators (`p.watches(live, next)` per queue item)
is quadratic in the constraint population, and an index by watched name
would make it a lookup — but the index's DESIGN decides whether it can be
cheap. An index **derived from the atoms' watched surfaces** (a digestion
product, like slots and kinds) can only stay truthful if the atoms
themselves stay textually renamed to representatives — which forces
rewriting every watcher of a bound var into persistent maps at every
bind. That trade was built and measured (the one-door arc, Aug 2026):
the rewrite machinery cost ~2.5× wall-clock on the genesis fixture while
the scan it replaced cost nearly nothing at reachable populations. The
shape that survives is core.logic's: an index that is **first-class
state, not a projection** — buckets of atom identities keyed by root
name, maintained by bucket MIGRATION at var-var aliasing only (one
id-set union, one entry drop; value binds maintain nothing — a bound
name never rebinds, its bucket is consumed at that trigger and stale
entries clean up at discharge), with every read, index lookup included,
walking to the root first. It composes with walk-at-read instead of
fighting it.

- **status**: refuted as built, survivor shape named (the derived index
  was built, measured, and reverted — archived with receipts on branch
  `one-door-rename-lens`; the migrating-bucket variant is unbuilt and
  currently fails its own payoff test)
- **evidence held**: measurement — (1) the differential receipt this
  note's obligation demanded, delivered Aug 2026: on the ratified
  benchmark (corelogic-bench packed lane, all answers, fair BFS,
  n = 3..7), index-based cascade wake vs the scan = IDENTICAL step
  counts and only 5–8% wall-clock — the scan is not the per-step growth
  term; the note's cheapest-kill clause fired. (2) The maintenance side:
  the derived index's upkeep (watcherAdded/watcherRemoved at every
  digestion door) alone cost ~20% on the same fixture with no reader —
  the maintenance exceeded the lookup's savings. (3) The rewrite-per-bind
  that kept the derived index truthful cost ~2.5× (n=5/6/7 =
  268/3,256/41,926 ms vs 100/1,072/13,272 pre-arc; steps identical).
  Per-step growth with n survives all variants — its cause is elsewhere
  (open question, profiler-first).
- **imports**: migrating constraint index ⋯import — core.logic 1.0.1
  `ConstraintStore`: `km` maps var → constraint IDS (`cm` id → object),
  `migrate` moves one bucket at var-var unification, `constraints-for`
  looks up by walked root; constraint objects are immutable and walk
  their rands at run. Receipt: source read Aug 2026
  (clojure/core.logic, logic.clj — km/cm/addc/remc/migrate verbatim).
- **obligations if ever built**: (1) a WORKLOAD receipt first — a real
  fixture (pldb-scale population) where the scan's share of wall-clock
  is measured dominant; no such fixture exists today and n ≤ 7 packed
  refutes the need; (2) the custody discipline: a first-class index is
  not derivable from the atoms, so every crossing that renames atoms
  into a new namespace (table replay, absorb) must migrate or rebuild
  buckets — staleness is silent, so each crossing needs its own failing
  test first; (3) steps identical on the pinned fixtures and the chaos
  harness green (bucket order changes examination order — on the
  separate-pairs scratch shape the derived index shifted step counts
  ±46% at n=6 with answer sets identical).
- **links**: constraint-kernel.md §7 (the rename-on-bind foreclosure
  this note's receipts underwrote), watched-revise.md (the custody
  trap's first sighting), disjunction-store-pays-in-products.md (the
  accounting discipline: no honing without a payoff in evidence —
  honored here by the kill).
