# Closed-semiring tabling — computing recursive weighted answers with star

**STATUS: DESIGN SKETCH, not implemented (July 2026, derived with the human
over two long conversations). The IDEMPOTENT weighted-tabling path is shipped
(`Weights.solveIdempotent`, `JoinMap`, the value flow in `Tabling`); this doc
is the CLOSED, non-idempotent path — `solveClosed` — which is designed here and
not built. Companions: `table-completion.md` and `group-seal.md` (the
completion machinery this reuses wholesale), `semiring-inference.md` (the
weighted-inference frame and the idempotent path), `lattice.md` (the two
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
  Min-plus, Viterbi, boolean. This is the shipped `solveIdempotent` path.
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
`solveIdempotent` is the fast specialization for the top row, exploiting
`a* = 1` to skip the matrix. §6 returns to why both are kept.

One hard limit, and it is mathematical, not an artifact of the solver: star
closes only LINEAR recurrences — each derivation of an answer uses at most one
recursive call into the cycle. Nonlinear recursion (two recursive calls
multiplied, `p :- q ⊗ r` with both in the SCC, grammar-shaped rules) has an
ALGEBRAIC / context-free fixpoint with no rational closed form at all. Closed
tabling detects and refuses those; it does not solve them.

## 2. Idempotent vs. closed — the implementation difference

The shipped idempotent path runs values THROUGH the continuations. The cell is
a `JoinMap<Reified, V>` that folds V by ⊕; a consumer ⊗s the cell value into
its running weight and yields; a value that ascends (a cheaper path) re-wakes
parked consumers; idempotence makes the ascent stationary, so it converges and
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
- **Emit.** The producer stopped at the seal, so it produces nothing more. Push
  the finished answers back THROUGH the parked continuations (the ordinary
  tabling sleepers), resumed with the star-computed values; because a produce
  into a sealed cell now just passes through, the recursive produces cannot
  re-fire and the answers reach `solve` without re-entering the loop (§4.5).

The through-line: exploration's continuations carry the STRUCTURE (a normal run
under the presence semiring), star computes the VALUE as data, and emission
pushes finished answers back through those same continuations with the producer
off. Ordinary tabling fuses structure, value, and delivery into one pass; closed
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
it. It runs the three phases of §2; the SOLVE phase fires at EACH SCC seal,
bottom-up, riding the group-seal cascade (inner SCCs finalize first and become
constant bases for the outer ones). At each seal it reads the captured `A`/`b` off the entries (§4.1–4.2), runs the
star as pure arithmetic (§4.4), and EMITS. Only emit touches fibers — it resumes
the parked continuations (§4.5) — so the seal hook returns that emit fiber onto
the SAME scheduler, interleaving with the ongoing search and finishing inside the
single `runToCompletion` drain. Bottom-up ordering is not imposed; it falls out
of seal order, since an outer SCC cannot seal until the inner ones it consumes
already have. (An earlier design PROBED the coefficients here with a fiber pass;
§4.3 explains why that is unnecessary — they are recorded at produce, so gather
and star are pure and only emit is fiber work.)

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
no separate probe pass. Each derivation carries, on its IMMUTABLE package, a
RECURRENT record of which looping (still-open SCC) answers it has consumed,
appended by `consume`. At the hook the produce routes on that count:

- **0 consumed → a base.** A non-recursive answer; its value is the base `b_i` —
  the seed the star's loop feeds on (`x = A*⊗b`; with no base, `A*⊗0 = 0` and
  there are no answers). ⊕-folded into the entry's base map.
- **1 consumed → an edge.** The one-step coefficient `A[i←j]`, `j` being the
  consumed answer. The presence cell stripped `j`'s value, so the SemiringStore
  holds exactly `i`'s body contribution. ⊕-folded into the entry's edge map.
- **≥2 consumed → nonlinear** (`A ⊗ x_j ⊗ x_k`), outside star's reach — throw
  (§4.3).

All of it is grabbed BEFORE the presence dedup drops the produce as a duplicate
key, so multiplicity survives: two paths to the same answer ⊕-fold (base or edge).
The record rides the immutable package, so it is per-derivation and order-proof —
no "first answer" race. And it is caller-agnostic: the master resets the running
value to `one()`, so base and coefficient are the goal's OWN weight; `callerWeight`
is applied on the way out (§2), never folded in — which is what lets them sit on
the SHARED entry, correct for every caller. For a one-answer relation the base is
a single number; a many-answer one (`path(a,Y)` — `Y = b, c, …`) keys base and
edges by answer.

