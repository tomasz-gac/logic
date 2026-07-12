# The vision — full exploitation of the lattice and the semiring

**Status: VISION + ROADMAP (July 2026, written with the human at the close of
the optimizer/lattice/semiring design arc). This is the north star the other
design docs serve; it prices nothing and schedules nothing by itself — the
roadmap at the end orders the work, and every item defers to its own doc for
the details. UNCOMMITTED until reviewed.**

Reading order for the theory: `lattice.md` first (the vocabulary), then
`optimizer.md`, `fixpoint-machine.md` §10, `semiring-inference.md`,
`tabled-constraints.md`, `ambient-optimizer.md`.

---

## 1. The identity

**One relational program is the free object; every capability is a question
plugged into it.** The two algebras divide the work: lattices let knowledge
live as data in any shape (finite, negative, continuous) and prove the
engine may STOP — iterate in any order, quiesce, terminate. Semirings let
one search answer any compositional question about all its ways and prove
the engine may REARRANGE — reorder, fold early, restructure, without
changing answers. Everything this engine does or will do is some exploitation
of those two freedoms, coordinated by one scheduler whose single number is
`order` = the answer states a goal emits.

## 2. What a user could ask of one program text

- **Which ones?** — relational solving, automatically planned: constraints
  first, generators last by live selectivity, recursion priced exactly once
  its tables complete. The naive program IS the fast program.
- **Does it terminate?** — tabling, plus call subsumption (specific calls
  reuse general entries) and TCLP: memoized subproblems keyed by REGIONS —
  "answers given this time window" — scheduling, bounded reachability,
  analysis fixpoints.
- **How many / cheapest / likeliest?** — semiring plugs over the same
  relations: counting, (min,+), Viterbi. With semiring tabling this is a
  dynamic-programming engine: the recurrence is a relation, the table is
  the DP array, closed-semiring stars solve cyclic cases analytically.
- **Constraint solving across domain kinds** — FD; intervals over reals
  with ε-widening (branch-and-prune); Booleans, where propagate-then-split
  IS DPLL; temporal networks. Each store climbs the capability ladder as
  far as its lattice allows; Neq correctly stops at rung one and residuates.
- **Why? Why not?** — provenance as a plug; failure explanations as the De
  Morgan dual plug; both memoized (a completed empty entry is a cached "no"
  with its reason).
- **Learn the weights** — the gradient semiring: the same search,
  differentiable. Research-grade; still just a plug.

Nearest real systems, for calibration: a Dyna-class semiring engine fused
with XSB's tabling/TCLP leg and an ECLiPSe/Gecode-class CP leg, with
ProbLog-style inference as plugs — a combination no single system ships.

## 3. Why it can be one engine

Three legs, never merged (the veto has survived four temptations —
`fixpoint-machine.md` §4/§9/§10): the narrowing fixpoint (knowledge
descends), the accumulating fixpoint (answers ascend), and the scheduler
that prices both and shuffles branch↔data in five moves (sort, domainify —
manual idiom only, force, park/wake, transfer). Every feature is an
annotation (`tabled`, `dom`, `Barrier`, `Bounded`), a store riding the
`Package` (weights, reasons, plans, the optimizer itself), or a plug handed
to solve. The kernel never changes. Folds are declared, never inferred; the
user licenses, the scheduler schedules.

## 4. Speed, theoretically

The machinery buys COMPLEXITY CLASSES: the planner removes exponents from
mis-ordered queries, tabling turns exponential recursion into polynomial
DP, fold-early keeps cells small, propagation prunes generate-and-test
trees, subsumption deletes recomputation. The substrate costs CONSTANT
FACTORS: persistent structures, boxing, fiber scheduling — one to two
orders vs tuned JVM imperative, two to three vs C solvers, permanently,
in exchange for free backtracking and parallelism. Specialists keep some
better ALGORITHMS (CDCL, global-constraint propagators, worst-case-optimal
joins) — hostable here, not free. The verdict: asymptotically competitive
with each community's textbook algorithm; constant-factor behind their
tuned implementations; strictly ahead on questions that CROSS communities,
where the alternative is glue code between three systems. Escape valves
reserved and benchmark-gated: representation swaps, best-first agendas
(legal exactly when the plug passes the superiority predicate).

