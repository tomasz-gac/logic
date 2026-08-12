# Negation of a finite tabled goal = its sealed answer set posted as nogoods

- **status**: argued — derived in conversation (August 2026); the
  two-component inversion is the human's design (August 2026), no code
- **evidence held**: derivation; both prerequisites already receipted
  in code — the stratification boundary (functional 2168065 "the ledger
  is the work" + `Frame.stepSealed`: seal-waiters keep their pair open,
  wait-for-yourself throws; demonstrated live by the join probe, run
  and deleted, August 2026) and sound collection
  (`AggregateTablingPinTest` @ ca27203 pins findall/count over cold
  tabled goals at the TRUE counts — "count over a tabled goal is
  negation-safe" — so the vacuous-success hazard is closed); the ∃/∀
  pair (`NogoodsUnderTablingTest`) receipts why witness literals may
  not ride a complement live
- **imports**: proposed, not yet adopted — "constructive negation"
  (Chan 1988) and the positive/negative suspension-edge split of SLG
  resolution (Chen & Warren 1996); receipts owed at adoption time
- **obligations**: (1) at front-door time, a logic-level pin on the
  `q :- not(q)` shape asserting the named refusal (and mapping it to a
  stratification error in the user's vocabulary); (2) the v1 boundary
  receipts — free-arg literal-drop, the all-free empty-nogood failure,
  the witness-coupled refusal; (3) at general-negation time, the
  witness-labelling path (below) — decide, don't inherit
- **links**: nogood-store.md (the kernel this compiles into),
  group-seal.md (licensed over peer rings by the value-waiter EOF arm),
  transcription-generifies.md (answer deltas as literals),
  condition.md §8 (negation's slot in the dependency chains);
  row-set-basis-to-core.md is NO LONGER on this chain (below)

## The claim

`not(g(xs))` for a tabled goal whose table seals: drive the table to
its seal, read the complete answer set, post the complement — **one
nogood per answer, the answer's whole delta read as literals**. A
tabled answer has exactly two components (the human's inversion,
August 2026): substitutions and constraint factors, and the posting
vocabulary covers both — bindings on call args become bind literals
("negate the substitutions"), residue factors become factor/statement
literals ("negate the constraints"). Coverage of the table is the
UNION of answer regions, so the complement is the CONJUNCTION of
per-answer nogoods — De Morgan at the answer-set level, region
inversion with no enumeration, no projection machinery, and no
dedicated store. The trial gives each nogood the exact semantics: a
caller inside a coupled answer region (x=2, y=2 against
x∈1..3 ∧ y∈1..3 ∧ x+y=4) finds every literal entailed and dies; a
caller outside it refutes a literal on the scratch and discharges.
The complement of a coupled region falls out of the nogood's
jointness.

Neq is the unit case (the negation of a one-answer goal). Unlike
negation-as-failure it does not flounder: `not(g(x))` with `x` free
yields a constraint on `x` — labellable, usable. The paying customer
is pldb: NOT EXISTS, set difference, the "no revocation record"
authorization slice. And the composition closes post-Neq-kill: an
answer carrying a `x≠3` residual makes the complement literal a
stated nogood — ¬¬(x=3) — which the empty-store classifier already
decides at ground.

Every stage exists: closed solve (the closed-aggregate frame, birth
watermark guarding capture), await the seal, transcribe (the posting
rows' own `rename`), post the nogoods.

The postings can even STREAM: each answer's nogood is sound the moment
the answer arrives — the complement only strengthens as the set grows —
so the seal gates `not(g)`'s COMPLETION, not its posting. The positive
dual cannot stream (a growing disjunction weakens): see
sealed-table-zip.md for the asymmetry and the either-record it licenses.

## The v1 boundary: what each answer shape contributes

- **Ground binds on args** → bind literals; the joint record excludes
  the point, not its slices.
- **A FREE call arg contributes NO literal**: the answer covers every
  value of that arg, and ¬(x=3 ∧ y=anything) = ¬(x=3). The limit case
  is the ruling for free variables (the human's, August 2026): an
  answer with ALL args free covers everything, its nogood has zero
  literals, and the empty nogood is ¬(TRUE) — born violated, the
  branch FAILS. Negation of a free variable is failure by the trial's
  own degenerate case, not a special rule. (This replaces the earlier,
  coarser "non-ground answers refuse".)
- **Ground witnesses drop their literals**: the table already labelled
  coupled witnesses at the answer boundary (answers may not leave with
  live records), so sealed answers arrive per-witness ground, and
  ∃w(x=1 ∧ w=3) ≡ (x=1) — dropping the witness literals IS the exact
  projection onto args. Free uncoupled witnesses drop for the same
  reason free args do.
- **Args-only residues** transcribe as factor/statement literals
  (the negated box ¬(x ∈ 1..5) is the one-literal case).
- **The one refusal: a residue coupling an arg to a still-unlabelled
  witness** (a TCLP answer riding x∈1..6 ∧ x·w=6 ∧ w∈1..3 with w an
  ∃-hole). There, both cheap moves are wrong in opposite directions —
  dropping the witness literals over-excludes (¬(x∈1..6) kills x=4,
  never covered; coverage is {2,3,6}), and keeping them with a
  fresh-minted witness under-excludes (nothing ever entails a fresh
  variable's domain; the nogood never fires — the ∃/∀ receipts). The
  exact complement needs the witness eliminated first, and the
  witness's domain is finite, so elimination is LABELLING it at
  negation time — turning the answer into per-witness ground form,
  where the drop rule applies. That path belongs to the
  general-negation design; v1 refuses the shape loudly.

## The row-set store leaves this chain

The earlier compilation (one nogood stating a positive row-set
membership constraint over the answer set) was an optimization, not
the semantics — the per-answer form needs no store at all, so #118 no
longer waits on the row-set extraction (#119 keeps its other two
customers: weighted-TCLP level sets and pldb). Revisit the compact
form only if the per-answer form measures too slow on wide tables.

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

The monotonicity of reuse holds by narrowing: a sealed entry's answer
set is complete FOR ITS REGION, so the complement derived from it is
valid for every caller inside that region — and knowledge only grows,
so callers only narrow after the seal. The wider-caller case is the
same sealed-subsumer gate the positive side already enforces; the
answer-side order on negated answers is ANTITONE (excluding a bigger
box denotes a smaller region), the standing ruling of nogood-store §7.

The original cheapest kill ran and became evidence: the join probe
demonstrated the substrate refusing a seal-waiter ring, closing the
note's central danger. What remains falsifiable is the front door —
the `q :- not(q)` pin and the v1 boundary receipts.
