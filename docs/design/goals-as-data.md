# Goals as data — programs, the call boundary, and the fold-planner

STATUS: DESIGN SKETCH (July 2026), not scheduled. The distribution story in
three layers, each independently useful, none touching the existing engine.
Grew out of one observation and one requirement: the engine's parallelism
comes from its algebra (ACI merges, upward-closed flags, monotone counters
— the CALM story), and any distributed version must REUSE that machinery,
not rebuild it. Prerequisites for the vocabulary: lattice.md §5a (the
quotient tower, the license table), table-completion.md and group-seal.md
(coats, ledgers, seals).

## 0. The one wall, and where not to cut

Everything in the engine crosses a machine boundary except one thing:
goals are FUNCTIONS (CPS closures). Answers are reified values; packages
are immutable; tables are monotone logs; calls are (relation, reified
pattern) pairs. An earlier analysis of a distributed executor concluded:
the failed cut is at the layer where everything is live closures; the
right cut is where everything is already data. This doc is that
conclusion, engineered: three layers, none of which ships a closure.

## 0a. Layer zero — the relation registry (the actual gateway)

The morning correction (July 2026): distribution does NOT need the
Program layer. The only missing piece was stable relation identity, and
NAMES close that gap without an AST: register goals under names
(`define("path", body)`), key `Call` by (name, reified pattern), deploy
the same artifact to every node and register at boot — the RPC answer,
as old as RPC. Bodies never travel in any design; here they never even
serialize. The wire carries names, patterns and reified answers — all
already values. A body-hash check at registration verifies the
same-artifact-everywhere invariant instead of trusting it. Only
STATICALLY REGISTERED relations distribute — runtime-minted define
lambdas stay local, consistent with "the boundary is declared, never
inferred".

What changes in the machinery, and what does not:

- **`MonotoneCell` is already the transport seam**: grow =
  publish-with-dedup (the home shard's JoinSet join eats redeliveries —
  at-least-once safe by law), park = subscribe-from-offset with the race
  resolved toward reading, replay-from-index = consumer offsets. A
  Kafka-shaped log, discovered not designed. The change is an interface
  with two implementations: monitor-backed (today) and shard-backed
  (parked subscribers are remote registrations).
- **Regions distribute to quiesce**: ledger events (started/finished/
  sleeping/awake) become messages to the region's home shard, the coat
  as metadata (region identity = the (name, pattern) key). The singleton
  rule evaluates at home — D–S un-miniaturized. The group seal ports by
  construction: stale monotone reads undercount and undercounting
  refuses; the only ordering requirement is completing all phase-one
  RPCs before any phase-two RPC, so every read pair brackets one global
  interval. Per-member CAS at each home arbitrates racing walks.
- **The one non-ACI structure pays at the network layer, as the license
  table predicts**: counter ticks are not idempotent — billing messages
  need acks and per-sender sequence dedup (a lost finish = never-seals =
  sound but stuck). Everything else redelivers harmlessly.
- **Partial failure remains the honest hard part**: home shards dying
  mid-production need leases/master re-election with fencing.

## 1. Layer one — the Program front door (goals as data, DEMOTED to optional)

A new package, purely additive; the `Goal` interface never changes. No
longer the distribution prerequisite (layer zero is): its standing
customers are LOCAL — Eq-kit sample programs and pldb's rule layer — plus
one future: shipping programs to nodes that do NOT already have the code
(dynamic topologies).

- **The unit is a `Program`**: a map of NAMED relations (params + body as
  an AST — `Fresh(names, body)`, `Unify`, `Conj`, `Disj`, `Call(name,
  args)`, term constructors with first-order variables) plus a query.
  Names give three things at once: recursion and mutual recursion (the
  compiler wires `defineRecursive` and the forward-reference knot
  internally), tabling (a `Call` node compiles to `rel.apply(...)` with
  the tuple discipline handled once), and SERIALIZATION — a Program is a
  value; ship it, compile it remotely, get reified answers back.
- **The compiler is a fold** from the AST (initial encoding) to `Goal`
  (final encoding). The existing fiber engine thereby becomes the
  REFERENCE INTERPRETER for free; any future backend is equivalence-
  tested by compile-both-ways-compare-answers, exactly like the
  scheduler-equivalence suite.
- **Binding is trivial in this direction**: first-order binders compile
  TO HOAS (`exist(x -> …)`) via a name→LVar environment at compile time.
  (Decompiling closures back to data is the hard direction; it is never
  needed.) LVar object identity stays exactly as is.
- **Graceful degradation is structural**: an `Opaque(Goal)` node embeds
  any hand-written goal — compiles and runs locally, refuses to
  serialize. Distribution coverage grows exactly as fast as programs stay
  first-order; no cliff.
- **Two local customers before any distribution**: `Semiring<Goal>`'s
  Eq-parameterized law kits want sample programs to solve on both sides
  of each axiom — data programs are generatable and comparable (property
  testing over program space); and pldb's rule layer is already halfway
  to this shape.

## 2. Layer two — the call boundary (the runtime distribution unit)

The machinery-reuse requirement is met by NOT distributing goals at all.

