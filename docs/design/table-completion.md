# Table completion detection

STATUS: APPROVED (July 2026), not yet implemented — Tier 1 scoped, Tier 2
deferred. Read with `tabling/` open and the landmine warning in mind: this
is surgery on produce/consume/respawn.

## 1. Why

An incomplete table entry can only be priced ∞ (growing substrate — stale
counts lie), so every tabled call is a permanent optimizer barrier. A
COMPLETED entry's answer count is frozen: price = exact count, the barrier
dissolves, and the sort can hoist a finished cheap lookup ahead of
expensive generators — the ∞→exact immovability transition
(optimizer.md). Completion is also the gate that makes aggregate, negation
and if-then-else over tabled goals sound (Aggregate's documented caveat:
consumers park before the relation is exhausted), the license to reclaim
dead registrations, the prerequisite of prefetch-to-price, and the
exhaustiveness half of failure explanation (§8). Five customers, one
mechanism.

## 2. The rule

Entry X can produce a new answer only from (a) its master's body fiber
still running somewhere, or (b) a registration created during X's
production, parked on some entry B, waiting for a new B answer. Hence:

    complete(X) ⟺ liveWork(X) = 0 ∧ every X-registration parks on a complete entry

Deliberately circular: for mutual recursion X↔Y with both masters done and
each parked only on the other, the greatest fixpoint declares both complete
together — correct, nothing can ever wake either. This is SLG's SCC
completion; structurally it is distributed termination detection
(Dijkstra–Scholten: count outstanding work, drain to zero) over the entry
dependency graph.

## 3. Why the substrate resists

- **Failure is silence.** CPS success calls the continuation; failure
  doesn't. A dead branch notifies nobody. The saving fact: FIBER completion
  is observable even though branch death isn't — the master's produce fiber
  completing means its subtree is explored (minus what parked), and
  respawned consumers are detached fibers whose completion can be wrapped.
- **No ancestry.** A Registration records (k, pkg, argsTerm, index) but not
  whose production it serves; respawn detaches, erasing lineage.
- **The only free signal is global.** Scheduler-dry completes everything at
  once, at end of search — useless for mid-solve pricing.

**The delimited-region reading (what the counting actually is).** What
completion wants is a DELIMITED drain — "run this production as its own
little search, tell me when IT reaches quiescence" — and a private
scheduler would give it free: its empty queue IS the region's fixpoint.
All frames share one queue, so the EnclosingCall coat re-attaches the region
identity the shared queue erases, and the counters reconstruct the
emptiness event: counting is how you virtualize a delimited scheduler
inside a global one, and it is the PRICE OF FAIRNESS — an actual nested
drain (SLG's local evaluation) detects trivially but lets the region
monopolize the search. This is also the second application of one move.
The engine's original quiescence problem (master/slave deadlock) was
solved by PARK-AS-DATA: a consumer that catches up ends its fiber instead
of waiting, which makes "fiber alive" mean "real work exists" — and then
the global drain became a sound fixpoint signal for free. Detach-k is the
same lifetime-restructuring one level down: it makes "master fiber alive"
mean "PRODUCTION work exists", so fiber-end carries the semantic the
counters need. The pattern's third shipped instance: Fiber.fork's
countdown latch is a spawned/finished pair for TREE-shaped regions
(children cannot leak); tabling needs tokens because its regions are
graph-shaped — parked continuations escape through the shared table.
The same escape is exactly why Aggregate (a REAL delimited drain: the
sub-search fused into one fiber, fiber-end = exhaustion) is unsound over
tabled goals: parking leaks work out of its region, and the completion
flag is the repair — for escaped work, region-end must be replaced by
entry-completion.

## 4. The design, tiered

**Tier 0 (free, already implicitly true):** scheduler-dry ⇒ all entries
complete. End-of-search only.

**Tier 1 (as built):**
- **The coat (`EnclosingCall`)**: a plain transport Store riding the
  Package, naming the innermost enclosing tabled CALL — the event whose
  ledger pays for this work (goals are text, calls are events; one goal
  object under two bindings is two calls, two ledgers, two seals). THE ONE
  RULE: state follows the data, the coat follows the CODE — it changes
  exactly where control crosses a call boundary (stamped on entry, restored
  to the remembered caller's coat on answer exit) and is carried untouched
  everywhere else: forks inherit it, parked Registrations freeze it, wakes
  resume it. Branch-local by construction — no trail, no unwind, no
  thread-locals.
- **The primitives** (tabling/primitives, logic-free, generic): a
  `MonotoneCell<V,S>` holds each entry's answers — a persistent
  `JoinSet<A>` value (join-semilattice; join-idempotence IS the dedup
  discipline, gate-checked) plus parked subscribers, grow-wakes,
  park-races-grow; a `WorkLedger<S,P>` holds each entry's work — the
  RUNNING half as the Dijkstra–Scholten monotone counter pair, the
  SLEEPING half as who-sleeps-where. `counted()` is the ONE pairing
  discipline every unit of work passes through (start ticks at wrap time —
  no gap for a racing seal — finish at fiber end).
- **detach-k**: produce caches each answer, then detaches its downstream —
  the CALLER's code, re-coated and billed to the caller (a nested master's
  downstream can still derive caller answers). The master's fiber
  completion then means BODY EXHAUSTED, the event the counters need.
- **The seal rule, no Tarjan** (fused into `Region.sealCascade`): ledger
  quiescent (counters drained, every sleeper parked home or at a sealed
  region — the predicate is the theorem, not domain input) → flag CAS →
  drain the parked subscribers (provably dead). The cascade rechecks each
  dead sleeper's owner — seals propagate backwards along sleeper edges,
  leaves first. The ONE domain input is `ownerOf`: a sleeper belongs to
  the region of the call whose body it is a line of — its coat.

**Tier 2 (deferred):** seal the strongly-connected components of the
sleeper-edge graph atomically (§5a) — table-side union-find over entries,
SLG-WAM's approximate-SCC design, over-merging sound. The scheduler stays
untouched.

## 5. Flag semantics: keys-final, NOT values-final

Semiring-proofing (the dragon check): under semiring tabling the cell is
Map<AnswerTerm, V> — a duplicate derivation ⊕-combines into an existing
key's VALUE, but the KEY SET grows idempotently under every semiring (map
keys are a set regardless of ⊕). So respawn-on-fresh-key and the
counter-drain argument survive non-idempotent plugs unchanged. The flag
therefore means KEYS-FINAL: no new answer bindings. That is all any current
customer needs — pricing (a tabled call emits once per key; multiplicity is
weight, not emission), negation/ifte (no new bindings), reclamation,
prefetch. VALUES-FINAL is a separate future flag belonging to semiring
tabling's star machinery: cyclic derivations diverge under counting unless
closed-semiring star computes them algebraically; on acyclic SCCs it
coincides with keys-final for free. Do not conflate the two flags.

