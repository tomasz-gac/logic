# The engine at the data boundary

**STATUS: NORTH STAR — grounded design direction, August 2026.**

*(Historical correction: the previous version repeatedly promoted algebraic
correspondences into complete operational guarantees — the failure mode
named EQUIVALENCE INFLATION in logic's method.md. This version
distinguishes the reusable semantic kernel from the protocols still
required at system boundaries; §3 and §11's not-purchased column are the
standing discipline. The original text lives in git history.)*

This document replaces the earlier `domain-layer.md` as the architectural direction for applying `logic` and `pldb` to real data and distributed systems.

It is intentionally not a claim that a database, transaction system, authorization product, streaming engine, or distributed runtime has already been built. It identifies:

- what the current substrate already provides;
- the smallest boundary needed to use relations backed by real systems;
- which properties follow from the engine;
- which properties remain the responsibility of databases, brokers, adapters, and application protocols;
- the sequence of vertical slices that would turn the research engine into a useful domain layer.

Companions:

- `pldb/docs/design/external-relations.md` — the external read seam;
- `pldb/docs/design/table-constraints.md` — returned rows as a narrowing domain;
- `logic/docs/reference/condition.md` — `Condition`, `Residues`, and semiring answer cells;
- `logic/docs/reference/table-completion.md` — current table consumption and completion;
- `functional/docs/design/await.md` and `completion.md` — channels, scopes, and computed quiescence.

Some older documents still describe completion as tabling-owned and TCLP as a separate or blocked design. That is historical. The current architectural split is:

> **Fibers own work, waiting, quiescence, and sealing. Tabling owns monotone answer accumulation. TCLP is tabling whose per-term value is `Condition`.**

---

## 1. The north star

The engine should make it possible to write domain relations once and evaluate them over facts whose extensions live in ordinary systems:

- in-memory collections;
- PostgreSQL or another transactional database;
- a materialized view maintained from a log;
- a mode-limited remote service;
- eventually, persisted derived relations produced by earlier solves.

The desired application shape is:

```text
real sources
    │
    │  pinned, finite relational reads
    ▼
FactSource adapters
    │
    ▼
one cold solve
    ├── ordinary relational goals
    ├── table constraints
    ├── recursive semiring tabling
    └── TCLP: term ↦ Condition
    │
    ▼
stamped answer set
    ├── ground answers
    ├── conditional answers
    └── explanations/provenance where requested
    │
    ▼
imperative adapter
    ├── HTTP response
    ├── database transaction
    ├── outbox
    └── materialized derived relation
```

The engine is the **derived-knowledge layer**. It is not the system of record and does not replace the infrastructure responsible for durability, concurrency control, retries, transport, or effects.

The central architectural claim is deliberately narrow:

> A cold relational solve over a declared source snapshot can derive recursive and constrained answers. Those answers can be cached and compared together with their conditions rather than as unconditional booleans.

Everything beyond that claim must be built and measured.

---

## 2. What already exists

The north star is based on concrete substrate, not a blank-sheet design.

### 2.1 Fiber substrate

`functional` provides:

- an explicit fiber interpreter;
- scheduler-independent runnable frames;
- monotone channels;
- visible suspension and resumption;
- scope-local accounting of running and blocked work;
- computed sealing when a finite dependency closure reaches quiescence;
- group sealing for mutually waiting scopes.

This is the execution substrate. Logic code should not reproduce completion accounting.

A seal means:

> No work in this scope's dependency closure can grow its channels again.

It does not mean:

- that an external database snapshot was chosen correctly;
- that a Kafka offset was committed;
- that a transaction is durable;
- that a remote service will not later change.

Those are boundary facts supplied by adapters.

### 2.2 Semiring tabling

A table entry stores a monotone map:

```text
answer term → value in a semiring
```

Alternative derivations combine with `⊕`. Composition along a derivation uses `⊗`.

The cell accumulates values; fibers determine when the producer scope is finished. Completion is therefore not a special algorithm embedded in each table mode.

Different modes provide different value algebras:

- ordinary/TCLP tabling uses `Condition`;
- bounded weighted tabling uses the selected bounded semiring;
- closed weighted solving records structure and solves it after completion.

### 2.3 TCLP as `Condition` tabling

For domain work, the important instance is:

```text
term → Condition
```

A `Condition` is a normalized disjunction of `Residues`.

For a fixed answer term:

- `0` means the term has not been proved;
- `1` means it holds unconditionally;
- `C₁ ⊕ C₂` means it holds in either proved region;
- `C₁ ⊗ C₂` means both conditions must hold.

A conditional answer is therefore:

```text
term holds GIVEN Condition
```

This provides one answer-cell model for:

- ground presence;
- constrained answers;
- subsumption between regions;
- replay of a condition into a consumer's package.

