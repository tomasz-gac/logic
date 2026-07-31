# The assembler — closed goal semirings, or weights that are programs

**STATUS: DESIGN, SHELVED (July 2026, derived with the human over one long
conversation). Nothing is built, and nothing is scheduled: the human wants
grounded practice with ordinary weighted inference before working at this
level — this document is the record for when that day comes. All three phases
are now fully designed; the (actuals, template) value constructor (§4, the
human's) closed what had been Phase C's open mechanism question. §9 is the
worked example to grow toward. Companions: `star-tabling.md`
(the closed-semiring machinery this rides — read it first), `lattice.md` §5a and
its shelved side-note (goals as a semiring; tabled as its star — this doc is
that note developed for the closed case), `semiring-inference.md` (the weighted
frame), `goals-as-data.md` (the Program front door this deliberately does NOT
require).**

---

## 1. The idea

Every ring we have evaluates derivations to a VALUE — a count, a cost, a
probability, a regex. The goal ring evaluates them to a PROGRAM: the weights are
goals, ⊗ conjoins them along a derivation, ⊕ disjoins rival derivations, and
star closes loops. `solveClosed` under this ring is an ASSEMBLER: the
meta-program's search decides which fragments to lay down (unification and
constraints prune candidate programs the way they prune candidate values), and
each answer comes back paired with a runnable `Goal` that re-derives it.

What that buys, compactly: search-directed synthesis (query for a program the
way you query for a value); residual programs specialized to everything the
assembly-time search resolved (the staging/Futamura direction — the engine
partially evaluating programs using itself as the evaluator); proof-carrying
answers (the weight is an executable certificate — and star means an answer
with infinitely many derivations ships a FINITE certificate of all of them);
and closure: this is the only ring whose outputs re-enter the engine — emitted
programs can be run, composed, tabled, or fed to another assembly pass.

One structural gift falls out of the machinery rather than being designed:
WHERE THE EMITTED PROGRAM LOOPS. A recursive assembler would emit an infinite
family of unrollings; under the goal ring the seal's star emits one finite
recursive program instead — and the loop is placed exactly where tabling's
variant check caught the repetition. Partial evaluators need hand-tuned
generalization heuristics for precisely that decision; here the memo key's
equality IS the criterion.

## 2. The goal semiring

```
⊕ = or        ⊗ = and        0 = fail        1 = succeed
```

Laws hold up to ANSWER-SET EQUIVALENCE, not structural equality — the same
regime as Provenance's laws holding up to language equivalence; the
Eq-parameterized law kits exist for exactly this. Distributivity is the
optimizer's factoring identity; the annihilator/identity laws are the algebra
pass's axioms (`0⊕x=x`, `0⊗x=0`, `1⊗x=x`).

