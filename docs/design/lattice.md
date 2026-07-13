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
| Table entries | answer-set ⊆ | GROWS | completion = the ascent's fixpoint; completed entry = exact count; the answer set SHIPPED as `JoinSet` (tabling/primitives) — the growing half's first native instance, join-idempotence = the dedup discipline, gate-checked |
| Adornments | pointwise bound/free (Boolean lattice per arity) | static | the optimizer's plan-memo key space; subsumption lookup = "reuse the plan of a less-bound pattern" (sound: wrong-only-slow) |
| Residues (TCLP) | per-store ⊑ | shrink | = `Domain` for FD, by construction (tabled-constraints.md §5.2) |
| `FiniteDomainConstraints` | pointwise domain meet × propagator-set ∩ | SHRINKS | SHIPPED: the store as a product order, canonical ⊥; the cascade's termination measure |
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

## 3a. The operators — the kernel's functions have literature names

The instances are the nouns; the kernel's moving parts are the verbs, and
they are all one kind of verb: functions over lattices constrained by the
order. The vocabulary (each name buys its theorem):

- **monotone**: `x ⊑ y ⇒ f(x) ⊑ f(y)` — the minimum for any of this to work.
- **deflationary** (reductive): `f(x) ⊑ x` — only shrinks. Mirror:
  **inflationary** (extensive), `f(x) ⊒ x` — only grows.
- **closure operator**: monotone + inflationary + idempotent. "Propagate to
  quiescence" IS applying one; AC-3 computes one. The lattice cousin of the
  Kleene star (§5a): `fix(f)` is `f*`, iterate-until-stable.
- **chaotic iteration** (Cousot & Cousot): a finite family of monotone
  deflationary functions applied in any fair order reaches the same greatest
  fixpoint. This is the "agenda order can't change answers" freedom of §3,
  with a citation — and it is exactly what `MonotoneDrain` (functional's
  `algebra`) mechanizes: its two per-step checks are "deflationary" and
  "strict-descent-or-stop" enforced pointwise.