- **Every node runs the existing engine, unchanged** — fibers, scheduler,
  optimizer, the whole algebra-parallelized machinery — on the call
  events it owns. Bodies never travel; calls do, and a Call is already
  data (the one gap: relations key by object identity today; the Program
  layer's names close it).
- **The distributed layer is only the Table**, which is already shaped
  for it: a partitioned monotone log with subscriptions. Route each call
  event to its home shard (hash of relation + pattern); a cross-node
  reader is a SUBSCRIPTION; addAnswer is a PUBLISH; respawn is
  notification with replay from the reader's index.
- **Each piece is licensed by a shipped, law-checked algebra fact**:
  `JoinSet`'s ACI join makes replica merge and at-least-once delivery
  lawful (the CRDT reading, load-bearing); the seal's upward-closedness
  makes it cacheable network-wide (a stale "unsealed" only defers —
  the sound failure mode is retry, never wrong); the coat becomes
  message metadata and Dijkstra–Scholten billing returns to its native
  habitat — it was a distributed algorithm before we miniaturized it;
  the group seal's two-phase monotone read survives staleness (stale
  reads undercount, undercounting refuses).
- **The seal is the one coordination point** — per-entry CAS on its home
  shard. CALM's prediction made exact: coordination retreats to
  precisely the engine's one non-monotone operation.

**Constraint stores — the meet side, distributed.** Meet is ACI like
join, so the dual licenses all hold: narrowings apply in any order
(chaotic iteration's confluence IS the async-message-tolerance theorem),
redeliver harmlessly, merge from concurrent propagators. Distributed
propagation to a common fixpoint is lawful — and still wrong to build,
for two reasons. Economics: the Jacobi lesson amplified — store-level
parallelism did not pay for THREADS at our store counts; a network hop
per narrowing is that verdict times four orders of magnitude. Semantics —
the real asymmetry: a store is BRANCH-LOCAL BY MEANING, knowledge
conditional on a hypothesis; meeting two sibling branches' stores is
algebraically defined and semantically nonsense (x∈{1,2} ⊓ x∈{3} = ∅
conflates worlds, it does not detect failure). An answer, by contrast,
is an UNCONDITIONAL fact about its relation — which is exactly why
tables cross branches and stores do not. The direction principle,
distributed edition: THE GROWING SIDE HOLDS GLOBAL TRUTHS AND SHARES
EAGERLY; THE SHRINKING SIDE HOLDS CONDITIONAL TRUTHS AND STAYS HOME.

Stores therefore enter distribution through the same one door everything
crosses branches by — the table:

- **TCLP residues** (the honest route): when tabling captures
  constraints, answers become (tuple, residue) pairs — a residue is a
  store REIFIED INTO A VALUE, and unlike goals, domains already
  serialize. Store distribution then comes free with table distribution:
  residues ride published answers; dedup at the home shard is the
  antichain/entailment check — the lattice leq already declared and
  law-checked on Domain and FiniteDomainConstraints; TCLP's finiteness
  gate (capability ladder tier 5) doubles as the distribution-worthiness
  gate. Sealed constrained relations are frozen (tuple, residue) sets —
  immutable, replicable, servable from any node.
- **Learned nogoods** (gated on learning machinery that does not exist):
  a conflict yields a branch-INDEPENDENT implication — shrink-side
  knowledge promoted to global truth by recording its conditions.
  Distributed SAT shares exactly these between workers; our door is the
  failure-provenance/CDCL line (semiring-inference §7b).
- **Within each node, unchanged**: stores keep doing everything they do
  today — per-branch, fiber-parallel, cascades under the MonotoneDrain
  discipline — because layer two ships the whole engine to every node
  precisely so this needs no redesign.

The compressed rule: joins distribute because they are unconditional;
meets distribute only after being reified into the table (TCLP residues)
or promoted to unconditional (nogoods) — otherwise they are hypotheses,
and hypotheses do not travel.

## 3. Layer three — the fold-planner (the semiring builds the plan)

"Use the semiring passing through the goals to build the distributed
program" — in the NON-EVALUATING mode the engine already demonstrates:
the pricing pass folds the goal tree through SATURATING without running
anything. A Plan-semiring folded the same way decides placement and
partitioning (which calls live where), with the existing prices as its
cost model. It inherits pricing's blindness deliberately: opaque leaves
plan as "local only", defer walls plan incrementally at the ambient
hook — the same half-blind discipline, the same soundness story
(plans are wrong-only-slow).

## 4. Gates and honest hard parts

- **Search branches do not travel** — only calls do. A query that is one
  giant untabled conjunction gains nothing; distribution coverage grows
  with tabling granularity. This is a feature with a cost, not a bug.
- **Partial failure**: a shard dying mid-production must not leave
  consumers waiting on a seal that never comes; leases or master
  re-election per entry — real distributed-systems work, not algebra.
- **Fairness/completeness across backends**: the virtual-threads gate,
  wearing another costume. Any new interpreter — vthreads or distributed
  — must pass the equivalence suite against the reference interpreter.
- **Exactly-once seals across shard handoff**: the CAS is per-shard; slot
  migration needs the usual fencing.
- Sequencing: layer one stands alone and has local customers now; layer
  two is gated on layer one's names plus a real distribution need; layer
  three is gated on layer two existing. Virtual threads slots in as a
  second LOCAL interpreter of layer one — subsuming the separate-module
  plan in virtual-threads-engine.md rather than duplicating it.
