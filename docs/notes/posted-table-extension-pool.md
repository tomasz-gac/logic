# The posted table's extension, coverage-cached on the propagator

- **status**: parked — design settled in conversation (Sep 2026), build
  trigger-gated
- **evidence held**: derivation (soundness rides shipped laws); no
  measurement — that absence is exactly why it is parked
- **imports**: none new; pool, coverage, and land are
  `CachingAnswerSource`'s own vocabulary
- **obligations**: (1) the trigger — a real workload showing
  `TableParkingPropagator` wake cost in the profiler chains or the
  step suite (apps/library is the expected first payer); (2) when
  built: `covers`/`land` doors opened on `CachingAnswerSource` (its
  sync `answers()` face becomes their composition), the propagator
  holding one as a droppable memo, receipt = a second wake under a
  narrower walked pattern serves without re-driving produce;
  (3) `CachingAnswerSource` moves out of `pldb.sql` — backend-agnostic
  by its own javadoc, and this would be its first non-SQL customer;
  rides the build commit
- **links**: sealed-table-zip.md (a different dial: replay imposition,
  not extension reads); docs/design/nogood-store.md (the Posting
  chokepoint the propagator answers through)

`TableParkingPropagator` re-drains its produce to the seal on every
wake: replay of the sealed cell, a fresh fold, then the verdict ladder
over the whole extension — O(|E|) with real fiber-frame constants,
once per narrowing. The claim: hold ONE `AnswerStore` pool on the
propagator, land each drained-to-seal extension with its probe
recorded as a call, and serve any later wake whose probe a recorded
call subsumes straight from the pool's buckets. This is
`CachingAnswerSource`'s doctrine unchanged — pool + probes-as-calls
ledger + `Call.subsumes` as the coverage proof — with the drain-to-seal
playing the role the seam's over-delivery law plays for SQL: the seal
IS the completeness proof for the recorded probe, so wide serves
narrow. It is NOT WriteBuffer's shape, despite the family resemblance:
WriteBuffer's delta holds facts its base lacks, so reads must union
both; the pool holds facts the base already proved, so reads may skip
the base on proof — and the skip is the entire point.

Soundness across branches is tabling's own argument: produce runs on
the clean package, so answers are world-level — they depend on the
probe, never the branch; branch state enters only through the probe's
residues, which `Call.subsumes` already judges. A sibling whose probe
fails the proof drains for itself, sound everywhere, fast where proof
holds. The memo is droppable: the table still holds everything, and a
rebuilt propagator (`watching`) starts empty and re-drains.

What it buys: covered wakes drop from O(|E|) to a bucket probe. What
it does NOT buy: the first wake (still drains to the seal), or
anything today — #144 deferred this door on "the table makes replay
cheap," and no workload has contradicted that with a number. The
cheapest kill: profile a posted-table-heavy domain; if wake cost never
shows in the chains, this note dies unbuilt.
