# The vision — full exploitation of the lattice and the semiring

**Status: VISION + ROADMAP (July 2026, written with the human at the close of
the optimizer/lattice/semiring design arc). This is the north star the other
design docs serve; it prices nothing and schedules nothing by itself — the
roadmap at the end orders the work, and every item defers to its own doc for
the details.**

**Project nature (made explicit July 2026): a research/learning library with
no client base. Viability is a CONSTRAINT, not a goal — designs must be the
kind that could be real (honest concurrency, real ledgers, no toy shortcuts
that wall off reality), because that keeps the research honest; but nothing
is scheduled by demand, since none arrives. The scheduler is
WHAT-TEACHES-FIRST, on four axes: theory density (effort that tests claims
vs plumbing), falsifiability (builds that could DISPROVE something written —
the most valuable kind), compounding (does it unlock the next question), and
demonstrability (a crisp example is a research artifact). Gates below that
read "when X becomes the most instructive next build" mean exactly that;
older client-fiction gates ("a paying use case") are retired.**

Reading order for the theory: `lattice.md` first (the vocabulary), then
`condition.md` (the two algebras meeting at the answer cell),
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

The exploitation has TWO AXES (the second discovered July 2026, designing
distribution): laws → legal OPTIMIZATIONS (rearrangement, pruning, reuse —
the original axis), and laws → INFRASTRUCTURE COSTS. The capability types
(`IdempotentSemiring`/`ClosedSemiring`/`SuperiorSemiring`) turned out to be
a deployment cost model: idempotent plugs tolerate at-least-once delivery
(cheap, retry-happy transports suffice), non-idempotent plugs demand
exactly-once (transactional infrastructure), closed plugs need per-SCC
centralization points, TCLP residues need compacted-log transport. Read a
plug's interfaces, know its infrastructure bill — statically. The severest
test of the thesis so far: an entire distributed-system design
(goals-as-data.md) needed ZERO new theory — every guarantee traced to an
already-shipped, law-checked structure. Its rival for the title arrived
August 2026 from the opposite direction (condition.md): at the answer cell
the two algebras MET — the constraint ring is a bounded distributive
lattice, and TCLP was weighted inference over it all along — and the loop
ran BACKWARDS (method.md step 8): one structure derived five capability
designs in one arc (negation, clause learning, the disjunctive store, soft
constraints, imposition planning), each priced with its dependency chain.
Zero new theory needed for a design is one kind of evidence; five designs
falling out of one structure is the other.

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
- **Under which conditions?** — conditional answers (c-tables): a tabled
  answer carries the region it is proven on (`t GIVEN x ∈ {…}`), summed in
  the constraint ring, streamed at 1 or delivered final at the seal
  (condition.md).
- **Why? Why not?** — provenance as a plug; failure explanations as the De
  Morgan dual plug — no longer a metaphor: Neq IS ¬Condition, and clause
  learning is tabling the failures (condition.md §§8.5–8.6); both memoized
  (a completed empty entry is a cached "no" with its reason).
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
  the master-claim CAS (already CAS-shaped) is an optimization there and
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
with a theorem), **bilattices** (RE-PARKED August 2026: the STRATIFIED
negation door turned out to be ¬Condition — De Morgan over the constraint
ring, constructive negation over seals, condition.md §8.5 — needing no
second order; bilattices remain the door to the NON-STRATIFIED fragment,
well-founded semantics, which §8.5 refuses loudly rather than stumbles
into).

---

## 7. Roadmap

Ordered; each item lands green on the full suite; benchmarks gate the
forks. Statuses: SHIPPED / NEXT / QUEUED / GATED / PARKED.

### 7a. The ledger (August 2026) — pending work keyed to the Tar Pit pillars

The founding text's "real system" needs four things: ESSENTIAL STATE as
relations fed from the world, ESSENTIAL LOGIC as declarative rules with a
complete vocabulary (negation and aggregation included), ACCIDENTAL
COMPLEXITY pushed into planners users never touch, and honest INTERFACES.
Every pending item slots under one. Costs 1–10, 10 expensive.

**Essential state** — the FactSource seam + in-memory reference (3: external
relations become goals); the SQL fact source (4: the first real feeder);
persistence — capture-solve, marshal, memo store, pin stamps as epoch
conditions (8: durable derived state, consistent reads, warm starts —
de-risked by the epoch-factors identification, condition.md §8.7);
external work as suspended fibers (3: cold execution, exactly-once).

