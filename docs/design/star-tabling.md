# Closed-semiring tabling — computing recursive weighted answers with star

**STATUS: AS BUILT (July 2026; designed with the human over several long
conversations, then twice restructured during implementation — record-at-produce
replaced the seal-time probe, and the ANONYMOUS-MASTER rework replaced
master-continuation emit with reader-chain replay). The BOUNDED weighted path is
`Weights.solveBounded` (`Streaming`, `JoinMap`); this doc is the CLOSED path —
`Weights.solveClosed`, with `weight/Closed.java` (the `TablingMode`),
`weight/StarTabling.java` (the joint gather/solve) and `weight/StarSolve.java`
(the Kleene sweep). Companions: `table-completion.md` and `group-seal.md` (the
completion machinery this reuses wholesale), `semiring-inference.md` (the
weighted-inference frame and the bounded path), `lattice.md` (the two
algebras). Read those for the vocabulary: cells, coats, ledgers, sleeper edges,
the seal, the group seal's virtual merge.**

---

## 1. The spine

Tabling catches recursion and parks it. A tabled call memoizes its answers; a
consumer that catches up on the cache parks its continuation *as data* (a
`Registration`) and terminates. While the call's body executes, the work is
COUNTED — two monotone counters, Dijkstra–Scholten style: `started` bumped when
a unit of work is wrapped (before its parent can finish), `finished` bumped at
fiber end. When `started == finished` and every sleeper is parked home or at a
sealed region, no new answer can ever arrive: that is the SEAL. (For a group of
mutually-recursive calls the same rule applies to their virtual merge, with a
two-phase `started` re-read reconstructing a lock-free consistent snapshot —
`group-seal.md`.)

The seal marks something precise and slightly surprising: **the set of answer
KEYS is complete, even though the set of DERIVATIONS is infinite.** A cyclic
relation has finitely many answers (the key set stabilizes — that is why
`p(X):-p(X)` seals with zero answers instead of looping), but infinitely many
*ways* to derive each one (loop zero times, once, twice, …). Plain set-tabling
only cares about keys, so it terminates. A weighted semiring cares about the
derivations, and here the two axes that govern this whole design appear:

- **Idempotence** (`a ⊕ a = a`) is the license to emit DURING exploration. When
  re-deriving a known answer is absorbed, the streamed partial value is already
  sound and only improves, so you can fold it into the cell and let it flow out.
  Min-plus, Viterbi, boolean. This is the shipped `solveBounded` path.
- **Non-idempotence** (counting, probability) forbids that. Streaming a
  non-idempotent value over a cycle DIVERGES — the cell folds `1, 2, 3, …`
  forever, never quiesces, never seals — and even on a DAG, re-waking a
  consumer as a value ascends double-counts. So the value cannot be folded
  during exploration; it must be DEFERRED to the seal.
- **Closedness** (having a Kleene star, `a* = 1 ⊕ a⊗a*`) is what makes the
  deferred value COMPUTABLE. Star sums the infinite derivation set in closed
  form, so you never run the loop.

These axes are ORTHOGONAL, and conflating them is the easy mistake. Min-plus is
closed AND idempotent — it streams fine and needs no star. Probability is
closed but NOT idempotent — it is the case that must defer *and* can be closed.
Counting is neither usefully closed nor idempotent — cyclic counting is a true
infinity and star cannot rescue it. So: **idempotence decides whether streaming
is SOUND; closedness decides whether star can close what you deferred.**

Soundness is not termination, and a third property — hidden under "idempotent" —
decides whether streaming HALTS: BOUNDEDNESS. A bounded semiring is one whose
`1` is the TOP (`a ⊕ 1 = 1`, i.e. `a ≤ 1` for all `a`); there the star is
degenerate, `a* = 1`, because `a ≤ 1 ⟹ aⁿ ≤ 1`, so the partial sums
`1 ⊕ a ⊕ … ⊕ aⁿ` are all `1` and stabilize at once. `a* = 1` is precisely
"looping adds nothing beyond the base" — which is WHY a bounded fixpoint is
stationary. Nonnegative min-plus (`1 = 0`, the top of the min-order), Viterbi,
boolean are all bounded, and all stream.

