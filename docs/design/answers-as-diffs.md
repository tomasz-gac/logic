# Answers as package diffs — auto-deduced call args, and where that breaks

**STATUS: DESIGN SKETCH (July 2026, from a conversation with Tom — his idea:
diff the package before/after a tabled goal to deduce its call args at seal,
so goals could be tabled without declaring a relation). Nothing built. The
verdict below: the REFRAMING is keeper, full auto-args hits four walls, and
one safe product (`memo`) plus one cheap canary (diff-as-verifier) survive.
Touches three parked designs — revisit when the first of them opens:
`assembler.md` (call-values), `goals-as-data.md` (templates),
`tabled-constraints.md` (answer purification, stage 2).**

---

## 1. The reframing (keep this even if nothing else is built)

**An answer is the goal's externally visible package delta, existentially
projected onto the outside world.** Diffing the caller's package against the
answer package unifies three operations treated separately today:

- **answer reification** — the substitution half of the delta (today:
  `reify(answerPkg.substitution, argsTerm)`, i.e. the delta pre-filtered by
  declared args);
- **answer purification** (TCLP) — the constraint-store half of the same
  delta: decide the purely-local residue, project the coupled residue
  (`tabled-constraints.md` §5, and the §6 stage-2 hazard: per-var FD residues
  lose arg↔arg correlation routed through a local — `x1 = w = x2, w ∈ {1,2}`
  must not decay to `x1 ∈ {1,2} ∧ x2 ∈ {1,2}`);
- **footprint deduction** — which external variables the goal touched at all.

Reify-the-args is the special case where a declaration says which part of the
delta matters. This is the operational restatement of TCLP's
answers-are-regions view.

## 2. The idea, and the four walls

Record the package at call entry and at each body success; diff; at seal,
declare the union of external diffs to be the call's args — tabling without
`defineRecursive`'s formal-parameter plumbing, also relieving the assembler's
template declarations.

1. **A diff sees writes, not reads.** The variant key must contain what the
   goal READ: a body dispatching on ground `x`, emitting `y = x+1`, writes
   only `{y}` — callers with `x=1` and `x=2` yield identical-shaped diffs and
   different answers. Keying on diffs conflates variants → silent wrong
   reuse. Observing reads means instrumenting walk — adornment analysis by
   instrumentation, and it must run BEFORE lookup, defeating lookup.
2. **Locals need a root set.** The diff contains body-local bindings; telling
   external from fresh is liveness — reachable from WHAT? Declared args are
   exactly the root set. Without them the only root is the solve's `out`,
   making footprint a global reachability walk per answer.
3. **Seal-time deduction deadlocks the fixpoint crank** (the fatal one for
   recursion). Consumers can unify nothing until the interface exists; for a
   recursive goal the consumers ARE the recursion — incremental consumption
   turns the LFP crank. Everyone-parks-until-seal + seal-needs-consumers =
   structural deadlock. (Closed tabling only APPEARS to wait: its TERMS flow
   incrementally during explore; only values wait for the star. An interface
   unknown until seal blocks the terms themselves.) Escape hatch — footprint
   as a monotone cell, consumers re-parked on footprint growth — is coherent
   park-as-data machinery, but heavy for an ergonomic win.
4. **Cross-callsite correspondence is what declaration actually provides.** A
   second caller's lvars are different objects; matching them to the entry's
   footprint needs a correspondence — which is the positional args tuple.
   Deriving it structurally is goals-as-data / the assembler's
   (actuals, template) split: the diff cannot conjure the correspondence,
   only a template can.

## 3. What survives

- **`memo(goal)` — the non-recursive subset, fully sound.** No fixpoint to
  turn, so wait-until-seal is fine (the master alone drives the seal); one
  effective callsite, so no correspondence problem. Diff at seal, project
  against liveness-from-`out`, cache. Recursion detection is free and loud: a
  coated re-entry of the same goal during its own explore throws "recursive
  goals need declared args". The findall/aggregate-style sub-search is the
  customer.
- **Diff as VERIFIER, not deducer.** Keep declared args; diff at produce and
  assert `footprint ⊆ declared args ∪ locals`. An over-binding body (touching
  an undeclared outside var) is today a silent answer-generalization hazard;
  the diff makes it a loud one — the coat-canary pattern, nearly free since
  produce holds both packages already.
- **Assembler relief, not replacement**: the template still declares the
  interface (wall 4), but observed footprint could spare declaring FORMALS
  separately from the constructor; actuals at each `apply` give per-call
  correspondence. Margin note for `assembler.md` when it opens.

## 4. Non-goals

- No automatic args for recursive tabled relations (walls 1, 3, 4 together).
- No walk instrumentation for read-sets (wall 1's cost note) without a
  customer that already pays for adornment observation.
