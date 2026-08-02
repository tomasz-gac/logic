# Method — how this design happens

Nobody designed the engine's algebra top-down. The structure was found in
working code, named from the literature, and policed by laws. This file
records the loop and its moves so they run on purpose. Entry criterion: a
move earns a place here after it has fired at least twice.

## The loop

1. **Build the pragmatic thing.** Working code first; the structure is not
   knowable in advance.
2. **Smell structure.** A hunch that something is "a known thing in
   disguise" — usually from the human, who holds pattern-memory across
   weeks; the assistant retrieves and formalizes on demand but does not
   volunteer connections until prompted. The division of labor is part of
   the method: hunch-holder and name-finder, neither sufficient alone.
3. **Name it from the literature.** The name arrives with theorems attached
   (closure operator → confluence; chaotic iteration → any-order; semiring
   → rearrangement legality). Naming is free rigor — fired for: price
   arithmetic = the counting semiring ("the semiring it always was"),
   cascade = chaotic iteration, ripeness = CCP's ask, Neq's
   `verificationStep` = the verdict protocol case-for-case.
4. **Write the laws, let them veto.** Law tests are not verification
   theater; they reject representations. Fired four times: `X∩∅=X` in
   Interval, the dual-⊥ in EnumeratedDomain, Neq's order-sensitive store
   equality, the List backing that no lawful meet could sit on.
5. **Decide placement** (the razors below).
6. **Adopt with a live consumer, else shelve WITH A TRIGGER.** Never an
   undated "later": every deferral names the event that reopens it (second
   ripeness author; next propagator author; a third toolkit user or a
   profile showing Neq; TCLP). A shelf without a trigger is a graveyard.
7. **Record the reasoning where the next reader will look** — the relevant
   design doc's lineage/shelved section, not a commit message. A revert
   records the refuted theory alongside its refutation (the seal-walk
   retry), not just the removal — the next person to have the same wrong
   idea should find the counterargument waiting.
8. **Run the loop backwards once it closes.** When the found structure is
   complete — laws green, both faces named — new capability is DERIVED,
   not smelled: ask what the algebra's missing operation means
   operationally and the design falls out priced. Fired across one arc
   (condition.md §8): ¬ → constructive negation over seals; the store's
   missing join face → lift; ⊥'s conditional answers → clause learning;
   the tensor → piecewise weights. Steps 1–7 find structure in code;
   this step finds code in structure. Questions asked against laws
   resolve as instance, dual, or missing face — each answer cheaper than
   the last, because the previous one strengthened what it is asked
   against.

## The moves

- **The comprehension veto.** "If I don't get it, it's not designed
  properly. We will try to make me understand it and if we can't then
  we'll be changing the code." Explaining to the human is a proof
  obligation, not a courtesy: an explanation that cannot be given plainly
  indicts the design, and the code changes until it can. Kill list since
  adoption: the resume referee (→ always-park suspension), the sealOnly
  flag (→ the Sealed node), the seal walk's edge taxonomy (→ "the ledger
  is the work"), the walk retry (→ reverted with its refutation), the
  split-brain answer cell — three delivery regimes the human refused to
  accept as necessary (→ the constraint ring). Distinct
  from adversarial deflation: deflation attacks necessity, the veto
  attacks intelligibility; a proposal ships only after surviving both.
- **Adversarial deflation.** Every proposal is attacked before it ships —
  by the other party, as a step, not a courtesy. Downgrades are wins of the
  method: normalize-at-meet died under "doesn't verifyAndSimplify already
  prune?"; the body-scope guard on Suspension died under "fires once".
  A proposal that survives deflation ships smaller and truer.
- **The negative witness.** A theory that cannot say "this is NOT an
  instance" explains nothing. Kept deliberately: the optimizer's rewrite
  passes (no order to descend — mutual inverses oscillate), protocol
  messages (`Revision.combine` would fabricate structure the protocol
  deliberately lacks), suspension stores (sets of closures are only
  trivially sets).
- **Honest ledger.** Benefits are stated with their tense: banked now vs
  promissory-with-customer. "One bug-shaped fact plus eligibility" is a
  complete and acceptable answer; inflating it is not.
- **The theorem import receipt.** A name does not import a result by
  resemblance; the theorem arrives with hypotheses attached. Record: the
  structure imported, the carrier, the operations, the EXACT hypotheses,
  where each is enforced (type, law kit, convention), the operational
  consequence claimed — and the NOT-PURCHASED list (termination, memory,
  speedup: whatever the theorem does not give). A law kit can reject an
  implementation; it cannot prove the import — so keep one counterexample
  showing a hypothesis earning its keep (⊕ = ⊗ = max: both idempotent,
  distributive, no absorption, no lattice). The algebra javadocs already
  carry half the form ("what it buys / what it does NOT buy"); the
  receipt completes it. The failure mode it prevents now has a name —
  EQUIVALENCE INFLATION: "can model" silently becoming "is" (the
  domain-layer original was the specimen; its rewrite is the correction).
  From the first external deflation (an outside reviewer, August 2026).
