# List relations should eventually be constraints, not recursion

- **status**: argued (August 2026, the human's direction after the Neq
  kill's scale pins)
- **evidence held**: measurement — `ExclusionStepPinsTest.scaledAllDifferent`:
  6-var all-different through pairwise nogoods costs 63,239 steps for 720
  answers, because n(n−1)/2 binary nogoods re-verify wholesale on every
  revise and tell the labelling nothing until each pair grounds. A
  constructed shape, not a workload that hurt — which is why this is a
  note and not a work order.
- **imports**: propagator, table constraint / row-set store (glossary);
  ⋯import: all-different filtering via matching (Régin 1994, the standard
  GAC algorithm for alldifferent) — receipt owed if that ambitious FD
  tier is ever built; the general core (duplicate-on-bind against a
  group) and the FD coupling (value elimination on bind) are folklore
  and need no receipt.
- **obligations**: none live — this waits on its trigger, not on thinking.
  When it fires: (1) the distinctness store, general core first (groups,
  duplicate-on-bind, ⊆ subsumption — see below), with `Logic.distincto`
  kept as the semantics reference (store answers ≡ relational answers,
  chaos-checked — the FD-relations pattern); (2) a paired step pin against
  the relational spelling, the kill-pins method; (3) the FD coupling only
  behind the cross-store doctrine's probe, as earliness; (4) lengtho only
  after distincto proves the seam.
- **links**: lattice-store.md (the instance catalogue this would extend),
  nogood-store.md §9 (the wholesale re-verify cost this routes around),
  ExclusionStepPinsTest (the measurement).

The relational list utilities (`appendo`, `membero`, `rembero`,
`distincto`, `sameLengtho`) are correct and compositional, but they pay
goal-application prices for what a store could propagate: `distincto`
posts a quadratic load of binary nogoods where one store item holding
the whole group could reject a duplicate the moment it binds — deciding
BEFORE labelling walks into dead branches instead of vetoing after. The
mapping onto the engine, by member of the family:

- **distincto** — the designated first slice, and NOT an FD citizen
  (the human's correction: this is set semantics applied to a list, and
  today's relational spelling already works over arbitrary terms —
  strings, tuples, unbound names; an FD seat would regress that). Its
  own small store: knowledge = distinctness GROUPS ("these n names are
  pairwise distinct"); revise = a binding member's walked value checked
  against the group's other ground members, duplicate fails the branch —
  O(1) per bind against a value set, versus re-verifying ~n²/4 pairwise
  nogoods, over anything with equality. Subsumption is free and exact:
  distinct-over-a-superset implies distinct-over-any-subset, so groups
  order by ⊆ and the store keeps an antichain of maximal groups — more
  LatticeStore-shaped than FD-shaped. FD value-elimination (on x bound
  to v, strike v from FD-resident siblings' domains) demotes to an
  OPTIONAL earliness coupling gated behind the cross-store doctrine
  (the probe as the one sanctioned bridge); the general win does not
  need it. What the general core deliberately does NOT buy: pigeonhole
  failure before labelling (3 names over 2 values) — that detection
  lives only on the FD side, matching-based GAC (the Régin import) its
  ambitious tier, only if a workload pays.
- **lengtho** — the interesting one, and second: it couples a partial
  STRUCTURE (an LList spine, possibly open-tailed) to an FD variable,
  propagating both ways — spine grounding tightens length bounds (known
  prefix = lower bound, closed tail = exact), length narrowing forces
  spine decisions (length ≡ 3 closes the tail at depth 3). Watches on
  spine variables; the knowledge is prefix-extension of a structure, not
  a lattice value on one name — likely its own small store or propagator
  family. The uniform store boundary exists so this can arrive without
  touching the kernel.
- **appendo / membero** — stay relational: their forking IS their
  semantics (membero generates), and a constraint version only pays in
  test position, which pricing already handles.

What this buys: eager narrowing where the relational spelling pays
branch-and-veto. What it does NOT buy: expressiveness — every member has
a correct relational spelling today, so this is purely a cost move, and
the relational versions remain the oracle the constraint versions are
tested against.

Cheapest kill: a profile showing distincto-shaped load is never the
bottleneck in any real workload — then the note stays parked forever and
nothing was built.

TRIGGER: a real workload (pldb phases included) where `distincto` or a
length-shaped constraint dominates a profile; or the vision workloads
adopting list-heavy shapes at sizes where the scale pin's regime is the
norm.
