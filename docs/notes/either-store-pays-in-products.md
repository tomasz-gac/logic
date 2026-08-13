# The either store pays only in products — and the 2021 scheduling question is its benchmark

- **status**: argued (August 2026, out of the human's challenge: "if we
  just hold postings, the list always gets applied anyway — this
  shouldn't be faster")
- **evidence held**: derivation, plus one external measurement — the
  human's own core.logic question (Stack Overflow 70288953, December
  2021): pairwise non-overlap over time strips, tractable in chains of
  thousands, blows up at TEN strips competing for one space; strips in
  DIFFERENT spaces cost the SUM of their separate solves, same-space
  strips the product. The accepted answer counts it: 55 pairwise calls,
  each a live 2-way conde, 2^55 worlds.
- **imports**: unit propagation (glossary-ratified August 2026, DPLL
  receipt); ⋯import: two watched literals (SAT's standard scheme for
  skipping quiescent clauses; Chaff's contribution, travels with the
  unit-propagation entry) — receipt owed if built.
- **obligations**: (1) the benchmark, buildable only after the store: a
  port of the scheduling question — strips as (start, duration, end,
  space), `stripo` via addo, non-overlap as ONE either item per pair —
  raced against today's conde spelling; success = the same-space n=10
  case tractable, cost tracking undecided clauses instead of forked
  worlds; (2) fold the verdict back here and into sealed-table-zip's
  streaming question.
- **links**: sealed-table-zip.md (the tabling consumer of the same
  store; its obligation (1) names the design pass this note's economics
  feed), nogood-store.md §6/§8 stage 5 (the sibling's spec),
  negation-over-finite-goals.md (the De Morgan dual).

## The concession first

For a SINGLE disjunction the store buys nothing: the optimizer already
runs postings before branching, so an eager fork duplicates no
deterministic work, and an either item whose alternatives survive to
labelling has re-applied the same postings later, with carrying cost in
between. Holding a posting list instead of a package per branch is not,
by itself, faster — the human's point, conceded.

## Where the buy is

Forks multiply EACH OTHER; posting lists add. k undecided two-way
disjunctions under conde materialize 2^k packages, every one a real
search branch that runs to its own failure even when a single constraint
was always going to kill it; k either items on one package hold 2k
postings — the same 2^k space held as a product DESCRIPTION, where every
refutation cuts a factor without enumerating what it multiplied into,
and every unit-imposed survivor adds knowledge that decides OTHER items.
Same worst case, wildly different typical case — the DPLL argument, and
the reason SAT is propagate-then-split rather than enumerate.

The 2021 measurement is this argument observed in the wild.
`non-overlappo` is a THREE-LITERAL CLAUSE wearing conde's clothes —
(sp₁ ≠ sp₂) ∨ (endₐ ≤ startᵦ) ∨ (endᵦ ≤ startₐ) — and every literal is
a Posting in today's vocabulary: one exclusion, two FD statements. The
observed scaling falls out: different-space pairs' clauses DISCHARGE
(the ≠ literal entails once spaces ground distinct) — that is why those
strips summed; same-space pairs' clauses narrow to the classic
disjunctive-scheduling pair (before ∨ after) and, under conde, fork —
that is why ten strips in one space blew up. The engine's own answer to
the question's "I thought more constraints HELP search?": a
conde-encoded disjunction is not a constraint, it is search — it never
narrows anything. The either store is the machinery that makes it a
constraint again: discharge on distinct spaces, unit-impose an ordering
when time windows refute the other direction, fork only the genuinely
independent orderings at labelling.

## The carrying-cost answer

Re-trialing every alternative on every revise is not the design. The
store cares about two transitions only — all refuted (fail) and one
left (impose) — which is what two watched literals buys: watch two
non-refuted alternatives per item, do nothing until a WATCHED one
refutes, only then scan for a replacement. Most revises touch nothing.
The same scheme is nogood-store §9's named mitigation on the negative
side.

## The Condition correspondence (banked, commits to nothing)

The human spotted it from the delivery side: a clause here is a
Condition wearing imposition clothes. Piece by piece — clause ↔
Condition, alternative ↔ region (an answer's delta; Residues and
Posting lists are the SAME content across the restate crossing, the
Projectable equivalence), discharge-on-entailed ↔ finality reaching 1,
alternative elimination ↔ a region dying, clause subsumption ↔ ⊕'s
absorption. The load-bearing difference is GROWTH DIRECTION: a
Condition in the cell grows (⊕ adds regions as answers arrive — a
weakening disjunction, anti-monotone, illegal as resident constraint
knowledge); a clause in the store only shrinks (alternatives die,
never join). They are one object on the two sides of the seal — a
Condition becomes admissible as a clause exactly when it stops
growing, and the zip is that conversion operator, the seal its
license. Second, smaller difference: Condition's absorption judges
regions against EACH OTHER (the internal order); the clause trial
judges alternatives against THE WORLD (entailed/refuted on live
state) — the same three-way classification lattice-three-way.md
banked from its third witness. Per that note's discipline, this
correspondence is an observation, not a refactor: any unification is
step 8 on structures that are not closed (the store has no code, the
trial's laws (#116) are unwritten), so it waits behind the same gate.

## The kill

If real workloads — the zip consumptions, the pldb joins, the
scheduling shapes — never present products where constraints decide
most alternatives before labelling, the store never earns its carrying
cost and stays a design. The benchmark exists to answer exactly this,
against the one workload already known to have hurt.
