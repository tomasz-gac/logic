# Negation of a finite tabled goal = its sealed answer set posted as notes

- **status**: argued — derived in conversation (August 2026), no code
- **evidence held**: derivation; both prerequisites already receipted
  in code — the stratification boundary (functional 2168065 "the ledger
  is the work" + `Frame.stepSealed`: seal-waiters keep their pair open,
  wait-for-yourself throws; demonstrated live by the join probe, run
  and deleted, August 2026) and sound collection
  (`AggregateTablingPinTest` @ ca27203 pins findall/count over cold
  tabled goals at the TRUE counts — "count over a tabled goal is
  negation-safe" — so the vacuous-success hazard is closed)
- **imports**: proposed, not yet adopted — "constructive negation"
  (Chan 1988) and the positive/negative suspension-edge split of SLG
  resolution (Chen & Warren 1996); receipts owed at adoption time
- **obligations**: (1) at front-door time, a logic-level pin on the
  `q :- not(q)` shape asserting the named refusal (and mapping it to a
  stratification error in the user's vocabulary); (2) the non-ground
  refusal receipt
- **links**: note-store.md (the kernel this compiles into),
  row-set-basis-to-core.md (the membership constraint the compact
  compilation states; no negative sibling store — the stipulation),
  group-seal.md (licensed over peer rings by the value-waiter EOF arm),
  transcription-generifies.md (constrained answers as postings, later),
  condition.md §8 (negation's slot in the dependency chains)

## The claim

`not(g(xs))` for a tabled goal whose table seals: drive the table to its
seal, read the complete answer set, post the complement — one note per
answer, the postings being the binds of the call args to that answer's
values. "NOT all these bindings simultaneously" is a note's exact
meaning, so answers transcribe directly. Neq is the unit case (the
negation of a one-answer goal); pldb's table constraint is the positive
form of the same shape, this is its co-store. Unlike negation-as-failure
it does not flounder: `not(g(x))` with `x` free yields a constraint on
`x` — labellable, usable. The paying customer is pldb: NOT EXISTS, set
difference, the "no revocation record" authorization slice.

Every stage exists: closed solve (the closed-aggregate frame, birth
watermark guarding capture), await the seal, transcribe, absorb the
notes.

## What it does NOT buy, and where the SLG boundary already lives

Not general negation — only goals whose tables complete. SLG's
positive/negative suspension-edge split — a consumer parked for ANSWERS
vs a `not` parked for the SEAL — is ALREADY BUILT into the substrate,
as billing rather than edge labels (functional 2168065, the human's
formulation replacing the edge-kind rule): a value-waiter closes its
pair into a blocked record, because its seal-wake is the terminal EOF
arm — a verdict, not a green light — which is exactly what licenses
group seal over tabling's peer rings; a seal-waiter's pair STAYS OPEN
for the whole wait, so its home's counters can never drain past it and
no seal, singleton or group, can pass it by. There is no blocked entry
to classify, hence no walk that could wrongly seal a negative ring.
Non-stratified programs land on refusals by construction: a `not`
waiting on its own workforce's seal throws immediately at the step
("awaits the seal of its own workforce — a wait for yourself",
Frame.stepSealed); longer negative rings (`p :- not(q), q :- not(p)`)
keep every home open, the drive dries, and the strand refusal names
the sources. The join probe (August 2026) demonstrated the mechanism:
an entry could not seal past a seal-waiter in its own workforce.

So STRATIFIED negation with loud refusal at the boundary is the
substrate's existing behavior; the residual work at the logic tier is
ergonomic, not semantic — the front door should map these substrate
refusals into a stratification error in the user's vocabulary.
Well-founded semantics (three-valued, delay lists, the full SLG
apparatus) is real research and stays off the table; the refusal
covers every named use.

The other half of the collection pipeline is already sound: aggregation
over a cold tabled goal drives the table to exhaustion before folding
(consumers suspend as frames — `AggregateTablingPinTest`), so a
negation front door riding the same path cannot vacuously succeed.

Non-ground answers refuse, v1: an answer with a free local makes the
bind-posting read "changed, still owed" forever — the note never fires
while `g` semantically covers everything. Same ruling as the aggregate
fold ("x, x ≠ 3 is infinitely many distinct answers — refuse").
Constrained answers come later, riding residue transcription into
absorb postings.

The cheapest kill is obligation (1): if the walk already refuses
seal-waiter rings, the gap is smaller than argued; if it seals one, the
note's central danger is demonstrated in one test.