**Essential logic** — the Aggregate reframe (3: CORRECTNESS DEBT, NOT A
FEATURE — today's aggregates run eagerly against worlds that keep
changing; the fix is the boundary reframe, condition.md §8.7: a
closedness refusal on today's Aggregate plus the solve→fold→seed idiom;
recursive/monotone folds were always Weights' job; the branch-state
residual is gated); negative
constraints, nogood-store stage 1 (4: `x ∉ 1..3`, forbidden combinations —
real rulebooks are half negatives, today inexpressible); negative
knowledge through tabling, stage 2 (2); or-between-constraints, stage 3
(6: brackets, tariffs, optionality, precedence pairs WITHOUT forking —
the toy-vs-business divider); suspensions as conditions on answers (5:
the last refuse-loudly wall); real negation (7: integrity constraints
need it; storage cheap once the nogood store exists — the operator is the
cost); TCLP key alignment (3: shelved on observed misses).

**Accidental complexity** — clause learning, cheap tier (4: never repeat
a proven-dead decision combo; zero semantic surface); the imposition
planner (6: the thesis itself — "the naive program is the fast program"
extended to constraint placement); weight-capture wiring (3: the chain's
first link); piecewise weights (8: wanted, not needed on day one); the
assembler (9: far horizon by its own doc's decree).

**Interfaces** — one rendering path for conditional answers (2: also
triggers the ring trio's package move); failure explanations (5: the
adoption feature; shares reason-tracking with clause learning); release
prep (3–4: the difference between a library and a repo).

**The critical path to "you could build a real system on this"**:
Aggregate redesign → negative constraints → or-between-constraints →
FactSource + SQL → persistence — sound aggregates, a complete rule
vocabulary, real data in, durable derived state out; ~28 points of the
~73 on the board. Everything else DEEPENS the vision rather than
completes it.

**The proposed order (August 2026)** — a repair wave, five feature waves
and a gated tail; each step lands on a green suite:

- **Wave R — boundary repairs (~12; cut the line, August 2026)**: the
  external code reviews' verified findings, all living where the method
  had least coverage — the boundary between the engine's laws and
  Java's. None touches the ring, the crossings, the cell or the seal
  discipline. Red test first for every item. In logic: the solve
  spliterator delivers ONE element per tryAdvance (the current
  completion-drain silently LOSES answers through iterator adapters —
  the worst finding at the smallest fix); the occurs check made
  recursive (x = [x] currently builds a cyclic substitution every
  walker downstream assumes cannot exist); the Revision boundary
  refuses cross-store replacement (the "unrepresentable by type" claim
  was convention — the flag-vs-capability disease, third occurrence);
  two small refusals (JoinMap ring identity on join; factor without a
  weight store refuses instead of silently running unweighted). In
  functional: BFS promotion polls its buckets instead of trusting
  PriorityQueue iteration order; manual Channel.seal repaired or
  removed (it strands held waiters; its one production caller is a
  fossil); ResumeHandle gains the duplicate-completion CAS refusal;
  ForkJoin structured failure + the run-API split — GATED on the
  failure-policy ruling (recommended: fail-fast + cooperative cancel).
  THE SUITE LEVER (the human's, August 2026): all test solves route
  through one seam with the scheduler as a property — the whole suite
  runs under the randomized scheduler at will (order-independence
  enforced by every test, not one harness; seed-reproducible), and the
  same seam times the suite per scheduler: performance regression at
  will. ~197 of 262 call sites sweep; the ~59 explicit-factory sites
  are the legitimate exemptions (trace semantics, step pins, scheduler
  tests). A seed failure is a find, never an exemption.
  STANDING GATE opened by the same reviews, scheduled before Wave 2
  leans further on consume: the TABLE-REPLAY FAIRNESS design
  conversation — deliver(answer) and consume(next) as sibling scoped
  branches (today they sequence, so a non-forking divergent
  continuation downstream of answer 0 starves answer 1: a
  fair-completeness regression tabling introduces). Touches completion
  accounting, billing, dedup, sealed and closed replay — a design
  pass with the human, not a patch.
- **Wave 0 — DISSOLVED (August 2026)**: both items left it. One
  rendering path for conditional answers was killed — reify is how
  stores EXPRESS infinities to a caller, project is how a live package
  is lifted into anonymized values transplantable into other packages;
  two distinct functionalities, nothing to unify. (Its rider dies with
  it: the ring trio's package move and the marshal format's early home
  now wait for persistence itself.) The weight-capture wiring moved
  into the weighted-TCLP line (docs/design/weighted-tclp.md, the
  streaming-fold step) and inherits that build plan's gate — it is not
  a wave; its ungated head, Aggregate-onto-Semiring, is Wave 1's
  Aggregate reframe.
- **Wave 1 — the correctness debt (~3)**: the Aggregate reframe, now
  fully dissolved (condition.md §8.7): every fold routes to existing
  machinery — idempotent → Weights anywhere including recursion;
  non-idempotent over proofs → Weights/closed (the types already refuse
  the divergent cases); over answers → fold the finished solve's stream;
  GROUP BY = table keys. What is BUILT: the closedness refusal on
  today's Aggregate (any pre-existing variable → loud named error), the
  routing docs (§8.7's worked pairs), the general watermark detector
  (Wave 2's state-independent bodies reuse it); sugar-or-retire for the
  Aggregate signatures is the human's call. The branch-state residual
  stays in the gated tail.
- **Wave 2 — the rule vocabulary (4+2+5+6)**: opens with the gated
  design conversation (the nogood-store pass + the naming session), then
  stage 1 (notin/exclude ships), stage 2 (nogoods ride tabling),
  suspensions-as-conditions (reuses the watermark; closes the last
  refuse-loudly wall; the aggregate rider lands here), stage 3 (`either`
  — or-without-forking, the scratch check, the agreement move).
- **Wave 3 — real data (3+4+3)**: FactSource seam, SQL source, external
  fibers (elaborated, with per-phase falsifiable proofs, as
  domain-layer.md §12 Phases 1–3; the whole §12 sequence is Waves 3–4
  and the tail expanded, Phase 0 being Wave R). AFTER the vocabulary deliberately: when real data arrives, the
  rules it meets are already correct and expressive — the first pldb
  demo gets to be a real one. Independent of Wave 2; parallelizable if
  focus allows.
- **Wave 4 — durability (8)**: persistence, consuming everything before
  it; its hardest design question (what a pin stamp IS) already
  answered. SPINE COMPLETE = the Tar Pit claim holds.
- **Gated tail** (triggers, not schedules): clause learning's cheap tier
  (a workload re-exploring dead decisions); real negation (the first
  rulebook needing "unless"; also the sound ifte); the imposition
  planner (measured mis-imposition pain, after width hooks); failure
  explanations (the adoption push); branch-state aggregates (the
  in-solve residual — lookahead counting, open-set capacity; likelier
  homes: search heuristics, global-constraint propagators); Neq re-seat
  (any quiet moment after stage 1); the research shelf unchanged.

The one deliberate deviation from cost-ordering: Wave 3 is cheaper than
Wave 2 and could run first — the vocabulary leads by the
what-teaches-first scheduler: or-without-forking is the falsifiable,
demonstrable build (does the bracket demo beat the forked encoding?),
while FactSource is plumbing that teaches most AFTER there are
interesting rules to feed.

**Phase 0 — land the platform (SHIPPED July 2026)**
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

**Phase 1 — the plug socket (SHIPPED July 2026: the algebra package +
law-kit/coverage-gate architecture in `functional` with the `functional-laws`
module; capability types `IdempotentSemiring`/`ClosedSemiring`/
`SuperiorSemiring` replacing the predicate defaults; `aggregate` on Monoid
witnesses; the optimizer on `Semirings.SATURATING`; the `answers(Package)`
widening with store-sighted post pricing and completed-entry pricing.
LANDED BEYOND PLAN, same period: the full TABLE COMPLETION arc —
`table-completion.md` and `group-seal.md`: the EnclosingCall coat (since
deleted — the frame's ambient scope took the role), detach-k
(since superseded by the anonymous master),
the Scope/WorkLedger/Channel/JoinSet primitives, the two-edge graph
and its seal criterion, SUBSUMPTIVE REUSE (sealed entries serve instance
calls; completed entries genuinely mobile), and the TIER-2 GROUP SEAL
(detection total for finite solves — the negation-free case of SLG completion as ~60 lines on a
generic primitive). Plus the distribution design corpus, goals-as-data.md.)**
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

**Phase 2 — memo and reuse (HALF-SHIPPED July 2026)**
6. Herbrand call subsumption SHIPPED without the map: Call.subsumes (the
   one-way matcher — which IS the leq the map will take) + a per-relation
   scan serve sealed-entry reuse and completed-entry reordering today.
   `SubsumptionMap<K, V>` (leq-parameterized; exact-hit fast path,
   nearest-more-general fallback) demotes to the adornment plan memo's
   trigger (ambient milestone 3); the scan swaps for it behind the same
   call sites when it arrives. TCLP stage-2 region keys later.
7. The runtime-bindings fork, decided by the pldb + FD benchmarks:
   adornment-memoized dynamic ordering XOR deferred lookups
   (substitutes — build at most one).

**Phase 3 — weighted inference (SHIPPED July–August 2026: weighted goals +
the SemiringStore product, bounded streaming and closed/star tabling —
star-tabling.md — and, unanticipated by this roadmap, the weighted cell and
TCLP's answer carrier UNIFIED into one ring — condition.md)**
8. Weighted goals + value-riding-the-package store (`semiring-inference.md`
   §4); counting and (min,+) end-to-end.
9. Semiring tabling: `Map<AnswerTerm, V>` cells behind the Channel
   seam, ⊕ at arrival (the cell demands `IdempotentSemiring<V>` for
   streaming), ⊗ at consumption, the call-boundary cut enforced (§7a).
   Acyclic first; closed-semiring star for cycles after (the group seal's
   closure walk doubles as the SCC finder star needs). DUAL-PURPOSE since
   the distribution design: locally it is weighted inference; it is also
   the gateway to the workload class where distributed tabling genuinely
   pays (weighted graph analytics — routing is planet-scale prior art),
   and idempotent-first sequencing is now ALSO the distribution-viable
   half of the taxonomy.
10. Failure provenance (§7b): reason-collector store (tracer pattern),
    deepest-failure plug; cached "no"s. Doubles as the adoption
    feature (§5). (August 2026: clause-learning tier 1 — condition.md
    §8.6, decision clauses over the shared co-store — is the new
    cheapest on-ramp.)

**Phase 4 — TCLP (SHIPPED July–August 2026 through stage 3 —
tabled-constraints.md, condition.md; the widenings remain gated on a
motivating user)**
11. Stages per `tabled-constraints.md` §6: FD-only exact keys → constrained
    answers with `restate` → pointwise-⊑ subsumption → widenings (Neq
    collapse, real ε) only with a motivating user. `Lattice<L>` F-bounded
    adoption rides in here (Domain implements; `entails` hook already
    deleted by design).

**Phase 5 — distribution (HORIZON; design complete, build gated on
instructiveness)**
13. The goals-as-data.md layers: relation registry (layer 0), distributed
    regions/cells, the fold-planner. The design needed zero new theory —
    which is now a stack of FALSIFIABLE PAPER CLAIMS (the two-phase seal
    survives networks; a parked consumer is a pending long-poll; counter
    ticks are the one exactly-once cargo). The research-honest first build
    is the CROSS-PROCESS EXPERIMENT: two processes, localhost REST with
    long polling, seal a cross-consuming ring across the process boundary —
    it tests every claim at near-zero ops cost, and it is the only pending
    item that can DISPROVE rather than extend. The standing obligation this
    phase creates today: Phases 1–4 built distribution-ready primitives
    without knowing it (Scope, Channel, JoinSet); future
    designs should preserve that property deliberately.

**Phase 6 — the condition chain (QUEUED August 2026; ordered with
dependency chains in condition.md §8)**: reify-over-project (the ring
trio's first outside customer — triggers its package move) → suspensions
as Residues factors → weight-capture normalize wiring → the disjunctive
store (design pass: the excursion fold, the join rung) → then the gated
research tier (the weight ⊗ Condition tensor, negation as a value, clause
learning, the optimizer's imposition plan space).

**Adoption track (parallel, independent of the above)**
12. `functional` release-prep + de-SNAPSHOT both repos; the parked
    Apache 2.0 decision; public-API vocabulary pass (front door says
    match/solve/explain); FD javadoc.

**Parked, deliberately**: propagation strategy knob (July 2026 — naive
while-loop / fiber-sequential / fiber-concurrent): store-level forking
alone is 2–3 units (built on the jacobi branch, verified equivalent,
reverted); real parallelism is STORE-INTERNAL (a cascade forking its
propagators via Folds.forkAll) — and once stores have it, a sequential
outer chain actively fights it, so the outer strategy must be chosen
WITH the inner one. Revisit when a store forks its cascade; the
primitives (forkAll, foldChained, Worklist monotone) are ready; cut/`once`/`ifte` (wants its own design
conversation); domainify as a pass (manual idiom only); quantales;
bilattices/negation (re-parked per §6 — the stratified door is
condition.md §8.5); virtual-threads engine (separate module,
`virtual-threads-engine.md`); representation swaps (benchmark-gated,
`substitutions-migration.md` §5); CDCL-class algorithms (tiered August
2026, condition.md §8.6: tier-1 decision-clause learning is nearly free;
full lazy clause generation remains specialist territory — hostable, not
planned).
