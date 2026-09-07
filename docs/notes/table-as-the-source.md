# The table as the source — AS BUILT: tabling compresses, the solve owns the residence

A derived relation is a goal COMPRESSED by tabling into answer cells,
and the compression's residence is a PACKAGE, not a mechanism: tabling
takes its table from the package a call runs in — and a solve has
exactly ONE, planted at the root (`Goal.solve` seeds
`Package.empty().withStore(Table.empty())`). Everything tabled in a
solve lands in that one table, keyed by (relation value, argument
pattern), so the whole discipline is inherited rather than imitated —
the producer guards, claim-once mastery, finality (ground answers
stream, conditional answers deliver converged at the seal), consume's
unification filter (a narrow probe reads a sealed wide entry and gets
exactly the answers it asked for), and completion detection, so
recursive bodies seal instead of hanging. pldb owns NO tables: it
translates between the seam's wire shape and the solve's residence.

## The seam, as shipped

Two kinds, mirroring the store's two propagator lanes (the two-lane
doctrine's third instance):

- `AnswerSource` (SYNC): `Iterable<Tuple2<Reified<?>, Condition>>
  answers(Call<Relation> probe)` — inline enumeration. Population:
  the in-memory `Database`, `SqlFetch`, `CachingAnswerSource`.
- `AnswerProducer` (ASYNC): `Fiber<Nothing> produce(Call<Relation>,
  Emitter<Tuple2<Reified<?>, Condition>>)` — the produce half of a
  table entry: probe in, (term, Condition) stream out, seal =
  end-of-stream. The BOUNDARY contract — the shape an HTTP face or a
  #64 foreign producer wears. Local population: `GoalProducer` and
  `SyncLift`.

Both carry `long estimate(Call)` and `String id()`, always synchronous
(the pricing law). `SyncLift` wears a source as a producer — the
enumeration runs inline inside whoever drives the produce, the cost
the sync kind always had.

`GoalProducer` is the ONE produce bridge: a rule driven into an
INJECTED table. It holds (relation, rule body, captured heads, table);
produce restates the probe image onto the heads, runs
`Tabling.call(rel, heads, body)` on a clean package carrying the
injected table, and images deliveries back over the heads. Residence
is the INJECTOR's decision: hand in the solve's table and the entry
this production fills is the same one every goal-side consumer reads —
one production, two readings.

## The rule kind: a relation is a method

`Literal` is the one public type — a relation applied to arguments,
with HOW it answers (source, producer, or rule) as a `Reading` chosen
by the builder's terminal and never visible above it. Relation
identity is by VALUE (name plus columns), all the way down:
`Call.subsumes` and `Call` equality both key tokens by `equals`, so
every mint of one defining method is the same relation — for
exact-key sharing and for subsumption/coverage proofs alike.

The rule kind (`Literal.relation(...).arg(...)....solving(body)`) has
two readings of one production:

- **applied** (bare in a conjunction): `Tabling.call(rel, args, body)`
  — an ORDINARY tabled call in the caller's own package, landing in
  the solve's table. Recursion is the defining method calling itself;
  the second entry into the same region becomes a consumer of the
  first, and completion detection seals the ring. There is no second
  tabling layer and no re-basing: the old fixpoint-vs-boundary
  two-layer story is dissolved on this side.
- **posted** (under `exclude`, or `posted()`):
  `TableConstraints.postedRule` parks a `TableParkingPropagator`
  whose producer is COMPOSED AT WAKE — `GoalProducer` over the
  SOLVE's table, extracted from whichever package the examination
  arrives in (a posted rule outside a solve refuses by name). The
  extension it drains is the same entries every goal-side consumer
  shares.

## Negation and the ring, resolved

