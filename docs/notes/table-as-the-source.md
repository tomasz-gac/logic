# The table as the source: AnswerSource in two kinds, TabledSource owns the table

An `AnswerSource` answers a call key; completion is the seal. The seam
has TWO KINDS, and the store's two propagator lanes mirror them — the
source's kind picks the consumer's lane:

- ASYNC, the primary kind for anything external — the produce function:

      Fiber<Nothing> produce(Call<Relation> probe,
              Emitter<Tuple2<Reified<?>, Condition>> emit);

  The workforce runs on the CALLER's scheduler: forked work continues
  while the source prepares results elsewhere. Consumed by goals and
  by the PARKING TablePropagator.
- SYNC, a small CLOSED tier: the same pairs enumerated inline. Its
  whole population: the preloaded in-memory `Database`, sealed cell
  reads (a sealed `TabledSource` serves synchronously whoever produced
  it), and the GAC/trial tier — the sync `TablePropagator` and
  `Trial.now` can only consume this kind. Not a migration path and not
  a convenience: the hot in-process tier, first-class and closed.

Both kinds carry `long estimate(Call<Relation> probe)` and
`String id()`, always synchronous (the pricing law). This is the
engine's two-lane doctrine, third instance and counting (`Trial.now`
beside the trial's fiber lane; `Propagator` beside
`ParkingPropagator`; sync beside async sources): sync is the hot
default, the fiber kind exists where something genuinely parks, sync
wraps in done at the composition point never the reverse, pricing
stays sync.

