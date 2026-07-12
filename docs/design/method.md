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
   design doc's lineage/shelved section, not a commit message.

## The moves

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
- **Verify at the source, not per step.** When an invariant holds by
  construction, pin it where it is constructed (DomainUpdateContractTest)
  and run the machinery unchecked; keep the checked twin one word away for
  development. Checking on every step what the toolkit cannot express
  violating is cost without information.
- **By-construction beats by-convention beats by-comment.** The same
  contract climbs: prose plea (Suspension's monotonicity javadoc) →
  runtime check (MonotoneDrain) → unrepresentability (threshold vocabulary,
  `Verdict.keep`). Climb when a customer justifies the rung, not before.

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