- **ask/tell** (Saraswat's concurrent constraint programming): agents over a
  store lattice built from two primitives — tell (add information: our
  `Prefix`/`resolve`) and ask (block until the store entails a condition:
  our ripeness). CCP's semantics: agents denote closure operators; asks must
  be upward-closed or scheduling leaks into meaning. A finitely-expressible
  ask is a principal filter — "everything ⊒ this threshold" — which is why
  suspension ripeness wants a threshold vocabulary, not a predicate.
  Modern kin: LVars (monotone writes, threshold reads — deterministic
  parallelism because monotone) and Radul–Sussman propagator networks.

The kernel's types, mapped:

| kernel | operator |
|---|---|
| `Propagator` body's own-factor action | deflationary monotone endofunction on the store; the cascade composes a family to their common fixpoint — the closure |
| `Revision` (own-factor swap, descending) | one application, observed at the store boundary; `fail` = the map to ⊥ |
| `Suspension` | an ask: an upward-closed condition plus the agent released when the store enters it |
| `MonotoneDrain` | the chaotic-iteration engine, generic over `MeetSemilattice & Bottomed` |
| propagate-to-quiescence | the closure operator |

The caveat that keeps this honest: `Propagator` is not literally `L → L` —
the algebraic core rides inside a protocol carrying effects (inferred
prefixes, runs: a writer-monoid on the side) and lifecycle (`keep`,
`subsumed`). The operator view captures the termination-relevant
projection, which is the projection the theorems are about; custody is the
protocol's business (constraint-kernel.md §3).

**The placement rule** (where algebra goes, decided by what a thing is):

- **Data becomes algebraic.** Knowledge carriers — domains, record sets,
  domain maps, answer sets — implement the interface: they ARE lattice
  elements, their equality is knowledge equality, their laws are checked by
  the gate. Test: entailment between two values means something (TCLP could
  compare them).
- **Control gets its ARGUMENTS parameterized, for free theorems.** Loops,
  parked continuations, wake indexes are not lattice elements — a set of
  closures is only trivially a set, and dressing bookkeeping as algebra is
  fabrication (the `Revision.combine` trap). Instead their argument types
  carry the bounds: `MonotoneDrain`'s `S extends MeetSemilattice & Bottomed`
  buys termination; ripeness-as-threshold buys fire-once soundness and
  scheduler independence; `Reductor` bounds a body. The theorem arrives
  through the parameter, not through an `implements` on the control
  structure.

- **Free syntax gets its laws checked against a supplied equivalence.** A
  third case, neither data nor control: goal trees are free algebra terms
  whose laws hold not on the syntax (no decidable equality) but on its
  denotation — the quotient by observational equivalence (same answers, all
  states). Annotating the syntax with equality-based laws is impossible;
  bounding arguments misses the structure. The correct instrument is
  Eq-parameterized law kits: check the axioms against the quotient's
  equality, sampled by solving. Consumers act by HOMOMORPHISM — the pricing
  pass is a semiring homomorphism from goal trees to `SATURATING`, which is
  why law-checking the count model was ever evidence about goal reordering:
  homomorphisms out of free syntax transport the laws. Note the ⊕ fragment
  is a join-semilattice only in the idempotent quotient (set-of-answers) —
  which is what tabling's duplicate check imposes; under multiset semantics
  (the counting semiring, the untabled engine) ⊕ is only a commutative
  monoid. "Are goals a join-lattice" and "which semiring runs the search"
  are the same question.

One test distinguishes the cases: ask what `equals` should mean. If two
values with the same content must be the same knowledge (Neq's record sets),
it is data — annotate it. If equality is identity of parked behavior
(suspensions), it is control — bound its arguments. If equality is
observational, it is free syntax — quotient, Eq-parameterized laws,
homomorphic consumers.

Deferred, with triggers:
- **`Reductor<L>`** (`f(x) ⊑ x`, monotone) plus a sample-chain law kit, so a
  new propagator body can be law-tested at the desk before wiring into a
  store — the mulIntervals sign-guard bug was a monotonicity violation, and
  this is the cheap net for the next one. Trigger: the next propagator or
  store author.
- **Algebraic ripeness**: replace `Suspension`'s `Predicate<Substitutions>`
  with the threshold vocabulary (per-term instantiation degrees, positive
  combinations only) — monotonicity and NAF-exclusion by construction, the
  watched/condition coupling collapses into one value. Trigger: the second
  ripeness author (freeze, width-gated labelling, or the aggregate
  completion gate — the last has a live soundness caveat attached).

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

**The junction, generalized: the quotient tower (July 2026).** The junction
is one rung of a ladder, and the ladder is the theory that connects the two
algebras (Tom's synthesis; the literature had already built it in pieces).
The program denotes a value in a FREE semiring — the space of derivations
(provenance polynomials, Green–Tannen); the substitution is the accumulated
⊗-residue along a derivation, which is why it is native to search. Every
other execution artifact is a homomorphic image of that free object, and
the images form a tower of quotients:

    free (derivations; search's native object; everything representable, nothing cheap)
      → counting        (forget WHICH derivations — pricing's model)
      → set / Boolean   (add IDEMPOTENCE — ⊕ becomes a join; semilattices
                         appear; tabling's dedup IS this quotient map)
      → per-variable    (forget CORRELATIONS — Galois abstraction; domains,
                         stores; propagation = chaotic iteration here)
      → saturating counts, masks, adornments (further compressions)

Three theorem shapes ride the tower:

- **Homomorphisms transport laws** (free-object property) — why checking
  the SATURATING model was ever evidence about goal reordering, and why the
  optimizer's rearrangements are sound in every model at once.
- **Each law added at a quotient enlarges BOTH freedoms.** Idempotence
  completes ACI — the license for unordered, redundant work — cashed
  independently as chaotic iteration (constraints), CRDTs (distribution),
  LVars (deterministic parallelism), CALM (coordination-freeness), and
  parallel-worlds-joined excursions (§5c). The sharp contrast: parallel
  COUNTING needs exactly-once delivery — real coordination — because it
  sits above the idempotent quotient; parallel EXISTENCE does not.
  Absorption/superiority (Sobrinho) buys greedy commitment (Dijkstra) and
  finite convergence over cycles. Compression ⇒ parallelism is not a
  coincidence of implementations; it is the same law read as a schedule.
- **Execution mode = choosing the quotient to compute the fixpoint in.**
  Search computes in the free object (complete, expensive); propagation
  and excursions compute in a quotient (cheap, incomplete — the cartesian
  abstraction loses correlations); the solving loop alternates
  (propagate-split; solvers exist built on exactly this identification —
  the AbSolute line, constraint solving AS abstract interpretation). The
  fragments where the quotient is LOSSLESS are the fragments that
  industrialized: Horn (unit propagation complete), Datalog (bottom-up
  complete). There is no primacy of search over propagation — only the
  free object's completeness against the quotient's cost; "search stays
  primary" below means primary as the completeness backstop, not as the
  preferred mode.

The finiteness gate splits the tower's citizens by WHO CERTIFIES THEIR OWN
FIXPOINT. A store descends a finite-height lattice: quiescence is
self-certifying — the step that does not move says so (the equal-domain
guard is a local no-change witness). A table ascends a lattice of
unbounded height (unbounded recursion is tabling's point): arrival of the
fixpoint is not observable in the value — it is semi-decidable, and the
only certificate is producer exhaustion, i.e. a termination-detection
protocol (table-completion.md's counters), not a value check. That is why
completion detection is where tabling hurts: it is the runtime admission
test for the finiteness gate. When the certificate arrives, the entry
DESCENDS THE TOWER AT RUNTIME — a growing set becomes a finite constant, a
store-grade finite-semilattice citizen — and every store privilege
switches on at once: exact price (∞→const), reorderability, negation
soundness, reclamation. TCLP's antichain condition is the same gate
imposed on residues. Tabling does not fail to unify with constraints; it
unifies CONDITIONALLY, and the condition has a certificate.

**The license table — how semiring laws project onto compressions.** A
compression FORGETS something; it is safe exactly when the target semiring
could not have depended on what was forgotten. Each compression is
licensed by one law; a target may use it iff the target satisfies that law:

| law | licenses | who has it |
|---|---|---|
| `0⊗x = 0` | refutation pruning: kill a branch with NO derivations | EVERY semiring — "impossible" is the one judgment all levels agree on |
| `x⊕x = x` | dedup, merge-as-answer: tabling's contains-check, a join of worlds read as a result | Boolean, min-plus (min is idempotent) — NOT counting |
| `x⊗x = x` | free re-application: wake a constraint arbitrarily often, twice = once | Boolean (∧) — NOT weighted (+ double-charges) |
| `a⊕(a⊗b) = a` | dominance pruning, commitment: branch-and-bound, Dijkstra's pop, condu | selective semirings only |
| commutativity/assoc. | reordering, parallel merging: the optimizer's sort | all our targets — one optimizer serves every future plug |

**Exports vs iteration — where the Boolean assumption actually lives.**
Constraints are NOT Boolean-only. What a store exports through the
chokepoint is `fail` ("zero derivations here") and inferred prefixes
("every derivation here shares this binding") — the first is 0-licensed,
the second creates and destroys no derivations; both port to every
semiring. Domains as PRUNING devices are universally sound (pruning only
removes zero-derivation values, and zero is absolute) — which is why
"stores prune, search enumerates" is the semiring-generic architecture:
multiplicities and weights always come from the free-level residue (the
substitution); stores only ever say "not here". The Boolean quotient is
load-bearing in two other places:

- **The store's ITERATION DISCIPLINE.** The cascade re-examines
  propagators freely, no application bookkeeping — paid for by
  ⊗-idempotence (applying ∧ twice is a no-op). A WEIGHTED store loses
  that luxury: re-propagation double-charges costs, which is why the
  soft-constraint literature exists (semiring-CSP, Bistarelli–Montanari–
  Rossi; the soft-arc-consistency repairs, EDAC). The store's CONCLUSIONS
  port everywhere; its cheap fixpoint style is a set-level luxury.
- **Tabling's dedup.** `addAnswer`'s contains-check is ⊕-idempotence
  baked in — right for existence and min-plus, silently wrong for
  counting. Semiring tabling's opening move is un-baking exactly that
  line into Map<Answer, V> with ⊕ at arrival.

The audit, per component: unification/substitutions — free level, always
sound. Optimizer reorderings — commutative level, sound for every target
(one optimizer, all plugs). FD/Neq as refuters, dead-post pricing at 0 —
universal. Pricing — saturating counting as an UPPER BOUND, advisory,
sound by direction. Tabling dedup, aggregate folds, a
constructive-disjunction join read as an answer — idempotent levels.
conda/condu — superiority ASSUMED without checking: committed choice over
a weighted search is Dijkstra without the non-negative-edges
precondition (already branded "not a pure semiring element" in §5a).

**The plug taxonomy — which semirings may compress to what.** The license
table pivoted from laws to plugs. The generating rule: answer-path
compressions available to target T are exactly the quotients BETWEEN the
free object and T in the congruence order (merging by L is safe iff
everything L merges, T merges too — coarser question, longer interval,
bigger repertoire). Refutation-path abstractions are exempt from the rule:
they export only zero-judgments, so they serve EVERY target — but their
internal discipline needs a ⊗-idempotent internal algebra, so a store is
not "in" the plug's semiring at all; it is a foreign distributive lattice
with a universal-judgment interface. That is the deep reason the
constraint core survives every semiring conversation unscathed.

| plug | class | reorder | refute-prune | dedup / tabling | greedy commit | native store discipline | self-cert. fixpoint |
|---|---|---|---|---|---|---|---|
| derivation forests | free commutative | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ |
| counting ℕ | no ⊕-idempotence | ✓ | ✓ | ✗ — weighted cells + star on cycles | ✗ | ✗ | ✗ |
| probability | no ⊕-idempotence | ✓ | ✓ | ✗ (same) | ✗ | ✗ | ✗ |
| min-plus | selective dioid, superior | ✓ | ✓ | ✓ (min per key) | ✓ — Dijkstra legal | ✗ (+ double-charges → soft-AC) | bounded chains only |
| max-min / fuzzy | distributive lattice | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ on finite chains |
| Boolean | 2-point distributive lattice | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

Column readings: reorder and refute are universal — why ONE optimizer and
ONE constraint core serve every plug. Dedup switches on at ⊕-idempotence;
commitment at superiority; NATIVE constraint iteration at ⊗-idempotence —
exactly the distributive-lattice rung, and that is a theorem, not an
observation: c-semirings with idempotent ⊗ collapse to distributive
lattices (the Bistarelli–Montanari–Rossi line). Constraint-native
semirings ARE lattices.

Not future work: the witness API already carries the taxonomy —
`Semiring<S>` declares `isIdempotentPlus`/`isClosed`/`isSuperior`, and the
law kits VERIFY the declarations. When `solve(out, semiring)` arrives
(semiring-inference.md), the engine derives its feature gates from the
plug's checked predicates: contains-check vs weighted cell in addAnswer,
condu legal vs rejected loudly, dedup on or off — every gate justified by
a law, not a comment. This table is the compatibility matrix
semiring-inference.md was missing, and the DUAL of the capability ladder
(§4): the ladder classifies stores by what they offer upward; this
classifies plugs by what they may consume downward. Two tables, one
interface between them.

Modern umbrella for the fixpoint side: Datalog over (pre-)semirings — POPS
(Abo Khamis–Ngo–Pichler–Suciu–Wang, PODS 2022): recursion over a semiring
whose ⊕ induces the order, convergence characterized by exactly these
properties (idempotence, absorption, finite height). Acceleration:
Newtonian program analysis (Esparza–Kiefer–Luttenberger). Consequence
already cashed: #35's Eq parameter is a CHOICE OF QUOTIENT — multiset
equality checks the counting level (disj(g,g) ≢ g there), set equality
checks the idempotent level (it holds there, and tabling lives there) —
so the kit takes the quotient explicitly and varies the law set with it.

**The mode framing (July 2026).** Search and optimization are the engine's
primary modes; constraints, tabling and aggregation are three converters
performing one move — reify search into data where operations are cheaper.
Reified data is not inert: propagation and excursions EXECUTE it in its
quotient (the tower above — abstract execution). A converter changes WHERE
execution happens, not whether.
Constraints convert PROSPECTIVELY (compress branching into a lattice value
before it spawns; meet kills branches unborn), tabling RETROSPECTIVELY
(freeze a completed sub-search into an answer table; lookup replaces
re-search), aggregation WHOLESALE (reflect a sub-search into one value).
Three consequences that were separate facts:

- **Barriers are conversion boundaries.** A tabled call prices ∞ because it
  is MID-CONVERSION — neither searchable nor readable; the immovability
  transition (∞ → exact at completion) is the conversion completing. The
  accounting identity at the other boundary: order(forcing) = width = split
  arity — the semiring's count and the lattice's measure are one number
  read from opposite sides of a data→search conversion.
- **TCLP is a coherence requirement, not a feature.** If converters compose
  with search, `table(constrain(g))` must work; today it is the hole —
  tabling a constrained goal loses the residue. TCLP is the closure of the
  conversion move under composition (answers = (tuple, residue) pairs, data
  paired with data); the antichain gate is the demand that composed data
  stay finitely latticed.
- **Conversion is partial, so search stays primary.** Infinite relations do
  not complete, unbounded variables do not domainify; flounders and
  permanent barriers are where conversion fails and search carries it. The
  claim is never "data replaces search" — it is that the optimizer's job is
  CHOOSING THE MODE, and the converters are what give it a choice.

**The triad.** Search executes in the free object; propagation and
excursions execute residues in their quotients — execution happens at
every level of the tower, and what distinguishes search is COMPLETENESS,
not execution: it is the only executor that produces ANSWERS, while
quotient execution produces judgments about them (refutations,
tightenings), never answers themselves. Everything else exists to make
the free-level run do less. Optimization speculates — it
never runs the program, it runs cheap previews of it (a price is a preview
by counting; the {0,1} probe is a preview by rehearsal; a shadow, if ever
built, is a bigger rehearsal) and reorders the real run by what they say.
Converters persist the speculation — and that is what data IS:

| the thing | what it actually is |
|---|---|
| a price | a speculation too cheap to keep — computed, used for one sort, gone |
| a domain | a persisted speculation — the shadow of an enumeration nobody ran |
| a completed table | a speculation run to certainty, frozen |

Not three kinds of thing: one thing — knowledge about a search that has not
happened (or will not happen again) — at three durabilities. The framing
pays for itself by explaining three design facts with one principle: prices,
domains and tables all live in lattices because a persisted preview is only
usable if it stays true while the real run proceeds — it must move one-way
or it lies (the direction principle, §5); a barrier is speculation IN
PROGRESS — a mid-fill table is a preview that cannot be read yet, so it
prices ∞ and holds position; and the optimizer is entitled to read stores
(the `answers(Package)` widening) because store data is congealed
speculation — a domain's width is the preview of an enumeration, exactly
the optimizer's diet. At a disjunction the optimizer's choices are then
three verbs, all priceable in its own arithmetic: SEARCH IT (fork, the
tail's order multiplies by the arity), IT IS DATA (a dom — a meet), or
SHADOW IT (§5c: excursion the disjuncts, post their join now, defer the
fork to the cheap end — declared like every fold, never inferred). The
boundary that keeps this honest: "optimization speculates" names the
architecture, not the current depth — today's abstract preview is counts
with ∞, the rehearsal previews are one tiny probe; the triad says where
each future instrument belongs, not that it is built.

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

## 5c. Speculation — computing ahead of certainty

Two distinct speculations, one lattice discipline; both are "reading
mid-conversion" in §5a's terms.

**Speculating on INCOMPLETE data** (the join-side systems: Bellman-Ford's
label-correcting, semi-naive deltas, CRDTs, LVars; CALM: coordination-free
⟺ monotone). If all updates are monotone, intermediate states are never
wrong, only incomplete — so compute on them: safe reads are UPWARD-CLOSED
questions (threshold reads — our ripeness, the ground-cache, a tabling
consumer's answer index, a stale FD width as a price: all shipped);
self-correcting work publishes provisional values and improves them
monotonically. We run the safe-read half everywhere and none of the
self-correcting half — an answer emitted is final, an incomplete table is a
barrier. Failure modes, exactly four: non-monotone reads of provisional
data (negation, count-before-complete — Aggregate's caveat); premature
commitment (Dijkstra's label-setting is legal only under superiority —
Sobrinho's `a ⊕ (a⊗b) = a`; one negative edge and it silently lies while
label-correcting still converges); deletion (monotone systems can't take
back — DRed/counting, tombstones); non-idempotent ⊕ (at-least-once safety
dies — counting double-counts what a join would absorb). The
self-correcting half becomes available exactly in semiring tabling: min-plus
cells could publish provisional bounds, sound as prices before completion —
completion needed for final exactness, not for safety.

**Speculating on HYPOTHESES** (excursions: shaving / singleton arc
consistency / probing; constructive disjunction). Joins combine
ALTERNATIVES where meets combine certainties — run join-propagation within
one world and chaotic iteration terminates at ⊤, vacuous; joins need worlds
to range over. So manufacture them: hypothesize `x=v`, propagate a scratch
package. What may cross back is derivable: a ⊥ excursion exports a certain
refutation (meet `x≠v` into the real store); a surviving excursion exports
NOTHING (propagation is incomplete — "didn't fail" is not "consistent");
a COMPLETE alternative set exports the JOIN of its surviving worlds — the
weakest common consequence, constructive disjunction's harvest. Failure
modes: exporting a survival; joining over a non-covering set; hull widening
(sound, lossy — exact `Union` spares us where interval solvers pay);
unbudgeted cost (shaving is width × propagation); and the sandbox boundary
IS the data/search boundary — an excursion that would fire a suspension has
left the pure fragment and must stop. Shipped instances of the refutation
half, unnamed until now: Disequality's trial unification (why raw `unify`
is legal there and nowhere else) and `UnifyGoal`'s {0,1} pricing probe.
Persistent packages make sandboxes free — no trail, no undo, drop the
scratch world. The join half is a fourth converter — a disjunction's data
shadow extracted without forking — and `Domain implements JoinSemilattice`
(join = union, exact) is its one-law-test prerequisite, gated on
constructive disjunction being wanted.

**Excursions, plainly, and where they fit.** The idea in one line: make a
guess, try it in a throwaway copy, and the only things allowed out are
failures (a guess that dies is dead for real) and unanimity (what every
alternative's world agrees on is true without choosing). The niche is
exact: STRONGER THAN PROPAGATION, CHEAPER THAN BRANCHING — the move for
when propagation plateaus but forking is expensive. The industrial record
says the niche is real: SAT's failed-literal probing (every serious
preprocessor), MIP's probing and strong branching (the heuristic flavor —
excursions scored to pick the branch variable, no soundness export at all,
and a pillar of why modern MIP works), job-shop shaving (Carlier–Pinson
time-window tightening), Sudoku's Nishio. Relation to DP, since the two
keep being confused: both are ONE move — enumerate alternatives, evaluate
each in its own world, fold the outcomes with an operator — at different
throttles. Full depth + memoize the fold as THE ANSWER = DP (the
100%-converted endpoint of the spectrum). One ply + discard, fold kept only
as a tightening = excursion. Ordinary search sits between. They merge in
semiring tabling's label-correcting future: a cell publishing provisional
bounds is DP becoming readable mid-flight, exactly as an excursion's join
is. Where it fits HERE: the refutation micro-instances are shipped (trial
unification, the pricing probe); the optimizer's use is the SHADOW verb
(§5a triad — excursion a disjunction now, defer its fork to the cheap
end); the full mechanism (shaving, constructive disjunction) is gated on a
workload that needs it — our FD problems are small and propagation
suffices, so the door is documented, not built.

## 6. Adoption — as it actually happened (lineage)

The original sketch here proposed one logic-side `Lattice<L>` adopted "when
a customer exists". Reality (July 2026) went further and differently: the
algebra lives in `functional.algebra` as a HIERARCHY (`MeetSemilattice`,
`JoinSemilattice`, `Lattice`, `Bottomed`, the monoid/semiring families with
capability subtypes), policed by the law-kit + coverage-gate architecture,
and adoption ran ahead of consumers under the completeness principle — the
gate, not a caller, is the honesty check. Shipped instances: `Domain`,
`FiniteDomainConstraints`, `NeqConstraints`, `JoinSet`, the witnesses. The
sketch's TCLP consequence (entails subsumed by `leq`) and `SubsumptionMap`
remain live consumers-to-be, unchanged in design.

## 7. Non-goals

- No engine merge — the three consumers share the algebra, not machinery
  (`fixpoint-machine.md` §4/§9/§10; the veto has now survived four
  temptations).
- No cross-domain semantic entailment (the product order's accepted
  incompleteness).
- No speculative operations on the interface: every method must have a
  shipping consumer at adoption time.
