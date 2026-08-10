# Goal delivery over Channels — retiring the Cont protocol from Goal

**STATUS: SHELVED (August 2026). The direction: `Goal` stops speaking
`Cont` and delivers through the emit machinery — answers as explicit
produces into a Channel, failure as seal-without-delivery, completion in
the type. The virtual-threads engine (docs/shelved/) is the same
instinct taken further; this is its one-step-less-radical cousin.**

## The smells this shelf collects

- `Cont<T,R> → List<T>` is not a continuation operation: the type has no
  completion signal, and a multi-shot continuation's invocations are not
  enumerable from the algebra. Every grounding of a Goal's deliveries is
  therefore a WORKFORCE operation — claim, collect, seal — rebuilt
  outside the type (`Exhaustion.collected`, the one sanctioned home).
- Silence-as-failure is a convention, not a type: nothing distinguishes
  "failed" from "not yet delivered" except the external seal.
- A smuggled `Resume` (callCC's captured continuation invoked from
  outside the claimed workforce) can deliver after the seal; the guard
  in `Exhaustion.collected` refuses it loudly, but the type admits it.
- The engine is already half-migrated without saying so: tabling,
  aggregation, committed choice and tracing all ride the emit machinery
  (produce/claim/await/seal). Only the inner loop — conjunction and
  disjunction as CPS composition — still speaks Cont.

## What the migration would buy

Deliveries become explicit produces; `collected` becomes "drain a
channel to its seal" — lawful by construction, because a Channel IS an
answer stream with completion in the type. The silence convention
disappears (failure = sealed empty). The Exhaustion class retires. The
delivery protocol stops being rebuilt at every grounding boundary and
becomes the type of Goal itself.

## The preferred shape: rebuild the monad's internals, keep its surface

The stronger variant (the human's, August 2026): the monadic type keeps
its interface — flatMap, pure, apply-style consumption — while its
INTERNALS become delivery-typed: a producer under a scope claim,
deliveries into a channel, seal as completion. pure = produce-once-and-
seal; failure = seal-empty (the silence convention becomes a typed
fact); flatMap = per-delivery spawn-and-forward. The hybrid stops being
an architectural convention and becomes the data type's private
representation. Consequences: Goal's type never changes and the
migration collapses to functional's internals plus the grounding sites;
consumers become scope-bounded subscriptions, so the smuggled-Resume
hole closes structurally; Exhaustion retires into the type.

## What gates it

- **Fusion, the crux.** A k-invocation is one function call; a channel
  delivery is counters, queues and waiter wakes. The hot conjunctive
  loop must not pay a channel tax: the representation needs a fused
  fast path — channel machinery reified only at genuine branching or
  parking, linear composition compiling to direct calls. If the fusion
  leaks, the step pins kill it, correctly.
- **Fairness preservation.** Scheduling rides the exact frame structure
  the CPS produces; the channel-monad must yield the same frame shapes
  or search order and fairness shift globally. The chaos harness and
  the pins are the judges.

## The cheap falsifier

The fusion question is prototypable STANDALONE in functional: build the
fused channel-monad with no logic wiring, race a linear chain and a
conde fan against Cont on step counts. Small, and it converts the
shelf's biggest unknown into a measurement before anything touches the
engine.

## Triggers

- Boundary-grounding sites multiply (each new `Exhaustion.collected`
  caller is a vote that the seam is in the wrong place).
- Measurement shows the CPS-vs-channel cost gap closing on the linear
  path.
- The virtual-threads experiment revives (its go/no-go gate is the
  completeness/fairness trap; this direction shares the answer).
