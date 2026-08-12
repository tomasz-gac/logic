# A defer-free tier makes finiteness structural — and negation total on it

- **status**: argued — derived in conversation (August 2026), no code
- **evidence held**: derivation, plus two receipts from the posting
  arc — the trial legally sees >1 worlds (`TrialEdgeTest`: a woken
  suspension body forks; so ≤1 was never a trial invariant, only a
  posting one) and double negation decides at the ground floor once
  empty stores read as no-knowledge (same file)
- **imports**: none new; De Morgan / clausal normalization in plain
  words
- **obligations**: (1) the cheapest kill — a spike typing ONE
  combinator (`any` as posting) and negating through it; requires the
  either store, already §6's next stage; (2) the three-tier
  practicality question — covariant combinator overloads, wrapper
  preservation (`named`, `Bounded`) at the middle tier — prototype
  before committing; (3) the naming family (below); (4) the standing
  unease: should trial impositions splice woken suspension bodies at
  all (the human's reservation, August 2026 — left as-is, receipted,
  revisit with the suspensions-as-factors line)
- **links**: nogood-store.md §2 (the built conjunctive tier) and §6
  (either — `any`-as-posting's home), negation-over-finite-goals.md
  (tabling reframed as finitization evidence), sealed-table-zip.md
  (the any-of-alls carrier and its seal asymmetry),
  condition.md §8.3 (the imposition spectrum `any` lands on),
  docs/reference/optimizer.md (the order algebra the tier makes exact)

## The claim

The posting vocabulary was built to exclude four dangers at once:
package access outside the chokepoint, non-monotone success, forking,
and non-termination. The last has a purely STRUCTURAL characterization
the others lack: in the combinator algebra, `Goal.defer` is the only
infinity door (raw lambdas are the other, and they are excluded the
same way they are today — membership is by constructed value, and
implementing the interface claims the law). A defer-free tree of
posting leaves under ∧/∨ is finite data with a finite search tree.
So a typed hierarchy is coherent:

    Goal            anything: lambdas, defer, tabled calls — no promises
      ⊃ finite tier  trees of ∧/∨/leaves; no defer, no raw lambdas
          ⊃ Posting     the conjunctive ≤1 slice: leaves, all, named

Two properties fall out. EXACT ORDERS: the optimizer's product-over-∧,
sum-over-∨ algebra stops estimating and computes — the finite tier IS
`Bounded` with exact bounds, generalizing the 0-or-1 taxonomy.
CLOSED UNDER NEGATION: ¬(leaf) is a nogood, ¬(all) is one nogood,
¬(any(a,b)) = all(¬a,¬b), and a disjunctive literal inside a
conjunction distributes to clausal nogoods — De Morgan never leaves
the fragment. `not(g)` becomes TOTAL AND STRUCTURAL on the tier, with
no tabling; tabling-to-seal is reframed as the finitization EVIDENCE by
which a recursive `Goal` earns entry (its sealed answer set is a finite
tree). The original "no general negation" prohibition was about
arbitrary goals, not finite forking — forking preserves chokepoint-only,
monotonicity and finiteness, and the trial's conservative >1 reading
already tolerates it (now receipted).

## The fork in the road: what `any` means

- **`any` as fork** — a disjunctive goal that branches. Needs the
  middle tier as a real type (order n, covariant overloads, wrapper
  preservation). Heavier; no named consumer yet.
- **`any` as posting** — no fork: post a resident disjunctive record,
  "one of these postings holds", unit-propagating as alternatives
  die. This IS the either store of nogood-store §6, and under it `any`
  stays a `Posting` (posting once, order 1) — the
  imposition-spectrum doctrine landing in the API, and the recommended
  first cut. The middle tier waits for a consumer that needs typed
  finite FORKING (labelling fragments are the candidate).

## What the type does NOT buy

The tier promises the goal ADDS no unbounded search; an imposition can
still wake resident suspension bodies whose termination is the
package's business. Monotonicity stays a law, not a type — no
hierarchy sees it. And the trial's >1-worlds conservatism stays
regardless of typing, because the wake cascade may fork legally.

## Tabling is `Goal → FiniteGoal`

The human's observation (August 2026): if the tier exists, tabling has
a type — it converts an arbitrary, possibly recursive `Goal` into a
finite one by driving the table to its seal (partial: total exactly on
goals whose tables complete; a never-sealing table is the function
diverging, and the strand refusal is its error value). Negation then
composes as types: `not : FiniteGoal → FiniteGoal`, so negation of a
recursive goal is `not ∘ table` — the whole of
negation-over-finite-goals.md as one composition.

And the function's output has a CARRIER (the human's zip, August
2026): a sealed table folds into one either-record —
any(all(answer₁'s literals), all(answer₂'s literals), …) — a
FiniteGoal VALUE built from the posting vocabulary, consumed in one
branch by the either store instead of forking per answer. `not` is De
Morgan on that tree, closed in the fragment. See sealed-table-zip.md
for the seal asymmetry (the zip cannot stream; the negation can) and
the fork-vs-data trade.

## The naming family — RESOLVED for the conjunctive slice

The type is **Posting** (the human's ratification, August 2026):
applying one is KNOWLEDGE INJECTION through the chokepoint — a
Resolution posts bindings, an Activation posts an item, an Absorption
posts a factor. The name returns to the ring it started in, now naming
the whole lifted vocabulary; *Statement* and *Literal* retire as type
names, "literal" stays the role word. Still open, with the tier: names
for the tier itself, `any`, and the either record — one session when
they build.