**Guarded constructors, from day one.** The witness's `plus`/`times`
short-circuit on 0 and 1 by object identity — the semiring axioms applied as
constructor normalization (Provenance's smart constructors are the precedent).
This is not an optimization: the dense star sweep evaluates identity and
annihilator applications by the thousands (§6), and an emitted program
assembled through unguarded ops would be full of `fail.or(…)` junk. The
residual program is a deliverable; it comes out normalized or it is illegible.

**The stationary-star theorem (the human's, and it reorganizes the design).**
For a goal `g`, repeated assertion over the SAME scope is absorbed:
`g ∧ g ≡ g` in answer sets, because a goal's effect on a package is a monotone
knowledge join and the substitution is a join-semilattice — re-adding absorbed
knowledge is absorbed. So the aliased series collapses in the idempotent
quotient:

```
1 ⊕ g ⊕ g⊗g ⊕ g⊗g⊗g ⊕ …   ≡   1 ⊕ g        (answer sets: "maybe g", never fails)
```

The powers become distinguishable only ABOVE the idempotent rung of the tower —
under counting, probability, provenance — where `g^n` carries `w^n`, and the
series per answer sums to `w*`, the SCALAR star of the fragment's weight.

Consequence — the division of labor that makes aliasing correct rather than a
bug:

| | who does it | aliasing status |
|---|---|---|
| state iteration (new bindings per level) | the answer-indexed equation SYSTEM — explore finds keys, edges wire them | needs plumbing (§5) |
| weight accumulation (loop in place) | the SCALAR star | aliasing is exactly right |

A self-loop in the system is `x_i = b_i ⊕ a⊗x_i`: the answer does not change
around the loop; only weight accumulates. Scalar star never chains state —
that was always the matrix's job — so the aliased ⊗-powers (same LVar objects
in every factor) are precisely the right semantics for it: same knowledge,
absorbed; compounded weight.

**Tower placement.** The goal ring sits beside provenance as a
structure-preserving ring: provenance is the free closed semiring of derivation
SHAPES; the goal ring is derivation shapes made EXECUTABLE. Running an emitted
program under any other ring is a homomorphic evaluation of it — which is what
makes staging (§1) compositional rather than ad hoc.

## 3. The star construction

```
s(g)    :- g | g ⊗ s(g)          -- the tabled transitive part, g⁺
star(g) :- 1 | tabled(s(g))      -- glue on the empty derivation
```

Star is a DERIVED combinator: `s` is a fresh tabled relation minted per star
(relation identity keys the cache — two `star` sites share a table iff they
share the operand), and the `1 |` supplies the empty derivation tabling itself
cannot produce.

Why `tabled` is load-bearing in the EMITTED form, given §2's theorem: not for
per-iteration freshness (a stationary loop needs none) but for TERMINATION and
RE-CLOSABILITY of the residual program. Untabled, `s(g) :- g | g ∧ s(g)`
recurses forever on a stationary key. Tabled, re-execution under plain solve
seals immediately (`≡ 1 ⊕ g`, the correct idempotent-quotient reading), and
re-execution under `solveClosed` re-records the self-edge coefficient and
re-stars it — **the residual program round-trips: its closed re-solve recovers
`w*`.** That round-trip is the correctness statement for staged star, and it
needs nothing beyond the machinery already shipped in `star-tabling.md`.

The nonlinearity guard applies to the ASSEMBLER (the meta-program must be
linear-recursive — one recursive call per derivation, refused loudly
otherwise); the EMITTED programs face no such limit.

## 4. Arguments: the manifest

Tabling needs three things from an operand: a KEY (the reified argument tuple —
the variant check), an ANSWER SHAPE (what to reify and cache), and a CANONICAL
ORDER for both. `Tabled<T>` gets all three by declaration. An opaque
`Package -> Cont` closure provides none — its free variables live in lambda
environments the engine cannot see.

**Inference is dead, not merely awkward.** Bottom-up free-variable propagation
dies at `defer`, twice over: (1) the thunk mints fresh lvars per forcing, so
the manifest read from an inspection-force names DIFFERENT variables than the
execution-force — the manifest lies about the goal that runs (and memoizing
the force changes `defer`'s semantics to fix an inspection problem); (2) the
manifest of a recursion is defined in terms of itself — forcing one layer of
`s(g)` meets a recursive occurrence behind another `defer`, and there is no
finite fixpoint because each forcing grows the variable universe. This is the
same infinite unfolding tabling exists to cut, appearing at inspection time.
Precedent agrees: Prolog (`:- table p/2`) and Scheme miniKanren
(`(tabled (x y) …)`) both declare; nobody infers, for this reason.

**Resolution: the manifest lives in the RING'S VALUE TYPE — nowhere else.**
The decisive observations, in the order they arrived:

- The ring witness's signature is `star(V) -> V`, called by `StarSolve` at seal
  time with no user in the loop. Args cannot arrive out of band; the value must
  be self-describing.
- Fragments enter the ring WHOLE and are never disassembled. Composition
  happens only through the ring ops (`factor` ⊗s via `times`, the graph maps
  fold via `plus`, `StarSolve` uses all three) — never through raw
  `Goal.and`/`or`.

**The value constructor (the human's final form):**

```
value(Tuple.of(x, y), (x, y) -> goal(x, y))
```

— the ACTUALS (live terms of the assembling scope) plus a TEMPLATE (a function
mentioning only its parameters). The split is load-bearing: the actuals are
TERMS, so the engine's existing reify/instantiate pipeline can translate the
weight's entire variable content across ownership boundaries (§5); the template
can be re-applied to fresh variables at any later time — the fragment carries
its own re-instantiation capability. A fragment value IS a call: arguments plus
body-maker.

`plus`/`times` compose call-values WITHOUT freshening (within a derivation,
shared actuals are the wiring — wanted); the manifest is the union of the
actuals tuples (union is ACI, so manifests obey the same laws the goals do);
`star` reads the unioned manifest and mints `s` from it. `Goal` learns
nothing. `defer` learns nothing. `Tabled` stays the declaration point for
ordinary tabling. The annotation burden is one tuple, said once, at the moment
a fragment becomes a value — and the ring's type makes forgetting it
unrepresentable. Arity is an overload family (1..8, vavr-style) so tuple and
function agree by type. HYGIENE CAVEAT: Java cannot stop the template lambda
from capturing an outer lvar (`(a,b) -> goal(a, x_outer)` compiles), which
silently reintroduces the alpha seam — "a template mentions only its
parameters" is documented contract, not checked type.

**Manifests may safely over-approximate.** Extra components in the key tuple
mean finer variant discrimination — less table sharing, never wrong answers
(consume's unification filter is indifferent). Declarations need not be
minimal to be sound; annotate generously and move on.

## 5. Binding semantics — the closure ABI and its two seams

Fragments capture LIVE LVar objects from the assembling scope. Consequences,
benign to sharp:

**The ABI is object identity, and that is a feature.** The manifest is the
formal parameter list; the emitted program is a closure over LVar objects.
Bindings live in Packages, not in variables, so the same emitted program
re-runs against fresh packages with different bindings of its formals — no
codegen, no substitution machinery. "The output can only be solved in relation
to `x`" means: `x` is its parameter.

**Intra-derivation plumbing is free.** Consecutive fragments in one derivation
share intermediates because the assembler's search minted them shared — the
fresh `z` wiring step 1 to step 2 is the same object in both fragments because
the search threaded it. The derivation does the wiring; ⊗-as-`and` is correct
BECAUSE the actuals were live at capture time.

**Aliasing is resolved by §2's theorem.** `a ⊗ a` over shared objects is one
step asserted twice, not two steps — and that is exactly right, because scalar
⊗-powers only ever arise in stationary loops, where the semantics is "same
knowledge, compounded weight." State-chaining lives in the system, not the
scalar ops.

**The two real seams — both at boundaries the engine already recognizes:**

1. **The cell boundary (alpha).** Answer TERMS are reified into the cell;
   answer WEIGHTS are not. All of one entry's derivations speak the master
   body's variable objects (uniform — the manifest of the cell IS the master's
   formals), but a consumer from another branch instantiates fresh variables
   for the answer term while the weight-program still speaks master-branch
   objects: disconnected from the consumer's world, and at risk of accidental
   capture if those objects are bound in the consumer's package lineage. Closed
   values (numbers, regexes) have no such seam; open formulas do, and you
   cannot substitute inside an opaque closure to fix it.
2. **The cross-entry glue.** The coefficient `A[i←j]` records `i`'s fragment
   but NOT the consume-site unification that wired `i`'s actuals to `j`'s
   answer — that glue lives in the substitution, invisible to the store.
   Inlining `A[i←j] ⊗ x_j` at solve time concatenates formulas over
   unconnected variable sets.

**The resolution — EMIT CALLS, NOT SPLICES, and §4's constructor makes it the
uniform representation rather than a bolt-on.** Since every fragment value is
already call-shaped (actuals + template), the emitted program is calls all the
way down: one emitted RELATION per entry, formals = the manifest, cross-entry
references as CALLS carrying actuals. Both seams close through machinery that
already exists:

- Seam 1: the weight's variables all sit in term-position (the actuals), so the
  cell boundary reifies the answer term and the actuals tuples JOINTLY (one
  canonicalization — reify is deterministic over one substitution) and
  instantiates them jointly at consumption — the weight rides the same rename
  pipeline the answer always rode.
- Seam 2: the missing glue is exactly the CONSUME-SITE ACTUALS, which the mode
  holds in hand (`argsTerm`) at consume time. Record them as one field on the
  `Edge` (ring-generic data; numeric rings ignore it), and the star's
  `A[i←j] ⊗ x_j` becomes `fragments ⊗ call(j-relation, those actuals)` — a
  well-formed call value like every other.

The cost, honestly: the emitted value is a program WITH STRUCTURE (relations
referencing relations, plus an entry point), not a flat formula — the value
type must carry that.

## 6. Zeros and sparsity

The star system is born sparse (`DependencyGraph.coefficients` is literally an
edge map) and the solve densifies it: `solveGroup` zero-fills `b` and the
`n×n` matrix, and `StarSolve`'s triple loop then evaluates
`plus(x, times(times(zero,…),…))` for every absent edge — O(n³) ring
operations, overwhelmingly identity/annihilator applications. Distinguish:
zeros in `b` are SEMANTICS (an answer whose every derivation was recursive has
a legitimately zero base); zeros in `A` are REPRESENTATION.

Remedies, in order:

1. **Witness guards (build with the ring — §2).** With `plus(0,x)=x` and
   `times(0,x)=0` as identity checks, the wasted ops become pointer
   comparisons and the junk structure never exists. Required for output
   hygiene regardless of cost.
2. **Sparse solve (SHELVED, benchmark-gated).** Tarjan over the answer-level
   edge graph, topological order, forward-substitute bases along actual edges
   (O(edges)), dense star only for nontrivial SCCs (O(k³), k typically 1).
   Consumes the DependencyGraph maps directly and DELETES the densification
   step rather than adding machinery. TRIGGER: a profile showing `solveGroup`,
   or closures past ~dozens of answers. Until then the guards make the dense
   sweep's waste nearly free.

## 7. What to build

**Phase A — the value type and the witness.** A `weight/`-package value type
(working name: `Fragment`) holding an ordered manifest tuple + a `Goal`;
`ClosedSemiring<Fragment>` with: `zero`/`one` = fail/succeed with empty
manifests; `plus`/`times` = guarded or/and with manifest union; `star` = mint a
fresh `Tabled` from the manifest per §3 and return `1 | tabled(s(g))` with the
star'd operand aliased throughout (correct per §2). Law kit: the existing
Eq-parameterized `StarLaws`/semiring kits with answer-set equivalence as the
Eq — solve both sides over a small universe, compare answer sets (the
`Semiring<Goal>` witness and Provenance's language-equivalence run are the
precedents). Ships alone.

**Phase B — the stationary round-trip.** `solveClosed` under
`closedProduct(FRAGMENT, …)` for self-loop-shaped programs (the retry-loop
test's shape): assert the emitted answer program (a) under plain solve behaves
as `1 ⊕ g` (succeeds always; g's answers when g applies), (b) under a second
`solveClosed` with a numeric ring re-records the loop and recovers `w*` — the
round-trip of §3. Also the transitive-closure demo: `star(edge)` emitted, re-run
against base facts, equals the direct query. Ships alone; exercises nothing in
Phase C because stationary loops never cross the seams of §5.

**Phase C — emit-as-calls for state-chained systems.** RESOLVED BY §4's value
constructor: fragments are call-shaped from birth, so emit-as-calls is the
representation, not a mechanism. The one engine-side addition: the mode
records the consume-site `argsTerm` as a field on each `Edge` (ring-generic
data, goal-ring-consumed — numeric rings ignore it), supplying the call
actuals for `A[i←j] ⊗ x_j`. Cell crossings reify/instantiate answer term and
actuals tuples jointly (§5). Still gated on the human's go — this level of
programming waits for grounded practice with the ordinary weighted layer.

**Non-goals, round one:** no `Goal`-layer manifest propagation, no `defer`
signature change, no general free-variable inspection, no staging towers
(fragments containing `factor(FRAGMENT, …)` — legal by closure, §1, but
untested), no sparse solve (§6's shelf). Each has a named trigger or a named
customer it waits for.

## 8. Boundaries

- The assembler must be LINEAR-recursive (the star-tabling nonlinearity guard,
  applied to the meta-program). Emitted programs are unrestricted.
- Key-finiteness gates explore, as in all tabling: star adds infinitely many
  DERIVATIONS, never infinitely many ANSWERS.
- The ring's laws hold up to answer-set equivalence; consumers that need
  structural identity of programs (caching on program text, say) are outside
  the ring's contract.
- Each `star` mints a fresh tabled relation; sharing across star sites happens
  only via operand identity — consistent with `Tabled`'s relation-identity
  keying, and an obligation of `star()`'s implementation, not the user's.

## 9. A worked example — the route planner (the target to grow toward)

Hypothetical code: `GOAL` and `value()` do not exist. This is what using them
would look like, and what each phase would do.

**The setup.** Two kinds of knowledge with different lifetimes. The MAP —
which cities link to which — is static, known at planning time, cheap to
search. The ROADS — whether a leg is actually passable — are live, expensive,
known only at travel time. Today you must either search the live world
directly (slow, repeated) or hand-write a planner and an executor and keep
them in sync. The assembler is: search the map, EMIT the executor.

```java
// the MAP — assembly-time facts (note the cycle WAW ⇄ GDA)
Goal link(Unifiable<String> a, Unifiable<String> b) {
    return a.unifies("KRK").and(b.unifies("WAW"))
            .or(a.unifies("WAW").and(b.unifies("GDA")))
            .or(a.unifies("GDA").and(b.unifies("WAW")));
}

// the LEG — a TEMPLATE: mentions only its parameters; at run time it
// suspends until ground and consults live road status (projection-guarded)
BiFunction<Unifiable<String>, Unifiable<String>, Goal> drive =
        (from, to) -> roadOpen(from, to);

// the ASSEMBLER — an ordinary tabled relation over the map, except every
// chosen leg also deposits a drive-fragment into the goal ring
Tabled<Tuple2<Unifiable<String>, Unifiable<String>>> route =
    Tabling.defineRecursive(self -> args -> args.apply((a, b) ->
        link(a, b).and(factor(GOAL, value(Tuple.of(a, b), drive)))
            .or(defer(() -> {
                Unifiable<String> m = lvar();
                return link(a, m)
                        .and(factor(GOAL, value(Tuple.of(a, m), drive)))
                        .and(self.apply(Tuple.of(m, b)));
            }))));

// ASSEMBLE: plan every destination reachable from KRK, and get the
// provenance alongside in the same pass
Unifiable<String> dest = lvar();
Weights.solveClosed(route.apply(Tuple.of(lval("KRK"), dest)), dest,
        SemiringStore.closedProduct(GOAL, PROVENANCE),
        BreadthFirstScheduler::new);
```

**What happens, phase by phase.** EXPLORE runs the assembler as plain tabling
over the map — pure metadata, `roadOpen` never fires; each derivation's
deposited fragments are recorded as bases and edges of the equation graph,
each fragment a (actuals, template) call-value. The WAW⇄GDA cycle makes the
assembler's recursion revisit a variant — tabling catches it, the entry
seals, and the STAR solves the loop: instead of the infinite family
"KRK→WAW→GDA, KRK→WAW→GDA→WAW→GDA, …" it emits ONE finite recursive program
whose loop sits exactly where the variant check caught the repetition. EMIT
replays the reader chains: each answer arrives at the collector as
(destination, store).

**What you hold afterwards.** Per destination, `store.get(GOAL)` is a runnable
`Goal` — for GDA, morally:

```
routeKrkGda(): drive(KRK, WAW) ∧ loop(WAW)
loop(X):       drive(X, GDA)  |  drive(X, GDA) ∧ drive(GDA, WAW) ∧ loop(WAW)   [tabled]
```

— a CLOSURE over the query's variables (its formals are the manifest), never
touching the map again. And `store.get(PROVENANCE)` is the same structure as
a regex: `drive(KRK,WAW) · (drive(WAW,GDA)·drive(GDA,WAW))* · …` — the shape
of all infinitely many routes, finitely.

**What it does for us — three uses of the emitted program:**

1. **Plan once, run many (staging).** The map search happened once, at
   assembly. Tonight's emitted program runs against tomorrow's road status —
   `roadOpen` legs fire against the live world, but only along the planned
   family, never re-searching the map. Plan on metadata, execute on data.
2. **The answer carries its own checker (certificate).** "GDA is reachable" is
   accompanied by a program that re-derives it. A skeptic runs the program —
   cheap, scoped — instead of trusting or repeating the search. Star means
   even the cyclic answer's certificate is finite.
3. **Re-ask without re-planning.** Feed the emitted program back to
   `solveClosed` under a numeric ring: COUNTING counts route shapes, MIN_PLUS
   with per-leg costs prices them — the same structure, interrogated again,
   with the search already paid for. The residual program round-trips (§3).
