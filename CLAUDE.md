# Working on `logic`

A Java miniKanren logic-programming library. This file is auto-loaded every session.
Read it fully before changing code. When in doubt, prefer the smallest change and run the
tests.

---

## Golden rules (do these every time)

1. **TDD.** Write a failing test first, watch it fail, then make it pass. For a bug, first
   write a test that reproduces it.
2. **The test suite is your safety net.** `mvn test` must end in `BUILD SUCCESS`. Never
   commit or merge with a red suite. Count today: ~286 tests. If the count drops, you
   deleted or emptied a test — don't.
3. **Test output must be pristine.** No `System.out.println` in tests or in `src/main`.
   Assert results; don't print them. (One intentional exception is documented below.)
4. **Git discipline.** Work on a branch (`git checkout -b <name>`), commit with explicit
   paths (`git add <path> ...`), NEVER `git add -A` (it once swept a stray debug line into
   a commit). Merge to master with `git checkout master && git merge --ff-only <branch>`,
   then `git push`. End commit messages with the Co-Authored-By trailer.
5. **Java 8 only.** No `var` in main, no records, no switch-expressions. vavr tuples and
   functions max out at arity 8, accessors are 1-indexed (`._1`).
6. **`functional` is a separate repo and a Maven dependency.** It lives at
   `../functional`. If you change it, `cd ../functional && mvn -q install` before `logic`
   can see the change. Schedulers, fibers, `Cont`, `Nothing` all live there.

## STOP and ask the human before touching

These are subtle and easy to break silently:
- **the constraint core** (`ckanren/`, `finitedomain/`, `separate/`, `projection/`) — see
  the composition limitation below;
- **tabling** (`tabling/`) — parked-continuation scheduling, easy to deadlock;
- **the fiber/scheduler substrate** in `functional`.
Small, local, well-tested changes elsewhere don't need to ask.

---

## Architecture in one screen

- A **goal** is `Package -> Cont<Package, Nothing>` (CPS). Success = calling the
  continuation; failure = staying silent. `Goal` is the central interface (`goals/Goal.java`).
- A **`Package`** (`unification/Package.java`) is the solver state:
  `substitutions (HashMap<LVar,Term>)` + `constraints (LinkedHashMap<Class,Store>)`. It is
  **immutable/persistent** — this is why backtracking is free (each branch keeps its own).
- **`Term` / `Unifiable` / `Reified`** (`unification/`): `Term` is the structural root;
  `Unifiable` is solver input (`LVar`, `LVal`); `Reified` is solver output. `solve` takes
  `Unifiable` and yields `Reified`.
- **Schedulers** (in `functional`) drive the search: `BreadthFirstScheduler` (default, fair),
  `DepthFirstScheduler` (Prolog order, used by tracing), `RoundRobin`, `ForkJoin`,
  `UnfairBreadthFirst`. All are drivers over `FiberStep`; they differ only in which frame
  they step next.
- **Constraint stores** implement `ConstraintStore` (`ckanren/`): `FiniteDomainConstraints`,
  `NeqConstraints` (disequality), `ProjectionConstraints`. `StoreSupport.processPrefix`
  re-runs them after each unification.

Key **seams** (the places behaviour is hooked):
- `goals/NamedGoal` — the tracing hook. A named goal reports box-model ports when a tracer
  is seeded. Zero cost otherwise.
- `ckanren/StoreSupport.processPrefix` — where constraint stores are composed.
- `functional`'s `FiberStep` — the single step interpreter all schedulers share.

---

## Landmines (read before editing the relevant area)

- **Constraint composition is NOT a general solver.** `StoreSupport.processPrefix` runs the
  stores in one ordered pass, and each applies substitutions with `withSubstitutions` (which
  **replaces** the map). This is sound only for a *single* domain, or independent domains
  that don't add bindings. Combining disequality + finite-domain works only for the cases
  covered by `NeqFiniteDomainTest`; in general it is unsound (a store that binds a variable
  can be clobbered; mutual triggering isn't run to a fixpoint). **Do not** add a pass-through
  `ConstraintStore`, and **do not** rely on arbitrary domain composition. The real fix is a
  fixpoint/propagator engine — see `docs/design/constraint-propagation.md`. `FiniteDomainTest#shouldMixMultipleConstraintSystems`
  is deliberately a printing test (no assertion) because it exercises this broken area.
- **`Package.withSubstitutions` REPLACES the substitution map; `Package.extendS` MERGES.**
  In the constraint/propagation path you almost always want monotonic extend, not replace.
- **Tracing runs depth-first.** `solve(out, tracer)` uses `DepthFirstScheduler` so the trace
  reads in Prolog order. The default `solve(out)` is fair breadth-first. Don't "fix" a trace
  by changing the default scheduler.
- **Sibling-disjunct `Call` ports fire together up front.** `Conde` materialises all fork
  options eagerly (apply-time, not step-time), so both branches announce `Call` before either
  body runs. This is cosmetic and not a scheduling bug — don't chase it.
- **Don't reintroduce debug prints.** The old `Goal.debug` eager-println was removed. To
  debug a logic program, use the tracer (below), not `System.out`.

---

## Debugging a logic program

```java
goal.trace(out)                          // full indented Prolog-order box-model trace
goal.solve(out, Trace.printing())        // same, explicit
goal.solve(out, Trace.spy("appendo"))    // only boxes whose label contains "appendo"
goal.solve(out, Trace.hiding("recursive call"))
```
Ports are Call / Exit / Redo / Fail. Labels are rendered against the live state, so
arguments show their current (deep-walked) values. See `debug/Trace.java`, `debug/DebugStore.java`.

---

## Backlog (what's left, with risk)

- **Cut / `once` / `ifte`** — a real cut, subsuming `conda`/`condu`. Feature work, medium
  risk. (Only `conde`/`conda`/`condu` exist today.)
- **Public-API docs** — e.g. `FiniteDomain` has 13 public methods and no javadoc. Low risk.
- **Dependency modernization** — assertj 3.4.1 and junit 4 are old; `logic` and `functional`
  are `-SNAPSHOT` and never released. Low-medium risk; do it as its own change.
- **`functional` release-prep** — de-SNAPSHOT, bump vavr (0.10.0), doc the public surface.
  Open decision for the human: promote `fibers` to its own Maven module (it's cleanly
  isolated — depends only on `category`) vs. leave it.
- **Constraint-propagation redesign** — HIGH risk, touches the core. Follow
  `docs/design/constraint-propagation.md`; do the Phase 0 "fail loud on multi-domain" guard
  first. Don't start without the human's go-ahead.
- **Semiring-weighted inference** — turn the engine into a weighted-inference machine
  (counting, probability, shortest-path, MAP, provenance, learning) via one small `Semiring`
  abstraction. Follow `docs/design/semiring-inference.md`; Phase 1 (refactor `aggregate`
  onto `Semiring`) is low-risk and the on-ramp. Later phases are research; don't start those
  without the human's go-ahead.

## Where knowledge lives

- `docs/design/constraint-propagation.md` — the propagator/fixpoint design, phased.
- `docs/design/semiring-inference.md` — weighted/probabilistic inference via semirings, phased.
- This file — architecture, landmines, workflow.
- Commit history is descriptive; read it when a change looks surprising.