## 5a. Coverage and limits: the two-edge graph

The runtime structure is a graph whose NODES are call events (table
entries — born when a call reifies to an unseen pattern; `path(1,_)` and
`path(2,_)` are two nodes) and whose EDGES are what a tabled call
occurrence inside a body turns into — exactly one of two kinds:

- **Master edge** (the pattern is FRESH): this occurrence masters the new
  event; the new body splices into the current fiber. Discharged by
  ordinary control flow — never in any ledger, can never block a seal.
  Every event is mastered exactly once, so master edges form a TREE: the
  call tree of events, rooted at the query.
- **Sleeper edge** (the pattern ALREADY HAS a master): this occurrence
  reads the existing event; if it catches up before that event seals, it
  parks — recorded in ITS OWN call's ledger ("a piece of me sleeps at E").
  The only edge kind a seal waits on. Discharged by E growing (wake) or E
  sealing (dead sleeper, owner rechecked).

The picture is always A TREE OF MASTER EDGES DECORATED WITH SLEEPER
CROSS-LINKS, and it is not an analysis — it is literally the data: nodes =
the Table's entries, master edges = who tracked whose produce, sleeper
edges = the (registration → parked-at) pairs in each ledger's sleeping
map; the cascade is the backwards edge-walk as a queue.

**THE SEAL CRITERION: the sleeper-edge graph is acyclic up to self-loops.**
A node seals when its fiber work drains and its outgoing sleeper edges all
point home (self-loop: waking would need a new answer here, circularly
impossible) or at sealed nodes; sealing kills the sleepers parked here and
rechecks their owners — seals propagate backwards along sleeper edges,
leaves first. A CYCLE of sleeper edges through distinct unsealed nodes
never seals: each refuses on the other's account, no counter event
remains. NOTE what the criterion is NOT: it is not "no cycle in the
variant call graph" — nested mutual recursion IS a variant-graph cycle
and seals fine, because its p→q direction is a master edge.