A value below `1` may still improve while the table grows. Fiber sealing supplies the point at which the converged value is final for an outside reader.

### 2.4 `pldb`

`pldb` already provides:

- typed relation descriptors over properties;
- an immutable in-memory fact database;
- indexed lookup;
- cardinality estimates;
- table constraints that treat returned rows as a narrowing domain;
- explicit labeling when concrete rows are required.

The next step is not to invent another query language. It is to put the existing relation operations behind an external-source contract and prove the same relation can run unchanged over PostgreSQL.

---

## 3. Boundaries that must remain explicit

The earlier document repeatedly identified an algebraic resemblance and promoted it into a complete system property. This document keeps the useful correspondences but records the missing protocol.

### 3.1 Absorption is not a transaction protocol

Immutable packages and constraint absorption are useful for:

- constructing a candidate state;
- checking whether a condition remains satisfiable;
- inferring missing values;
- abandoning a failed candidate without undo.

They do not by themselves provide:

- concurrent commit arbitration;
- isolation;
- durable atomic publication;
- write conflict detection;
- crash recovery;
- exactly-once external effects;
- a transaction across independent sources.

The engine may participate in admission or validation. The backing database or application protocol owns commit.

### 3.2 Persistent values are not MVCC

Persistent packages make historical candidate states cheap to retain. MVCC additionally defines:

- which versions a reader sees;
- which version a writer may replace;
- conflict and visibility rules;
- commit order;
- garbage collection of old versions.

A source snapshot can be represented by a token or handle, but the token's meaning comes from the source.

### 3.3 Factor independence is not a DDD aggregate definition

A factor that splits cleanly across variable sets is evidence that the represented invariants can be checked independently.

That may help test a proposed aggregate boundary. It does not decide:

- business ownership;
- entity lifecycle;
- authority over commands;
- acceptable contention;
- which invariants the model failed to encode.

Use algebra to test a boundary, not to declare the domain solved.

### 3.4 Monotone table growth is not a streaming engine

Within one finite solve, table growth incrementally wakes dependent computation. That is useful fixpoint evaluation.

A production incremental-view system additionally needs:

- updates after a solve has ended;
- retractions or explicit epoch changes;
- durable operator state or replay;
- checkpoints;
- recovery;
- backpressure;
- cancellation and error semantics;
- versioned input.

The initial architecture remains cold and one-shot. A resident incremental driver is an optimization to consider only after rerun cost is measured.

### 3.5 Idempotent joins are not exactly-once delivery

Idempotent accumulation can make re-posting the same identified fact harmless inside a set-like relation.

It does not make:

- two distinct equal-valued events identical;
- counting idempotent;
- a payment side effect exactly once;
- input offsets and outputs atomic.

Exactly-once processing, where required, belongs to an adapter transaction or outbox. Event identity remains part of the data model.

### 3.6 A source offset is not a fiber seal

A source offset or version says which external facts were visible.

A fiber seal says internal evaluation over the chosen facts has reached quiescence.

A complete result requires both:

```text
source snapshot/pin
    +
sealed solve
    =
complete answer set as of that snapshot
```

There is no need for tabling to generalize its completion algorithm into “seal-at-offset.” The source supplies a finite snapshot; the fiber runtime seals the finite computation over it.

---

## 4. The external relation contract

The immediate target is the read side described in `external-relations.md`.

An external relation is a normal `pldb` relation whose extension is supplied by a backing adapter.

The logical layer should need only:

```text
enumerate(pattern)  → matching rows
estimate(pattern)   → cheap cardinality estimate or unknown
modes()             → binding patterns the source can answer
```

Real-data consistency adds one more concern:

```text
pin() → a solve-scoped snapshot handle or declared consistency capability
```

AS BUILT (August 2026, designed against the first SQL implementation as
this section prescribed):

```java
public interface FactSource {
    Iterable<Fact> get(Relation relation, IndexedSeq<Optional<Object>> args);
    default Iterable<Fact> get(Relation, IndexedSeq<Optional<Object>>, Residues region);
    default long estimate(Relation relation, IndexedSeq<Optional<Object>> args);
    default String id();     // source identity AS KNOWLEDGE: equal lookups
                             // against one backend are one constraint
}
```

Deviations from the sketch, each deliberate: `pin()` became LIFECYCLE
rather than a method — the SQL source pins at construction (auto-commit
off, REPEATABLE READ where the driver's metadata admits it, the granted
level recorded as the declared capability, the snapshot anchored by a
first read) and `close()` rolls back; a method returns when something in
the engine exists to call it. `supportedModes()` is UNBUILT — SQL answers
every practical pattern, so the first mode-restricted source (REST) is
its design customer, as pin's was SQL. And `pushedCondition` was
SUPERSEDED by the REGION parameter (§4.4): instead of a pre-compiled
source predicate, the probe carries the engine's own knowledge about its
argument positions and each source compiles what it can.