The unbounded idempotent semirings are the trap. PROVENANCE (regular languages:
`⊕` = union, `⊗` = concat, `1 = {ε}`) is idempotent and closed but NOT bounded —
`a* = {ε, x, xx, …} ≠ {ε}`. Streaming it over a cycle DIVERGES despite
idempotence: the value is the infinite set of derivation strings, the cell
ascends forever, and only the SYMBOLIC star names it finitely. So idempotence
never was what made streaming terminate — boundedness is. (And min-plus with
NEGATIVE weights is idempotent but not even closed: `a* = min(0, a, 2a, …) = −∞`
for `a < 0`, the negative-cycle divergence.) The honest partition is therefore
BOUNDED vs. NEEDS-STAR, not idempotent vs. closed:

| semiring | streams (halts) | needs star |
|---|---|---|
| bounded idempotent (`a* = 1`) — boolean, nonneg min-plus, Viterbi | ✔ | star agrees, degenerately |
| unbounded idempotent, closed — provenance / regex | ✘ diverges | ✔ |
| non-idempotent, closed — probability | ✘ | ✔ |
| idempotent, not closed — min-plus w/ negatives | ✘ | ✘ undefined |

`solveClosed` is the general path for the whole NEEDS-STAR column;
`solveBounded` is the fast specialization for the top row, exploiting
`a* = 1` to skip the matrix. §6 returns to why both are kept.

One hard limit, and it is mathematical, not an artifact of the solver: star
closes only LINEAR recurrences — each derivation of an answer uses at most one
recursive call into the cycle. Nonlinear recursion (two recursive calls
multiplied, `p :- q ⊗ r` with both in the SCC, grammar-shaped rules) has an
ALGEBRAIC / context-free fixpoint with no rational closed form at all. Closed
tabling detects and refuses those; it does not solve them.

## 2. Idempotent vs. closed — the implementation difference

The shipped bounded path runs values THROUGH the continuations. The cell is
a `JoinMap<Reified, V>` that folds V by ⊕; a consumer ⊗s the cell value into
its running weight and yields; a value that ascends (a cheaper path) re-wakes
parked consumers; boundedness makes the ascent stationary, so it converges and
answers stream out eagerly. Values and control flow together.

The closed path cannot do any of that, and the reason is the opaque
continuation. A continuation is `Package -> Cont`; running it may recurse back
into tabling OR leave to `solve`, and you cannot tell which. In the idempotent
case you never need to — you run everything and values flow, and partial values
are sound. In the closed case the escaped value is a FRAGMENT (the acyclic part;
the cyclic contributions that star will add are still missing), so letting it
flow out emits a wrong answer. And you cannot "explore without emitting,"
because exploration and emission are the same act through the same opaque
continuation.

So closed tabling keeps values out of the continuations while it explores and
solves, and runs in three phases:

- **Explore.** Ordinary structural (key-level) tabling — REUSED unchanged, a
  NORMAL run. It finds every key and seals, exactly as plain tabling does. The
  real value is never folded into the (presence) cell; it rides the package,
  escapes toward `solve`, and is tagged EXPLORATION so the closed collector drops
  it. The tag names the phase — a bounded solve tags the same escapes and just
  never filters.
- **Solve.** At each seal, compute the answers' values by star, as PURE DATA —
  a matrix computation over recorded coefficients, touching no continuation.
- **Emit.** The producer ended at the seal, so nothing produces anymore. Replay
  each finished TOP-LEVEL reader chain once from the start with the
  star-computed values injected; coated readers are never replayed — their
  contribution already rides the edges they captured (§4.5).

The through-line: exploration's continuations carry the STRUCTURE (a normal run
under the presence semiring), star computes the VALUE as data, and emission
replays the reader chains that carried the structure, this time with the values
in. Ordinary tabling fuses structure, value, and delivery into one pass; closed
tabling separates them along the seal, so delivery is a linear pass, never a
re-entered search.

## 3. From sleepers to the closed form

### 3.1 From sleepers to the system — reading the equations off the parked cycle

Before the math, the bridge: at a seal, all you physically hold is a ring of
PARKED CONTINUATIONS. Turning them into a linear system is mechanical, and the
one trap is doing too much.

A sleeper is one directed edge, and it already carries both indices it needs.
Its COAT names the call whose body is suspended (`i`); the ENTRY it parked in
names the call it waits for (`j`). So the sleeper IS the edge `i→j`, with no
reconstruction — read the two indices straight off it. Feeding it a `j`-answer
whose value is `one()` and reading the weight at its next produce yields `A_ij`
— the ONE-STEP weight, `j`'s own value stripped out. (§4.3: the presence cell
already strips `j`'s value, so in practice `A_ij` is just the recursive produce's
own weight, recorded there during explore — no separate probe.)

