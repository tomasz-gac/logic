# logic

A relational (logic) programming engine for Java 8 — miniKanren with constraints,
tabling, semiring-weighted inference, self-planning queries, and pluggable, fair
search. Embeddable: your data stays Java objects, your queries are Java
expressions, and answers come back as a `java.util.stream.Stream`.

```java
Unifiable<LList<Integer>> xs = lvar(), ys = lvar(), zs = lvar();

// appendo is a RELATION, not a function — run it backwards:
// "which xs and ys concatenate to [1..6]?"
Logic.appendo(xs, ys, zs)
        .and(zs.unifies(LList.ofAll(1, 2, 3, 4, 5, 6)))
        .solve(lval(Tuple.of(xs, ys)))
        .forEach(System.out::println);
// ((), (1,2,3,4,5,6)), ((1), (2,3,4,5,6)), ... all seven splits
```

This is a research/learning project (see [Status](#status)) — but a serious one:
the engine's guarantees are theorems of two algebras, and the test suite checks
the algebra's laws alongside the code.

## Why this engine

The combination is the point — these rarely live in one system:

- **Relational core** — unification over Java values, including vavr tuples and
  collections structurally (`Tuple.of(x, 42)` unifies element-wise). Goals compose
  with `and`/`or`; relations run in any direction.
- **Constraint domains** — finite domains with bounds propagation to fixpoint
  (`dom`, `leq`, `addo`, `multo`, …), disequality (`separate`, `distincto`), and
  projection (suspend a goal until a term is ground). Domains compose: mix FD,
  disequality and plain unification in one query and the answers stay complete.
- **Tabling with full completion** — memoized relations. Left-recursive and
  mutually recursive rules terminate; the engine detects, per call, the moment
  no further answer can arrive (full SLG-style completion, including
  mutual-recursion rings), and completed calls become reusable data: a finished
  general call answers its instances without recomputation.
- **Weighted inference** — attach a weight to any branch (`factor`) and the
  same program answers quantitative questions: how many solutions (counting),
  how likely (probability), cheapest path (min-plus), best derivation
  (Viterbi), and *which facts, combined how* (provenance — the answer's
  lineage as a regular expression). Rings compose in one product store, so one
  pass computes several at once. Two tabled strategies, chosen by the ring's
  TYPE: bounded semirings stream through the fixpoint (`solveBounded`); closed
  semirings defer to the seal, where **recursion is solved, not run** —
  a cyclic relation is read off as a linear equation system and its
  infinitely many derivations are summed in closed form by Kleene star
  (`solveClosed`). "Probability a retry loop ever succeeds" is one query,
  answered exactly — the geometric series, not a truncated simulation.
- **A self-planning optimizer** — every priceable goal declares the maximum
  number of answers it can emit; conjunctions sort cheapest-first around
  barriers, dead branches price to zero before they spawn, and a tabled call's
  price drops from unknown to exact the moment it completes. Clause order in
  the source stops mattering: the naive program is the fast program.
- **Fair, pluggable search** — breadth-first by default (complete: an answer at
  depth n is found even if another branch diverges), depth-first for Prolog-order
  traces, fork/join for `solveParallel`. Schedulers are drivers over one step
  interpreter; swapping them never changes the answer set, only the order.
- **Aggregation** — `findall`, `count`, `sum`, `max`, `min` reflect a sub-search
  into a value, folding through law-checked monoid witnesses.
- **A real debugger** — a Prolog box-model tracer (`Call`/`Exit`/`Redo`/`Fail`)
  with arguments rendered against the live state, and spypoints.

## The algebra is load-bearing

The engine's core claims are algebraic, and the code enforces them mechanically:

- Knowledge carriers (FD domains, constraint records, tabled answer sets) are
  declared **lattice instances**; goal pricing runs through a law-checked
  **semiring**; aggregation folds through **monoid witnesses**. The sibling
  `functional` library ships the interfaces and the law kits.
- A coverage gate fails the build if any algebraic implementor lacks a law
  test — claims are audited, not aspirational. Writing these laws found real
  bugs in mature code (an `X∩∅=X` in interval intersection among them).
- Optional semiring capabilities are **types**, not flags: `IdempotentSemiring`
  (the dedup license), `ClosedSemiring` (Kleene star — "no closure" is
  unrepresentable), `BoundedSemiring` (`a⊕1=1`, hence `a*=1` — the exact
  threshold at which streaming through a cycle terminates, and the type the
  streaming path demands), `SuperiorSemiring` (best-first commitment). Call
  sites that need a capability demand it in their signature — which is how
  the engine picks stream-vs-star per solve, visibly, at the call site.
- The weighted witnesses are law-checked like everything else — including
  `Provenance`, the free closed semiring (regular expressions), whose star
  laws hold up to *language* equivalence; the law kit takes the equivalence
  as a parameter rather than pretending structural equality.
- Tabling's completion detection is Dijkstra–Scholten termination detection
  built from the same discipline: monotone counters, an upward-closed seal flag
  readable without locks, and a generic `Region` primitive whose group-seal
  rule is one lattice fixpoint. `docs/design/table-completion.md` and
  `docs/design/group-seal.md` tell that story end to end.

## Building

`logic` depends on its sibling library [`functional`](../functional)
(continuations, fibers, schedulers, the algebra and its law kits). Both are
Maven projects, Java 8, currently `-SNAPSHOT`:

```bash
cd ../functional && mvn install
cd ../logic      && mvn install
```

```xml
<dependency>
    <groupId>com.tgac</groupId>
    <artifactId>logic</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## A tour

### Relations and unification

```java
import static com.tgac.logic.unification.LVar.lvar;
import static com.tgac.logic.unification.LVal.lval;

Unifiable<String> who = lvar();
who.unifies("world")
        .solve(who)                    // Stream<Reified<String>>
        .forEach(System.out::println); // {world}
```

A `Goal` is a value; build them with `and`, `or`, `Goal.defer` (for recursion),
`Logic.exist` (fresh variables), and the pattern-matching sugar in `Matche`.

### Disequality

```java
Unifiable<Integer> x = lvar();
Logic.membero(x, lval(LList.ofAll(1, 2, 3)))
        .and(Disequality.separate(x, lval(2)))
        .solve(x);                     // 1, 3
```

`separate` records exactly the bindings that must never all hold, verifies the
record on every subsequent unification, and shows surviving records in reified
answers.

### Finite domains

```java
Unifiable<Long> a = lvar(), b = lvar(), sum = lvar();

FiniteDomain.dom(a, EnumeratedDomain.range(0L, 10L))       // a ∈ {0..9}
        .and(FiniteDomain.dom(b, EnumeratedDomain.range(0L, 10L)))
        .and(FiniteDomain.addo(a, b, sum))                 // a + b = sum
        .and(sum.unifies(10L))
        .and(FiniteDomain.lss(a, b))                       // a < b
        .solve(lval(Tuple.of(a, b)));
// (1,9), (2,8), (3,7), (4,6)
```

Constraints propagate as bounds narrow — `x≤y≤z` chains prune before labelling,
not during generate-and-test. Domains and disequality cooperate through the
substitution: `x ∈ {4,5} ∧ x ≠ 5` yields exactly `4`.

### Tabling

```java
Tabled<Tuple2<Unifiable<String>, Unifiable<String>>> ancestor =
        Tabling.define(args -> args.apply((x, y) ->
                parent(x, y)
                        .or(defer(() -> {
                            Unifiable<String> z = lvar();
                            return parent(x, z).and(ancestor.apply(Tuple.of(z, y)));
                        }))));

x.unifies("alice").and(ancestor.apply(Tuple.of(x, y)))
        .solve(y);                     // bob, charlie, david — and it TERMINATES
```

Each distinct call pattern gets its own answer table; the engine detects
per-call completion mid-solve (mutual recursion included), after which the
call prices exactly, reorders freely, and serves more-specific calls from its
cache — `ancestor("alice", Y)` completed means `ancestor("alice", "david")`
is a lookup, not a search.

### Weighted answers

```java
// two routes A→D; ask for count and cost IN ONE PASS
Goal viaB = mid.unifies("B").and(factor(MIN_PLUS, 1L)).and(factor(MIN_PLUS, 5L));
Goal viaC = mid.unifies("C").and(factor(MIN_PLUS, 2L)).and(factor(MIN_PLUS, 2L));

SemiringStore total = Weights.solve(viaB.or(viaC),
        SemiringStore.product(COUNTING, MIN_PLUS), BreadthFirstScheduler::new);
total.get(COUNTING);   // 2 routes
total.get(MIN_PLUS);   // 4 — the cheaper one
```

`factor` multiplies a weight along a derivation; `⊕` combines rival
derivations; the ring decides what those mean. Through a *tabled* recursion the
ring's type picks the strategy: a `BoundedSemiring` streams
(`solveBounded` — min-plus shortest paths over cyclic graphs), a
`ClosedSemiring` waits for completion and solves the recursion as equations
(`solveClosed`):

```java
// retry loop: succeed now (1/6) or pay a step (5/6) and loop.
// P(ever succeeds)? The star sums the geometric series: exactly 1.0.
// PROB is user-defined — probability (+,×) with star a* = 1/(1−a); it is
// kept out of Semirings deliberately (⊕-as-probability is only sound over
// DISJOINT derivations, and that is the user's claim to make)
Tabled<Tuple1<Unifiable<Integer>>> ever = Tabling.defineRecursive(self -> t ->
        t.apply(x -> x.unifies(1).and(factor(PROB, 1.0 / 6))
                .or(x.unifies(1).and(factor(PROB, 5.0 / 6))
                        .and(defer(() -> self.apply(t))))));

Weights.solveClosed(ever.apply(Tuple.of(lval(1))), out,
        SemiringStore.closedProduct(PROB), BreadthFirstScheduler::new);
// one answer, weight 1.0 — infinitely many derivations, summed in closed form
```

Under `PROVENANCE` the same query returns the *shape* of all those derivations
as a finite regular expression (`step*·base`) — an executable audit trail for a
recursive answer. Mutual recursion works (the coupled calls are solved as one
matrix); nonlinear recursion (two recursive calls in one clause) is refused
loudly — star closes linear systems only.

### The optimizer

```java
// same answers regardless of clause order — the pass sorts cheapest-first
goal.solve(out, new OrderingOptimizer());
```

The optimizer rides the solver state: freshly unfolded recursion layers are
re-planned against live bindings, a `dom`-post over an already-disjoint domain
prices to zero (killing its branch before it spawns), and completed tabled
calls price at their exact answer count.

### Aggregation and projection

```java
Aggregate.count(Logic.membero(x, lval(LList.ofAll(1, 2, 3))), n);   // n = 3

// suspend until x is ground, then compute with the actual value
ProjectionConstraints.project(x, v -> y.unifies(v * 2));
```

### Debugging

```java
goal.trace(out);                          // full indented Prolog-order trace
goal.solve(out, Trace.spy("appendo"));    // only boxes whose label matches
```

```
Call: (1,2,3) ++ <_.1> ≣ (1,2,3,4,5,6)
 Call: (2,3) ++ <_.1> ≣ (2,3,4,5,6)
  ...
 Exit: (2,3) ++ (4,5,6) ≣ (2,3,4,5,6)
Exit: (1,2,3) ++ (4,5,6) ≣ (1,2,3,4,5,6)
```

## What it's good at

Embedded logic inside JVM systems: test-data generation (write the invariant as a
relation, run it backwards, enumerate fairly), configurators and rule engines
(valid-combination problems with recursive rules), deductive/Datalog-style queries
over in-memory data, type checkers and program analyses for DSLs, puzzle-class
constraint search and procedural generation. The weighted layer adds the
algebraic-path-problem family over the same programs — shortest/most-reliable/
bottleneck routes, route counting, Markov absorption probabilities, lineage
audits of recursive answers — one relation text, many rings.

Scale honestly: bounds-consistency FD over tens-to-hundreds of variables, search
spaces that fit propagation-then-label — decision support, not an industrial CP
solver (no global constraints yet; the extension point below is where they'd go).

## Architecture, briefly

- A **goal** is `Package -> Cont<Package, Nothing>` (CPS). Success calls the
  continuation; failure stays silent.
- A **`Package`** is the immutable solver state: substitutions + constraint
  stores. Backtracking is free — each branch keeps its own.
- **Search** is a set of scheduler drivers over one step interpreter (in
  `functional`); breadth-first is the default, and tracing uses depth-first so
  traces read in Prolog order.
- **Constraints** follow a capability design: the driver (`constraints/Propagation`)
  speaks to stores through two triggers (`revise`, `stated`), each answered by a
  `Fiber<Revision>` — a store can swap only its own factor, and the breaking
  actions (touching the substitution, another store's state, forgetting to
  re-park a constraint) are unrepresentable by type. New constraint domains
  implement one interface; the propagator toolkit is `finitedomain`'s private
  machinery.
- **Tabling** is built on three generic, logic-free primitives
  (`tabling/primitives`): a `JoinMap` (answers as a join-semilattice value),
  a `MonotoneCell` (grow-and-wake with parked subscribers), and a `WorkLedger`
  (who still works for a call, running or asleep) — composed into a `Region`
  whose seal rule is the completion theorem. A tabled body runs as an
  **anonymous master** (detached work billed to its own call) and every
  caller reads through a consumer, so entries seal in dependency order.
  The concurrency contract — the lock graph and the three invariants that
  keep completion sound under the fork/join scheduler — is written down in
  `table-completion.md` §6a and guarded by a dedicated parallel stress test.
- **Weighted tabling** is a strategy seam (`TablingMode`) on that skeleton:
  `Streaming` folds values through the fixpoint; the weight package's
  `Closed` mode explores structure only, records each derivation as a base
  or an edge of a first-class equation graph, and at each seal runs
  Floyd–Warshall/Kleene over the sealed closure and replays the reader
  chains with the solved values.

The design record lives in `docs/design/` — start with `lattice.md` (the
engine's one algebra and its quotient tower), `constraint-kernel.md` (the
constraint engine as shipped), `table-completion.md` (tabling's completion
machinery), `star-tabling.md` (closed-semiring tabling: why streaming
diverges, how star closes it), `method.md` (how the design process itself
works), and `vision.md` (the north star and roadmap). `CLAUDE.md` carries
the working-on-this map: landmines, seams, backlog.

## Status

A research/learning project, built with viability as a constraint rather than a
goal: the designs are the kind that could be real (honest concurrency, measured
claims, no toy shortcuts), but there is no release, no client base, and APIs
move freely. Java 8, no runtime dependencies beyond vavr and the sibling
`functional` library. ~430 tests, including law suites for every declared
algebraic instance and a parallel stress test on tabling's completion
machinery. If you're reading this as a source of ideas rather than a
dependency, `docs/design/` is the interesting part.