Negation is COMPOSITION: `exclude(p(x, y))` — a `Literal` is a
`Postable`, `posted()` returns the table `Posting`, and the trial
imposes the posted extension on scratch and judges it three ways. The
sealed extension is the completeness certificate (the closed world is
the relation's own definition over its pinned sources); de Morgan
happens inside the machinery: ground rows forbid tuples, a wide row
excludes its item outright, the diagonal negates to a disequality, a
CONDITIONAL row filters through its guard.

Self-negation is now WRITABLE — the defining method can `exclude`
itself — and it REFUSES LOUDLY: because posted rules share the solve's
table, `p(x) :- x=1, ¬p(x)` is a genuine quiescent cyclic wait (the
nested trial consumes the OPEN entry its own production holds), and
the substrate's strand refusal names the channel
("blocked at unsealed … selfNeg"). Receipt:
`UnstratifiedNegationTest`. This discharges the old
experiment: the previously-observed hang under PRIVATE tables was
neither park-quiescence nor a substrate gap but a silent livelock by
world multiplication — each negation minted a fresh world and the
regress never quiesced, so there was nothing to refuse. The shared
residence is what turns the same program into a refusable wait; that
receipt is what killed owned tables.

The probe rule stands, both kinds: the call key is minted WITHOUT the
asker's own family — the posted table IS the question, and transcribed
into its own probe it re-animates inside the producer and rings; its
per-wake supports in the key would fragment the coverage ledger. FD
domains and nogoods stay: the question's honest context and the
pushdown's material. One mint, `Extension.probe`, shared by both
propagator kinds.

## The seats: generators, guards

An applied literal ENUMERATES — the choice lives in the search tree,
bindings are ground per branch, distinct derivations of one tuple fold
by alpha-equality. A posted literal DEFERS — the choice lives in the
condition. A fixpoint converges in rows or diverges in conditions, so
the GENERATOR SEAT (a recursive body's base case) must enumerate; a
deferred generator compounds per unfold into an infinite condition
antichain — each unfold a genuinely new conditional answer, absorption
correctly refusing to fold incomparable claims. Posted in the GUARD
SEAT (after the recursion has ground its inputs) is sound and
discharges through the verdicts; its one generative corner is
collapse, which fires exactly when the relation is locally
deterministic. posted ≡ applied is an accidental-control equivalence
precisely where discharge is reachable — labelling present, or
determinism, or groundness; outside that, the failure mode is
non-termination, not wrong answers.

## The cache story, settled by deflation

- `CachingAnswerSource`: the SQL sync tier's coverage-ledger cache —
  pool + probes recorded as calls, `Call.subsumes` proving coverage
  (value-keyed, so per-mint relations hit coverage recorded under an
  earlier mint), exact pricing over covered probes. Its soundness
  precondition is §5.1 stability (the `isolation()` witness, declared
  and not yet checked). The GAC tier's probes reach it as DATA
  (region → WHERE), never as re-imposed goals.
- Cross-solve reuse of a RULE's extension: none engine-side — the
  residence is solve-scoped by design. What persists across solves is
  the SOURCE tier (the coverage cache under one pin); rule extensions
  recompute per solve against it.
- Engine-side PERSISTENCE of tables: SHELVED. The caller persists
  results as domain data (the request-scoped transaction is the pin
  lifecycle worn as ordinary infrastructure; same-transaction writes
  need no stamp). Reopening triggers: a workload with slow-moving,
  honestly-tokened sources AND a measured rerun cost that hurts —
  and the first form is then writeback-materialization of the ground
  corner, not engine-opaque marshal. Warm-start/rekeying shelved with
  it.
- Staleness: the pins scaffolding is DESIGNED, deferred —
  `Pin<S extends AnswerSource> implements PartialOrder<Pin<S>>`
  (per-class reuse policy distinct from `leq`), keys `(class, id)`
  slot-style, the `Pins` transport store recording per-branch
  footprints with the §5.1 conflicting-re-record refusal,
  `SnapshotVector` on the two caches; ⊗-only, no ⊕ anywhere until
  per-answer validity; pins are ENVELOPE METADATA (cache invalidation,
  marshal validity, the commit-time stamp), never conditions — a
  constraint is an obligation that dies satisfied, a pin is a record
  that must outlive satisfaction.

## considered, built, and torn down

- `TabledSource` / `Derived` / `solvingRecursive` — the owned-table
  design: token and producer sealed together, each derived relation
  carrying its private residence, typed `_N` faces making foreign
  pairing unwritable, `table()` as the warm-start door. Built,
  receipted, torn down by its own negation story: private residences
  multiply worlds under negation, turning an unstratified program
  into an invisible livelock the substrate cannot refuse. The one
  shared residence replaced it; the typed surface moved to the
  user's own method signature (the defining method IS the typed
  face); the warm-start door died with the owned table (reopening
  trigger unchanged, under persistence above).
- `Relations._0.._8` — the arity family, deleted with the above: the
  builder states columns, the method states types, nothing is left
  for an arity façade to say.
- `LookupGoal`'s `Either<AnswerSource, AnswerProducer>` — dissolved
  into `Literal`'s `Reading` seam: one public type, the kind chosen
  by the builder terminal, never branched on above it.
- The REPLAY CONTAINER (the original design of this note):
  TabledSource as claim/cursor/replay over tabling's public pieces.
  Built, evaluated, torn down — the replay was a degraded
  `Tabling.consume`, and fixing it meant rebuilding tabling
  piecemeal. Inheriting-not-imitating survived it and carried into
  the final form.
- Generic seam kinds (`AnswerProducer<Tuple…>`): answers are PATTERNS
  (Anys, couplings, conditions), not value tuples; typing lives at
  the defining methods, the seam stays transport.
- Pins as factors: wrong lifecycle (constraints discharge; pins must
  survive grounding) and wrong delivery (knowledge about unprojected
  variables is noise). Envelope metadata instead.
- The rest of the original rejections stand as recorded (TablingMode
  split, the workforce front door, `entry()` on the seam, scheduler
  factories, Optional-pattern probes, Rx faces).

-----
- **status**: SHIPPED (Aug–Sep 2026) and receipted end to end: the
  seam identity oracles (memory ≡ PostgreSQL via testcontainers,
  nonrecursive + posted + recursive — domain-layer §12 Phase 2's
  proof), value-equal mints sharing one production, conditional/Any
  answers through delivery, posting and negation,
  `postedAgreesWithExists`, the unstratified-negation strand refusal,
  the function-shaped test surface (no bare `RelationN` anywhere).
  Graduation to a reference doc is the open question for the human.
- **imports**: unchanged (EDB/IDB residence; completed tables enable
  non-monotone consumption — the negation receipts are the first
  consumer; formal receipt still owed at graduation).
- **remaining obligations**: (1) The condition-depth watchdog (refuse
  a cell whose condition ascends past a budget — the loud failure for
  deferred generators). (2) #64 foreign producers; `SqlFactSource`
  becomes a producer then. (3) The pins scaffolding when in-process
  cross-pin sharing becomes real. (4) The tabling-always-on question
  for the rule kind (whether applied reading should ever unfold —
  parked pending a measurement; flipping it changes multiplicity
  semantics, not just cost).
- **links**: tabled-constraints.md §9 (the conditional posted table),
  constraint-kernel.md §4 (the two lanes), condition.md,
  one-speculative-judge.md, #118, #144, #145, #149, #150, #75.