- **Evidence has a type.** The honest ledger's tense discipline, applied
  to epistemics: algebra DERIVES inside a stated model; law kits FALSIFY
  over generated cases; stress tests EXPOSE schedule failures without
  establishing their absence; benchmarks MEASURE one workload on one
  box; a live consumer DEMONSTRATES one problem solved. Record the
  strongest evidence actually held and never silently promote between
  kinds. Speculation stays legal when labeled — vision.md's
  "theoretical, no benchmarks yet" sections are the standing customers;
  a "plausible win" written in the grammar of a conclusion is the
  failure mode. (Same review.)
- **Close the design when it lands.** An implementation is not complete
  until its document graduates: status header replaced, principal code
  and tests linked, deviations recorded, remainders re-shelved with
  triggers, supersessions marked from both directions. A document saying
  "nothing built" beside shipping code is an unfinished implementation
  task. Fired: the semiring-inference and lattice-store headers (caught
  stale after their phases shipped, August 2026); pldb's
  table-constraints.md — the external review's catch — "Nothing built"
  on the same master as `TableConstraints`, `Support` and the tests that
  name the doc. (Same review.)
- **Verify at the source, not per step.** When an invariant holds by
  construction, pin it where it is constructed (DomainUpdateContractTest)
  and run the machinery unchecked; keep the checked twin one word away for
  development. Checking on every step what the toolkit cannot express
  violating is cost without information.
- **By-construction beats by-convention beats by-comment.** The same
  contract climbs: prose plea (Suspension's monotonicity javadoc) →
  runtime check (MonotoneDrain) → unrepresentability (threshold vocabulary,
  `Verdict.keep`). Climb when a customer justifies the rung, not before.
- **Instrument, don't derive.** When armchair analysis of concurrent
  behavior spirals past two rounds without converging, stop and make the
  system answer: build the reproduction ladder up from primitives
  (substrate test → minimal composed case → shrink → bisect), then move
  the question into the code as throwaway probes. Fired: the condu
  premature-drain hunt (test ladder plus scope prints found the group-seal
  bug); the ForkJoin loss (frame lifecycle events, then the tagged pending
  audit, whose one full capture named a lost fork after days of derivation
  could not).
- **The schedule as adversary.** Order-dependence is a broken law made
  observable: a fold whose result varies under schedules is a ⊕ that is
  not commutative-associative-idempotent — no further analysis needed.
  Randomized-scheduler properties (the chaos harness) are law tests at
  the SYSTEM level, the operational twin of "write the laws, let them
  veto": value laws reject representations, the adversarial schedule
  rejects delivery designs. Fired: the entailment dedup bug (rediscovered
  at seed 1 on unfixed code), min-plus order-luck (4 vs 6 under a driver
  reorder), provisional-value duplicates (seed 2).
- **Fix bugs with laws, not memory.** A delivery bug that tempts
  per-reader bookkeeping — a delivered-set, a seen-values map, a mode
  flag — is a law's absence made manifest: ask which algebraic property
  would make the bookkeeping unnecessary and strengthen the VALUE
  instead. The answer cell's three memories each dissolved into a law:
  the delivered-set into ⊕'s absorption, the re-delivery map into
  distributivity over the ascent log, the finality flags into
  1 ⊕ a = 1.
- **A lawful seam is a demand letter.** A substrate whose types carry
  laws (Channel demands Semilattice; the ambient scope bills by
  construction) issues debt to every layer above: the layer must become
  lawful too, and its order bugs are the unpaid installments — CALM in
  the small, coordination-freedom purchasable only with monotone
  structure. Budget the domain refactor when signing the seam; an
  extraction is not done until the layers above stop special-casing.
  Fired: Channel's Semilattice bound forcing the three-regime cell into
  one ring; the ambient scope forcing the coat's deletion.
- **The conditional-guarantee trap.** A contract that holds only while a
  global non-property holds breaks a stranger's code the day the condition
  first fails, silently. Name the condition; then make it structural or
  refuse loudly. Fired: fork completion as sub-search exhaustion (true
  only while nothing in the subtree suspends → Exhaustion over the seal);
  fire-and-forget `fork()` (eventual execution is backstopped by joins
  nobody performs → `pool.execute`).
- **Instruments are scaffolding; refusals are product.** Hunt diagnostics
  — lifecycle traces, tagged audits, state dumps in messages — are torn
  out with the hunt; "keep it, zero cost when off" was overruled twice
  and stays overruled. What ships is the refusal path: an exceptional
  completion is never swallowed into a clean result, and a detector that
  can misfire under concurrency is stabilized (two observations over a
  quiet epoch) or removed.

## The razors

- **Placement rule** (lattice.md §3a): data becomes algebraic — knowledge
  carriers implement the interface, their laws gate-checked. Control gets
  its ARGUMENTS parameterized for free theorems — the bound rides the type
  parameter (`MonotoneDrain`'s `S`), never `implements` on the control
  structure.
- **The equals test**: ask what equality should mean. Same content ⇒ same
  knowledge → data, annotate. Identity of parked behavior → control, bound
  its arguments.
- **Knowledge vs control**: entailment between two values means something
  (TCLP could compare them) → knowledge. An index of who to wake, a parked
  continuation → control.

## What this file is not

Not a history (the per-doc lineage sections hold those), not a style guide
(CLAUDE.md), not a claim that the loop was followed when it wasn't. When a
change bypasses the loop — and some will — the burden is to say so out
loud, not to retrofit a justification.
