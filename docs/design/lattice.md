# Lattices — the engine's one algebra

**Status: THEORY + ADOPTION NOTE (July 2026, from design conversations with
the human). The `Lattice<L>` abstraction was deferred for months under the
rule "adoption not rewrite, when a customer exists" — the customers arrived
three at once (TCLP, FD exposure, the optimizer) with four instances already
shipping. This doc is the shared theory, the instance inventory, and the
consumer map. UNCOMMITTED until reviewed.**

Companions: `fixpoint-machine.md` (the two fixpoint engines + the scheduler),
`optimizer.md` (order, the capability ladder), `tabled-constraints.md`
(TCLP: Residue = Domain), `constraint-kernel.md` (the store boundary).

---

## 1. Theory, engine-directed

A **partial order** ⊑ on a set: reflexive, transitive, antisymmetric. Read
`A ⊑ B` as "A knows at least as much as B" (knowledge orders point DOWN in
this codebase's convention: smaller = more constrained).

A **meet-semilattice** adds a meet `A ∧ B`: the most general element knowing
everything A and B both know (greatest lower bound). Two laws matter here:

- **Entailment is free from the meet**: `A ⊑ B  ⟺  A ∧ B = A`. Any store
  with intersection and equality has entailment — no new operation.
- Meets are associative, commutative, idempotent — which is why POSTING
  knowledge commutes (constraint posts reorder freely) and why re-posting
  is harmless.

**Top and bottom**: ⊤ = knows nothing (the free variable, the unconstrained
region); ⊥ = contradiction (the failed branch, the wiped domain).

A function `f : L → L` is **monotone** if `A ⊑ B → f(A) ⊑ f(B)`, and
**contracting** if `f(A) ⊑ A`. A set `U ⊆ L` is **upward-closed** if
membership survives knowing less — equivalently, over a GROWING substrate,
once true stays true.

**Fixpoints** (Knaster–Tarski / Kleene): a monotone f on a complete lattice
has a least fixpoint, reachable by iterating from one end. **Chaotic
iteration**: a FAMILY of monotone operators applied in any fair order
reaches the same fixpoint — order changes the path, never the destination.
This single theorem is the engine's "confluence" everywhere it appears.

**Termination** comes from chain conditions: finite descending chains (a
domain can only shrink so often), finite ascending chains (a table over a
finite answer space fills up). An **antichain** is a set of pairwise
incomparable elements; an infinite antichain is how a growing process can
ascend forever without repeating — the TCLP termination hazard.

A **monotone measure** μ : L → ordered set, `A ⊑ B → μ(A) ≤ μ(B)`. Width,
interval length, volume. Comparison only — measures never enter arithmetic
(see §4, the count/measure distinction).

A **product lattice**: tuples ordered pointwise. Entailment decomposes
per-factor; the product order under-approximates semantic entailment
(missed cache hits, never wrong answers).

## 2. The instances (implementors)

| lattice | order | direction | notes |
|---|---|---|---|
| `Domain` (FD) | ⊆, meet = intersect | SHRINKS | the prototype: finite, measured (width), splittable (enumeration), restatable (`dom`) — every tier of the ladder |
| `Substitutions` | extension, meet n/a (grows by extend) | GROWS | ripeness and the ground-cache are upward-closed sets over it |
| Table entries | answer-set ⊆ | GROWS | completion = the ascent's fixpoint; completed entry = exact count |
| Adornments | pointwise bound/free (Boolean lattice per arity) | static | the optimizer's plan-memo key space; subsumption lookup = "reuse the plan of a less-bound pattern" (sound: wrong-only-slow) |
| Residues (TCLP) | per-store ⊑ | shrink | = `Domain` for FD, by construction (tabled-constraints.md §5.2) |
| `Package` | product of the above | mixed | pointwise entailment; the accepted under-approximation |
| Neq record sets | record implication (syntactic superset as the sound approximation) | GROWS | ordered ONLY — no useful measure, no split, infinite antichains: tier 1 of the ladder and correctly nothing more |

**The non-example, kept deliberately**: the optimizer's rewrite passes do
NOT form a lattice — factoring and distribution are mutual inverses, so the
rewrite relation has no order to descend and a naive drain oscillates. This
is why the pass pipeline is fixed-order, not a fixpoint (`optimizer.md`
§4-pipeline). A theory that cannot say "this is not an instance" explains
nothing; this is our negative witness alongside Neq.

## 3. The consumers — three aspects of one structure

- **Constraints consume the MEET** (operational aspect). Propagation is
  repeated meets driven to quiescence; the equal-domain termination guard
  IS the entailment test `new ⊒ old` computed as intersect+equals; failure
  is reaching ⊥; chaotic iteration is why agenda order can't change answers.
- **Tabling/TCLP consumes the ORDER** (relational aspect). Region keys,
  call containment, subsumption dedup — comparisons, never meets for their
  own sake. The §5.5 gate is a chain condition: participate iff your
  residues form a finite lattice (no infinite antichains).
- **The optimizer consumes the LAWS** (and owns the adornment instance).
  Every soundness argument it makes is a monotonicity statement:
  - μ monotone under ⊑ (tier 2a's contract for split choice);
  - stale bounds valid iff the substrate moves in the safe direction —
    shrinking data keeps stale upper bounds sound (domains), growing data
    invalidates them (incomplete tables price ∞; completed ones exactly);
  - wake conditions upward-closed (fire-once for suspensions AND tabling
    consumers — one triple, two growing substrates);
  - plan-cache subsumption over the adornment lattice (and over region
    keys if TCLP lands — same interface, same move as TCLP stage 1→2).

## 4. The capability ladder (from `optimizer.md` §5c)

Stores opt in per tier; FD is the model organism implementing all of them:

1. **ORDERED** (meet → entails) — mandatory; the kernel's contraction
   contract already requires factors to be ordered.
2. **PRICED** (order as emitted-state COUNT) — enables Bounded/sorting.
   Counts are the ONLY numbers in the global arithmetic (the counting
   semiring over populations); a forcing's count is its split ARITY.
3. **2a. MEASURED** (μ) — split choice and tie-breaking INSIDE the store;
   ordinal, local, never in the arithmetic. (FD hides the 2/2a distinction
   because enumeration arity = width.)
4. **SPLITTABLE** — a complete finite branching of a region; enumeration
   (finite), bisection (continuous). Stated this way, DPLL and interval
   branch-and-prune are instances of propagate-then-split — the evidence
   the model is lattices, not FD in a trenchcoat.
5. **FINITELY-LATTICED** — enables TCLP (the antichain gate).

## 5. The direction principle

The engine runs two monotone processes in opposite directions — knowledge
shrinks (constraints), enumerations grow (tables, substitutions) — and one
scheduler over both (`fixpoint-machine.md` §10). Direction decides:

- **Pricing**: stale widths sound (shrink), stale counts unsound until
  completion (grow).
- **Caching**: only upward-closed facts survive staleness (ground-cache,
  ripeness).
- **Flush policy** at end-of-search, per parked mechanism: FAIL (pending
  suspensions), DIE (slaves on completed entries), FORCE (labelling,
  deferred lookups).

## 5a. The two algebras of search — why both keep appearing

A search has exactly two ingredients, and each has its own algebra. Every
branch carries a STATE (what this branch knows); the branches themselves
form an AND/OR TREE. **Lattices are the algebra of the states; semirings
are the algebra of the tree.** Every feature shuffles between these two
representations, so both algebras appear everywhere, each policing its
side.

**Semirings, from the problem.** To ask a compositional question about
ALL the ways a search can succeed — exists? how many? cheapest?
likeliest? which? — you must specify exactly two things: what `or` does
to your quantity and what `and` does. That pair IS a semiring: not an
exotic structure that happens to apply, but the minimal job description
of an evaluator for an and/or tree. Each question is a plug: (∨,∧)
exists, (+,×) counts, (min,+) optimizes — Bellman noticed that min
distributes over +, and "optimization" is the and/or tree in that plug.
Distributivity is the load-bearing law: RESTRUCTURING THE TREE DOES NOT
CHANGE THE ANSWER.

**Goal trees are expressions in the free semiring, and the optimizer is
a semiring-expression rewriter.** The algebra pass's laws are the
axioms: `success ∧ g = g` is 1⊗x = x; `failure ∧ g = failure` is
0⊗x = 0; `failure ∨ g = g` is 0⊕x = x. Factoring
`(g∧a)∨(g∧b) = g∧(a∨b)` IS the distributive law. The pricing walk is
evaluation in the counting semiring. Every licensed rewrite is a
semiring identity; every banned one (across barriers) marks a goal that
is not a pure semiring element (committed choice, keyed calls).

**Where the two algebras meet: memoization.** A memo's KEYS form a
lattice (variants, adornments, regions — entailment decides reuse); each
cell's CONTENTS are a semiring fold of derivations (the set plug today,
any lawful plug under semiring tabling). Optimization touches semirings
twice: optimization PROBLEMS are the (min,+) plug; the OPTIMIZER is the
free-semiring rewriter pricing itself in the counting plug.

**Branch↔data is conversion between free structure and folded value.**
Branches = unfolded syntax (all the ways, as tree); data = a fold (a
domain is its values' ∨ folded to a set; a table cell is derivations
folded to a set or a value; a parked lookup is an unevaluated subtree).
branch→data = evaluate (lawful only under the algebra's laws — which is
why every conversion move carries a lattice or semiring condition);
data→branch = unfold (labelling regenerates the ∨ it once folded).

**The one-line duality — the engine's two freedoms:** lattices prove you
may STOP, and iterate in any order (fixpoints, chaotic iteration — the
kernel's freedom); semirings prove you may REARRANGE, and fold at any
time (rewrites, fold-early = fold-late — the optimizer's and tabling's
freedom). Both are "order does not matter" theorems: one over states,
one over structure.

**The junction, exactly (July 2026)**: an idempotent-⊕ semiring IS a
join-semilattice, so the set quotient is the precise point where the
structure algebra becomes the state algebra — which is why tabling
starred in both stories without being two features. Tabling is the
SHIPPED branch→data fold (the thing domainify wanted to be): the Table
store is pruning-as-data for recursive re-exploration exactly as a
domain is pruning-as-data for finite disjunction — two stores, one job.
And fold moves are DECLARED, never inferred: `tabled` joins the
annotation family (Barrier = don't move; Bounded = here's my price;
dom = defer this disjunction; tabled = fold and share this subtree) —
the user licenses folds, the optimizer only schedules and prices them.

## 5b. Beyond the fold: infinite knowledge, residuation, AI-widening

The fold story covers only the FINITE fragment. `dom(x, 1..5)` is a
folded conde; `x ≠ 3` and real `x > 2.5` are knowledge with NO tree
counterpart — no conde of uncountably many alternatives exists for them
to be a fold of. **Folding tree into data is total; unfolding data into
tree is partial.** Constraints are strictly more expressive than
deferred branching: the knowledge lattice exceeds the image of the fold,
and the excess is exactly what unification cannot say — negative
information (Neq), continuous information (reals), partial information
generally.

The capability ladder grades the unfold: ENUMERABLE (finite domains —
spend as values); SPLITTABLE-NOT-ENUMERABLE (real intervals — spend as
subregions, and the descent may never bottom out: [0,1] ⊃ [0,½] ⊃ … is
an infinite descending chain, so even propagation can Zeno on reals;
solvers stop below a precision ε); NOT SPENDABLE (Neq — cannot
enumerate, cannot usefully split), which forces the fourth flush policy,
already shipped unnamed: **RESIDUATE** — hand the unspent data to the
caller in the answer (`Constrained.of` — "x, provided x ≠ 3"). Flush ∈
{fail, die, force, residuate}.

Infinite rungs break TCLP by the gate, as predicted sight unseen: Neq's
infinite antichain (x≠1, x≠2, …) and real intervals doubly (infinite
antichains AND infinite descending chains — unboundedly many distinct
call regions, the ascent never repeats). The standard remedy is a
**widening operator** in the abstract-interpretation sense (Cousot): a
deliberate over-approximation that forces convergence by jumping coarser
— collapse Neq records above size k to ⊤; round real intervals outward
to an ε-grid. Precision traded for termination; soundness kept
(over-approximation costs missed pruning or missed reuse —
wrong-only-slow). TERMINOLOGY LANDMINE: this is NOT the goal taxonomy's
"widening" (branch-creating goals) — an unlucky collision with a
standard term; call the operator AI-widening wherever both could be
meant.

## 6. Adoption sketch (not scheduled)

The algebra lives ON THE VALUES, F-bounded (the `Comparable` pattern):

```java
public interface Lattice<L extends Lattice<L>> {
	L meet(L other);
	boolean isBottom();
	default boolean leq(L other) {          // entailment, free from the meet
		return meet(other).equals(this);
	}
}
```

- `Domain<T> implements Lattice<Domain<T>>` with existing operations
  (meet = intersect; isBottom = isEmpty); the adornment type implements it
  trivially. Adoption, not rewrite; the kernel does not change.
- **TCLP consequence: the `entails` store hook is SUBSUMED** — `project`
  returns a Lattice-implementing residue and the driver compares values
  directly (`mine.leq(other)`), written once. Custody is not violated:
  custody governs writes, comparison is read-only. What irreducibly stays
  store-side: `project` (only the store can create its residue) and
  `restate` as a hook with a note (FD could restate value-side through the
  public `dom` factory; other stores may need private context).
- The typing residue: the driver holds residues as class-keyed wildcards
  and Java cannot prove two wildcards share an L — ONE unchecked
  class-keyed cast in the generic fold, the same idiom `Package.getStore`
  already uses. The old store-mediated `entails` was a type witness
  dressed as an operation.
- The generic consumers that justify the interface: the TCLP pointwise
  fold and `SubsumptionMap<K extends Lattice<K>, V>` (exact-miss →
  nearest ⊑-larger key), shared by the adornment plan-memo and TCLP
  stage-2 region keys.
- Home: `functional` (pure algebra, sibling of the planned `Semiring`) —
  pending the human's call given functional's release-prep status.

## 7. Non-goals

- No engine merge — the three consumers share the algebra, not machinery
  (`fixpoint-machine.md` §4/§9/§10; the veto has now survived four
  temptations).
- No cross-domain semantic entailment (the product order's accepted
  incompleteness).
- No speculative operations on the interface: every method must have a
  shipping consumer at adoption time.
