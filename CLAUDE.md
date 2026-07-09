# Working on `logic`

A Java miniKanren logic-programming library. This file is auto-loaded every session.
Read it fully before changing code. When in doubt, prefer the smallest change and run the
tests.

---

## Golden rules (do these every time)

1. **TDD.** Write a failing test first, watch it fail, then make it pass. For a bug, first
   write a test that reproduces it.
2. **The test suite is your safety net.** `mvn test` must end in `BUILD SUCCESS`. Never
   commit or merge with a red suite. Count today: ~315 tests. If the count drops, you
   deleted or emptied a test — don't.
3. **Test output must be pristine.** No `System.out.println` in tests or in `src/main`.
   Assert results; don't print them. (One intentional exception is documented below.)
4. **Git discipline.** Work on a branch (`git checkout -b <name>`), commit with explicit
   paths (`git add <path> ...`), NEVER `git add -A` (it once swept a stray debug line into
   a commit). Merge to master with `git checkout master && git merge --ff-only <branch>`,
   then `git push`. End commit messages with the Co-Authored-By trailer.
5. **Java 8 only.** No `var` in main, no records, no switch-expressions. vavr tuples and
   functions max out at arity 8, accessors are 1-indexed (`._1`).
   **Always import at the top level — NEVER inline-qualify** (`io.vavr.Function2 f`
   is wrong; import it). The only excuse is a genuine simple-name clash in the same
   file (e.g. `java.util.stream.Stream` vs vavr's `Stream`).
6. **`functional` is a separate repo and a Maven dependency.** It lives at
   `../functional`. If you change it, `cd ../functional && mvn -q install` before `logic`
   can see the change. Schedulers, fibers, `Cont`, `Nothing` all live there.

## STOP and ask the human before touching

These are subtle and easy to break silently:
- **the constraint core** (`constraints/`, `finitedomain/`, `separate/`, `projection/`) — see
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
- **Constraint stores** implement `ConstraintStore` (`constraints/store/`):
  `FiniteDomainConstraints`, `NeqConstraints` (disequality), `ProjectionConstraints`.
  The driver (`constraints/Propagation`) speaks to them through two triggers — `revise`
  (bindings arrived; the store's COMPLETE reaction: custody, own watchers, own
  cascade) and `stated` (your item was stated) — each answered by a
  `Fiber<Revision>` (own factor + consequences; fiber so long cascades stay fairly
  stepped — see `functional`'s `Worklist`). How a store
  computes it is its own business: FD administers its own propagators (now
  `finitedomain`-private: Propagator/Verdict/Update), Projection parks bare
  (term, body) suspensions, Neq re-verifies its records wholesale;
  `constraints/store/Watches` is the shared chain matcher.

Key **seams** (the places behaviour is hooked):
- `goals/NamedGoal` — the tracing hook. A named goal reports box-model ports when a tracer
  is seeded. Zero cost otherwise.
- `constraints/Propagation` — the chokepoint (`resolve`/`activate`), the agenda
  drain, and the revision router: where constraint stores are composed.
- `functional`'s `FiberStep` — the single step interpreter all schedulers share.

---

## Landmines (read before editing the relevant area)

- **Constraint composition works, but only through the chokepoint.**
  `Propagation.resolve(Prefix)` is the ONLY way substitutions may grow in constraint-aware
  code (its javadoc is the caller contract — read it). All bindings route through it (user
  unify, FD collapse, labelling); a `Prefix` is mintable only by the unifier
  (`MiniKanren.unifyPrefix`) or the checked `Prefix.binding`. Stores answer triggers with
  `Revision`s and may swap only their OWN factor; cross-store consequences ride the
  revision payloads (inferred prefixes, narrowed terms, runs). Propagators are
  store-internal (`finitedomain`'s private toolkit) — the framework owns parking, so
  `keep` is default-safe and evaporation is unrepresentable. The equal-domain check in
  `finitedomain/DomainUpdate` is the termination guard of wake-on-narrowing — do not
  remove it. Raw `MiniKanren.unify` is legitimate only inside the unifier and for
  Disequality's trial unification. Use a plain `Store` for transport (Table pattern).
  Constraint bodies wake on narrowing too — they must tolerate any mix of wide/ground
  args (see the mulIntervals sign-guard lesson).
  Details: `docs/design/constraint-kernel.md`.
- **`Package.withSubstitutions` REPLACES the substitution map.** In constraint-aware code
  never touch it directly — obtain a `Prefix` and `resolve` it.
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
- **Constraint-propagation redesign** — DONE, all of it (July 2026): Phases 1–2,
  the capability API (Steps 1–3.5), and the uniform store boundary
  (`constraint-kernel.md` records the shape). The Neq→FD bridge was DROPPED (optimization-
  only). Nothing remains on this line.
- **Semiring-weighted inference** — turn the engine into a weighted-inference machine
  (counting, probability, shortest-path, MAP, provenance, learning) via one small `Semiring`
  abstraction. Follow `docs/design/semiring-inference.md`; Phase 1 (refactor `aggregate`
  onto `Semiring`) is low-risk and the on-ramp. Later phases are research; don't start those
  without the human's go-ahead.

## Where knowledge lives

- `docs/design/constraint-kernel.md` — AUTHORITATIVE: the constraint kernel as
  shipped (package product, Propagation's three entries, the store protocol and
  2×2 vocabulary, FD's toolkit, structure's one owner, the contracts, the
  lineage). Read before touching the constraint core.
- `docs/design/semiring-inference.md` — weighted/probabilistic inference via semirings, phased.
- `docs/design/fixpoint-machine.md` — the shared fixpoint mental model tying the two above
  together, AND why NOT to merge them into one engine prematurely.
- `docs/design/virtual-threads-engine.md` — a Java 21 direct-style-on-virtual-threads engine
  (native debugging, simpler tabling, natural cut) as a separate experimental module; the
  completeness/fairness trap is the go/no-go gate. Not a change to the Java-8 engine.
- `docs/design/tabled-constraints.md` — DESIGN SKETCH: merging tabling and constraints
  (TCLP): three intra-domain store hooks (project/entails/restate), pointwise product-order
  entailment, the antichain termination gate. Read before weakening the tabling guard tests.
- `docs/design/substitutions-migration.md` — PLANNED: retype the unifier onto the
  Substitutions interface (completes capability §2.1), one kind-tagged decompose
  shared by unify and members, representation swaps gated on benchmarks. Read
  before touching MiniKanren internals.
- This file — architecture, landmines, workflow.
- Commit history is descriptive; read it when a change looks surprising.