How recursion maps: at each recursive call occurrence one question decides
the shape — DOES THE CALL REIFY TO A FRESH PATTERN OR THE SAME ONE?
Arguments that change (descending/right recursion) mint fresh events:
recursion-as-TREE, no sleepers, seals trivially. Arguments that revisit
the same question park: recursion-as-BACK-EDGE — a self-loop (fine) or,
cross-linked with another, a cycle (Tier 2). The five pinned cases
(`═` master, `┈` sleeper; tests in TableCompletionTest):

    left recursion               variant chain
    query ═ path(1,_) ⟲┈         query ═ p(1) ═ p(2) ═ p(3)
    seals: home rule             seals: no sleepers at all
    (leftRecursivePathCompletes) (variantChainOfNestedMastersSealsBottomUp)

    nested mutual                cross-root ring
    query ═ p ═ q                query ═ p     query ═ q
            ↑┈┈┈┘                        └┈┈> q       └┈┈> p
    seals: one edge, no cycle    unsealed: sleeper cycle
    (nestedMutualRecursion-      (crossConsumingRingStaysIncomplete)
     CompletesBottomUp)

    single-root double-read ring:  p :- 42 | q | q.  q :- p.
    the SECOND q-occurrence is a reader (masters are once-per-event):
    p ┈> q and q ┈> p — a cycle from one root; both stay unsealed
    (secondReaderInsideNestingFormsARingAndStaysUnsealed)

Why counters alone cannot see a ring: both ledgers drain — no live frames
anywhere, which is TRUE — but the residual waiting is a ring of parked
registrations: data in the table, not frames in the queue. "Can anything
ever run again" is reachability over sleeper edges, not counting. A
scheduler Region facility in its naive form (per-tag frame counting) does
not help — regions see frames, the cycle lives in what isn't a frame.
Tier 2 seals sleeper-SCCs atomically: union-find over entries, merging
when a reader parks at an unsealed foreign entry; over-merging is sound
(entries seal later, together). Billing note for nested masters: the new
event's BODY is billed to the new event's own ledger; the caller pays only
for CONSUMING the answers (each answer-exit downstream re-coated and
billed to the caller). "Does the existing entry contain what I need" is
answered today by PATTERN EQUALITY only — upgrading that check to
entailment against a SEALED general entry is subsumptive reuse (§8a).

Degradation per customer when Tier 1 cannot detect (sleeper cycles):
pricing → ∞, wrong-only-slow; prefetch → stays a barrier, graceful;
reclamation → deferred to end-of-solve; the completion-GATED features
(sound aggregate/negation/ifte) → the gate never opens — an
expressiveness boundary, not a performance one, failing CLOSED: a
suspension parked on a completion that never arrives is flushed as a
failed branch at end of search (no answer, never a wrong answer). The
asymmetry is by construction — every customer treats "undetected" as the
sound side — and it locates Tier 2's real motivation: the optimizer
merely goes faster with it; negation over variant cycles does not EXIST
without it.

## 6. What the substrate pays us (and what it doesn't)

- **Immutability kills the classically-hardest layer.** SLG-WAM's most
  intricate machinery is stack freezing — preserving a suspended consumer's
  environment across backtracking. Our Registrations park a package VALUE:
  no freeze/melt protocol, and the EnclosingCall coat is branch-local with zero
  maintenance.
- **The flag is an upward-closed fact** (direction principle): once
  complete, forever complete. Racy reads are sound in one direction — a
  stale "incomplete" prices ∞ (sound), a read "complete" is permanent — so
  the pricing pass reads it LOCK-FREE under parallel schedulers.
- **addAnswer's duplicate check IS the strict-ascent guard** — the dual of
  DomainUpdate's equal-domain guard and MonotoneDrain's strict-descent law:
  respawn only on strict answer-set growth. It terminates the DETECTOR too:
  no duplicate respawns = no counter churn = liveWork drains on recursive
  predicates. Under semiring tabling it generalizes (respawn on strict
  value improvement in a finite-height order — superiority territory)
  rather than breaking.
- **The D–S split restores direction analysis to a non-monotone quantity**:
  liveWork goes up and down, but spawned and finished each only grow. Read
  ordering matters: read spawned first, then finished — a stale finished
  undercounts (sound "not yet"); the opposite order can lie.
- **The non-dividend, honestly:** placing the increment/decrement wrapping
  points around produce and the detached respawns is event-accounting
  exactness. A leaked decrement never completes (sound, useless); a doubled
  one completes early (unsound — wrong prices, wrong negation). No algebra
  places the wrapping points; tests do.

## 7. Considered and declined: a failure continuation

Double-barreled CPS (success + failure continuations, the classic
Prolog-compilation encoding) was assessed for both completion detection
and explainability, and declined for both.