Relation ownership is decided by the RICH BOUND PATTERN: a source is a
multi-relation backend (one PostgreSQL connection serves many
relations), and the pattern carries the relation plus the bound
arguments — which is the in-memory `Database.get` signature's shape,
promoted. (The alternatives — one source per relation, or a registry of
relation loaders — collapse into adapters over this form.)

This is illustrative, not a frozen signature (and written in sketch Java —
the house is Java 8).

**The landing design tightens the type**: there is no free row parameter — `enumerate` returns pldb `Fact`s, because rows
LAND (§4.1) in the solve-local `Database`, whose surface already speaks
the whole contract: `Iterable<Fact> get(Relation, IndexedSeq<Optional<Object>>)`
IS enumerate-under-a-bound-pattern (the `BoundPattern` sketch ≈ the
existing `IndexedSeq<Optional<Object>>`), and `withFacts(List<Fact>)` IS
the landing. The adapter's whole job is therefore backend-row → `Fact` —
Phase 2's "hand-written row mapper" named precisely — and the seam's
shape is not invented against SQL; it is the reference implementation's
existing signature, promoted to a contract. This also sharpens the
no-ORM non-goal: the mapping target is facts, never objects.

### 4.1 `enumerate`

`enumerate` returns rows matching the bound pattern in the pinned view.

The baseline implementation may return rows one by one and unify each row. For a larger result set, the rows may be installed as one table constraint and narrowed locally.

Both are physical execution strategies behind the same relation.

**The landing design** (from the original design discussions; the piece
this rewrite could not have known): fetched rows are not consumed as a
transient stream — they LAND as pldb facts in a SOLVE-LOCAL, IN-MEMORY
database (the same immutable `Database` that is Phase 1's reference
implementation), and lookups over fetched relations post as TABLE
CONSTRAINTS (`TableConstraints`/`Support` — shipped, pldb
`table-constraints.md`). What that buys, concretely:

- **constraint propagation over external data, for free** — a fetched
  relation narrows like a domain: candidate rows shrink as other
  constraints bind columns, wrong rows die before any branch exists;
- **GAC-style in-memory joins** — two sources' fetched relations joining
  through shared columns propagate SUPPORTS against each other instead
  of running nested remote loops: the join executes as propagation,
  branching deferred to `labelo`;
- **solve-local source reuse** — SHIPPED in per-source form (August
  2026, pldb `sql/`): `CachingFactSource` decorates ANY source with the
  landed pool (an in-memory database, idempotent by fact value) plus the
  fetch-coverage ledger, whose entries are (bound pattern, region) —
  each fetch recorded with the region passed through it, sound by the
  seam's own over-delivery law. A probe is served locally only on PROOF:
  pattern subsumption AND the probe's region entailing a recorded one
  (`Residues.leq`); anything short re-fetches idempotently. This is
  CALL SUBSUMPTION at the data boundary — a lookup probe is a TCLP call
  key (bound values + canonical holes + region), the ledger is a table
  of calls the source has answered, and wide serves narrow, never the
  reverse. Without coverage, a partial fetch (orders for customer 42)
  would be mistaken for a complete local relation. The landed pool and
  the table constraints share one extensional source but are NOT one
  structure: the pool stores candidates; `TableConstraints` maintains
  branch-local narrowing state over them. Still future: the CROSS-SOURCE
  solve-level pool, and `SubsumptionMap` retrieval when ledger entry
  counts grow.

What it does not buy: remote join pushdown (that is Phase 6's compiled
predicates); freedom from memory costs (landed rows are resident — the
pull/materialize trade of §4.5 still governs what gets fetched at all);
or a license to scan un-moded sources (modes still gate the fetch).
Phase consequence: Phase 1's reference `Database` doubles as Phase 2's
landing store — the in-memory implementation is not a stand-in but a
permanent component.

### 4.2 `estimate`

`estimate` exists for planning, not correctness.

It may return:

- an exact or cheap in-memory count;
- a database estimate;
- a paid `COUNT(*)`;
- unknown.

Unknown must degrade to a conservative optimizer barrier. A stale or coarse estimate may make a plan slower; it must not change answers.

The cost of obtaining the estimate is itself part of planning. A remote round trip on every recursive forcing is not free.

### 4.3 `supportedModes`

A source is allowed to support only some binding patterns.

Examples:

- SQL can usually answer every practical combination of bound columns;
- a REST endpoint may require `customerId`;
- a remote search endpoint may require at least one indexed field;
- a source may accept a range constraint but not an arbitrary coupling.

