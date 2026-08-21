# Constraint: one type for items, stores, factors, and crossings

**SUPERSEDED (August 2026) by the Factor/Theory/Atom migration as built
and the Constraint-pair ruling** — kept as the design that seeded them.
How its questions resolved: §1's one type landed as THEORY (which carries
exactly the proposed surface: watched via atoms, leq, meet, rename,
split) with ATOM as the item and FACTOR keeping the aggregate/execution
sense — §9's naming ruling went to the no-collision candidate; §2's
derivations shipped where predicted (stated = meet + normalize; revise
renamed into the normalize overload with its agreement law in
NormalizeAgreementLawsTest) except the activate door, which stayed and
went generic instead of folding into absorb; §3's fast-path doctrine and
§4's carves all held; `verdict` was never built (its consumer died with
the disjunctive store); "Constraint" now names the PACKAGE PAIR
{Theory, Factor} (constraint-pairs-theory-with-factor.md), not the one
type. Authoritative as-built: constraint-kernel.md.

DESIGN (August 2026, unratified). Born of the disjunctive-store
post-mortem arc: the profiler campaign, the fork-timing measurements,
the filter/insert discussion, and the bag-of-Stored closure. Proposes
a constraint-core refactor; nothing here is built. Read alongside
`docs/reference/constraint-kernel.md` (the shipped shape this would
replace) and `docs/notes/postings-are-the-store-language.md` (restate,
the registry, pushdown — this design's decomposition operation).

## 1. The claim

The constraint core currently speaks four types: `Stored` (an item),
`ConstraintStore` (the resident factor with its reactive protocol —
revise, stated, enforce, reify), `Absorbable` (the factor algebra —
leq, combine, normalize), and `Projectable` (the namespace crossing —
split, rename). The claim: these are one type at different
granularities, and the reactive protocol is derivable from the
algebra. A store is a constraint built by meeting smaller constraints;
an item is a constraint of one generator; the doors construct, restate
decomposes.

Two types — the algebra, and its atoms (the human's refinement,
August 2026):

```
Constraint                      — the algebraic citizen, possibly aggregate
  watched              — the variable surface: the wake index, the read surface
  leq                  — entailment: is this constraint implied by that one
  meet                 — combine two constraints of the same family
  normalize(Package)   — complete the meet against the package context
  rename               — rewrite names (namespace crossing, replay)
  split()              — decompose fully: Constraint → atoms

ATOM extends Constraint         — naming open, §9
  name        — the registry identity (marshal, compilation)
  payload     — the content (marshal, rendering)
```

`name` and `payload` sit on the atom, not the aggregate: a met-together
constraint has no one name and no one payload — its marshal form IS its
atoms, each through the registry. `split()` returns the atoms;
semantically a SET (meet's idempotence and commutativity make order and
duplicates irrelevant — that is the round-trip law's own content),
operationally an ordered collection for deterministic marshalling and
stable pins.

`split()` is the complete factorization: every constraint re-expresses
as the atoms that reconstruct it through the front doors — splitting to
atoms and re-posting them is restatement, the pipeline the postings
note names. The imposition law's idempotence clause is the round-trip
guarantee — re-absorbing a constraint's atoms re-normalizes to the same
constraint. Under this view "a store is a bag of items" stops being a
metaphor: the store IS the normalized meet of its atoms, and
item-versus-store is only how far the meeting has proceeded.

Both fast paths of §3 are OVERLOADS of core operations, not new names:
`normalize(Prefix, Package)` is delta-normalization (the old revise,
renamed so the agreement law lives in the signature), and
`split(names)` is the two-way cut (Projectable's split, kept). One
asymmetry, stated honestly: `split()` returns a collection of atoms,
`split(names)` returns two Constraint halves — same verb, different
granularity of the cut.

## 2. The derivation table

Everything the four current types can do, as theorems over the core.
Two of these are already shipped facts, not proposals.

| current protocol | derivation | status |
|---|---|---|
| `stated(item, p)` | meet + normalize | SHIPPED — absorb replaced stated (July 2026, #71) |
| `revise(prefix, p)` | `normalize(Prefix, Package)` — delta-normalization; `watched` says whose context moved | renamed into the overload; kept as a fast path (§3) |
| `enforce(x)` | split into search vocabulary, fork the atoms | FD labelling already has this shape (domain → value alternatives → fork) |
| `reify(...)` | rename into the answer namespace + render payload | reify is marshal-to-display |
| `split(names)` | split() ∘ partition ∘ meet | kept as a fast path (§3) |
| verification's trial | meet on a scratch copy, read the outcome | the scratch-copy check, unchanged |
| activate door | absorb of a one-generator constraint | door count drops by one: resolve / absorb / suspend remain |

The derivations are the SPECIFICATIONS. They are deliberately not all
the implementations — that is §3.

## 3. The two-layer shape: semantic core, lawful fast paths

The August benchmark campaign is the empirical argument for this
section. Every measured disaster was a semantic default running where
a fast path belonged: the disjunctive store implemented revise as
wholesale re-normalization — semantically correct — and that decision
was ~64% of its lane's steps (the rent). Every measured win was a fast
path with the right agreement property: FD is invisible in every
profile because its revise is watched and incremental; the staged fork
beat every resident lane because it deferred exactly as long as
deliberation could still progress.

So the type is two layers:

- **Semantic core** (mandatory): the eight members of §1. Small enough
  to state laws over; complete enough to derive everything.
- **Lawful fast paths** (optional overrides, each bound to an
  agreement law testable at the laws tier — the override's reference
  implementation is its own derivation):

| fast path | default | agreement law |
|---|---|---|
| `normalize(Prefix, Package)` | `normalize(Package)` wholesale | `normalize(prefix, S1) == normalize(S2)` where `S1 + prefix = S2` — the law IS the overload relationship |
| `split(names)` | `split()` ∘ partition ∘ meet | meet of the halves ≡ the original up to normalize; each half touches only its side's names |
| `verdict(constraint)` | meet on scratch (the trial) | agrees with the trial's outcome reading |

A family that overrides nothing is correct with the wholesale
economics — measured, not assumed. FD overrides the delta overload (map surgery on
Term-keyed factors makes split(names) cheap too); enumerable families
override verdict with a one-comparison leq answer. The differential
law is stronger than anything the current interfaces can state:
today "revise is complete" is a javadoc plea; here it is a test
of one overload against the other.

## 4. The carves

Three exclusions are load-bearing, not omissions:

1. **Substitutions stay outside.** Bindings are the blackboard the
   chokepoint alone may grow; `normalize` READS the substitution as
   context, no Constraint family CONTAINS it. This preserves the
   standing rejection of Substitutions-as-Store and the capability
   wall around the one mutation path.
2. **Transport and observation stores are not Constraints.** `Table`
   stays a plain inert Store (a reacting table starves later stores —
   the old ruling); Debug/Profiler/Optimizer stores are luggage. The
   Package becomes: Constraints, plus luggage.
3. **SemiringStore stays behind the capability wall.** No door touches
   it; that unreachability is what makes the weight machinery
   unconditionally correct. "Every constraint store" never includes it.

## 5. Obligations

- **Split totality per family.** FD propagators split to atoms because
  they rebuild by name (the named-schema contract, built for replay in
  #65 — this design is its second consumer). Every family owes the
  same: payload attributable to names, atoms reconstructible through
  the doors. Checked at the laws tier.
- **Custody attaches to `watched`.** The re-keying discipline (a
  watched representative merged under another name must re-key before
  any wake filter is read) does not simplify away; it moves from
  revise's contract to the `watched` member's.
- **The coupled-generator fine print.** A generator touching both
  covered and uncovered names goes to the remainder under split — the
  depth policy question the pushdown note owns (strict names /
  coupling closure / ∃-eliminate). Deriving split from restate
  inherits the question unchanged; the law states it once instead of
  every implementation restating it.
- **The laws kit becomes the gate for one type.** Core laws: leq sound
  (a false "already entailed" is the branch-killing direction), meet
  idempotent-commutative-associative, normalize a closure, split()
  round-trips up to normalize, rename capture-free. Plus the three
  agreement laws of §3. Eight laws, one type, every family attested.

## 6. Who consumes it

- **The deliberation prototype** (fork-timing arc): guard
  classification and one-shot deciding become `verdict` calls —
  the store-name string check dies.
- **Bulk nogoods** (#118, negation over finite goals): the sleep
  license for mechanically generated nogood sets — a family whose
  undecided constraints emit nothing may be watched-woken without
  loss — is readable off the type instead of asserted.
- **Weighted folds** (#88): the fold gate — which conditions
  evaluation can decide on the ground basis — is `verdict` at the
  answer boundary. The overlap arithmetic (meet AND difference to cut
  regions into disjoint pieces) needs MORE than this type: difference
  is deliberately absent — some families cannot subtract (the
  complement of a unification is not a unification; nogoods are how a
  family records a subtraction it cannot perform). Difference stays a
  per-family extra where representable, landing with its consumer.
- **Marshal, pushdown, persistence** (#122, #75): name + payload +
  restate ARE the registry surface; a pushdown adapter compiles the
  generators its dialect speaks and residualizes the rest — the
  never-dropped contract applies per generator. Replay stays
  rename ∘ absorb.
- **Tabling** (shipped TCLP): rename and split are the crossing;
  sharper family leq means better answer absorption and entry sharing
  with no new feature.
- **The optimizer's plan space** (condition.md §8.3's ruling: eager
  kernel ⟷ resident data ⟷ pure weight is a plan space and the
  optimizer decides): the placement decision reads this type's dials —
  verdict cost for selectivity, emission behavior for propagation
  value. Fourth in line by that section's own dependency chain; this
  design feeds it without front-running it.

## 7. The taxonomy: what fits

The informal completeness check — every constraint kind of general
logic programming, classified. Nothing falls outside unclassifiably:
every misfit is one of §4's carves or lives in the search plane.

- **Native fits (lattice-valued families):** finite domains,
  interval/bounds arithmetic, linear arithmetic (the CLP(R) solved
  form — meet = add-and-resolve, leq = the classic entailment test,
  split(names) = projection), set-interval domains,
  extensional/table constraints (the row-set store), regular/automata
  constraints (languages closed under intersection, inclusion
  decidable), graph constraints (edge-set lattices), pseudo-Boolean.
  Infinite-extension families split to INTENSIONS (their stated
  atoms), never value enumerations.
- **Filter fits:** disequality, nogoods, SAT clauses, learned clauses
  — recorded subtractions (below).
- **Indecomposable atoms:** global constraints (alldifferent,
  cumulative, circuit...) — split() returns self, and that is the
  point: decomposing a global (alldifferent into disequality pairs)
  preserves SOLUTIONS but destroys PROPAGATION STRENGTH. Addendum the
  remainder policy must carry: a transport split may be
  strength-lossy while solution-lossless — the MiniZinc pattern
  (named global where the target speaks it, weaker decomposition
  fallback where not).
- **Graded fits:** CHR-style user-defined families — meet = multiset
  union, normalize = rule application to fixpoint (CHR's operational
  semantics IS normalize), but no entailment test unless the rules
  encode one. Fits as a weak-leq family; participation graded
  exactly as condition.md §8.4 already grades factors.
- **Half-fits — the complement boundary:** reification (b ⇔ C: the
  b=false direction must impose ¬C), constructive negation, and
  piecewise weighted arithmetic (cutting overlapping regions into
  disjoint pieces needs difference). All three reach exactly as far
  as a family's difference does. The boundary, surfaced by the
  human's meet-and-difference challenge: some representations are
  closed under subtraction (enumerated domains, row sets), some are
  not (a substitution cannot say "anything but x=3"; an interval
  minus an interval leaves the family; the complement of a linear
  system is not a solved form). Difference is therefore ABSENT from
  the core — it cannot be total — and per-family where representation
  permits. **A nogood is a recorded subtraction**: the difference a
  family cannot take as a value, kept as an obligation and re-checked
  by the trial. That is why the nogood store is the universal
  fallback, and why it has the shape it has.
- **Carved out (§4's rulings):** Herbrand equality (the blackboard),
  soft/weighted constraints (the capability wall), coroutining
  primitives (suspensions are driver citizens).
- **Misfit by plane:** aggregates over answer sets (seal-layer, the
  closedness refusal), dynamic symmetry breaking and branch-and-bound
  objectives (search machinery — though a posted BOUND is an ordinary
  FD atom; the objective PROCESS is search, its knowledge is
  constraints).

## 8. Migration sketch

Staged; most convergences already happened: stores are single-sorted
and Term-keyed (#70), absorb replaced stated (#71), propagators are
named and rebuildable (#65), the doors are the only imposition API.
The plausible sequence — each stage green before the next:

1. `verdict` with the trial default (no type changes; the deliberation
   prototype and the laws kit get their surface).
2. `split()` per family with the round-trip law, returning the atom
   type — which forces the §9 naming ruling and the Posting
   relationship first (this is #122's restate pipeline's opening move).
3. The interface collapse: `Constraint` subsumes
   Stored/Absorbable/Projectable members; ConstraintStore's reactive
   methods become the §3 fast paths with their agreement laws; the
   activate door folds into absorb.
4. Doc pass: constraint-kernel.md rewritten against the one type.

Risk concentrates in stage 3 (the constraint core, all landmines
apply); stages 1–2 are additive and independently useful even if 3 is
never taken.

## 9. Vocabulary proposed for ratification

None of these is adopted until ratified: **Constraint** (the type,
replacing the four), **verdict** (the read-only store×item deciding
fast path), **agreement law** (an override's differential law against
its derivation). The renames shrink the list: `split` and `normalize`
are existing vocabulary extended by overloads; "revise" retires into
`normalize(Prefix, Package)`; "restate" retires into prose (split +
re-post = restatement — #122's pipeline keeps the word for the
pipeline, not the operation).

**The atom type's name is an open ruling.** The human proposes
**Factor**, noting the collision himself: the docs already use
"factor" for a store's resident entry in the package product ("swap
only their OWN factor", the bindings factor) — the AGGREGATE, exactly
what the atom is not. Renames retire, never alias, so taking Factor
for the atom would force a rename of the package-product usage.
Candidates on the table:

- **Factor** — the human's proposal; requires retiring/renaming the
  package-product sense;
- **Atom** — the meet-semilattice's own word for it (an element with
  nothing below it but bottom); no collision;
- **enriched Posting** — the adjacency that must be settled either
  way: #122 already says restate returns POSTINGS (the store language:
  restate, registry, pushdown, marshal), and the registry maps posting
  names to predicates. A Posting is the atom's ACTION face (impose
  through a door); the atom is the VALUE face (content with identity).
  Either the atom type carries a `post()` to the door and Posting
  stays the action, or Posting itself grows name/payload and IS the
  atom. Two types or one is a ruling, not a derivation.

"Filter/insert" served this arc as discussion vocabulary; this design
does NOT propose it — its content survived as verdict (the deciding
face) and the emission facts §6's optimizer entry reads, and the term
itself retires with the conversation unless separately adopted.