**For completion detection it is neither needed nor much help.** The
detector counts at FIBER granularity, and fiber completion is already
observable (§3's saving fact); a failure continuation adds
branch-granularity death events the counters have no use for —
parked-vs-dead is distinguished by registration records, and "subtree
exhausted" IS fiber completion. It would spare the two andThen wrappings
at the price of retyping `Goal`, the engine's central type. Even for
negation it does not substitute: knowing A branch failed is not knowing
ALL branches failed — that universally quantified fact is the completion
flag, and per-branch callbacks do not aggregate into it without rebuilding
the counters.

**For explainability the reasons live elsewhere.** A branch almost never
knows why it died; it dies because a primitive refused. Exactly two
chokepoints hold a reason at the moment of refusal: unification (which
terms clashed) and Propagation's revision-fail (which store, which item).
Everything else is exhaustion — an aggregate of reasons, not a reason.
Double-barreled CPS threads a failure channel through a hundred call sites
with nothing to say to reach two that do. The design with ~90% of the
payoff at a fraction of the cost, all on existing seams: failure LISTENERS
at the two chokepoints (the StepListener/Trace pattern — seeded per solve,
zero cost when absent, payload = state + culprit), the box model's Fail
ports for tree structure, and completion certificates for the
universally-quantified half.

## 8. Failure explanation is a completion customer

"Why did this query fail" decomposes into LEAF REFUSALS (the chokepoint
payloads above) plus an EXHAUSTIVENESS CERTIFICATE — "and these were all
the branches" — because a why-not answer is a universally quantified
claim. Over a tabled sub-search, the certificate IS the keys-final flag.
Provenance theory says the same from the other side: why-provenance rides
successes (Green–Tannen semirings); why-NOT provenance needs the dual
account of what did not happen, well-defined only over a completed space.
A failure continuation supplies neither half — not the reasons (branches
do not know them) and not the certificate (branch events do not
aggregate). Explainability therefore queues BEHIND completion detection,
not beside it.

## 8a. Subsumptive reuse — closing the reorder loop

Completed-entry pricing dissolves the barrier, so goals may cross a
completed tabled call — and crossing a BINDER over it splinters
execution into fresh more-bound variants. Three ledgers, because "safe"
means three things: ANSWERS are safe unconditionally (conjunction
commutativity; each variant self-contained); PRICE BOUNDS are safe by
the subset property (a more-bound call emits ≤ the priced count, so
every product the sort computed stays an upper bound — the
MonotoneLaws pin); COST is the blind spot — the sort can choose a
placement that abandons the completed cache and recomputes per binding,
because the multiplicity model deliberately does not see cache affinity
(and the abandoning plan is SOMETIMES right: restricted fresh runs can
beat enumerate-and-filter — the bound-join tradeoff).

The repair is engine-side, not a fence: SUBSUMPTIVE REUSE (task #44,
gated on Tier 1). A completed entry is a read-only relation; tabled()'s
lookup, before minting a variant entry, checks for a COMPLETED
subsuming entry (one-way instance matching on reified args — a
structural walk, far cheaper than anti-unification; per-relation scan
suffices before SubsumptionMap). Consumption needs NO new machinery:
consume() already unifies cached answers against the caller's args —
the filter more-bound calls need — and against a completed entry it
degenerates further (no parking, no registrations; iterate a frozen
list and end). Enabled by the per-solve shared Table: a completed entry
produced in one branch is consumable from every other. With reuse in
place, the sort may order freely and the cache is never abandoned —
"Herbrand call subsumption is the precondition for reordering completed
calls" (optimizer.md), now with its failure-mode derivation attached.
The asymmetric-fence alternative (completed calls sortable but
uncrossable) was considered and DROPPED as moot: nothing needs to hold
position for sharing reasons once sharing is automatic. Caveats: forced
reuse always picks enumerate-and-filter (a policy, benchmark-refinable,
not a theorem — a huge general entry against a tiny restriction can
favor fresh computation); completion is not strictly required for
soundness (mid-fill subsumptive consumption is sound in Herbrand:
stream, park, filter) — the completion gate buys simplicity and
profit-certainty first, mid-fill is a later relaxation.

## 9. Test anchors

- Left-recursive path over a finite acyclic graph: entry completes
  MID-SOLVE (not at scheduler-dry), flag observable, registrations
  discarded.
- Mutually-recursive pair: does NOT complete under Tier 1; solve results
  unchanged; prices stay ∞.
- Duplicate-heavy relation: counters drain despite duplicate derivations
  (the strict-ascent guard at work).
- Parallel scheduler run: same completions, no synchronization on the flag
  read path.
- The ∞→exact pricing pin rides #36's consumer (b) once both land.