The trap is chaining. A sleeper's continuation, resumed, will happily run on to
the next consume and the next — all the way around the loop and back. Do NOT let
it: take each edge's LOCAL weight exactly once, with `one()`, and stop. Chaining
a sleeper around the cycle is literally running the infinite recursion star
exists to avoid. One `one()` per sleeper gives one coefficient; STAR does every
composition and every cycle.

Placement is then trivial, and the two indices say exactly where each
coefficient goes: **the first index picks the equation, the second picks the
variable term.** `A_ij` lands in row `i` (this is `x_i`'s equation) as the `x_j`
term. Worked, with a three-sleeper cycle `a→b→c→a` (data-flow: `a` feeds `b`
feeds `c` feeds `a`, so the DEPENDENCIES run the other way — `b` on `a`, `c` on
`b`, `a` on `c`):

```
sleeper for b, waiting on a   → A_ba   → row b, col a
sleeper for c, waiting on b   → A_cb   → row c, col b
sleeper for a, waiting on c   → A_ac   → row a, col c
```

Each sleeper drops ONE off-diagonal entry. Add the bases `b_i` (from the
produces that consumed no SCC member — `a`'s, if `a` has a non-recursive
derivation), and the rows ARE the equations:

```
x_a = b_a ⊕ (A_ac ⊗ x_c)
x_b = b_b ⊕ (A_ba ⊗ x_a)
x_c = b_c ⊕ (A_cb ⊗ x_b)
```

**Why one parked cycle yields three DISTINCT values, not one.** The instinct is
that a single ring of sleepers is "one recursion" and should have one value; it
does not, and provenance shows why cleanly. Solve the system symbolically (edge
facts `p, q, r` for the three coefficients, base `s` at `a`):

```
x_a = (rqp)* ⊗ s
x_b = p ⊗ (rqp)* ⊗ s
x_c = q ⊗ p ⊗ (rqp)* ⊗ s
```

All three SHARE the star `(rqp)*` — that shared star IS the single recursion the
instinct senses. They differ only in the PREFIX — how far into the loop each
answer sits: `a` at the top (`ε`), `b` one edge in (`p`), `c` two (`qp`). So a
cyclic SCC is one loop entered at several positions; each answer's value is "the
path that reaches it, then the shared loop," and the paths differ. In min-plus
the shared `(rqp)*` collapses to a constant (`0` for a nonnegative loop) and the
prefix is the distance — `x_a, x_b, x_c` come out `0, 2, 5` for edge costs
`2, 3, 4`. In counting the shared star diverges — cyclic counting is a true
infinity, correctly. Same structure, three answers.

### 3.2 The system

Each tabled call in a strongly-connected component (SCC) has a value equation:
its non-recursive contribution, plus a weighted sum of the others' values.

```
x_i = b_i ⊕ (A_i1 ⊗ x_1) ⊕ (A_i2 ⊗ x_2) ⊕ … ⊕ (A_in ⊗ x_n)
```

- `b_i` — the BASE: the ⊕-sum of `i`'s derivations that consume no SCC member.
- `A_ij` — the COEFFICIENT: the ONE-STEP ⊗-weight of `i`'s dependence on `j` —
  the factor `i`'s body applies between consuming a `j`-answer and producing its
  own, ⊕-summed over the ways it does so. Independent of `x_j`'s value — which is
  why the presence cell can strip it, leaving `A_ij` on the recursive produce (§4.3).

The index runs over ANSWERS in the SCC, not calls (shortest-path over a graph is
a node×node system — Floyd–Warshall).

### 3.3 One self-loop: star is the fixpoint

A single self-recursive answer is one equation with itself on the right:

```
x = b ⊕ (a ⊗ x)
```

Group everything that is not the self term into one constant `c` (from x's point
of view, the other variables are fixed this step): `x = c ⊕ a⊗x`. Its solution
is `x = a* ⊗ c`, where

```
a* = 1 ⊕ a ⊕ a² ⊕ a³ ⊕ …          ("loop zero-or-more times")
```

Two ways to see it. **Unfold** — substitute x into itself forever:

```
x = c ⊕ a⊗x = c ⊕ a⊗c ⊕ a²⊗x = c ⊕ a⊗c ⊕ a²⊗c ⊕ …
  = (1 ⊕ a ⊕ a² ⊕ …) ⊗ c = a* ⊗ c
```

**Axiom check** — `a* = 1 ⊕ a⊗a*`, so `c ⊕ a⊗(a*⊗c) = (1 ⊕ a⊗a*)⊗c = a*⊗c`.

### 3.4 Why star, not division

Over a field you would solve `x = b ⊕ a⊗x` as `x = b/(1−a)` — subtraction and
division. A semiring has NEITHER (no additive inverse: you cannot un-min, un-or,
un-count; no multiplicative inverse). But `1/(1−a)` is exactly the geometric
series `1 ⊕ a ⊕ a² ⊕ …` — so `a*` is `1/(1−a)` written without subtraction or
division. Star is the semiring's stand-in for "invert `1 − a`", and a CLOSED
semiring is by definition one where that stand-in exists (`a* = 1 ⊕ a⊗a*`). A
field is automatically closed, which is why ordinary linear algebra never
mentions star.

### 3.5 Mutual recursion — Gaussian elimination folds a cycle into a self-loop

Two calls that reference each other, neither self-recursive:

```
p = b_p ⊕ (a ⊗ q)
q = b_q ⊕ (c ⊗ p)
```

Substitute q into p — this is one Gaussian elimination step:

```
p = b_p ⊕ a⊗(b_q ⊕ c⊗p) = (b_p ⊕ a⊗b_q) ⊕ (a⊗c) ⊗ p
```

Back to the self-loop shape — but the thing you star, `(a⊗c)`, is the ROUND
TRIP `p→q→p`, and it appears on `p`'s diagonal even though `p` had no self-loop.
Eliminating `q` MANUFACTURED the self-loop. So:

```
p = (a⊗c)* ⊗ (b_p ⊕ a⊗b_q)
```

Star does not require any call to be syntactically self-recursive; it requires a
CYCLE, and elimination collapses any cycle onto the last-eliminated member's
diagonal. (The 2×2 matrix here has a ZERO diagonal and star still appears.)

### 3.6 The general fold — Kleene's algorithm

For `n` mutually-recursive answers, arrange the coefficients as an `n×n` matrix
`A` and the bases as a vector `b`. Eliminate variables one at a time. Removing
`k`, whose equation has `A_kk ⊗ x_k` on both sides, uses star to solve for it:

```
x_k = A_kk* ⊗ (b_k ⊕ Σ_{j≠k} A_kj ⊗ x_j)
```

Substituting into every remaining equation updates each surviving coefficient:

```
A_ij ← A_ij ⊕ (A_ik ⊗ A_kk* ⊗ A_kj)
b_i  ← b_i  ⊕ (A_ik ⊗ A_kk* ⊗ b_k)
```

That update, swept over all `k`, IS Floyd–Warshall/Kleene — "add the ways from
`i` to `j` that route through `k`, looping at `k` any number of times." It is
classical Gaussian elimination run additively, with "divide by `(1 − pivot)`"
replaced by "multiply by `star(pivot)`". The whole matrix becomes `A*`, and:

```
x = A* ⊗ b
```

Compactly:

```
for k in members:
  for i in members:
    for j in members:
      A[i][j] ⊕= A[i][k] ⊗ A[k][k]* ⊗ A[k][j]
x = A* ⊗ b
```

`O(n³)` semiring operations — tens of lines over a `ClosedSemiring`. At `n=1`
(one self-loop) it collapses to `x = A_11* ⊗ b_1`. On an ACYCLIC graph every
derived diagonal stays `0`, `0* = 1`, and it degenerates to plain forward
substitution — ordinary DP, no star.

### 3.7 Worked example — min-plus loop

```
loop :- factor(3).          % base: cost 3
loop :- factor(2), loop.    % recursive: pay 2, then loop
```

min-plus is `⊕ = min`, `⊗ = +`, `one() = 0`, `zero() = +∞`. The system is 1×1:
`b = 3`, `a = 2`. So `loop = a*⊗b = 2*⊗3`. In min-plus `2* = min(0, 2, 4, …) = 0`
(looping only adds cost), hence `loop = 0 + 3 = 3`. The infinite loop was summed
without being run.

### 3.8 Provenance — star is the regular expression

Compute in the FREE semiring (symbols, not numbers): `⊗` = "in sequence", `⊕` =
"or". Then star is the Kleene star of regular expressions — `a*` = "zero or more
a". The provenance of a recursive answer is a REGULAR EXPRESSION over the base
facts. For a path relation `path = edge ⊕ (edge ⊗ path)`, the closed form is
`path = edge* ⊗ edge = edge⁺` — "a sequence of one or more edges", which is
literally what a path is, as a finite symbolic object standing for infinitely
many derivations. Every concrete semiring is a homomorphic evaluation of that
regex: min-plus gives the shortest length, counting the number of paths (∞ on a
cycle — correctly), probability the summed probability. Acyclic provenance is a
polynomial; recursive provenance is a rational expression (polynomial + star).

## 4. The algorithm

`solveClosed` reuses the completion machinery whole and adds four pieces around
it. Two facts of the tabling core make the phases line up. First, the ANONYMOUS
MASTER (table-completion.md): a tabled body runs as detached work billed to its
own entry, and every caller — the first included — reads the cell through a
consumer whose parked registration is a dependency edge, so entries SEAL IN
DEPENDENCY ORDER. Second, and following from the first: SEALED ⟹ SOLVABLE. A
seal freezes one entry's slice of the equation graph, and an entry is solvable
only with its whole dependency closure over that graph — the equation system's
coupling; dependency-ordered sealing guarantees that at any entry's seal the
whole closure has sealed too, earlier (a constant by then) or atomically with it
(a sleeper ring group-seals). So the closed mode solves at the closure's last
announcement — nothing ever waits across cascades — and inner SCCs solve first,
becoming constants for the outer ones, bottom-up without imposing an order. At
each solve it reads the captured `A`/`b` off the `DependencyGraph` (§4.1–4.2),
runs the star as pure arithmetic
(§4.4), and EMITS — the only fiber work: the seal hook returns the replay fiber
onto the SAME scheduler, interleaving with the ongoing search and finishing
inside the single lazy drive. (Two earlier designs died here: a fiber pass that
PROBED coefficients at seal — §4.3 explains why record-at-produce makes it
unnecessary — and an emit that replayed the MASTER's own continuation, which
under sequential singleton seals re-entered the co-recursive caller's body and
double-counted; the anonymous-master rework replaced it with reader-chain
replay, §4.5.)

### 4.1 Explore (reused, presence cell, normal run)

Run ordinary structural tabling under the EXISTS (presence) semiring. This is a
NORMAL run — the continuations execute exactly as in plain tabling, discovering
every key and sealing (proven by the `p(X):-p(X)` termination). The presence cell
folds presence, which is bounded, so nothing diverges and there is no special
machinery here. The real semiring value rides along on the package but is NOT
folded into the presence cell — it escapes toward `solve`, and every such escape
is tagged EXPLORATION and dropped at `solveClosed`'s collector (each is a fragment
until the loops are summed). The tag names the PHASE, not a verdict: a
bounded/streaming solve tags the same escapes and simply never filters them; the
closed collector filters them, while an untagged non-tabled answer (from a branch
that touched no tabled call) is always kept.