The emission is the answer cell's own entry shape — (reified term,
Condition), ground rows at ONE — so the seam is symmetric: probe in =
`Call`, answers out = (Reified, Condition), both directions tabling's
boundary vocabulary, and an async source is literally the produce half
of a table entry. `Fact` exits the seam and retreats to the schema layer
where it is genuine (Database storage, the mutation/trigger face,
user-facing construction, Support's indexes); the term↔row codec
lives SOURCE-side, where schema knowledge already is. Constrained
answers are thereby a per-consumer CAPABILITY, not a type gap: SQL
and `Database` emit at ONE; a snapshot may serve conditional cells;
`LookupGoal` may fork per conjunct exactly as tabling's deliver does
(restate); `TablePropagator` stays on the ground corner and refuses
sub-1 conditions (GAC over constrained candidates is the parked
research).

The probe IS the call key — the reified argument image plus the
region, minted at ONE reification site (the consumer's
`Residues.about`), so key numbering holds by construction and
couplings are representable (p(X,X) carries the shared any; SQL may
push col1 = col2). No source owns a driver, no engine nests inside the
engine, and an in-flight fetch is a parked frame like any other — a
fold structurally cannot seal past it. Producers: `Database`
enumerates its bucket (sync kind); `GoalSource` runs a goal FROM THE
KEY (the anonymous master's caller-agnostic discipline, from
`Package.empty()` plus its table). The async kind's FIRST
IMPLEMENTATION is `TabledSource` itself — the table's streaming IS
in-engine async (channel park/wake/seal, machinery already shipped),
needing nothing from #64, which adds only FOREIGN-THREAD completion.
SQL is the first foreign producer (after #64), and EXECUTOR-WRAPPED
BLOCKING JDBC IS THE READY IDIOMATIC THING, not a stopgap — there is no async JDBC (ADBA died 2019), every
mainstream data layer wraps (jOOQ ships fetchAsync(executor); Hibernate
Reactive had to abandon JDBC to do better); R2DBC is the genuine-async
upgrade if fan-out ever asks. The pushdown compilers are untouched
either way: they emit SQL text and parameters; who executes is the
source's business.

THE EXTERNAL FACE (#64) is a MULTIPLE-SHOT ResumeHandle, two verbs:
`emit(v)` (foreign thread, any number of times, FIFO) and `complete()`
— or `complete(throwable)`, which the claimed workforce rethrows
fiber-side so the exception escapes via the scheduler's get. Unbounded
mailbox inside; each call nudges the scheduler's ready door; the
workforce drains into the channel through the NORMAL emit path;
completion seals. Externality stays confined to the handle — the
channel keeps its one write door — and #64's substrate work is
unchanged: the thread-safe ready door and idle-wait. Retries,
fallbacks, mapping: adapter-level, before completion, invisible to the
engine.

`TabledSource implements AnswerSource` is the ONE container —
memoization as a decorator, invisible to consumers. It owns a `Table`
(per-source lifetime, persistent between solves): probe →
`findSealedSubsumer` → replay the sealed cell; miss →
`getOrCreateEntry` + `Fiber.produce` claim of the wrapped producer
(losers join; concurrent probes cannot double-fetch). Its
constructors are the producer axis, and the producer decides the miss
policy: `over(producer)` and `solving(relation, body)` PRODUCE the
missing region (`solving` = over a `GoalSource` sharing the owned
table, so inner tabled calls accumulate beside the probe entries);
`snapshot(solved, naming)` is producer-less and REFUSES — a probe
outside every sealed region is UNKNOWN, and returning empty would
read as falsity. The snapshot's input comes through the door that
already exists: seed your own `Table.empty()` into the public
`solveFrom`, drain, hand over the sealed entries.

Everything is assembled from tabling's PUBLIC pieces — `Table`'s memo
and subsumer retrieval, `TableEntry`'s cell/channel/seal,
`Fiber.produce`, `solveFrom` — and tabling itself changes ONCE:
`Call<R>` genericized (`Call<Tabled<?>>` for goals, `Call<Relation>`
for pldb) so the seam takes the key cast-free. Containers (`Table`,
`TableEntry`, the trie) hold `Call<?>` — they only invoke
subsumes/equals and partition by the token's identity — so the ripple
is signatures, not semantics. Identity comparison stays the contract:
the schema's canonical `Relation` instance is the key. The recursion
machinery (completion detection, the modes' derivation accounting,
star) is never entered: it sits behind `Table`'s package-private line,
the goal producer's private equipment.

RESIDENCE follows extensional vs intensional (the EDB/IDB import):
the solve's `Table` is per-solve because OPEN state is solve-relative
— an incomplete entry's absence means "not yet" relative to running
completion detection. A SEALED entry is a PORTABLE value: reified
keys and `Renaming` anonymize call and answers relative to no
package, which is what licenses per-source lifetime, the snapshot
face, and persistence. Validity over TIME is the pins' job (#75):
marshalling a table is marshalling sealed entries, postings the wire
format, pin stamps the license for reuse.

Consumers dispatch by kind at three points and nowhere else:
`LookupGoal` enumerates a sync source inline and drives an async one's
produce; `TablePropagator` is the sync propagator over sync sources
(as shipped — the GAC tier) and the PARKING propagator over async
ones (deferred until the first async source exists); a sealed
`TabledSource` cell serves the sync face whoever produced it.
`estimate` sees the region (the pushdown arc's deferred region-aware
estimate, free from the signature); the upper-bound law makes ignoring
it sound. Retirements when built: `CachingFactSource` (the ledger into
`Call` subsumption, the dedup into the cell join), `Regions.about` and
its positional numbering (into the one reification site), the
pattern-shaped get signature (the sync KIND survives first-class,
re-typed to the answer shape).

**considered and rejected** (Aug 2026, in order):
- Source entries inside the SOLVE's table, under the modes — sources
  never recurse; every mode question they raised existed only because
  of this residence.
- Splitting `TablingMode` into ConsumeMode × ProduceMode — the code
  refused: bodyState → absorb → capture are one derivation-accounting
  thread riding the package, and in closed mode the value is computed
  at the seal from mode-private state; the mode does not decompose.
- A workforce-parametrized front door in tabling, with mode-mediated
  emission — unnecessary once sources own their tables.
- Extracting a container layer from tabling — the split already
  exists as `Table`'s visibility line; reuse beats extraction.
- The per-probe fresh solve (`derived`) — expands compression at the
  boundary, shares nothing.
- `entry()` on the source seam — container leakage; the
  per-source variation is only the workforce.
- A scheduler factory on `solving()` — an engine nested inside the
  engine, violating the one-home grounding discipline.
- `IndexedSeq<Optional<Object>>` as the probe — cannot represent
  couplings, and its second numbering site is where the
  canonicalization drift lived.
- A new channel type that fires on future completion — fuses value
  transport with external resumption; `Fiber.external` + emit keeps
  the channel's single write door.
- Widening `Call.relation` to `Object` — the generic gives the same
  reuse cast-free.
- "The table subsumes the Database" — shrunk to shape reuse: the
  mutation/trigger face, Property-driven indexes, and the
  schema/codec layer stay genuinely pldb.
- `Fact` growing a `Condition` — the cell already owns the condition
  slot (the value keyed by the term, ⊕-folded), so a conditional Fact
  duplicates it and reopens value identity; instead Fact exits the
  seam and the emission is the cell's entry shape.
- One uniform seam kind — forced uniformity makes the in-memory/GAC
  tier pay fiber composition so the rare parking case can park; the
  cascade redesign landed on two lanes for exactly this reason, and
  the seam mirrors it.
- The Rx contract stack on the external face — backpressure (the
  mailbox at worst holds one probe's result set, exactly what the
  sync fetch materializes today), cancellation hooks (the pinned
  connection's close already bounds orphaned tasks), error-channel
  semantics (the one necessity is the exception CROSSING the thread
  boundary so it escapes via the scheduler's get). Two verbs survive:
  emit and complete.

-----
- **status**: design converged (the human's rulings, Aug 2026); no
  code; unblocks as its own arc. Phase 1 is the `Call<R>` signature
  pass in logic (STOP-listed, design-first).
- **imports**: EDB/IDB ⋯import (Datalog: extensional vs intensional
  relations — the residence rule; receipt owed at graduation).
  Completed tables enable non-monotone consumption ⋯import (SLG/XSB:
  the seal as the consumption law for folds/negation over sources;
  receipt owed).
- **obligations**: (1) `Residues.about` visibility for the one
  reification site — confirm public or open it. (2) Source-side
  accessors: a read-only per-position view of the reified image's
  members; raw-value extraction for SQL parameters written once in
  `sql` and receipted (the toString-braces gotcha); constructing a
  `Reified` from ground row values (likely reify over empty
  substitutions — confirm the public face); the emitter reuses the
  substrate's face if the shape fits. (3)
  Receipts, red first: the snapshot's miss-refusal; re-seeding a
  grown table into a second solve (sealed entries consumed through
  the normal subsumer path). (4) Conditional consumption arrives per
  consumer: `LookupGoal`'s per-conjunct fork (the shipped deliver
  pattern) may ship with the arc; `TablePropagator` refuses sub-1
  until the GAC-over-constrained research opens; first conditional
  PRODUCER is snapshot persistence (#75), not the SQL direction. (5) Probabilistic facts wait for a
  customer. (6) Concurrent solves sharing one `TabledSource`
  serialize until #64. (7) `GoalSource` needs `Residues.restate` (or
  equivalent) to impose the probe's region on the body — check
  visibility. (8) FORWARD PRESSURE, seam holds (the human's correction, Aug 2026):
  join pushdown rides `Call<Relation>` ITSELF — lookups are posted
  constraints (`TableConstraints`), so DEFERRED (posted, not eagerly
  enumerated) partner lookups project into the probe's region as
  atoms, and a `TableConstraints`-family compiler under the shipped
  registry emits the join, gated by `id()` equality. The ladder:
  SEMI-JOIN narrowing first — sound under the over-delivery law as
  filed, the partner posting stays enforced engine-side, zero new
  obligations, just the third compiler (aliases in ColumnResolver,
  coupled existentials as join keys); FULL-JOIN DISCHARGE later — one
  fetch retiring the partner posting is absorption, not narrowing,
  and needs the crossing design. The genuine new piece is the
  DEFERRAL POLICY (when a lookup posts instead of enumerating — the
  imposition spectrum at the data boundary; Barrier pricing already
  orders). Aggregation pushdown keeps the derived-relation route (a
  compiled fold, GROUP BY, its seal discharged by the backend's pin).
  The hand-rolled Database's trajectory as these land: reference
  oracle + zero-dependency default; in-memory H2 is the tunable
  middle tier, swap-per-relation, profiler-gated (JDBC per-statement
  overhead vs GAC's per-step probes is the number to watch).
- **links**: tabled-constraints.md (`Call`, subsumption, replay),
  table-completion.md (the seal), condition.md (cells, finality),
  domain-layer.md §4/§6, postings-are-the-store-language (the wire
  format), negation-over-finite-goals.md (#118 — a sealed cell is a
  finite goal), #64, #75, #111 (the front door likely wears this
  face).
