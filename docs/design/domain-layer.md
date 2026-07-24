# The engine as a domain layer — DESIGN (July 2026, nothing built)

The pldb phase's driving design: what real programs — databases behind,
REST in front, DDD in the middle — look like when the engine is the domain
layer. The thesis is Out of the Tar Pit's, with the machinery to cash it:
**essential state is base facts; everything the business asks is a derived
relation; reads are projections of the fact base and writes are absorptions
into it — one algebra** (`split`/`rename` depart, `meet`/`normalize`
arrive; constraint-kernel.md has the store contracts, lattice.md §5a the
theory). Status: DESIGN. The build list is §7 (the seam §5, versioning §6); the driving example is
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

The two apparent enlargements are compositions, not interface growth:
CONSTRAINED ROWS (a column holding a canonical factor, §4.4) change the
row's TYPE — delivery must be rename+absorb instead of unify — not the
seam; and the UNENDING SOURCE (the log, §4.5) breaks exactly one thing,
per-pattern completeness, so the streaming species is a SEPARATE, LATER
extension whose only addition is the offset declaration ("complete up to
N") that seal-at-offset consumes. FactSource proper stays finite,
pull-based, pldb-pure.

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
torn world mid-solve) and the epoch stamp for anything persisted. A
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

## 7. Build list (dependency order — this IS the pldb phase plan)

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
5. **Standing solve**: a scheduler mode idling on injection instead of
   declaring exhaustion — most of it is #64's wait-for theory.
6. **Seal-at-offset** (§4.5): the barrier generalization of completion
   detection (table-completion.md).
7. **#68 suspensions × tabled calls**: this workload trips it immediately
   (external checks suspend inside tabled bodies).
8. The bounded context itself: documents/folders/groups/delegated grants
   with windows and limits; Postgres behind; `check`/`list`/`explain` in
   front; each item above lands against it, not against toys.

## 8. Non-goals and limits

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

## 9. Where knowledge lives

constraint-kernel.md (store contracts, absorb's triggers) ·
tabled-constraints.md (regions, witnesses, consumption) · lattice.md §5a
(the ⊕-placement theory this design leans on) · answers-as-diffs.md (the
delta framing §4.2 operationalizes) · goals-as-data.md (the distribution
story §2.4's marshal feeds) · table-completion.md (the seal §4.5
generalizes) · functional's fiber-external.md (#64's contract).