Explore captures the VALUES the star needs, right at the produce hook — there is
no separate probe pass. It writes them into a first-class `DependencyGraph`
(`weight/`): the vertices are `Node`s — an `(entry, answer)` pair, because the
answer term is unique only within its entry and the graph spans a whole closure
of entries — and the directed edges are `Edge(from, to)`, "from depends on to."
Each derivation carries, on its IMMUTABLE package, a RECURRENT record of which
looping (still-open) `Node`s it has consumed, appended by `onConsume`. At the
hook the produce routes on that count:

- **0 consumed → a base.** A non-recursive answer; its value is the base `b_i` —
  the seed the star's loop feeds on (`x = A*⊗b`; with no base, `A*⊗0 = 0` and
  there are no answers). ⊕-folded onto the produced node in the graph.
- **1 consumed → an edge.** The one-step coefficient `A[i←j]`, `j` being the
  consumed node. The presence cell stripped `j`'s value, so the SemiringStore
  holds exactly `i`'s body contribution. ⊕-folded onto `Edge(i, j)` in the graph.
- **≥2 consumed → nonlinear** (`A ⊗ x_j ⊗ x_k`), outside star's reach — throw
  (§4.3).

All of it is grabbed BEFORE the presence dedup drops the produce as a duplicate
key, so multiplicity survives: two paths to the same answer ⊕-fold (base or edge).
The record rides the immutable package, so it is per-derivation and order-proof —
no "first answer" race. And it is caller-agnostic: the master starts its body
from `one()`, so base and coefficient are the goal's OWN weight; each reader's
running value is ⊗'d on at replay (§4.5), never folded in — which is what lets
one shared graph serve every caller. For a one-answer relation a node's base is a
single value; a many-answer one (`path(a,Y)` — `Y = b, c, …`) is several nodes.

