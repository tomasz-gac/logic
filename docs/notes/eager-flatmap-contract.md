# The eager Done-flatMap is a load-bearing contract; make its breakage loud

- **status**: argued, measured, and located (Aug 2026) — the human's
  experiment (eager flatMap in Done commented out, suite run) plus the
  kill-check ladder and a site audit; nothing built yet.
- **evidence held**: measurement and a negative witness.
  (1) The stepping-cost ladder (scratch bench, functional repo): plain
  Java loop ~free; raw Frame.step loop ~10ns/step (~100M/s); DFS/BFS
  ~12ns/step; the ENGINE ~700ns/step warmed (~1.3–1.4M steps/s,
  measured by nanoTime bracket around the job-shop solve — the earlier
  ~450k figure was a cold-run wall-clock derivation, corrected ~3×).
  Consequence: interpreter+scheduler overhead ≈ 2% of an engine step —
  the SCHEDULING-side eager-run idea (budget loop collapsing scheduler
  ticks) is refuted by Amdahl: ≲2% ceiling. The in-place frame work
  already ate the allocation cost the old implementation beat.
  (2) The human's experiment: with eager Done.flatMap off, the suite
  fails widely — pins, constraint simplification, and CONDU.
- **imports**: none new.
- **obligations** (LAYERS 1+2 BUILT, Aug 15, eager-contract branches
  in both repos — with two corrections the suites forced):
  1. LAYER 1 — CORRECTED IN THE BUILD: the residual sites turned out
     NOT to be Done-guaranteed — the unifier's walks carry defer nodes
     (stack-safe recursion), so they are STRUCTURALLY non-Done and
     were always fresh-engine grounding by design. They take the
     SANCTIONED DOOR ground() (deliberate pure-fiber grounding), not
     getDone; getDone exists for seams that truly require
     completeness. The guard's first sweep censused the population:
     seven tracing-path sites through format, the trial's two walk
     groundings, the unifier's fold, both reify renames, one nested
     test utility.
  2. LAYER 2 — BUILT, and one hypothesis DISPROVED on the way: a
     per-step budget reset was implemented against a "depth leaks
     across chains" story and reverted — the eleven errors it was
     meant to fix were entirely defer-caused (identical counts before
     and after the reset), and resetting inside a deliberately nested
     engine UNDERCOUNTS real stack, the unsafe direction. The balanced
     counter alone is the design: push before the apply, pop in a
     finally — it always equals the eager applies open on the thread's
     stack, across engine nesting, which is exactly what bounds
     overflow. Done-ness preserved where the engine leans on it: the
     verification fold's stepless binding pass, the isDone guards,
     born-violated pricing.
  3. LAYER 2 companion — BUILT: the Fiber.get REENTRANCY GUARD; the
     contract's final surface is THREE DOORS — get (refuses non-Done
     inside a running engine), getDone(context) (requires
     completeness, names its site), ground() (sanctioned deliberate
     fresh-engine grounding, pure fibers only). Superseded-in-waiting
     by ambient-engine-delegation.md, where all three collapse into
     prioritized demand.
  4. The LAW, ratified only after 2+3, and SCOPED: eager-below-budget
     ≡ lazy for PURE fiber segments; Done-ness guaranteed below the
     budget. The scoping clause is mandatory because of the
     counterexample below.
- **links**: the fibers substrate (functional: Frame, the
  schedulers), nogood-store.md §3 (the sync gate that leans on
  Done-ness), trial laws test (asserts Done-ness — fired correctly
  during the experiment), the residual-get backlog item this makes
  concrete.

## The condu counterexample — RECORDED MECHANISM REFUTED (Aug 2026)

The original record: condu failed under the elimination experiment,
attributed to commit-flag timing (eager runs closures at CONSTRUCTION,
lazy at SCHEDULING; an impure read moving between the two changes
which answer committed-choice commits to). That mechanism is REFUTED
on the current tree, twice over:

1. EMPIRICAL: the budget-0 suite census (equivalent to the
   elimination — budget 0 builds a node on every apply) ran conda and
   condu GREEN, both census runs.
2. STRUCTURAL: every impure read in Conda/Condu (the commit flag, the
   won cell, the results list) sits in the continuation of an
   `Exhaustion.exhausted(...)` fiber — a seal-parker, structurally
   non-Done. Eager application only crosses DONE nodes, so it cannot
   reach those closures at construction under any budget: the reads
   run at seal time regardless. The parker is a BARRIER — the same
   role an explicit defer plays — and it is why condu is
   budget-invariant by construction.

The historic failure's real cause is INFERRED, not witnessed: the
experiment ran on the tree where walk/format/reify paths still called
the guarded Fiber.get — eliminating eagerness made those pure fibers
non-Done, the guard threw inside running engines, and condu's tests
took collateral damage alongside the pins and simplification tests.
Every one of those sites has since been restructured (the ground
conversions, then Trial.now), which is why the failure cannot be
reproduced. This note's era already produced one plausible-but-wrong
mechanism (the reverted budget-reset); this was the second.

The law's honest scope, restated: eager-below-budget ≡ lazy for any
fiber whose impure reads are behind non-Done barriers (parkers,
defers). The failure surface is impure reads reachable through
Done-rooted chains — none exist in the tree today (the budget-0
census is the receipt) — and new timing-sensitive code keeps the
invariant by placing its reads behind a barrier, the pattern condu
already exhibits.

## The observation dependence (the one that remains, Aug 2026)

Answer semantics are budget-invariant — the budget-0 suite census
after Trial.now proved it — but OBSERVATION is not, and the mechanism
is one fact: under eagerness, answers from Done-shaped branches
deliver at goal CONSTRUCTION time (Conde builds fork tasks by applying
branches to the continuation; a pure branch's whole delivery chain is
Done-rooted and runs while the task list is built). Two surfaces lean
on that:

1. The trace's Fail port is detection — "exploration drained with zero
   exits" — and its documented "exact for suspension-free goals" half
   was purchased by construction-time delivery: at budget 0 the fork
   completes before its children deliver and a spurious Fail lands
   inside a succeeding box ([Call, Fail, Exit, Redo, Exit], captured).
2. Answer ARRIVAL ORDER: construction-time delivery lands in clause
   order; scheduled delivery lands in rotation order.

TRIGGER: if the eager optimization is ever removed or defaulted off,
the trace's Fail detection must move from root-fiber control-drain to
the box workforce's SEAL (the emit doctrine's honest end-of-stream),
in an observation-only form — tracedCont deliberately plants no
delimiter, because batching deliveries would starve tabling's
incremental feedback.

## What died and what lived (the kill-check ledger)

DEAD: eager stack/budget execution at the SCHEDULER (the human's
original memory of "considerably faster") — the substrate it beat no
longer exists (in-place frames, zero plumbing allocation), and the
engine's steps are ~60× heavier than plumbing steps, so the ceiling
is ~2%. ALIVE: the same idea at CONSTRUCTION (the budgeted
Done.flatMap) — justified by stack-safety-with-preserved-Done-ness,
not by throughput. The scratch benches (ScratchEagerBench in
functional, ScratchStepRateTest in logic) are left in the trees,
uncommitted, for verification.
