# The engine as a domain layer — DESIGN (July 2026, nothing built)

The pldb phase's driving design: what real programs — databases behind,
REST in front, DDD in the middle — look like when the engine is the domain
layer. The thesis is Out of the Tar Pit's, with the machinery to cash it:
**essential state is base facts; everything the business asks is a derived
relation; reads are projections of the fact base and writes are absorptions
into it — one algebra** (`split`/`rename` depart, `meet`/`normalize`
arrive; constraint-kernel.md has the store contracts, lattice.md §5a the
theory). Status: DESIGN. The build list is §8 (the seam §5, versioning §6, execution §7); the driving example is
chosen and everything else is expressed against it.

## 1. The driving example: caveated authorization

Every system has authorization, and it rots the same way everywhere: a
role column, then an if-else jungle, then a service nobody trusts. The
domain is genuinely relational and genuinely recursive:

- users belong to groups, **groups contain groups** — the membership graph
  acquires cycles; recursive resolution over a cyclic graph is why
  Datalog-shaped authz engines (Zanzibar, SpiceDB, OPA) exist. Untabled
  resolution loops (`WhodunnitTest` pins the shape: every lap of a cycle
  is another derivation);
- permissions are **delegated** — head grants deputy, resources inherit
  from folders: `mayRead(user, doc)` is transitive closure through several
  relations at once;
- real grants are **conditional** — "during Q1 close, from a managed
  device, under 50k". Each delegation step carries its own window and
  limits; the effective condition of a chain is the MEET of every
  condition along it, and the chain itself is nobody's business.

The mechanism map — every row is something already shipped:

| domain fact | engine mechanism |
|---|---|
| cyclic membership/delegation graphs | tabling: termination + one entry per question |
| windows, limits, trust levels on grants | FD domains and couplings — the residue IS the caveat |
| effective condition = intersection along the chain | the meet, computed by propagation during derivation |
| "∃ some chain of grants" | witness locals riding cached answers (stage 2.5) |
| "may Alice read this, now, for 30k?" | consumption: unify, restate, verify — elimination from the cached region |
| "what may Alice read?" (the list endpoint) | a REGION answer — compressed ⊕, labelled only at pagination |
| grant tuples in Postgres | the row-set store (#61): a relation as a factor, absorb its front door |
| the IdP / device-trust REST call | Fiber.external (#64): a suspended fiber, deduplicated by the table |
| "not the author may approve" | Neq, on keys and answers |
| per-request evaluation, policy epochs | the per-solve Table; revocation re-epochs (§6) |

Three queries front the bounded context: `check` (a consumption),
`list` (a region answer), `explain` (the box-model tracer rendering the
derivation — the WHY endpoint, for free). A caveat that survives
reification ("allowed, given business hours") is a RESPONSE THE CLIENT
CAN ACT ON — Biscuit/SpiceDB caveat semantics falling out of conditional
answers instead of a bolted-on DSL.

The commercial argument, sharpened: **the conditional-answer cache is the
product.** Authorization services live on their cache, and hand-built
caches over conditional logic are where the vulnerabilities live (cache
"allowed" without its condition and you shipped a hole). This cache is
correct by construction — the condition is part of the answer's identity,
dedup is entailment, reuse is containment.

## 2. Caveats are values (what a residue is FOR)

A canonical factor — hole-named domains plus NAMED couplings — is not "a
map with propagators"; since value-equality landed, a propagator is
(name, terms): a syntax node. A caveat is a value with four consumptions,
in order of importance:

1. **Absorb it later — the caveat is executable.** Admission returns a
   ticket; commit is `absorb(ticket.rename(holes ↦ actuals))` — mint,
   meet, normalize; ⊥ means the condition lapsed. This is `consume`'s
   replay path, public.
2. **Query it**: admissibility of a value (absorb a binding, check ⊥),
   bounds (`getDomain(hole).min()/max()`), emptiness.
3. **Compare it**: `leq` is grant subsumption — "does the auditor's
   permission imply the intern's?" — the PartialOrder as a policy-analysis
   operation.
4. **Render it**: slots map to field names by the hole↔column
   correspondence; couplings serialize as {name, args} nodes. Cross-process
   transport needs a name→body registry per store at load (§5.3) —
   in-process the object travels and there is no gap.

## 3. The read path: capture-solve (the one missing door)

`solve` today emits through `enforce` — FD labels, regions grind to
points. The engine ALREADY has the other emission mode: `Tabling.produce`
reifies with holes and captures each store's factor normalized against the
answer's substitutions — the conditional answer. It is just private.

**Capture-solve**: a public solve variant yielding (term-with-holes,
canonical factors) per answer instead of labelled points — the produce
path with a return value instead of a cache insert, and the first consumer
of `AnswerKey` outside tabling. Three outcomes make the validation/read
API: no answers = REJECTED (and `trace()` is the explanation); ground with
empty factors = ACCEPTED, possibly COMPLETED (fields inferred); term plus
factors = ADMITTED CONDITIONALLY — the ticket.

## 4. The write path

### 4.1 Validation is admission, and admission returns tickets

Run the candidate write as a goal against facts + invariants, in capture
mode. Propagation upgrades validation twice over imperative checkers: it
COMPLETES the write (domains narrow, derived fields inferred), and it
accepts CONDITIONALLY (the ticket: "accepted GIVEN amount ≤ remaining
budget" — a hold or reservation as a first-class object, not an error
code). At commit, absorb the ticket renamed onto the actuals **against
current facts**: the residue guards the row-shaped condition, normalize
guards freshness — stale-validation and TOCTOU, two classically separate
bug farms, collapse into one operation because absorb re-verifies by
construction.

### 4.2 Transactions ARE absorbs

A transaction is a factor: new tuples plus their constraints. Commit =
meet into resident state + re-verify what the meet touched + die whole on
violation — `absorb` verbatim; `Revision.fail` IS rollback. Persistent
packages give MVCC for free: a transaction is a branch, a failed absorb
never becomes anyone's package, commit makes the package the epoch root.
The write path and the read path share one algebra.

### 4.3 DDD aggregates are split frames

An aggregate, stripped of ceremony: the set of facts whose invariants must
be checked together in one transaction. That is a var-list, and the
aggregate boundary is `split(aggregateVars)` — the covered half is what a
command touches and re-normalizes atomically; the remainder is other
aggregates' business, eventually consistent by decree. DDD's haziest
concept becomes checkable: the boundary is right iff commands' factors
split cleanly, and `_1 ∧ _2 = this` certifies nothing was lost drawing it.

### 4.4 Canonical factors are stored constraints (the marshal)

Grants carry caveats IN the database. A hole-named factor is lineage-free
pure data: `project`-to-canonical is marshal, `rename(ofSlots(...))` at
load is unmarshal — the single-sorted refactor made constraint factors
column-storable values. Inserting a rule/caveat/policy is inserting a
canonical factor; FactSource re-instantiates by renaming. Rules are rows.

### 4.5 Append-only streams are the native write model

Event sourcing's commandment — the log appends, never retracts; state is
derived — is the engine's commandment: tables grow monotonically. CALM
(lattice.md §5a) is the formal coincidence; even "deletion" agrees
(cancellation is a POSITIVE appended fact; deletion is a derived
judgment). The pipeline:

    log ─► FactSource ─► absorb ─► base facts ─► tabled derived relations
        ─► standing queries with PARKED consumers ─► derived events out
            (execution model: §7 — realized as per-watermark reruns of
             cold one-shot solves; the resident driver is parked)

**The parked-consumer machinery is an incremental view-maintenance
engine**: an arriving event flows through the fixpoint and wakes exactly
the consumers whose entries gained answers — the delta, not a recompute.
Standing queries' outputs are themselves append-only streams, so engines
compose (Kafka-Streams topology written as relations; the Tar Pit's FRP
with a pulse).

Four payoffs, each bought by a checked law:

1. **at-least-once → exactly-once semantics**: absorbing a duplicate event
   is idempotent re-posting (`x⊕x=x`, structural since duplicate posts
   merge) — redelivery is a no-op by the lattice, not by a dedup table;
2. **out-of-order tolerance**: ACI / chaotic-iteration freedom — partition
   order does not matter to the monotone fixpoint;
3. **coordination-free read models**: CALM cashed — replicas absorbing the
   same log converge without talking;
4. **sound negation/aggregates via the seal**: "not yet cancelled" and
   `count(overdue)` are nonmonotone; a SEALED entry answers them soundly
   (the seal is the finality certificate — the quotient tower's runtime
   descent). On a live stream nothing seals, which forces the design
   nugget:

**Seal-at-offset.** Completion detection today certifies "producer
exhausted". Generalize the certificate to "producer reached the barrier":
a FactSource declares an offset horizon; entries it feeds may seal AT the
horizon; negation and aggregates are then sound as-of-N — the consistency
token event-sourced APIs already pass around. One sentence to keep: THE
LOG OFFSET IS THE SEAL CERTIFICATE OF A LIVE SOURCE. This slots into
Fiber.external's contract (external-completions-only, injection queue,
outstanding counter — fiber-external.md in functional).

Commands close the loop: a command handler is a goal deriving against
read models whose ANSWERS are the events to append — and a caveated
answer is a PENDING event (`SeatHeld GIVEN paid-by ∈ [now, now+15min]`),
the residue riding the event as data (§4.4), the expiry check an absorb.
Provisional state without timeout sagas.

Honest edges, which are ES practice in this vocabulary: business reversal
= compensating event (monotone, fine); hard deletes and revocation =
re-epoch = projection rebuild (their standard procedure, not our
embarrassment); snapshots = persist a sealed entry's answers in canonical
names, boot = absorb them + consume the log tail (marshal at rest, rename
at load — §4.4 closing §4.5's loop). One discipline that is NOT a law:
counting/weighted projections lack idempotence (`x⊕x≠x` in counting) —
redelivery tolerance requires event IDs in the fact (the standard ES fix,
mapped directly).

## 5. FactSource: a small seam with prepared sockets

The seam is deliberately pldb-shaped and stays that way — "a relation
whose extension lives elsewhere", the EDB half of a Datalog system:

    FactSource<T>:  rows(pattern)   — tuples matching a partially-ground pattern
                    count(pattern)  — cardinality estimate (cheap, approximate)
                    modes()         — which binding patterns are answerable

It knows nothing about tabling, absorb, logs or fibers. Everything larger
in this document is a COMPOSITION: the engine already has the sockets, and
one call `employee(id, name, dept)` passes through them in order:

1. **walk** — the args against the substitutions: the pattern.
2. **suspend** — pattern unanswerable per `modes()`: park via
   `Propagation.suspend` until the search grounds enough (the "deferred
   lookups on suspensions" note from the kernel work, verbatim).
3. **price** — the goal is `Bounded.sighted` with `count(pattern)`: the
   conjunction-ordering optimizer orders JOINS by source cardinality —
   a query planner falling out of the existing pricing seam.
4. **project = query compilation** — a TCLP call key is (pattern,
   canonical residues), and a canonical residue over the call vars
   COMPILES TO A PREDICATE: `dept ∈ {sales,hr}` on slot 2 is
   `WHERE dept IN (...)`; a leq coupling is a comparison. The same
   operation that keys the cache pushes the constraint down to the
   source; the SQL adapter (#60) reads the residue, the seam stays dumb.
5. **table** — wrap in `Tabling.define`: same pattern twice = one fetch,
   and subsumption serves `employee(42,_,sales)` from the fetched
   `employee(_,_,sales)` entry — a query cache correct by containment.
6. **deliver** — the fork, both modes BEHIND the seam: per-row `unify`
   branches (a Conde at call time — the humble baseline) or the whole
   result set as ONE factor absorbed into the row-set store (#61) —
   rows-as-region, GAC propagation. Which mode is per-call economics
   (ten rows: branch; ten thousand feeding a join: factor).
7. **seal** — a query's result is ALL matching rows: per-pattern
   completeness is the certificate the entry's seal needs — counts go
   ∞→exact for the optimizer, negation over the source becomes sound.
8. **transport** — whether `rows()` blocks or suspends a fiber is a
   delivery detail; Fiber.external (#64) makes it a suspension.
   Orthogonal to all of the above.

One apparent enlargement is a composition, not interface growth:
CONSTRAINED ROWS (a column holding a canonical factor, §4.4) change the
row's TYPE — delivery must be rename+absorb instead of unify — not the
seam. The other apparent enlargement is a CATEGORY ERROR, corrected:
**FactSource has no streaming species — logs are not sources.** A log
answers exactly one question ("what's after offset N?"); the seam's
value is answering ARBITRARY questions (the walked pattern, residues as
predicates, count for the optimizer) — and "implement rows(pattern) by
scanning the topic" is a parody, not an implementation. The industry
name is STREAM–TABLE DUALITY: a stream is a table's changelog, a table
is a stream's materialization, and queries go to tables, always:

    topic (transport, ordered, unqueryable)
      │  consumed by a MATERIALIZER — an adapter, never the engine
      ▼
    materialization (a DB table via a sink connector; an in-engine
                     row-set factor absorbed per wave, #61)
      │  fronted by FactSource: rows/count/modes/pin
      ▼
    the solve — which never sees the log at all

The log's offset SURVIVES the demotion: the materialization's `pin()`
token is "applied through offset N". FactSource stays finite,
pull-based, pldb-pure — with no exceptions at all.

## 6. Versioning: epochs, pins, and the rest of the world

Derived knowledge is only as valid as the base facts it was derived from,
and base facts change in two ways with OPPOSITE consequences for a
monotone cache:

- **append**: every cached answer stays SOUND (more facts only add
  answers); what breaks is COMPLETENESS — a sealed entry's "these are all"
  becomes "all as-of N". Seal-at-offset (§4.5) is the repair, not a
  re-derivation.
- **retract/update**: cached answers can become WRONG, and a monotone
  engine has no un-derive. The only sound move is a line: discard behind
  it, re-derive. **That line is the epoch** — the validity scope of
  derived knowledge. Within an epoch, tables accumulate and are sound; a
  non-monotone change ends it; invalidation is wholesale by version
  comparison, never per-entry surgery.

The engine already epochs at the finest grain: the per-solve Table makes
EVERY SOLVE ITS OWN EPOCH. Persistence is what extends an entry's life
past its solve, so a persisted entry carries the proof that "current"
has not moved non-monotonically since: the epoch stamp.

### 6.1 Epochs are borrowed, not computed

Never hash source data (O(data), racy, redundant). Every serious source
maintains a version token as part of ordering its writes — an LSN, an
exportable snapshot, a log offset, a bumped counter — and the engine
READS it. The seam grows one operation:

    FactSource<T>:  rows / count / modes
                    pin() → Snapshot     — the token, plus reads served as-of it

`pin()` at solve start does both jobs at once: the consistent read (no
torn world mid-solve) and the epoch stamp for anything persisted.
(August 2026, condition.md §8.7: `pin()` is one instance of the
freeze-and-certify pattern the constraint ring unified — pin stamps are
EPOCH FACTORS, `t GIVEN source@epoch` as an ordinary conjunct; memo
validity = `leq` on the factor, replay = `restate`. Phase 4 should
build the stamp as a `Residues` citizen, not a bespoke mechanism.) A
derivation's epoch is a VECTOR — {source → token} for each source it
read (a dependency footprint: which SOURCES, not which facts). Reuse
checks per source, and the two kinds check differently:

- append-only: token = offset; reuse sound at ANY current offset,
  completeness marked as-of-N — no equality required;
- mutable: token must EQUAL the source's current version; a change
  re-epochs the entries depending on THAT source only. The vector buys
  partial invalidation for free.

The upgrade path beyond epochs, if surgical invalidation ever earns its
complexity, is provenance (semiring-inference.md): record which FACTS
support an answer, delete exactly the dependents (DRed-shaped). Epochs
are the simple model that is correct now.

### 6.2 Rows: offsets and validity intervals, not version fields

The epoch token is per-source, not per-row. Where row-level versioning
seems wanted, two honest shapes exist:

- a LOG row carries its offset — its address in the order, which IS the
  source token materialized; nothing to add;
- a mutable relation worth versioning row-by-row should get **validity
  intervals** instead (`valid_from`/`valid_to` — SCD-2/bitemporal): an
  update becomes an APPEND (close the interval, open a row), the relation
  becomes MONOTONE, and its epoch problem DISSOLVES — no re-epoch on
  update, because there are no updates. "Current" becomes a constraint
  (`valid_to = ∞`; as-of-T a time-window predicate), and time windows are
  FD domains — as-of queries ride the caveat machinery. Only true erasure
  still re-epochs, which no scheme escapes.

### 6.3 Foreign REST sources: the cooperation ladder

Sources we do not control grant no snapshots. The ladder, by decreasing
cooperation — and at the bottom the model degrades into CAVEATS rather
than breaking:

1. **ETags** (`If-None-Match`): per-resource version tokens over HTTP;
   `pin()` records them, reuse revalidates at the cost of a 304. The
   token vector gains per-resource grain.
2. **Leases**: `Cache-Control: max-age`, or a declared policy Δ. A lease
   is a VALIDITY INTERVAL STAMPED AT INGESTION — §6.2's move applied at
   the boundary: `trusted(device)` observed at 14:02 with a 5-minute
   lease is the fact `trusted(device) valid ∈ [14:02, 14:07]` — the
   unversioned source converted to monotone-with-expiry, and the window
   rides derivations as a caveat: "allowed GIVEN now ≤ 14:07". Staleness
   surfaces as machine-checkable domain content instead of hiding in a
   cache.
3. **The observation log**: route every foreign read through an ingestion
   log — "at t, API X said Y". Their state cannot be versioned; OUR
   KNOWLEDGE of it can, and observations are monotone by construction
   (the world changes; that the API said Y at t stands forever). The
   foreign world becomes one more append-only source: epoch = our
   offset, replay works, and `explain` answers "why did we allow this in
   March" with the observation that justified it. This is DDD's
   anti-corruption layer given a precise job.
4. **Non-sources**: an endpoint with effects (charge the card) is not a
   read and never enters FactSource — it is the command side (outbox,
   sagas; Fiber.external as transport, different seam).

### 6.4 Distribution: no global epoch

No coordination service and no global clock. Each token's authority is
its own source; the vector IS the cross-source epoch; sources never agree
on a shared counter. Append-only sources need nothing at all — replicas
at different offsets serve sound as-of answers independently (CALM,
again). What cannot be had without paying: a SIMULTANEOUS snapshot across
independent mutable sources — ordinary distributed read-skew, mitigated
per-request or by unifying sources behind one log, at which point the
epoch degenerates to a single offset ("one log makes the epoch one
integer" — §4.5's architecture paying again).

Client-visible consistency is the token PASSED THROUGH THE API
(Zanzibar-style consistency tokens; session guarantees in the classic
vocabulary): a write returns its epoch token, the client hands it back,
the engine evaluates as-of ≥ it — read-your-writes without global
synchronization. Between our own engine-backed services the vector
forwards end-to-end (a downstream service's response token is our
`pin()` token for it), so consistency composes across the estate;
foreign sources terminate the chain by the ladder above. The one-liner:
the rest of the world does not grant consistency — the engine converts
that into VISIBLE, CHECKABLE staleness instead of invisible, hoped-for
freshness.

### 6.5 Snapshot mechanics: one pin per solve

The pin is per-SOLVE, not per-answer. At solve start (or lazily at each
source's first touch) the source pins and the handle joins a solve-scoped
`Snapshots` store — the Table pattern: a plain Packaged citizen, invisible
to propagation. All reads go through the handles; answers carry no stamps
in memory (they would all carry the same one — the vector is a property of
the solve, ambient). The stamp materializes only where something LEAVES
the solve: the client response (one vector per reply — the consistency
token) and the persisted entry (one vector per entry).

The pin is not an added luxury: TABLING ALREADY IMPOSES SNAPSHOT
SEMANTICS, implicitly. The first call to a source-backed relation caches
what its fetch saw; every later call is served from the entry — the
extension is frozen at an accidental instant, per relation, uncoordinated.
`pin()` takes the snapshot the Table was already taking and makes it
declared, simultaneous across sources, and reportable.

The seal's certificate is against the composite: `sealed@{source→token}`,
complete fixpoint over the facts visible in that vector. "Up to N" is a
property of COMPONENTS, not the vector: an offset component is ordered
(reuse composes — a reader pinned ≥ N warm-starts and consumes the tail);
a mutable component is an equality point (reuse demands ==, no "up to").
One log collapses the vector to a single ordered component. Stamping
starts COARSE (the whole solve's vector per entry — sound,
over-invalidates); the per-entry dependency footprint is the refinement,
evidence-gated like every sharpening in this engine.

## 7. Execution at the boundary: the engine stays cold

The correction that shapes everything here: reactive-streams territory —
per-item demand, error propagation, cancellation, backpressure — is NOT
where the engine belongs, and it does not need to go there.

**Solve is cold and pull-based, and that is load-bearing.** The answer
stream's `tryAdvance` drives the scheduler: no pull, no work — request(1)
semantics by construction. A one-shot solve has perfect backpressure,
error = exception to the caller, resources scoped to the call. Preserve
this property; do not build a hot engine.

**Effects never live in goals.** A goal is a semiring element — the
optimizer's whole license is reorder/factor/price, and a side-effecting
goal is not reorderable; worse, goals run per derivation branch (a branch
that emits then fails has published a lie) and respawned consumers re-run
continuations (per-respawn double-fire). The boundary is already the
effect seam: sources are adapters IN (FactSource), sinks are adapters OUT
— the solve returns stamped answers and the SUBSCRIBER does IO.
Transactional produce, retries, outbox: the adapter's problem, solved
with the infrastructure's own tools.

**The execution model for live data: a reactive runtime outside, the
engine as a pure function inside.**

    reactive pipeline (backpressure, errors, disposal — its job)
        per batch/watermark:  poll log → advance materialization (§5) →
                              pin@offset → one-shot solve (warm) →
                              stamped answer SET out

The WAVE is the honest demand unit: per-answer backpressure inside a
fixpoint is not honorable (a propagation cascade is as big as it is);
inter-wave flow control is natural because both edges pull (a Kafka poll
IS demand). This is batch processing at watermark granularity, said
plainly.

**The law that defines correctness**: a standing evaluation at watermark
V is observationally equivalent to a fresh one-shot solve pinned at V.
V1 IS rerun-per-pin — zero new semantics, correct by construction,
blunted by warm starts. A resident "standing driver" (cursors preserved
across waves, re-arming aggregates, notify-not-kill barrier seals) is
PARKED as a possible optimization behind the same pure interface, with an
explicit admission test: (a) measured rerun cost a real workload cannot
bear, AND (b) designed demand, error and cancellation semantics. Note
what rerun-per-pin dodges: a resident driver's operator state is parked
continuations — CLOSURES, which cannot be checkpointed; stateful
streaming engines built their hardest machinery (aligned barriers,
atomic state snapshots) precisely because their operator state is not
reconstructible. Ours is: log + pure function; the cache is an
accelerator, never consistency-critical state.

### 7.1 Exactly-once: the adapter transacts, purity does the rest

    loop { begin txn
           batch   = consumer.poll()                  // offsets (N, M]
           answers = solve(query, pin@M, warm)        // PURE, deterministic
           producer.send(stamped answers)
           producer.sendOffsetsToTransaction(M)       // offsets+outputs atomic
           commit }

Crash → abort → redeliver → the solve REPRODUCES the outputs
(determinism). Two obligations and one liberation:

- **determinism of answer SETS** is the leaned-on property (answer ORDER
  under parallel schedulers varies; the adapter emits waves as
  canonically-ordered sets, or downstream dedups on (key, stamp)). This
  wants its own pin, template: the scheduler-equivalence suite.
- event IDs belong in fact terms (entry dedup is by answer identity; two
  genuine equal payments must be distinct answers) — the counting
  discipline of §4.5.
- **the memo store needs NO transactional coordination: it is a cache of
  THEOREMS.** An entry stamped @M is a true statement about the prefix
  ≤ M regardless of consumer-group commit state; aborted waves re-derive
  identical entries; content-addressed writes make re-persisting a no-op.
  The cache may run ahead of the committed offset harmlessly. Only the
  classic pair — offsets + output topic — is atomic, and Kafka
  transactions cover exactly that pair natively.

This loop is §6.3's rung 3 GENERALIZED — poll the world forward, convert
to ordered observations, solve pure per pin, record how far you read
atomically with what you emitted. One pattern, one parameter: WHO
MAINTAINS THE LOG (the broker: Kafka, cost zero; CDC: the changelog
manufactured from the WAL; raw REST: you build it — the ingestion log).
`sendOffsetsToTransaction` is Kafka-specific sugar for the universal
obligation; the general form is the OUTBOX: one transaction over
(observations, cursor, outputs).

**Division of labor, decided:** base facts (EDB) land by COMMODITY
plumbing — a Connect JDBC sink into Postgres, `pin()` on LSN, the
FactSource SQL adapter unmodified; doing this "with logic" adds risk and
nothing else. Derived relations (IDB) are materialized BY THE ENGINE —
per-watermark solves persisting sealed answers back into tables ordinary
SQL clients can also read — because that layer is what nothing on the
shelf can express: a MATERIALIZED VIEW WHOSE ROWS ARE CONDITIONAL
(recursive closure with caveats, witnesses, entailment-deduped). We
compete with the streaming-SQL products (ksqlDB, Materialize et al.)
nowhere and complement them everywhere: they keep plain views fresh —
including true incremental retraction, the hard math we deliberately
skip — we produce the views SQL cannot define. Kafka itself stays
SPINE, NOT FACE: never a query surface, but the durable ordered source
of truth, the replay substrate every re-epoch and rebuild presupposes,
and the authority the pins are borrowed from.

### 7.2 Warm starts: the in-memory ladder first

Between waves and retries IN ONE PROCESS, before any persistence:

1. **Retry at the same pin = share the Package.** Shipped behavior
   (every shared-package solveFrom does it): the retried solve finds
   masters run and answers cached; retry costs ≈ emission. No
   coordination with the aborted transaction — theorems again.
2. **Advancing the pin = `Table.advance()`** — the one small new
   feature: per entry, clear the seal, reset the master flag, KEEP the
   answer set as a dedup seed; rebuild fresh entry shells (regions and
   ledgers are per-solve runtime — the answers are the durable part, the
   counters never were). Next solve re-runs masters, source entries
   fetch only the tail, re-derived answers bounce off the insert-guard,
   nonmonotone reads wait for the new seal. Sound because monotone. A
   pin field on Table guards misuse (assert solve pin == table pin
   unless advanced).
3. **Mutable component changed = drop the table.** `Table.empty()` is
   the in-memory epoch.

ACROSS RESTARTS, the persistence tiers (each optional, each gated):
T1 — persist SOURCE entries only (ground tuples: trivial marshal, no
registry) — avoids re-fetching history; derived layers recompute in
memory from warm base facts; small effort, dominant win (IO rules).
T2 — persist sealed DERIVED entries for exact-stamp reuse (as-of/audit,
repeated queries, cross-process): needs the marshal gaps — durable
relation names, `Renaming.canonicalizing` (witness locals mint HOLES at
persist, not lvars), hole-keyed `into` seeding at load, the name→body
registry, an `onSealed` write-behind. Each small, together bounded.
T3 — incremental extension of warm derived entries (≥-reuse plus tail):
semi-naive across solves = delta rules = bodies as data — GATED ON
GOALS-AS-DATA, indefinitely deferrable; T1+T2 with in-memory
recomputation approximates it wherever IO dominated.

## 8. Build list (dependency order — this IS the pldb phase plan)

0. **In-memory table reuse** (§7.2): the pin field and retry-at-pin
   assertion; `Table.advance()` (fresh entry shells, answers as dedup
   seeds); drop-table epoch — the enabling step for per-wave reruns.
1. **Capture-solve** (§3): produce's path public; small; first `AnswerKey`
   consumer outside tabling.
2. **#59/#60 FactSource**: the SEAM of §5 — rows/count/modes plus
   `pin()` (§6.1), an
   in-memory reference, then the SQL adapter reading call residues as
   predicates (§5.4); delivery starts as unify branches, the absorb mode
   arrives with #61.
3. **#61 row-set store**: relation-as-factor, GAC in `normalize`, absorb
   its front door — the store the theory seat predicted
   (lattice.md §5a, branches-as-data).
4. **#64 Fiber.external**: the REST/injection seam (designed, unbuilt).
5. **Determinism pin**: per-pin answer-SET determinism across schedulers
   (§7.1 leans on it) — template: the scheduler-equivalence suite.
6. **Seal-at-offset** (§4.5): the barrier generalization of completion
   detection (table-completion.md).
7. **#68 suspensions × tabled calls**: this workload trips it immediately
   (external checks suspend inside tabled bodies).
8. The bounded context itself: documents/folders/groups/delegated grants
   with windows and limits; Postgres behind; `check`/`list`/`explain` in
   front; each item above lands against it, not against toys.

## 9. Non-goals and limits

- **Not an ORM, not a general database**: base facts live in real stores;
  the engine is the derived layer and the write-admission layer.
- **Retraction is not monotone** — no in-place invalidation; epochs are
  the model (§4.5), matching ES projection rebuilds.
- **Cross-process constraint transport** needs the name→body registry
  (§2.4); in-process there is no gap.
- **Performance is unmeasured** on these workloads; the optimizer/pricing
  line (#63) and the substitutions step D remain benchmark-gated as ever.
- The weighted/semiring line (semiring-inference.md) composes here
  (counting projections, provenance for `explain`) but is NOT assumed by
  anything above.

## 10. Where knowledge lives

constraint-kernel.md (store contracts, absorb's triggers) ·
tabled-constraints.md (regions, witnesses, consumption) · lattice.md §5a
(the ⊕-placement theory this design leans on) · answers-as-diffs.md (the
delta framing §4.2 operationalizes) · goals-as-data.md (the distribution
story §2.4's marshal feeds) · table-completion.md (the seal §4.5
generalizes) · functional's fiber-external.md (#64's contract).