### 4.2 At each seal — gather

Take the merged region (the SCC — group-seal's virtual merge already computes
its membership; the cycle is a property of the recursion paths, which cross
table boundaries freely). The system is ALREADY on the entries — gather is a
read, not a probe:

- **The base map gives `b`** — captured at produce (§4.1); the presence cell
  holds no values.
- **The edge map gives `A`** — also captured at produce (§4.1), keyed by
  `(i, j)`, with multiple one-step ways to the same edge already ⊕-folded.

No resuming sleepers, no re-running the body: explore already took every
recursive step once and recorded its coefficient. The sleepers matter for
detecting the SEAL (they are the completion graph's edges — `table-completion.md`),
not for the values.

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

Build `A` (n×n) and `b` from the gathered edges and bases, run the Kleene sweep
of §3.6 (`x = A* ⊗ b`) as pure data. This all lives in a solve-TIME value layer —
`A`, `b`, and the solution vector `x`, keyed by the presence cells' answer keys
but stored OUTSIDE the cells. The cells stay presence-typed (which is what kept
explore bounded and terminating); the real semiring values never enter them,
existing only in this transient layer the seal builds and emit consumes. `x_i`
(keyed by answer) is what emit injects into the resumed continuations. No
continuation runs during this step, and no cell is rewritten.

### 4.5 Emit — push finished answers through, producer off

The cell is NOT the endpoint; `solve` is, and the path to it — the rest of the
program AFTER the tabled call — lives only in the continuations. So you cannot
deliver by reading the cell; the finalized value has to TRAVEL out through the
continuation that carries it to the collector.

Emit resumes the parked continuations (the ordinary tabling sleepers) with the
star-computed `x_i` injected into the package, and pushes them forward. What makes
this safe is that the producer STOPPED at the seal: a produce into a sealed cell
fires nothing — no append, no re-wake — it just lets the continuation pass
through. So the recursive produces a resumed sleeper would hit are inert, and the
answer flows out to `solve` without re-entering the loop. No per-package "final"
tag and no produce switch — the seal is the off state. (This is the one change to
the producer: today a produce into a sealed cell fails the branch; in wait mode it
passes through instead. Streaming never reaches it — its answers left before the
seal.)

Nothing at emit is tagged EXPLORATION (tagging is the producer's explore-phase
behavior; a sealed producer does not tag), so these answers clear the collector's
filter and are kept. Composed queries thread the same way: a tail tabled call is
itself sealed, so consuming it is a lookup that flows the composition on to
`solve` as an ordinary untagged answer.

### 4.6 What is reused vs. new

Reused, unchanged: the structural key search, completion detection (both tiers),
the group seal and its virtual merge, the cascade order. NEW, and small: a
discarding collector (explore), base + coefficient recording plus the
nonlinearity guard (explore), the ~20-line Kleene solver (solve), and the
producer-off replay (emit) — most of it in the weight package with one hook into `sealCascade`,
not in the tabling core. The part that is usually hardest — knowing WHEN an SCC
is complete and WHICH calls it contains — is exactly what the completion
machinery already provides.

## 5. Optional: pruning doomed stragglers

Under a parallel scheduler, a partial (pre-star) package can be in flight when
its coat's region seals concurrently. Since a sealed region's exploration is
complete and its answers will be emitted, such an EXPLORATION-tagged package (a
doomed straggler) is redundant and can be killed early
(`coat.region.isSealed()` → fail the branch) instead of running to the collector
to be dropped there. It is SAFE because the detach-k
billing keeps a region unsealed while any straggler that could still trigger
work is alive (`table-completion.md` §4) — so a sealed coat provably has nothing
left to do; and it is a NO-OP under sequential schedulers (the seal and the
straggler's finish coincide), so it is scheduler-level, benchmark-gated, and
falls back to the existing late death (`addAnswer` returning none on a sealed
cell) with no behavior change. It needs trigger points beyond the optimizer's
single `defer` hook — a seal check at the tabled boundaries.

## 6. One algorithm — the degenerate star and the capability switch

`solveClosed` is not merely a sibling of `solveBounded` (the shipped
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
fast path against it. The chosen entry seeds the Table's stream-vs-wait mode — it
is never inferred behind the caller's back, so the choice is not taken away. What
the two modes share, because production ends at the seal either way (§4.5), is the
producer; they differ only in whether the value is folded eagerly (stream) or
left to the seal and starred (wait).

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