### 4a. The parallel dividend

Parallel search is historically hard because classic solvers share one
mutable binding store and undo by trail (Prolog's OR-parallelism — Aurora,
Muse — died on this). This engine paid the persistence tax up front: every
branch owns an immutable Package sharing structure with its siblings, so
OR-PARALLELISM IS STRUCTURALLY FREE — no contention, no trail, answer set
schedule-independent by confluence, schedulers already pluggable drivers
(ForkJoin and solveParallel ship; the equivalence suite pins that all
drivers agree). The sequential verdict ("10–100× behind tuned imperative")
divides by the core count, with no configuration, on exactly the workloads
where the tax hurt — branchy search — and JVM servers with idle cores are
what the adoption audience owns. Caveats, revised (July 2026 — the algebras dissolve two of them):
- PROPAGATION parallelizes by asynchronous chaotic iteration (Cousot;
  Bertsekas): monotone operators converge to the SAME fixpoint under full
  concurrency with stale reads — a stale read yields sound-but-weaker
  narrowing that re-fires; races cost iterations, never answers. CAS-loop
  meets on factors (meets commute), ⊥ absorbing. Only sync point:
  quiescence detection.
- The TABLE is already a CRDT except at completion: entries are G-Sets
  (answer-appends commute, idempotence eats duplicates, stale slaves wake
  again). Racing masters are SOUND under idempotent plugs —
  tryBecomeMaster's CAS (already CAS-shaped) is an optimization there and
  an exactly-once REQUIREMENT under non-idempotent plugs (counting
  double-counts). Only sync point: completion detection over the SCC
  graph (the parallel-tabling literature's known hard part).
- BFS frontier still costs memory per core; semiring folds parallelize
  lawfully (⊕ commutative → parallel reduction).
- The concrete mechanism for parallel propagation is BETTER than
  Jacobi (July 2026, the human's catch): reviseAll's sequential fold
  threads the package between stores, but custody makes the threaded
  information UNREADABLE by the next store (a store cannot read another's
  factor; cross-store effects ride prefixes into NEW agenda items) — the
  serialization is a vestigial dependency, not a semantic choice. Fork
  the revise fibers against the snapshot (`Fiber.fork` — Conde's own
  primitive; ForkJoin already steps independent fibers), JOIN by a
  **commutative monoid on Revisions**: same fixpoint, SAME round count,
  zero staleness cost. Jacobi's extra-rounds price applies only if
  parallelizing ACROSS agenda items (a separate decision). Granularity
  stacks: revise returns Fiber<Revision>, so a store's long cascade is
  itself work-stealable mid-revision. The parallelism ladder: branches
  (shipped) → stores per drain step (fork the fold) → steps within a
  cascade (already fibered) → agenda items (the only genuinely
  Jacobi-priced rung). Monoid join —
  factor swaps compose disjointly BY CUSTODY (the product design pays
  off), inferred prefixes merge by unification-meet (conflict = ⊥ = the
  branch dies, correctly), suspensions/runs bag-union. The join IS the
  quiescence detection (barrier per round, "all unchanged" = stop) — the
  one sync primitive becomes structural. Same monoid serves the
  intra-store parallel cascade (fork FD's propagators, merge Updates) and
  the distributed knowledge merge. Only the ⊕ half of a semiring is
  needed (rounds sequence; nothing multiplies). Economics: store-level
  Jacobi loses (2–3 stores per branch); propagator-level wins on large
  constraint graphs; benchmark-gated.

**The one-synchronization-point principle**: the engine needs exactly one
primitive at every scale — "has this monotone process finished?" — as
drain quiescence, table completion, and distributed termination detection.
Everything else, cores to machines, is lock-free by algebra: lattice laws
are the network/race-tolerance laws (idempotence = at-least-once safe,
commutativity = reorder safe), CALM certifies the monotone bulk as
coordination-free, and the flush points (fail/die/force/residuate) are
exactly where coordination is irreducible. Distributed leg: a signpost,
not a phase — persistence makes branches shippable values, data-goals are
the serializable fragment, provenance-ids restore idempotence for
counting plugs; the distributed table's hard half is completion, per
CALM.

### 4b. Scoring against the competition (theoretical, no benchmarks yet)

| against | sequential | with cores | verdict |
|---|---|---|---|
| Choco/JaCoP (Java CP) | lose ~10–50× on propagation kernels | gap narrows on search-heavy | competitive at business scale; lose FD kernels; win auto-tuning + expressiveness |
| Gecode (C++ CP) | lose 100–1000× | they parallelize too | don't compete on their turf |
| CDCL SAT | not close — algorithmic gap, not constants | portfolio SAT scales too | DON'T ENTER: we reproduce DPLL; they left it decades ago |
| SWI-Prolog / XSB | lose ~10–100× (WAM) | they have no real OR-parallelism | plausible outright WIN on multicore search-bound relational work — the headline |
| Soufflé (parallel Datalog) | lose bulk bottom-up | they scale too | lose batch Datalog; win interactive/top-down/constraint-touching |
| Timefold | different game (local search, no completeness) | — | complementary: they handle sizes we can't; we prove/enumerate/explain what they can't |

Composite position: never the fastest specialist; plausibly the fastest
GENERALIST on a multicore JVM; unique regardless of speed on
cross-paradigm queries, where the competition is glue code between three
systems. The performance pitch that survives scrutiny is three-part:
asymptotically right by default (the planner), scales with your box by
default (persistence + pluggable schedulers), explains itself (provenance
both ways) — with the SAT row as the standing reminder of where we don't
pretend.

## 5. Adoption thesis

SQL's founding promise — declarative queries plus an automatic planner —
applied to general relational/constraint programs, embedded in plain Java:
goals are ordinary values, vavr collections unify natively, `solve` returns
a `java.util.Stream`. The folklore that gates every logic/CP system
(clause order, labelling strategy) is the optimizer's defaults. Market
evidence the embedding thesis works: Timefold, with less machinery.
Non-logic use cases: configurators with "why is this invalid", test-data
generation under invariants, rostering at business scale, compliance rules
with provenance audit trails, object-graph queries without a database —
all valuing explainability and integration over raw solver speed, which is
the substrate's exact trade. The distance to adoptable is packaging, not
architecture: releases, licensing, vocabulary, and one debugging feature
(failure provenance — the first question every newcomer asks).

## 6. The abstraction pipeline (name before building)

Consistent finding: naming a structure before building it deletes code
(`entails` fell out of the meet; the TCLP store surface shrank; plan memo
and subsumption tabling collapsed into one `SubsumptionMap`). Next in the
pipeline, by customer-readiness: **Galois connections** (one proof shape
for every cache/key/projection; composition free), **closure operators**
(TCLP keys must compare PROPAGATED regions — a silent-reuse-loss bug
nameable only with this vocabulary; `drain twice == drain once` as a pin),
**star + superiority predicates** (cyclic weighted queries; best-first
agendas without wrong answers — the rare knob that can corrupt, so it gets
an interlock), **the provenance hierarchy** (coarsest-sufficient-plug
downgrades; explain one answer without paying for all). Signposted and
parked: **quantales** (resource-bounded memoization — the merge temptation
with a theorem), **bilattices** (the negation door; do not bolt negation
onto the single knowledge order).

---

## 7. Roadmap

Ordered; each item lands green on the full suite; benchmarks gate the
forks. Statuses: SHIPPED / NEXT / QUEUED / GATED / PARKED.

**Phase 0 — land the platform (NEXT)**
1. Merge branch `optimizer` (12 commits: seam, ambient delivery, Barrier,
   ordering layer, UnifyGoal, spawn-count benchmark, design corpus).
2. pldb `LookupGoal implements Goal, Bounded` + `estimate()` — the second
   Bounded citizen, first real index estimates; pldb Phase 2 benchmark
   (probe-yield counts). The pldb planner collapses to one data type.
3. **The Bounded sweep** (broadened from the FD retrofit, July 2026):
   FD posts as `Bounded.of(1, …)` + labelling at construction-time width
   (FD mis-ordered benchmark — constrain-first for the constraint
   library itself); `unifyNc` (1); `Disequality.separate` (1 — a post);
   suspension-creating goals like `project` (1 — parking is knowledge
   injection, the dom argument); `success`/`successIf` (1) and `failure`
   (0 — sorts first and kills doomed segments before the algebra pass
   exists); `Aggregate` goals (1 — one folded answer state; javadoc note:
   order prices branching, not runtime, and aggregates are expensive
   order-1 goals). Free by derivation: everything COMPOSED of priced
   parts — the boolean gadgets (conjo/disjo) price at 4 with zero
   retrofit. Not in the sweep: tabled calls (need the Phase-1 widening),
   membero-as-data-goal (a rewrite, stretch), Matche cases (opaque by
   construction, correctly barriers).

**Phase 1 — the plug socket (NEXT after 0)**
4. **The algebra package** (decided July 2026: the abstractions have paid
   for engine-level presence): `Lattice<L>` (F-bounded), `Semiring<S>`,
   `CommutativeMonoid<M>` + LAW KITS (property-test harnesses:
   associativity, commutativity, idempotence, distributivity,
   star/superiority predicates, `foldEarly == foldLate`) — home:
   `functional` (pure algebra, release-prep synergy). Adoption rule (REVISED
   July 2026, the human's call): declare the interface on EVERY genuine
   instance — the honesty gate is the LAW KIT, not a caller. Algebraic
   interfaces earn their keep by law-attachment and discoverability
   ("find implementations of Lattice" = the engine's theorem index), and
   the consequences arrive from unforeseen directions — proven within a
   day: Substitutions-as-lattice was declared caller-less, then the
   Jacobi join's prefix-merge turned out to BE its meet called
   generically. A fake instance fails associativity in the suite; laws
   prove truth where callers only prove use. Implementation friction is
   itself informative: Substitutions has no ⊥ (failure is CPS absence),
   so the hierarchy needs MeetSemilattice-without-bottom vs
   BoundedLattice — a documented fact made type-visible by the mere
   attempt to declare. Then Semiring Phase 1 proper:
   `aggregate` refactored onto `Semiring<S>` (`semiring-inference.md`
   §2–3); five capabilities queue behind it: aggregation, cost
   arithmetic, DP, provenance, failure explanations.
5. `answers(Package)` widening — three customers (force-early, live
   labelling, completed-entry re-pricing); ends its speculative status.

**Phase 2 — memo and reuse (QUEUED)**
6. `SubsumptionMap<K, V>` (leq-parameterized; exact-hit fast path,
   nearest-more-general fallback). Customers in order: adornment plan memo
   (ambient milestone 3), Herbrand call subsumption in tabling (unlocks
   completed-entry reordering — barrier dissolves at completion), TCLP
   stage-2 region keys later.
7. The runtime-bindings fork, decided by the pldb + FD benchmarks:
   adornment-memoized dynamic ordering XOR deferred lookups
   (substitutes — build at most one).

**Phase 3 — weighted inference (GATED on Phase 1)**
8. Weighted goals + value-riding-the-package store (`semiring-inference.md`
   §4); counting and (min,+) end-to-end.
9. Semiring tabling: `Map<AnswerTerm, V>` cells, ⊕ at arrival, ⊗ at
   consumption, the call-boundary cut enforced (§7a). Acyclic first;
   closed-semiring star for cycles after.
10. Failure provenance (§7b): reason-collector store (tracer pattern),
    deepest-failure plug; cached "no"s. Doubles as the adoption
    feature (§5).

**Phase 4 — TCLP (GATED on 6 + a paying use case)**
11. Stages per `tabled-constraints.md` §6: FD-only exact keys → constrained
    answers with `restate` → pointwise-⊑ subsumption → widenings (Neq
    collapse, real ε) only with a motivating user. `Lattice<L>` F-bounded
    adoption rides in here (Domain implements; `entails` hook already
    deleted by design).

**Adoption track (parallel, independent of the above)**
12. `functional` release-prep + de-SNAPSHOT both repos; the parked
    Apache 2.0 decision; public-API vocabulary pass (front door says
    match/solve/explain); FD javadoc.

**Parked, deliberately**: cut/`once`/`ifte` (wants its own design
conversation); domainify as a pass (manual idiom only); quantales;
bilattices/negation; virtual-threads engine (separate module,
`virtual-threads-engine.md`); representation swaps (benchmark-gated,
`substitutions-migration.md` §5); CDCL-class algorithms (hostable, not
planned).