### 4.2 At each solve — gather

Take the dependency closure over the `DependencyGraph` (the SCC, plus any
already-solved entries it references as constants — `graph.dependencyClosure`
walks the edges themselves; the cycle is a property of the recursion paths, which
cross table boundaries freely). The system is ALREADY in the graph — gather is a
read, not a probe:

- **The node bases give `b`** — captured at produce (§4.1); the presence cell
  holds no values.
- **The edge coefficients give `A`** — also captured at produce (§4.1), keyed by
  `Edge(i, j)`, with multiple one-step ways to the same edge already ⊕-folded.

`StarTabling.solveGroup` indexes the closure's nodes and reads base and
coefficient straight into the `S[][]`/`S[]` the Kleene sweep consumes. No
resuming sleepers, no re-running the body: explore already took every recursive
step once and recorded its coefficient. The sleepers matter for detecting the
SEAL (they are the completion graph's edges — `table-completion.md`), the same
dependency at a coarser grain (entry, not node) and an earlier lifetime, not for
the values.

### 4.3 Coefficients are recorded at produce, not probed

An earlier design gathered coefficients at seal by PROBING — resume each parked
sleeper against a `one()`-valued answer and read the produce. It is unnecessary:
the coefficient is already at the recursive branch's produce during explore. When
`i`'s body consumes a `j`-answer and produces `i`, the presence cell has stripped
`j`'s value, so the SemiringStore carries exactly `i`'s one-step contribution —
the coefficient `A[i←j]`. The `consume` that crossed into `j` recorded `j` on the
RECURRENT list, and the produce reads it and ⊕-folds the value into `A[i][j]`
(§4.1). So the coefficient is captured the same way and at the same moment as the
base, differing only by the RECURRENT count — no seal-time fibers, and all of the
base/coefficient work stays inside the ordinary explore.

**Nonlinearity guard.** A single derivation must consume at most ONE SCC member.
If its RECURRENT list holds TWO, the derivation is `A ⊗ x_j ⊗ x_k` — nonlinear,
outside star's reach — so throw loudly ("nonlinear recursion in this SCC; star
handles only linear systems"), in the spirit of `assertNoConstraints`. This is
where the linear-only limit of §1 is enforced.

### 4.4 Star — the solution vector

Index the closure's `Node`s, build `A` (n×n) and `b` as plain arrays from the
graph's edge coefficients and node bases, run the Kleene sweep of §3.6
(`x = A* ⊗ b`) as pure data. The graph and the solution live OUTSIDE the cells,
which stay presence-typed (what kept explore bounded and terminating); the real
semiring values never enter them. `x` comes back keyed by `Node` — `x_i` is what
emit injects into the replayed reader chains. No continuation runs during this
step, and no cell is rewritten.

### 4.5 Emit — replay the reader chains

The cell is NOT the endpoint; `solve` is, and the path to it — the rest of the
program AFTER the tabled call — lives only in the continuations. So you cannot
deliver by reading the cell; the finalized value has to TRAVEL out through the
continuation that carries it to the collector.

The carriers are the READER CHAINS. Every caller consumes through one, and
during explore every delivery a chain makes is a FRAGMENT (tagged EXPLORATION,
dropped at the closed collector). A chain ends at a sealed entry in exactly one
of two ways — PARKED at the seal (drained by it and handed to the mode) or
CAUGHT UP after it — and at that end a TOP-LEVEL chain is replayed exactly
once: from index 0, over the final answers, with `x_i` ⊗'d onto the chain's own
running value and handed to its continuation. The exactly-once split is what
makes chains that cross the seal mid-read safe: they just end a little later
and are replayed then. Replayed deliveries carry no tag, so they clear the
collector's filter.

Two kinds of chain are never replayed. A COATED reader (a line of some entry's
body) already delivered its contribution structurally — its consume recorded
what its produce captured as an edge, and the star folds that edge; replaying
it would run the body suffix a second time and double-count. When a coated
reader consumes an already-SOLVED entry, the value is instead ⊗'d INLINE at the
consume so its produce captures the constant — and the two paths agree, because
an edge to a solved entry folds to exactly the inline value. And a FRAGMENT
chain — one whose own call site sits inside a tagged delivery, a call reached
during someone else's explore — is skipped, because the upstream replay re-runs
the continuation with values and thereby spawns that chain's valued twin.

Composed queries thread through these rules with nothing added: a second call
to a solved relation is read by a fresh valued chain whose deliveries ⊗ both
values — `loop(x) ∧ loop(x)` comes out `x ⊗ x` by construction.

### 4.6 What is reused vs. new

Reused, unchanged: the structural key search, completion detection (both tiers),
the group seal and its virtual merge, the cascade order. NEW: the mode seam
(`TablingMode` — the skeleton's phase hooks, with `Streaming` and `Closed` as its
two implementations), base + coefficient recording plus the nonlinearity guard
(explore), the closure walk and the ~20-line Kleene solver
(solve), and the reader-chain replay (emit) — all of the closed logic in the
weight package. The tabling core's own contributions are the anonymous master
and the seal hook that hands the drained subscribers to the mode. The part that
is usually hardest — knowing WHEN an SCC is complete and WHICH calls it
contains — is exactly what the completion machinery already provides.

## 5. Optional (unbuilt): pruning doomed stragglers

Under a parallel scheduler, a partial (pre-star) package can be in flight when
its coat's region seals concurrently. Since a sealed region's exploration is
complete and its answers will be emitted, such an EXPLORATION-tagged package (a
doomed straggler) is redundant and can be killed early
(`coat.region.isSealed()` → fail the branch) instead of running to the collector
to be dropped there. It is SAFE because the billing discipline keeps a region
unsealed while any straggler that could still trigger work is alive (a body is
billed to its own entry, a reader to its caller — `table-completion.md` §4) —
so a sealed coat provably has nothing left to do; and it is a NO-OP under
sequential schedulers (the seal and the straggler's finish coincide), so it is
scheduler-level, benchmark-gated, and falls back to the existing late death
(`addAnswer` returning none on a sealed cell) with no behavior change. It needs
trigger points beyond the optimizer's single `defer` hook — a seal check at the
tabled boundaries.

## 6. One algorithm — the degenerate star and the capability switch

`solveClosed` is not merely a sibling of `solveBounded` (shipped first as
`solveIdempotent`, renamed to name the real criterion — §1); it SUBSUMES it. On a
bounded semiring `a* = 1` (§1), so the Kleene sweep computes exactly the
⊕-closure streaming would — the same answers, reached by matrix instead of
fixpoint. One code path, run with the degenerate star, covers all three
NEEDS-STAR rows AND the bounded row.

Which raises who picks the strategy. The capability TYPE gates LEGALITY, not the
choice: `solveBounded` demands a `BoundedSemiring` (so it can promise streaming),
while `solveClosed` accepts any `ClosedSemiring` — and since `BoundedSemiring`
extends `ClosedSemiring`, a bounded semiring is a legitimate argument to
`solveClosed` as well. So the CALLER picks the entry point, and running a bounded
semiring through the star path is allowed: you pay `O(n³)` for the degenerate
`a* = 1`, but it is correct, and you might want it for uniformity or to check the
fast path against it. The chosen entry picks the Table's mode (`Streaming` vs
`Closed` — the two `TablingMode` implementations) — never inferred behind the
caller's back, so the choice is not taken away. What the two modes share is the
whole skeleton (anonymous master, consumers, park, completion); they differ only
in whether the value is folded eagerly and streamed (bounded) or captured as
structure and starred at the seal (closed).

Two reasons the bounded fast path is kept rather than folded into the star sweep:

- **Cost.** The star sweep is `O(n³)` over the SCC's `n×n` answer matrix. On a
  bounded semiring streaming is a monotone fixpoint that exploits `a* = 1` to
  never build the matrix — far cheaper on a large SCC.
- **Laziness.** Star needs the SCC SEALED before it solves — inherently batch.
  Streaming emits answers incrementally, before completion; for "the first cheap
  path," it delivers where star would still be waiting for the seal.

So the mental model to carry is BOUNDED vs. NEEDS-STAR, not idempotent vs.
closed: `solveBounded` is the incremental fixpoint for the bounded row where
`a* = 1`, `solveClosed` the general batch solver for everything else, and the
Table picks between them from the semiring's type — the two agreeing wherever
both apply.
