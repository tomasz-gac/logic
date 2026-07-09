# logic

A relational (logic) programming engine for Java 8 — miniKanren with constraints,
tabling, and pluggable, fair search. Embeddable: your data stays Java objects, your
queries are Java expressions, and answers come back as a `java.util.stream.Stream`.

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

## Why this engine

The combination is the point — these rarely live in one system:

- **Relational core** — unification over Java values, including vavr tuples and
  collections structurally (`Tuple.of(x, 42)` unifies element-wise). Goals compose
  with `and`/`or`; relations run in any direction.
- **Constraint domains** — finite domains with bounds propagation to fixpoint
  (`dom`, `leq`, `addo`, `multo`, …), disequality (`separate`, `distincto`), and
  projection (suspend a goal until a term is ground). Domains compose: mix FD,
  disequality and plain unification in one query and the answers stay complete.
- **Tabling** — memoized relations. Left-recursive rules (`ancestor`, graph
  reachability, grammars) terminate instead of looping; Datalog-style queries work
  out of the box.
- **Fair, pluggable search** — breadth-first by default (complete: an answer at
  depth n is found even if another branch diverges), depth-first for Prolog-order
  traces, fork/join for `solveParallel`. Schedulers are drivers over one step
  interpreter; swapping them never changes the answer set, only the order.
- **Aggregation** — `findall`, `count`, `sum`, `max`, `min` reflect a sub-search
  into a value.
- **A real debugger** — a Prolog box-model tracer (`Call`/`Exit`/`Redo`/`Fail`)
  with arguments rendered against the live state, and spypoints.

## Building

`logic` depends on its sibling library [`functional`](../functional) (continuations,
fibers, schedulers). Both are Maven projects, Java 8, currently `-SNAPSHOT`:

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
constraint search and procedural generation.

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
- **Constraints** follow a capability design: the driver (`ckanren/Propagation`)
  speaks to stores through three triggers (`revise`, `changed`, `stated`), each
  answered by a `Revision` — a store can swap only its own factor, and the
  breaking actions (touching the substitution, another store's state, forgetting
  to re-park a constraint) are unrepresentable by type. New constraint domains
  implement one interface; parked-propagator scheduling is a reusable toolkit
  (`ckanren/propagator`).

The design record lives in `docs/design/` — start with
`constraint-kernel.md` (the current constraint engine),
`constraint-kernel.md` (how it got that way), and
`fixpoint-machine.md` (the shared mental model). `CLAUDE.md` carries the
working-on-this map: landmines, seams, backlog.

## Status

Java 8, no external runtime dependencies beyond vavr and the sibling
`functional` library. ~315 tests. Unreleased (`-SNAPSHOT`); APIs may still move.