Unsupported directions must be refused or deferred until enough variables become bound. They must not silently trigger a full remote scan unless that is an explicit capability.

### 4.4 Condition pushdown — SHIPPED (August 2026, pldb `sql/`)

The probe carries its REGION: the package's knowledge about the lookup's
argument positions, extracted as the name cut per family (coupled atoms
stay home) and renamed to positional canonical names — the variable at
argument position i becomes `_.i`, which is both the column resolution
(no term traffic through the seam) and what keeps regions from different
probes comparable for coverage. The parameter is ADVISORY by the
over-delivery law: a source must return every fact matching pattern ∧
region and may ignore the region wholly or per family — narrowing it did
not apply stays local, enforced by propagation over the returned rows.

The SQL adapter compiles regions through a REGISTRY of per-family
compilers keyed by factor class: one atom in, optionally one predicate
out — a WHERE fragment with positionally bound parameters, never inlined
text. The engine-core families are wired by default and overridable (FD:
enumeration → IN, interval → BETWEEN, point → equality, union → its
members disjoined, order and separateness propagators by name; nogoods:
De Morgan over the registry itself — binding literals negate directly,
store literals compile positively through their own family and take the
complement). User families register their own compilers and their
literals inside exclusions push the moment they do.

The direction laws, as shipped and law-kit checked (the harness judges
admission ENGINE-TRUE — a row is admitted iff imposing the posting with
its values bound solves — against H2's selection of the compiled WHERE):

- **Positive: weaker or equal.** Selection ⊇ admission, always; dropping
  atoms or whole families is free. A missed pushdown costs bandwidth; an
  unsound one changes answers.
- **Negation flips the direction ONCE, at the conjunct boundary.** The
  predicate value carries an EXACTNESS bit — factories say exact, a
  compiler that approximates marks itself weakened — and the complement
  is available only while exact, because a weakening's complement
  under-delivers. Structural, not conventional: the unsound composition
  is unrepresentable.
- **Disjunctions push whole or not at all** (dropping a disjunct
  strengthens); a fused nogood's conjunct level drops freely and marks
  the result weakened when it does.
- Double negation is boolean on BOTH sides — demonstrated against the
  oracle, not assumed: the trial keeps ¬¬P ≡ P (a satisfied inner
  exclusion discharges without trace), and the compiler's registry
  self-delegation pushes the complement.

Convention, with its reason on file: columns backing relation properties
are NON-NULL — SQL's three-valued logic drops a NULL row from both sides
of a comparison, the silent under-delivery the laws forbid, while the
engine has no null vocabulary at all.

Deferred as ONE design decision: boolean nesting beyond the flat
disjunction and arithmetic operands (`end = start + 1` — the expression
layer). Reopening triggers: an `addo` atom crossing a region in a
workload that matters, or a second SQL dialect forcing late rendering.

### 4.5 Pull and materialize

Two source shapes remain useful.

**Pull**

- query the source on demand;
- bounded memory proportional to the solve's footprint;
- fresh according to the source's pin;
- remote latency and mode restrictions.

**Materialize**

- a separate acquisition process maintains a local table;
- fast and fully queryable;
- freshness depends on materialization lag;
- memory/storage proportional to the materialized relation.

The acquisition loop owns authentication, retries, backoff, and source-specific polling. It is not a goal.

Logs are not `FactSource`s. A log is ordered transport. A materialization built from a log can be exposed as a `FactSource`.

---

## 5. Solve-scoped consistency

A solve is the unit of logical evaluation and the default unit of consistency.

### 5.1 Stability

Within a solve, repeated reads of one source must observe one stable view.

For an in-memory immutable database this is immediate. For SQL it means a transaction or exported snapshot. For a REST source it may mean only a solve-local memo of the first observed response.

Stability is the minimum contract:

> The source does not change underneath one derivation.

### 5.2 Atomicity is source-limited

A SQL transaction may provide a point-in-time view across all relations in one database.

Several unrelated REST requests generally cannot.

The engine must not claim a simultaneous global snapshot that the sources did not provide.

### 5.3 Snapshot vector

A solve that touches several sources may carry:

```text
SnapshotVector = { source identity → source snapshot token }
```

The token is descriptive. Its comparison rule is source-specific:

- mutable snapshot ID: reuse may require equality;
- append-only offset: later prefixes may extend earlier knowledge;
- ETag: reuse may require revalidation;
- lease: validity is a time condition;
- unversioned observation: valid only as the recorded observation.

There is no universal `>=` across all token types.

The vector is attached to results that leave the solve and to any cache persisted beyond it. It need not be copied into every in-memory answer.

*(Carrier note, marrying this section with `condition.md` §8.7: each
source's token is its own FACTOR CLASS with its own per-class order — an
append-only offset gets a real order, an ETag gets
equality-plus-revalidation, an unversioned observation gets identity-only
`leq`, conservatively incomparable. That is exactly how `Residues.leq`
already composes pointwise per class, so "pin stamps are epoch factors"
and "no universal `≥` across token types" are ONE design, not rivals: the
vector rides as conditions where it must travel, and each token class
answers its own reuse question.

Source tokens begin as SOLVE-LEVEL SNAPSHOT METADATA. Each source owns
the meaning and comparison rules of its token, and the adapter obtains
and revalidates tokens — revalidation (an ETag check) is I/O and lives
in the adapter, never inside `leq`/`meet`/`⊕`/`⊗`. One pin per source
per solve prevents INTRA-SOURCE movement during the solve; cross-source
skew remains explicit in the vector (§9.4 — the combined world need not
be point-in-time consistent).

A later EXPERIMENT may represent source requirements as answer factors,
in a three-level carrier mirroring factor → `Residues` → `Condition`:

- an **EpochRequirement** — one source-specific requirement;
- a **Footprint** — the conjunction of requirements across sources;
- an **EpochCondition** — a normalized disjunction of footprints
  (alternative proofs requiring different snapshots are a DISJUNCTION
  of footprints, not one footprint — the same
  raw-conjunct-cannot-carry-⊕ lesson `condition.md` §4 records for
  residue maps).

Promotion beyond hypothesis waits on a theorem receipt (carrier,
operations, per-token-class laws, contradiction handling, operational
purchase, NOT PURCHASED). What such factors may buy: deciding whether
an individual DERIVATION remains admissible under another source world.
What they do NOT buy: that a table completed under an earlier world is
complete under a later one — new facts add terms, broaden conditions,
open recursive paths — so cross-snapshot TABLE reuse stays gated on
recomputation or a source-specific delta protocol. Epoch factors are
also not free entrants to `Residues`: variable-freedom removes the
renaming problem, but a lawful `Projectable` face (identity-like
projection and renaming) is stated integration work.

Fine-grained source attribution is PARKED as a research note:
dependencies attach to more than variables — ground facts, relation
calls, constraint postings, propagated narrowings, table answers,
absence/completion evidence — so the likely foundation is a general
KNOWLEDGE-ANNOTATION seam (shared with explanations and provenance,
logic's `condition.md` §8.6) with per-variable projections as one view
over it. Trigger: measured over-refusal from answer-level footprints,
or a real cross-snapshot reuse workload.)*

### 5.4 Per-solve source cache

A pull source should normally memoize its fetched result inside the solve.

This provides:

- stable repeat reads;
- deduplication of identical probes;
- bounded lifetime;
- no cross-request invalidation problem.

Tabling may already deduplicate a source-backed relation at the relational call level. A lower source cache can still be useful where several physical calls normalize to the same backend request. The layers should not accidentally fetch twice, but they should remain conceptually separate:

- source cache: backend request reuse;
- table: relational answer reuse and recursion.

Per the landing design (§4.1), the solve-local source cache is the landed
fact pool TOGETHER WITH the fetch-coverage ledger — facts answer matches,
coverage answers completeness; neither alone implements reuse.

### 5.5 Foreign sources

Use the strongest consistency mechanism the source actually exposes:

1. database snapshot or versioned API;
2. ETag or resource version;
3. declared lease/TTL represented as a validity condition;
4. an observation log under our control;
5. no reusable consistency claim.

Where freshness cannot be guaranteed, expose it as data:

```text
answer holds GIVEN observedAt ≤ now ≤ validUntil
```

This is useful only when the condition is honest and machine-checkable. A guessed TTL is an application policy, not a theorem about the remote system.

---

## 6. Conditional answers as the domain result

The domain-facing result should not be forced immediately into a list of ground points.

A solve mode should be able to return:

```java
record ConditionalAnswer<T>(
    T term,
    Condition condition,
    SnapshotVector snapshot) {}
```

Possible outcomes for a particular requested fact:

- **no answer** — not derivable in the pinned world;
- **unconditional answer** — `Condition.ONE`;
- **conditional answer** — valid under the returned condition.

This API is the bridge from TCLP to applications.

### 6.1 Consumption

A client with concrete request values can consume an answer by restating its condition and binding those values.

Example:

```text
mayApprove(alice, invoice42)
GIVEN amount ≤ 50_000 ∧ deviceTrust ≥ managed
```

A `check` request supplies the current amount and device trust. Propagation determines whether the condition survives.

### 6.2 Listing

A list query may return conditional rows rather than labeling every condition into points.

Pagination and serialization need an explicit policy:

- ground rows first;
- conditional rows with a supported condition representation;
- or label only over a declared finite presentation domain.

Do not imply that arbitrary constraints can always be rendered as a friendly API response.

### 6.3 Comparison

`Condition` ordering can support questions such as:

- does one grant cover another?
- is a cached answer at least as general as this request?
- did a new derivation add any permitted region?

This is a valuable capability, but its precision is limited by the orders implemented by the individual constraint stores.

### 6.4 Explanation

Conditions are not full proof provenance.

An explanation endpoint additionally needs:

- derivation witnesses;
- named relations or rule identifiers;
- source references;
- possibly a provenance semiring or trace mode.

The current answer algebra provides a place to attach this work. It does not make a production-quality explanation “free.”

---

## 7. Driving vertical slice: caveated authorization

Caveated authorization remains a good proving workload because it simultaneously requires:

- recursive graph traversal;
- cycles;
- conditional answers;
- external facts;
- negative or completion-sensitive questions;
- caching;
- explainability;
- source versioning.

It should be treated as a vertical slice, not yet as a product claim.

### 7.1 Base relations

Possible external facts:

```text
member(user, group)
member(group, parentGroup)
owns(folder, document)
inherits(document, folder)
grant(subject, permission, resource, caveat)
deviceTrust(user, level, validUntil)
```

PostgreSQL is the primary backing. Device trust may initially be materialized into PostgreSQL rather than fetched live from a REST service.

### 7.2 Derived relation

```text
may(user, permission, resource)
```

derives through:

- nested membership;
- delegated grants;
- resource inheritance;
- conjunction of caveats.

The table cell for a fixed answer accumulates its permitted region as a `Condition`.

### 7.3 Public queries

**Check**

```text
may Alice approve invoice 42
given amount = 30_000, now = ..., device = ...
```

Returns yes/no against a pinned policy snapshot.

**List**

```text
what may Alice approve?
```

Returns resources and any surviving conditions.

**Explain**

Returns the rule/source path and condition that justified a selected answer. This requires explicit tracing work and is not assumed to emerge automatically.

### 7.4 What this slice proves

The slice is successful when:

- the same domain relation runs over in-memory and SQL facts;
- cyclic authorization terminates;
- conditions survive recursion correctly;
- a ground grant absorbs narrower conditional duplicates;
- repeated calls in one solve do not repeat the same source query unnecessarily;
- source mutation after the pin does not affect the running solve;
- answers carry a snapshot token;
- all schedulers produce the same answer set;
- performance is measured against a straightforward SQL/application implementation.

---

## 8. Reads, writes, and effects

### 8.1 Reads are the first product boundary

The first useful system is read-only:

```text
external facts → derived conditional answers
```

This is enough to validate:

- the source seam;
- pinning;
- TCLP over real rows;
- query planning;
- conditional serialization;
- caching.

Do not couple the first SQL adapter to a write model.

### 8.2 Goals remain pure

A goal may:

- read through a declared source adapter;
- unify;
- post constraints;
- derive answers.

It must not:

- send an email;
- charge a card;
- publish Kafka output;
- mutate a database;
- call a non-idempotent endpoint.

Search can branch, retry, reorder, and replay table consumers. Effects inside goals would therefore be duplicated or published from branches that later fail.

### 8.3 Admission and validation

A later write-facing API may ask the engine to evaluate a proposed command against a pinned world.

It may return:

```text
Rejected
Accepted(completedProposal)
Conditional(completedProposal, condition, snapshot)
```

This can be useful for:

- completing inferred fields;
- returning an executable precondition;
- explaining rejection;
- separating domain derivation from persistence.

The result is not a committed transaction.

### 8.4 Commit remains external

A safe commit requires one of:

- re-run validation inside the backing database transaction;
- verify the relevant source token has not changed, then write atomically;
- encode the condition as a database constraint or compare-and-set predicate;
- accept weaker consistency explicitly.

Across multiple independent sources, the engine cannot manufacture an atomic transaction. Use an outbox, saga, or source-specific protocol.

A conditional answer may help express what must still be true. It does not enforce that truth at the commit point by itself.

---

## 9. Live and distributed data

The engine should remain a cold function inside a runtime that owns streaming concerns.

### 9.1 Stream–table boundary

```text
log / CDC / events
    │
    ▼
materializer
    │
    ▼
queryable table
    │
    ▼
FactSource
    │
    ▼
cold solve
```

A log is not queried as an arbitrary relation. It updates a materialization that is.

### 9.2 Wave execution

The initial live-data model is micro-batch or watermark execution:

```text
poll input wave
    → update materialization
    → pin sources
    → run one cold solve
    → produce canonical answer set
    → commit cursor and outputs in the adapter
```

The correctness reference is:

> Evaluation for wave `V` is observationally equivalent to a fresh cold solve over the source snapshot for `V`.

A future warm or resident implementation must preserve that equivalence.

### 9.3 Exactly-once processing

Where a broker supports transactions:

```text
begin adapter transaction
    poll input and obtain cursor M
    update/read materialization at M
    solve at snapshot M
    emit canonically identified outputs
    commit outputs and cursor M atomically
commit
```

The engine contributes:

- deterministic answer-set semantics, once verified;
- pure recomputation;
- conditional and recursive derivation.

The adapter contributes:

- transaction boundaries;
- cursor management;
- output atomicity;
- retry behavior.

Event IDs or output identities must distinguish genuinely distinct equal-valued events. Counting and non-idempotent semirings require special care.

### 9.4 No global epoch

Independent sources do not automatically share one timeline.

A snapshot vector can report what was read. It cannot eliminate distributed read skew.

Read-your-writes or minimum-version guarantees are available only where a source token has a meaningful order and the source can serve at or beyond the requested token.

One log can simplify the consistency model, but using one log is an application architecture choice, not a property derived from the logic engine.

### 9.5 Retractions

The tabling substrate is monotone during a solve.

For a mutable source:

- a new solve uses a new snapshot;
- a cache from an incompatible snapshot is discarded;
- derived answers are recomputed.

Do not add cross-solve truth-maintenance until a real workload demonstrates that full recomputation is unacceptable.

Append-only sources permit more reuse in principle, but extending a sealed table from one prefix to another still requires a designed delta protocol. Keeping the old answers as a dedup seed is plausible; it is not assumed correct until implemented and tested against fresh solves.

---

## 10. Caching and materialization

Caching is an optimization, never the consistency authority.

### 10.1 Per-solve table

This is the default:

- complete lifetime ownership;
- no cross-request invalidation;
- source snapshot is ambient;
- completion is computed by fibers;
- table values are discarded with the solve.

### 10.2 Cross-solve source cache

A source adapter may cache backend responses using:

- snapshot identity;
- ETag;
- lease;
- explicit invalidation.

This is ordinary data-access caching. It does not replace relational tabling.

### 10.3 Persisted table entries

Persisting derived entries is a later optimization requiring:

- stable relation and rule identities;
- canonical term and condition serialization;
- a snapshot vector;
- a compatibility/reuse predicate per source;
- schema and rule-version handling;
- comparison against a fresh solve.

Persist only entries that are complete for their recorded snapshot.

A persisted entry is evidence of what the old program derived from the old snapshot. Reuse additionally requires that:

- the program semantics are compatible;
- the sources are compatible;
- the condition representation can be restored faithfully.

### 10.4 Materialized derived relations

The promising differentiated use case is not “replace SQL views.”

It is:

> Materialize a derived relation that SQL does not naturally express because it is recursive and its rows carry conditions.

Examples:

- recursive authorization with caveats;
- dependency reachability with validity windows;
- configuration compatibility regions;
- planning reachability with admissibility constraints.

The output can be stored in an ordinary database for ordinary clients. The engine owns derivation; the database owns storage and serving.

---

## 11. What the algebra buys

The algebra is valuable when each claimed consequence states its assumptions.

| Mechanism | What it can buy | What it does not buy |
|---|---|---|
| Immutable packages | cheap branches, no destructive rollback inside search | database transactions or MVCC |
| ACI/idempotent `⊕` | order-insensitive set-like accumulation and duplicate absorption | exactly-once effects |
| `Condition` absorption | compact conditional answers and containment-based reuse | proof provenance or friendly serialization |
| `⊗` over conditions | caveats composed along derivations | external enforcement at commit |
| Semiring table cells | one tabling architecture for several value interpretations | every semiring terminating or streamable |
| Fiber scopes and seals | finality of finite internal evaluation | source snapshot selection or durability |
| Table constraints | local propagation over returned row candidates | remote query planning by themselves |
| Source estimates | better conjunction order | soundness from inaccurate capability declarations |
| Snapshot vectors | explicit statement of what was read | simultaneous global snapshot |
| Pure cold solves | reproducibility and simple retries | transactional output publication |
| Factor splitting | evidence about invariant independence | automatic DDD aggregate discovery |

This table is the claim boundary for the north star.

---

## 12. Build sequence

Every phase should produce a working vertical slice and a falsifiable result.

*(Ordering note: these phases are the PLDB TRACK — `vision.md` §7a's Waves
3–4 and the gated tail, elaborated. Phase 0 IS Wave R. The engine-side
Waves 1–2 — the aggregate reframe and the note store — proceed
independently and are deliberately absent here.)*

### Phase 0 — harden the current substrate

Before external data becomes a debugging multiplier:

- resolve known stream-contract issues;
- complete cyclic-term handling;
- pin table-consumer fairness;
- enforce store-revision identity;
- remove unsafe completion paths;
- make query resource ownership explicit.

The distributed design should not be used to route around local correctness problems.

### Phase 1 — `FactSource` seam

- define `enumerate`, `estimate`, and `supportedModes` over existing `Property` columns;
- express the current in-memory `Database` as the reference implementation;
- preserve existing behavior and tests.

Proof:

> No derived relation changes when the backing is replaced by the interface.

### Phase 2 — PostgreSQL source

- hand-written row mapping;
- parameterized lookup from bound patterns;
- cardinality estimate strategy;
- explicit source capabilities;
- transaction-scoped snapshot/pin.

Proof:

> One nonrecursive and one recursive relation produce the same answer set over in-memory and PostgreSQL facts.

### Phase 3 — solve-scoped source state

- one pin per source per solve;
- solve-local source request cache;
- snapshot vector attached to results;
- tests where the backing mutates after pinning.

Proof:

> A running solve is stable and reportably tied to its source views.

### Phase 4 — conditional solve API

- return term plus final `Condition`;
- serialize a deliberately small supported condition vocabulary;
- consume a conditional answer with concrete request data;
- refuse unsupported condition transport.

Proof:

> A recursive SQL-backed query can return and later recheck a caveated answer.

### Phase 5 — caveated authorization slice

- PostgreSQL base relations;
- cyclic groups and delegation;
- time/amount/device conditions;
- `check` and `list`;
- explicit trace work for `explain`;
- benchmark and operational profile.

Proof:

> The engine solves a real bounded context whose core query is difficult to express safely as hand-written caching around imperative code.

### Phase 6 — query planning and pushdown

- compile supported projected conditions to SQL;
- use source estimates without repeated expensive planning calls;
- choose branch-per-row versus table-constraint delivery by measured cost;
- record query counts and transferred rows.

Proof:

> Optimization changes cost, not answers.

### Phase 7 — mode-limited remote source

- one REST-backed relation;
- supported-mode deferral;
- solve-local stability;
- ETag or explicit lease handling;
- failure, timeout, and cancellation policy.

Proof:

> Partial source capability fails or defers loudly and never causes hidden full scans or inconsistent repeat reads.

### Phase 8 — live wave adapter

- broker/CDC materialization outside the engine;
- pin per wave;
- cold solve;
- deterministic answer-set test;
- transactional output/cursor adapter where supported.

Proof:

> Replaying the same wave produces the same identified output set, while crash recovery is owned by the adapter.

### Phase 9 — warm and persisted caches

Only after profiling shows cold solves are too expensive:

- reuse at identical snapshots;
- append-prefix experiments;
- persisted complete entries;
- canonical condition transport;
- fresh-solve equivalence tests.

Proof:

> Removing the cache changes latency, not results.

---

## 13. Non-goals

For this phase, the project is not:

- a replacement for PostgreSQL;
- an ORM;
- a distributed transaction coordinator;
- a Kafka Streams or Flink replacement;
- a general-purpose reactive runtime;
- a hot always-resident logic process;
- an automatic DDD designer;
- an exactly-once effect system;
- a truth-maintenance engine for arbitrary retractions;
- a promise that all external sources form one snapshot;
- a promise that every constraint can be pushed down or serialized.

These may be adjacent research directions. They are not prerequisites for delivering value from external recursive conditional relations.

---

## 14. Success criteria for the north star

The direction is validated when a real application demonstrates all of the following:

1. Base facts live in PostgreSQL or another ordinary store.
2. Domain reads are written as relations, not source-specific control flow.
3. The same relation runs against the in-memory reference source.
4. Recursive tabled evaluation terminates over cyclic data.
5. TCLP returns final conditional answers using `Condition`.
6. The result states the source snapshot under which it is complete.
7. Effects occur only after the solve, in an adapter.
8. Scheduler choice does not change the answer set.
9. Removing indexes, table reuse, and pushdown changes performance but not behavior.
10. The system is compared with a conventional implementation on correctness, latency, source calls, memory, and comprehensibility.
11. At least one external user can implement or understand a relation without reading the fiber or tabling internals.
12. Every distributed guarantee names the component that enforces it.

The larger `Out of the Tar Pit` direction is reached incrementally:

```text
base facts in ordinary stores
    +
derived relational logic
    +
constraints as explicit values
    +
execution and storage choices outside essential logic
```

The decisive test remains:

> Can an optimization layer be removed while preserving the same stamped answer set, leaving only a slower solve?

When the answer is yes over real data, the project has moved from an advanced logic runtime toward a credible domain architecture.

---

## 15. One-sentence direction

> Build a cold relational engine that reads a pinned world through small adapters, derives recursive conditional answers to fiber-computed completion, and hands a stamped answer set back to ordinary transactional infrastructure.
