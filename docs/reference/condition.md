# The constraint ring — answers as semiring values

**STATUS: AS BUILT (August 2026, branch `tabling-residues-fix`; designed with
the human in one arc from a delivery bug to the algebra). The code:
`tabling/Residues.java`, `tabling/Condition.java`, `tabling/JoinMap.java`,
and `Tabling.consume`/`deliver`/`deliverSealed`. Prerequisites: `lattice.md`
(the engine's one algebra — this doc adds three instances to its inventory),
`constraint-kernel.md` (the store boundary the factors come from).
`tabled-constraints.md` remains the TCLP design history and pricing; its
answer-side mechanics are superseded by this doc.**

---

## 0. Orientation: one object, its homes, its crossings, its admissions

Everything in this doc is organized by one sentence: **there is one object
— a region of constraint knowledge — and everything built here is either
a HOME for it, a CROSSING between homes, or an ADMISSION of a new kind of
thing into it.** A region's algebra has two operations: ∧ (conjoin —
narrower) and ∨ (alternatives — wider). The rest is bookkeeping about
where a region lives while the search runs.

Homes for ∧ (one world's knowledge):

| home | who evaluates | character |
|---|---|---|
| the stores | the kernel, eagerly — revise, propagate, prune | knowledge WORKS here; branch-local, live names |
| `Residues` (a carried conjunct) | nobody — inert | knowledge TRAVELS and COMPARES here; canonical names |

Homes for ∨ (alternatives):

| home | who evaluates | character |
|---|---|---|
| the search (fork) | the scheduler — a branch per disjunct | eager-est; pays a subtree per disjunct |
| the disjunctive store (§8.3, designed) | the kernel — prune/unit/lift | ∨ RESIDENT in the ∧-world, propagating without forking |
| `Condition` (⊕ of conjuncts) | nobody — inert | ∨ carried as a value; where the CELL keeps it |

Why `Condition` is not redundant with the stores: the stores were never
the whole algebra — they are the ∧-HALF (within one branch, knowledge
only conjoins; a domain compresses ∨ over one variable's values, but ∨
BETWEEN constraint conjunctions belonged to the search). `Condition` was
forced by the CELL: tabling is where different derivations' knowledge
must be summed, and that ⊕ needed a lawful carrier the ∧-only,
live-named, branch-local stores could not be. Stores = the ⊗ fragment,
Condition = the ⊕ fragment — halves of one lattice, never rivals (§4).

The CROSSINGS — `project`/`normalize`/`restate` (§3) — are moves between
homes of the same region, lossless on the roundtrip. Nothing is ever
reinterpreted: the region means the same thing in every home, which is
the entire optimizer story in one line — home-choice is PURE PRICING
(eager: prune now, pay propagation; carried: inert now, pay at
finality) — §8.3's plan space, nothing more mystical.

The ADMISSIONS — suspensions (§8.1), aggregates (§8.7), epoch pins
(§8.7), assembler fragments (§8.1) — add no machinery; they widen who
may be a factor, each paying the same ticket: named, value-equal,
state-independent, conservatively comparable.

And the apparent circularity of a disjunctive store's factor being a
`Condition` — a ∨ inside an ∧ — is DISTRIBUTIVITY, which the ring
already owns: `(A ∨ B) ∧ C = (A∧C) ∨ (B∧C)` is what `and` computes. The
"two ∨s" are one ⊕ in two homes, resident and carried; lifting the
store in and out of `Condition` is choosing ∨'s home, exactly the
choice ∧ already has. Being a ring means precisely that sums nest in
products and normalize — the structure is not folding in on itself; it
is big enough to hold its own operations.

## 1. The problem: three delivery regimes in one cell

By July 2026 the tabling cell had grown three answer kinds, each with its own
delivery discipline:

- **ground presence** answers streamed to every reader on arrival;
- **weighted folds** (min-plus costs) streamed, then RE-streamed when a
  cheaper derivation improved a cached key — and outside readers saw
  provisional values, an order-dependence the chaos harness caught as
  duplicate deliveries under some schedules;
- **constrained answers** (TCLP residues) were withheld during explore and
  delivered as a maximal antichain at the seal, because subsumption can EVICT
  an answer already handed out.

Three regimes meant a split-brain carrier (an indexed log for ground answers,
an antichain for constrained ones), two kinds of per-reader memory (a
delivered-set for the antichain, a value-map for weighted re-delivery), and
mode flags deciding which arm ran. Every regime was a special case; the
special cases kept breeding bugs.

## 2. The insight: conditions are a semiring

The engine's own theorem — constraints compress branching into data — read
backwards: if a residue is a compressed DISJUNCTION of ground branches, then
residues have a sum and a product, and the values tabling caches are elements
of a ring.

An answer with a residue is a CONDITIONAL answer: `t holds GIVEN R`. For a
fixed term, its value is the region it is proven on:

- `⊕` = region union — two derivations of the same term prove the union of
  their regions;
- `⊗` = conjunction — a derivation that consumes a conditional answer
  inherits its condition;
- `0` = no region proven (the answer is absent);
- `1` = TRUE — an UNCONDITIONAL (ground) answer.

This is a known algebra: conditional tables (Imieliński–Lipski) and the
PosBool provenance semiring (Green–Karvounarakis–Tannen). What the engine
adds is the observation that ONE carrier then serves every tabling mode,
because a weight ring (min-plus) has exactly the same shape: a per-term value
folded by ⊕, with `1 ⊕ a = 1` deciding what may stream.

The three regimes collapse into one rule (§6), and the special cases become
instances.

## 3. `Residues` — the ⊗-monoid

One REGION of constraint knowledge: per-store factors keyed by store class,
conjoined. `(Residues, meet, TRUE)` is a meet-semilattice with top — the
pointwise product of the store lattices, an absent factor being that store's
⊤.

- `meet` — pointwise factor meet (`Absorbable.meet`, each store's own
  law-tested lattice); a class only one side knows joins whole.
- `leq` — containment: narrower entails wider, pointwise, absent = ⊤. This
  is the store-level `Absorbable` convention lifted one level: `leq`
  REVERSES the accumulation order of `combine = meet`, exactly like the
  stores it aggregates (`ResiduesLawsTest` pins it with
  `checkLeqReversesAccumulation`).
- `absorbedBy(other) = other.leq(this)` — meet-combine flips absorption; the
  wider conjunct is the one that contributes nothing.
- `TRUE` — the empty conjunct: ⊗'s identity and the region ⊤.

`Residues` also owns the NAMESPACE CROSSINGS — the boundary compositions the
`Projectable` javadoc lists, finally with one owner:

- `project(pkg, callVars)` — call side: each store's knowledge about the
  call vars, split and canonically renamed; the key citizen that joins the
  `Call`. Non-projectable non-empty knowledge refuses loudly (unkeyed
  knowledge means silently wrong reuse).
- `normalize(pkg, holeVars)` — answer side: each factor renamed against the
  answer's substitutions (spent entries drop — the ground-answer fast path
  is a factor that normalizes to empty), then slot-canonicalized so residues
  from separate derivations compare in one basis.
- `restate(renaming)` — the ONE replay primitive, both spellings of which
  used to live apart: master seeding renames the key's conjunct onto the
  live call vars (`Renaming.ofSlots`); answer delivery renames an answer's
  conjunct onto the instantiation's fresh holes (`Renaming.into`, unseeded
  locals minting — the existential). Statement stays the driver's: each
  factor rides `Propagation.absorb`.

## 4. `Condition` — the ring

A term's value under plain tabling: a DISJUNCTION of `Residues` conjuncts —
how much of the term's space the entry has proven.

- `⊕ = or` — region union kept in ABSORPTION NORMAL FORM
  (`a ∨ (a ∧ b) = a`): a dominated conjunct contributes nothing and drops; a
  dominating newcomer evicts what it covers. **Subsumption dedup is this
  ⊕'s absorption law, not a separate mechanism** — the old answer Antichain
  was ⊕'s normal form maintained by hand, one level too high.
- `⊗ = and` — the cross product of pairwise conjunct meets; the value-level
  mirror of what restate + propagation do operationally through the package.
- `1 = ONE` — the single TRUE conjunct: a ground answer. `1 ⊕ a = 1` IS
  absorption, so the ring is `BoundedSemiring` — the same rung as min-plus,
  with the same consequence (§6).
- `0 = ZERO` — no conjuncts; annihilates under ⊗.
- Equality is the SET of conjuncts: a DNF is knowledge, not arrival order.

The full ladder is claimed and green (`ConditionLawsTest`: semilattice,
semiring, idempotent, star, bounded — over `Span`, a value-only test store
whose solver triggers refuse, because a conjunct inside a value is data,
never driven).

The two-level structure is deliberate: monoid laws on `Residues`, ring laws
on `Condition` sitting on top of them. A raw residue map could not be the
ring carrier — the union of two incomparable regions is not one conjunct
(pointwise join over-approximates: `{x∈{1},y∈{1}} ∨ {x∈{2},y∈{2}}` is not
`{x∈{1,2},y∈{1,2}}`, which admits `(1,2)`), so ⊕ forces the free join: a set
of conjuncts, normalized by absorption.

**The fuller structure** (the human's question, August 2026: what is the
relationship between a lattice and a semiring?). Reintroducing ⊗ into a
semilattice has two inequivalent forms. FREE ⊗ (non-idempotent) yields an
idempotent semiring — min-plus: ⊕ gives the order, ⊗ carries a QUANTITY
the order cannot see; two structures on one carrier. IDEMPOTENT,
COMMUTATIVE, ABSORPTIVE ⊗ collapses the two induced orders into one
(viewed from opposite ends): that is exactly a BOUNDED DISTRIBUTIVE
LATTICE — 0 = ⊕-identity = bottom (annihilation is bottom absorbing),
1 = ⊗-identity = top (`1 ⊕ a = 1` is top absorbing). Absorption is the
hinge and is not free (⊕ = ⊗ = max: both idempotent, distributive, no
absorption, no lattice). One line: a bounded distributive lattice is a
semiring about WHETHER; a general semiring is about HOW MUCH. `Condition`
is the lattice case caught in the act — `C and C = C`, and
`a ⊕ (a ⊗ b) = a` is literally `orConjunct`'s drop rule — the code
declares `BoundedSemiring` because that is the rung call sites demand;
the full structure is PosBool, the free bounded distributive lattice.

**The engine forgot different halves.** The STORE side kept ⊗ and forgot
⊕: `Domain.combine = meet` — the kernel is the conjunctive fragment,
knowledge accumulating by ∧, never needing join until lift asks "what do
alternatives agree on." The CELL side kept ⊕ and forgot ⊗: `JoinMap`
folds answers by union — tabling is the disjunctive fragment, its ⊗
riding operationally through the package until `and` named it as a
value. `Condition.and` completed the additive fragment; lift completes
the multiplicative one — two completion arrows converging on one bounded
distributive lattice, and the disjunctive store (§8.3) is exactly where
both fragments must hold both faces at once, which is why designing it
exposed the gap.

## 5. `JoinMap` — the one cell

The answer cell for EVERY mode: term → value in the mode's ring, with three
views.

- `order` — terms in arrival order, append-only. A value ascent never moves
  a term's index; eviction is a value change, not a membership change, so
  the enumeration is cursor-stable by construction.
- `members` — each term's current ⊕-fold.
- `log` — every arrival that MOVED its term's value: a fresh term, a new
  region, a cheaper cost — ONE event kind. The append's strict-ascent step
  (`fold = existing` → refuse, no wake) is the cell's termination signal,
  and it is lawful precisely because ⊕ is idempotent.

`order` and `log` coincide under a ⊕ that never moves a stored value and
diverge exactly when values improve — which is when the distinction pays.
The cell rides a `Channel` (its `Semilattice` join replays the delta's log;
an inert join keeps object identity, so `v != snapshot` is the wake
predicate). Equality is `members`: knowledge, not order.

Instances by mode: plain tabling — `Condition.RING` (ground answers arrive
at 1); bounded-weighted — the weight ring (`SemiringStore` product); closed
(star) — `Condition.RING` with every capture 1 (explore is for structure;
the value rides the `DependencyGraph`, `star-tabling.md`).

## 6. Finality: delivery timing is the value's own property

The one rule that replaced the three regimes:

> **A value at ⊕'s top is final on arrival — `1 ⊕ a = 1`, nothing can ever
> move it — and streams immediately. Anything below the top is provisional
> until the seal, where each term's converged fold delivers.**

- Plain ground answers arrive at `1` → stream (the old presence behavior,
  now DERIVED, not declared by a flag).
- Conditional answers are below `1` → their regions sum toward the seal;
  the outside reader receives the converged DNF, one delivery per conjunct
  (each conjunct restated onto that delivery's fresh holes).
- Weighted folds are essentially never `1` → the outside reader receives
  the final cost at the seal, exactly once. A genuine 0-cost min-plus
  answer IS final on arrival and streams — the rule needs no exception.
- A conditional term later absorbed to `1` by a ground arrival streams at
  that log event; ⊕-inertness makes exactly-once delivery free.

INSIDE readers (a tabled call in another tabled body) are different and must
be: they are the fixpoint's fuel, and mutual recursion deadlocks if either
side waits for the other's seal. An inside reader walks the LOG and takes
EVERY ascent — delivering each arrival separately is sound by
DISTRIBUTIVITY (`min(6⊗w, 4⊗w) = min(6,4)⊗w`): the downstream cell's own ⊕
converges the folds. This is the semi-naive rule expressed by the algebra
instead of per-reader memory.

`consume` is therefore one loop: a single int cursor over the log
(inside: deliver every event; outside: deliver tops, skip the rest), and a
wake predicate of `cursor < log.size || !sealed`. The seal's arm delivers
each non-top member fold to outside readers (`deliverSealed`), then the
mode's `caughtUp` runs.

## 7. What dissolved, and the healed seam

`AnswerKey`, the `Answers` product, the separate `JoinLog` and `Antichain`
carriers, the reader's delivered-set and value-map, the improvement
re-delivery scan, `groundValuesFinal`, `supportsConstrainedAnswers`, and the
`PRESENCE` boolean-cast — all were shards of one fact the value now carries
itself. The modes shrank to five hooks (`cellSemiring`, `bodyState`,
`absorb`, `capture`, `caughtUp`), with `capture` owning the residues call:
plain folds them into a `Condition`; weighted and closed refuse them.

One seam HEALED for free: a ground `t` and a conditional `t GIVEN R` used to
live in different carriers and never see each other. In the ring they are
values of one term, and `1 ⊕ {R} = 1`: a ground arrival absorbs every
conditional version of its term
(`JoinMapTest.aGroundArrivalAbsorbsEveryConditionalOne`).

The suite's shape after the change is itself evidence: order-independence is
a property of ⊕ (commutative, associative, idempotent), so the chaos
harness's 24-seed properties hold structurally rather than by careful
scheduling.

## 8. The questions this makes askable

The value of a clean abstraction is the holes it exposes as QUESTIONS rather
than incidents. Three are now well-posed:

### 8.1 Are suspensions `Residues`?

**RULED (August 2026): no.** Suspensions never become store or theory
citizens — the driver treats them specially for the agenda fixpoint to be
safe. What CAN cross is a named positional description that re-parks
through the driver's own door in the consumer's scope:
`docs/notes/suspensions-cross-as-schemas.md` holds the design, task #136
its execution. The section below is kept as the question's history — its
factor-admission framing is superseded; its transcription insight (the
(actuals, template) call-value, the SLG delay-list reading) survives
inside the note.

Today a parked suspension at a tabled boundary refuses loudly (the answer
would owe a condition its key cannot carry). Under the ring, the question
becomes precise: **a suspension participates iff it can be a factor** — that
is, iff `Suspensions` (or a per-suspension transcription) implements the
`Projectable` capability: `split`/`rename` for the crossings, `meet` for ⊗,
`leq` for absorption.

The laws are the admission ticket, and they are more permissive than they
look: `leq` may be CONSERVATIVE (identity-comparable only, like carried
couplings before named value-equality) and the ring stays lawful —
absorption simply never fires for incomparable pairs, costing dedup, never
soundness. Precision of the order is the upgrade path, not the entry fee.
The real work is the transcription (a suspension's body is a closure; its
comparable citizen is a named, value-equal form — the same move Neq records
and couplings already made) and the same termination caveat every store
signs (finite ascent is the author's responsibility). This subsumes task
#74's "suspensions as owed conditions in AnswerKey" — the AnswerKey is gone;
the owed condition is a factor.

**The transcription already has a design** (the human's identification,
August 2026): the shelved assembler's `(actuals, template)` call-value
(assembler.md §4) IS the suspension factor — watched terms + a body-maker
whose closure is excluded from identity, manifests unioning by ACI, the
answer term and the actuals canonicalized into ONE hole space (its Seam 1
= `Renaming.canonical`). And the object has a literature name: a
Condition whose factors are pending calls is an SLG DELAY LIST —
conditional answers carrying delayed literals, simplified as they resolve
(`1 ⊕ a = 1` is SLG's answer simplification). Delayed NEGATIVE calls as
factors are the standard route into the non-stratified fragment §8.5
refuses — still research, now with a named road.

**Two rings, one value shape** (the human's question: does the suspension
vocabulary make the ring CLOSED rather than bounded?). No flip — the same
datum `(term, pending calls, factors)` serves two jobs with two algebras:

- **"This is what I've PROVEN"** — the answer cell. Stays BOUNDED
  regardless of factor vocabulary, and boundedness is one concrete
  behavior: a ground answer swallows its conditional versions (the cell
  holds `reach(a) GIVEN path(a,z)`; a derivation later proves `reach(a)`
  outright; the conditional entry is absorbed — nobody needs the
  condition anymore). Delivery restates the pending call into the
  CONSUMER's package, where it re-parks and evaluates when bindings
  arrive — answer mode, agenda-driven.
- **"This is what to RUN"** — the assembler's fragment. Here ⊕ is `or`,
  and `success().or(g)` is NOT the same program as `success()` — more
  answers — so absorption fails: not bounded. Its star is the
  interesting operation, and in engine vocabulary it is literally
  `Tabling.defineRecursive(self -> args ->
  success().or(g.and(self.apply(args))))` — STAR IS MINTING THE NAME.
  That is why this ring is closed, and why it needs suspension-shaped
  values: the value must be able to say "call `s` here, later" without
  running it.

The two jobs connect through `solve`: run the minted star to its seal
and read the cell — `TRUE` per reachable term. The answer ring's
"degenerate" star is just THE SEAL OF THE LOOP: the program-side
recursion, completed, reads back as plain membership. Star-tabling's
explore-under-presence has been computing exactly this all along. The
two types stay SEPARATE AND NAMED even though structurally twins
(the Semilattice doctrine: one carrier, two structures — never one type
wearing both); "run" means restate-and-park for the answer ring,
solve-to-seal for the program ring.

**Suspensions vs stores, and the agenda** (the human's concern — and his
correction of this doc's first draft): suspensions are NOT a store behind
the `revise`/`stated` boundary. They are FIRST-CLASS DRIVER CITIZENS:
`Propagation` parks them (its private `Suspensions` holder — an inert,
branch-local transport), ripens them itself after bindings, and splices
their bodies into the run lane; `Projection`'s goals are a facade over
`Propagation.suspend`. DESIGNED SO for a direction reason (the human's):
a store's `revise` signs the termination contract — updates may only
SHRINK knowledge, the drain's fixpoint being finite descending chains —
while a woken body GROWS the problem (new postings, new bindings,
branches). The run lane quarantines growth BETWEEN drains: descend to
quiescence, splice the grown work, descend again — fixpoint-machine.md's
two-fixpoint split in miniature, inside one package. This is also why
the FACTOR is safe where store residence never was: a Condition carrying
the obligation is inert — direction problems belong to running, not to
carrying. The consequence for excursions is CLEANER than
the store story would have been: an excursion drives propagation, so it
inherits the whole suspension lifecycle from the driver directly —
parking, ripening, body-running all happen inside the speculative drain
with zero store machinery, and quiescent-with-debt is literally the
driver's own `suspensionsPending`. For the FACTOR, the driver ownership
means #74's transcription is not "make an existing store Projectable" —
there is no projection store; the transcription gives the driver-owned
parking lane a knowledge shadow. The latent constraints a body will
post are handled by the NAME AS A DEBT CERTIFICATE: restate re-imposes
the obligation, not its consequences; the consumer pays when the body
runs in its world; equal names + actuals owe equal debts. The price is precision (different-named suspensions are
conservatively incomparable), never soundness. CONTRACT LINE the
transcription must state: bodies are STATE-INDEPENDENT — meaning fixed
by name + actuals, never by constraint state read at capture time (the
assembler's hygiene caveat, generalized from variables to stores).
Consequences for the disjunctive store (§8.3): excursions may RUN woken
bodies, and bodies may branch — the excursion becomes a bounded
sub-search (the findall/drained shape; prune fires iff all branches
fail); a merely-parked suspension prunes nothing (sound); the item
watches the conjunct's ACTUALS — the suspension's own wake surface, so
no new wiring; lift over call factors is name+actuals intersection. The
open edge for the design pass: EXCURSION RE-ENTRANCY — a speculative
absorb grounding a var can wake a body that states another disjunctive
item; nested speculation needs a decided discipline. Its principled
shape follows from the direction split: an excursion is the ALTERNATION
(drain → run-lane splice → drain…), so verdicts tier — at
drain-quiescence, cheap and sound-but-incomplete (parked debt
unexamined); at full alternation, complete but terminating only as the
program does. The run lane IS the nesting frontier, and the
run-nested-vs-defer decision sits exactly there.

### 8.2 What is weight ⊗ Condition?

The refused combination (weighted answers with residues) is now a MISSING
PRODUCT INSTANCE rather than a flag: a value that is a piecewise weight —
"cost w on region R" — i.e. an antichain of `(Residues, weight)` pairs.
The shape of the ring is clear:

- `⊗`: `(R, w) ⊗ (R', w') = (R ∧ R', w ⊗ w')` — conjoin regions, compose
  costs;
- `⊕`: fold weights on EQUAL regions (`(R, w) ⊕ (R, w') = (R, w ⊕ w')`),
  keep incomparable regions as separate pieces.

The open decision — the reason this is research, not a patch — is the mixed
case: a DOMINATED region with a BETTER weight (`(x∈{1..5}, cost 7)` vs
`(x∈{1..3}, cost 2)`). Piecewise semantics says keep both (the cheap piece
is real on its sub-region); plain region-absorption would lose the cheaper
cost — which is exactly the bug the old flag guarded against, now visible
as a definable choice instead of a silent overlap. Whether the right
normal form is "maximal regions with best-cost refinements" or a full
weight-function representation is the design question. This is
provenance-with-conditions; `Semirings.Provenance` and the c-table
literature are the references.

### 8.3 Constraints as weights — the two evaluators

The human's observation, once the ring existed: **weighted inference can
IMPOSE constraints.** `Condition.RING` is a `BoundedSemiring` — exactly the
type `Weights.solveBounded` demands — so the constraint ring plugs into the
weighted machinery AS a weight ring: imposing a constraint is multiplying
by it (`factor` with a condition ⊗s a region into the running value the
same way it ⊗s a cost).

That makes the engine's two big subsystems TWO EVALUATORS OF ONE RING:

- the KERNEL evaluates eagerly — post a factor, propagate, prune the
  search now; imposition as operation;
- the WEIGHT PATH evaluates lazily — ⊗ conditions along the derivation,
  ⊕ across alternatives, deliver `(t, C)` at the end; imposition as
  bookkeeping — the constraint rides, nothing prunes.

The loop also closes backwards: TCLP was ALREADY weighted inference over
the constraint ring — the cell folds Conditions by ⊕ and restate-at-
delivery ⊗s regions into consumers. Tabling built a weighted-inference
machine for conditions without calling it that; the general machine can
run the same ring directly.

Between the two evaluators sits a third discipline, recorded here as a
design direction (its full design, plain-vocabulary chassis, API
sketches and build stages live in `nogood-store.md` — the ring-side story
stays here): a DISJUNCTIVE STORE — a store holding a `Condition` as
live constraint data ("the world is in one of these regions"), so a
disjunction of constraints propagates instead of forking the search.
Its three moves collapse into ONE operation at two grains (the human's
observation, August 2026): an EXCURSION — speculatively absorb a conjunct
into the live package and observe. The persistent Package makes the
speculation free (a scratch absorb is an absorb on a derived package you
drop — no undo, the same reason backtracking costs nothing), and every
piece pre-exists:

- PRUNE is the excursion's boolean shadow — `Propagation.absorb` IS the
  consistency check (its contract: the reaction is COMPLETE — custody,
  watchers, cascade), and failure-as-silence is observed by the
  drained/exhaustion machinery aggregate and conda already ride;
- LIFT is the excursion's join-fold — what every surviving world agrees
  on holds unconditionally; the per-class JOIN (`Lattice`, where stores
  opt in) folds the narrowed states, and the lift DELIVERS as revision
  payloads (inferred prefixes, narrowed terms — the cross-store
  consequence channel the kernel already routes);
- UNIT is the singleton case — the join over one world is that world:
  commit the absorb for real.

The store is a ⊕-FOLD OF EXCURSIONS: zero survivors fail, one commits,
many lift the join and keep the shortened Condition. Fairness is
pre-solved too — the store answers `revise` with a `Fiber<Revision>`,
designed exactly so an expensive reaction interleaves. What stays open at
search's end labels like a domain: enumerate conjuncts as branches,
materialization deferred to the last moment. It is the DNF dual of
lattice-store.md's co-store (excluded boxes = CNF over lattice literals;
same unit-propagation engine), and its canonical workload is its namesake
— disjunctive scheduling (`endA ≤ startB ∨ endB ≤ startA` per pair:
forked = 2^pairs, as data = one Condition per pair, bounds-lifting
pruning across all). The genuinely new parts shrink to three:

- **the JOIN RUNG** — lift needs the second lattice face, and the house
  pattern for it already exists: `lattice.Domain` is meet-only by design,
  and `Lattices.Mask`'s fixture states the doctrine — "meet inherited,
  join exposed as a projection." So the widening is NOT raising the
  family bound but an optional capability on domains that have a join,
  demanded by lift's signature (the ladder discipline). Soundness note
  from the `Range` exemplar: an INFLATIONARY-HULL join over-approximates
  and is still sound for lift — lift asserts what every surviving world
  entails, and a wider statement stays entailed — so exactness buys
  sharpness, never correctness. Gradation: exact join (finite sets) →
  sharpest lift; hull join (intervals) → sound, blunter; no join →
  prune-only. Neq opts in via record-set intersection (one line);
  Substitutions tiers from all-worlds-bind-`x`-equally (an equality
  check) toward full anti-unification (msg) later;
- **WATCHED-CONJUNCT discipline** (two viable disjuncts watched,
  SAT-style, so a wake does not re-run n excursions);
- **the orchestrating store itself** — a lattice-store.md phase.

The spectrum, then: EAGER KERNEL (propagate now) ⟷ DISJUNCTIVE STORE
(data, but prune/unit/lift) ⟷ PURE WEIGHT (data, nothing prunes). Three
imposition disciplines, one ring — and the choice is a PLAN SPACE, not an
API menu: the condition is the declarative object, imposition discipline
and timing are physical planning, and the OPTIMIZER should decide (the
human's call, August 2026). The license is a theorem already in hand —
kernel confluence (monotone stores: posting earlier vs later changes cost,
never answers) plus the two-evaluators identity above — with two guards:
defer only where the sub-search below the imposition point prices FINITE
(the ∞→exact transition; an eager post that cuts an infinite generator is
load-bearing for termination and must stay), and charge the lazy plan for
labelling zombie conditions out before answers escape. The cost model is
classical predicate migration (Hellerstein–Stonebraker: rank =
cost/selectivity): selectivity = region width over space width — the same
width capability tabled-constraints §5.5 already promised to pricing —
against propagation fan-out (eager), probe checks (middle), or priced
sub-search size × zombie rate + DNF growth (lazy). One free move sweetens
it: `factor` goals commute with everything (⊗ commutative, no store
touched), so the lazy end is maximally reorderable. Dependency chain,
honestly: live-name wiring for factor-conditions, then the disjunctive
store, then width-selectivity hooks — the optimizer's decision layer is
fourth, after each has a customer.

**The license has a mechanism** (the human's observation, August 2026):
implementing `Projectable` created an EQUIVALENCE between a constraint's
two residences — resident-in-a-store (runnable: the drain evaluates it)
and riding-as-a-value (semiring: ⊗ composes it, ⊕ sums it). Not an
analogy: the roundtrip is lawful — `split` is lossless
(`_1 ∧ _2 = this`), `restate = rename ∘ absorb`, `absorb = meet +
normalize` — extract-then-reimpose is identity up to normalization. So
the crossings ARE the plan space's conversion operators, per store,
callable today: DEFER = don't post, carry the factor; IMPOSE = restate
it. One qualification: NO RETRACTION — a resident factor cannot lift out
mid-drain (that would grow the region; the direction rule). But
persistence + confluence make relocating the IMPOSITION POINT equivalent
to lifting over any interval, and the roundtrip is exercised at scale
already: every tabled call performs strip → project → restate — the
master-from-key discipline is lift-out-and-selective-relift, load-bearing
since TCLP stage 1.

**The toll gate** (the human's, August 2026): the Barrier forbids moving
GOALS across a tabled call — control. But a LIFTED goal is a factor, and
factors do not move across the barrier; they flow THROUGH THE CROSSINGS,
which were sanctioned all along: touching call vars → `project` takes it
into the key (the region honestly narrows — nothing snuck past, the key
SAW it); caller-private → `split`'s remainder stays caller-side and
consumption filters. The illegal control-move becomes a legal data-flow,
and the thing the barrier protects — key integrity — is preserved by
construction. What passes: FILTERS defer through cleanly (run after
consumption when answer bindings ripen them — predicate migration
through tabling, priced); BINDERS deferring through mean the key
genuinely WIDENS (a more general entry, answers a superset filtered at
consumption — sound by the subset property, potentially explosive). The
barrier rightly remains the DEFAULT contract; lifted movement is the
deliberate, priced override, and the price is region width — the same
selectivity number the plan space runs on.

What the lazy end unlocks:

- **Soft constraints** — the semiring-CSP frame (Bistarelli–Montanari–
  Rossi): product rings like `Condition × MIN_PLUS` say "holds on R, at
  cost w" — prefer-but-don't-require, violation penalties, fuzzy and
  probabilistic CSP. The literature's premise is "constraints as semiring
  values"; the carrier now exists.
- **Assumption-based reasoning** — a condition ⊗'d in as a weight is an
  ASSUMPTION; every answer reports what it depends on. That is an ATMS
  (PosBool provenance) — diagnosis, what-if, explanation queries, with
  `factor` as the assumption operator.
- **Impose-later** — collect conditions through a search the stores
  should not prune, then `restate` the surviving Condition into a real
  store at the end; laziness becomes a per-goal choice, not an
  architecture.

Three honest caveats:

1. **Zombie derivations.** Lazily-imposed constraints never prune: a
   branch whose accumulated condition is unsatisfiable carries a dead
   region to the end. On tight problems the kernel's eagerness is the
   whole game — this is a choice per constraint, not a replacement.
2. **The live-name gap.** A weight is scopeless; a condition stated
   mid-derivation holds LIVE vars. For `(t, C)` to mean anything at
   reification, weight capture needs the `normalize` crossing (walking +
   slot canonicalization) — built on `Residues`, not yet wired into
   `SemiringStore` capture.
3. **Componentwise ≠ piecewise.** `boundedProduct(MIN_PLUS, CONDITION)`
   type-checks today but computes `(C ∨ C', min(w, w'))` — "some region
   where it holds; the best cost over any of them" — losing the
   region↔cost correlation. Sound for some queries; the correlated
   piecewise weight is §8.2's tensor, still research. The observation
   makes the uncorrelated half free and gives the correlated half its
   on-ramp.

### 8.4 Which stores ride, and how well?

Any `Projectable` store is already a factor — FD, Neq, pldb's lattice
stores ride today. The quality of their participation is graded by the
same two dials §8.1 names: the precision of the factor's `leq` (better
absorption, more dedup) and the finiteness of its ascent (termination).
The ring made the dials independent of the machinery.

### 8.5 Negation as a value: Neq is ¬Condition

The relationship the human spotted (August 2026): a Neq store IS a negated
Condition. A record is `¬(x=1 ∧ y=2)` — the negation of a binding box —
and the store conjoins records: `∧ᵢ ¬boxᵢ = ¬(∨ᵢ boxᵢ)`, the negation of
a DNF. De Morgan makes the correspondences exact:

- each record is the CLAUSE `x≠1 ∨ y≠2` — the store is the CNF a negated
  Condition is;
- Neq's operational behavior is clause propagation: all-but-one binding
  entailed watches the last (unit), fully entailed fails (empty clause),
  any binding impossible discharges (satisfied clause);
- the dedup rules coincide: Condition drops a dominated conjunct, Neq an
  implied record — both maintain the maximal-box antichain. One normal
  form, two polarities.

Beneath the structural duality sits a value-level pairing lattice.md
already states ("finite and cofinite respectively"): **FD and Neq are the
two halves of the finite–cofinite algebra.** Neither store is closed
under complement alone — `¬(x ∈ {1..3})` over an unbounded universe is
cofinite, which FD cannot say but Neq IS — together they are
complement-closed. Neq is not merely similar to Condition; it is the
store the algebra needs for negation to have somewhere to land.

GENERALIZED NEGATION AS A VALUE is then a small extension, half of which
already exists: a `Residues` conjunct already carries Neq factors, so
Condition is ALREADY a DNF over mixed-polarity literals. What is missing
is only the operator `¬ : Condition → Condition`:

1. a per-store LITERAL COMPLEMENT capability — a factor answers with its
   negation as a Condition, possibly in ANOTHER store class (`¬FD-factor`
   = per-var cofinite pieces = Neq literals; `¬record` = binding boxes).
   Negation is a cross-store map; the finite–cofinite pairing makes it
   total for the in-tree stores;
2. generic De Morgan in Condition — `¬(∨ᵢ Rᵢ) = ∧ᵢ ¬Rᵢ`, distributed
   back to DNF, absorption re-normalizing. Nothing store-specific above
   the literal level.

The payoff: `¬` composed with the seal is CONSTRUCTIVE NEGATION of tabled
goals (Chan) — a sealed entry's Condition is the COMPLETE proven region,
so its complement is exactly where the call FAILS, deliverable as
constraints rather than as negation-as-failure's coin flip. The timing
discipline is already built, because **negation is the ultimate outside
reader**: sound only against a FINAL value, which is §6's rule verbatim.
Stratification comes free from completion detection — a negated call must
seal before its negation is read (a sleeper edge); a cycle THROUGH
negation surfaces as a seal waiting on itself — refuse loudly, honestly
marking the non-stratified fragment (well-founded semantics is a research
bridge, not an accident to stumble across). This also gives the
cut/once/ifte backlog item a sound core for the tabled fragment:
if-then-else = consume the condition or its complement.

Caveats: CNF→DNF distribution is worst-case exponential (absorption
mitigates; an NNF tree that never materializes the DNF is the escape
hatch); a literal no store can complement — a coupling body, a suspension
— has no negation and refuses loudly; FD complement needs the declared
universe, Herbrand cofinites ride Neq — both checkable at the refusal
point.

### 8.6 Clause learning: tabling the failures

The human's observation on the disjunctive store (August 2026): it is the
shape clause learning wants. The engine has a name for CDCL the SAT
literature does not: **a learned nogood is a conditional answer for
false** — "these decisions together entail ⊥" is `⊥ GIVEN R`, and §8.5
says its useful form is `¬R`: a CLAUSE, an EXCLUDED BOX — the co-store's
item (lattice-store.md). The "clause database" is not new machinery; it
is the co-store FED BY CONFLICT ANALYSIS instead of user postings. The
disjunctive store (§8.3) and the learned-clause store are the two
polarities of §8.5's duality — one accumulates included regions, the
other excluded ones.

The CDCL cycle, in engine vocabulary:

- DECIDE = a labelling branch (`enforce`'s Conde);
- PROPAGATE = the kernel drain;
- CONFLICT = failure — silent, but every failure passes ONE DOOR (the
  chokepoint: `resolve` refusing, `absorb` failing, an enforce dying), so
  the learn-hook has a single home;
- ANALYZE = read the reason off the failing package (the tiers below);
- LEARN = post `¬R` to the clause store. Finality (§6) applied to
  negative knowledge: a nogood is an entailment of the PROGRAM, not of
  the branch — born at 1, valid everywhere on arrival — so **nogoods
  STREAM**, no seal, no waiting;
- BACKJUMP = unnecessary, and structurally so: backjumping exists to
  escape a doomed DFS stack region. A fair scheduler with all branches
  live has no stack to unwind — the streamed clause reaches every
  sibling, and unit propagation kills the doomed ones at their next
  chokepoint pass. BACKJUMPING WITHOUT BACKJUMPING: the clause prunes
  the frontier instead of rewinding it. (DFS solvers need restarts to
  spread learning; fair breadth gets continuous spread for free.)

The one architectural question is already answered elsewhere in the
engine: persistent packages isolate branches, so a clause posted in one
never reaches siblings — but that is exactly the TABLE TRANSPORT PATTERN.
The clause DB is a shared, monotone, append-only object riding the root
package's store map, consumed per branch (per-branch watch state over
shared clauses, the way readers hold per-branch cursors over shared
entries), with the same soundness argument as answer cells: monotone
growth of globally-valid facts.

The real cost is REASON TRACKING, and it tiers:

1. **Decision-set tagging — nearly free.** A `Packaged` marker
   accumulating this branch's labelling choices (the `Recurrent`/`InBody`
   pattern); on failure learn the DECISION CLAUSE `¬(decisions)`. Weak by
   SAT standards but sound and cheap — the engine never repeats a
   proven-dead decision combination.
2. **Condition-annotation riding** — §8.3's lazy evaluator as the reason
   carrier: ⊗ decisions into a running Condition; at ⊥ the accumulated
   annotation is an explanation. Derivation-level reasons; blind to
   store-internal cascades.
3. **Full lazy clause generation (Ohrimenko–Stuckey)** — each propagator
   explains its narrowings (the FD Verdict/Update path carrying "this
   pruning because those literals"). Invasive per-propagator work; where
   SAT-strength learning lives, if a workload ever demands it.

THE ANNOTATION SEAM IS SHARED — PARKED, a research note (August 2026):
tier 3's per-narrowing explanations, fine-grained source attribution
(domain-layer.md §5.3 — which source-at-which-state fed each piece of
knowledge), and fact-level PROVENANCE ("which facts, combined how") are
one infrastructure: an annotation ring threaded through the kernel's
meets (unification, store meets, propagation — annotations ⊗ where
knowledge composes). Dependencies attach to more than variables —
ground facts, relation calls, postings, propagated narrowings, table
answers, ABSENCE/COMPLETION evidence — so the foundation is knowledge
annotation with per-variable projections as one view over it. Expensive
exactly once; three customers; whichever arrives first pays the seam
and the others ride. Triggers: a workload demanding SAT-strength
learning, measured over-refusal from answer-level footprints, or the
adoption push needing explanations.

The design is the finality theorem applied twice: positive conditional
answers WAIT for seals because their regions grow; negative ones stream
because they are born at 1. Failure is the easier half of knowledge.

### 8.7 Aggregation as an owed condition

Aggregates are the engine's standing non-monotonicity debt (the human's
long-running worry): `Aggregate(x, g(x,y), z)` runs a sub-search whose
extension depends on knowledge that may still grow — evaluated eagerly
at its conjunct position, it reads a world that is not final, and a
stale aggregate is WRONG, not weak. Negation (§8.5) is its boolean case
(`not(g)` = "count(g) = 0"), so the non-stratified boundary is shared.

The design arrived by three rounds of deflation — each shape recorded
with what killed it:

- *seal-orchestrated finality* (hang every aggregate on entry seals)
  died on "where is the finality condition on y? I don't see any scope"
  — a bare sub-goal has no entry and needs none: THE BRANCH IS THE
  SCOPE. Along one branch the package grows monotonically and is final
  when the branch delivers; the per-branch seal already ships as the
  ENFORCE pipeline ("runs once per answer, at the end of the search").
- *dependency-graph orchestration* (declared-surface edges, topological
  firing) died on "I don't see how groundedness of y is not enough" —
  it IS enough: with the sub-goal's COMPLETE free surface ground, the
  extension is fixed (sub-search locals are fresh; knowledge about
  ground terms is spent; the relation is immutable per solve). Ordering
  then solves itself by DATAFLOW THROUGH RIPENESS: an aggregate whose
  input is another's output is simply not ripe until that output is
  bound; simultaneous fires commute (unification and meet are
  commutative); a genuine cycle leaves both parked at branch end —
  caught by enforce's existing "fail on anything still unrun", upgraded
  to a loud non-stratified refusal.
- *the in-solve suspension design itself* — the shape the two rounds
  above deflated toward (declared surfaces, groundness-ripeness, the
  enforce backstop) — was SUPERSEDED by the human's boundary reframe:
  it deferred every aggregate to the branch's end anyway, and the
  engine, being EMBEDDED, already has a sounder end to defer to — the
  SOLVE BOUNDARY ("solve is giving us a result of running a relation
  and we could always aggregate over it, safely; this could then seed
  a next solve"). The suspension shape survives only as the gated
  residual's design (below).

**The primary design — aggregate at the solve boundary.** Every solve
is surrounded by a host language that already has folds; the boundary
IS the finality certificate. Three pieces:

1. **The closedness refusal** — SHIPPED (August 2026). The sound
   primary form is the CLOSED aggregate: the sub-goal is a
   self-contained program, branch-independent, deterministic. AS BUILT:
   every `Aggregate` primitive is function-shaped —
   `count(x -> g(x), n)` — the body receives a template born inside the
   aggregate's scope, so a closed body carries no pre-existing variable
   by construction; the template-first forms are gone (their template
   pre-existed by construction and could never carry the mark). The
   birth watermark (`LVar.getBirth()`, the monotone counter; `Watermark`
   is a mode marker riding the sub-solve's packages) refuses at every
   door outside knowledge has: the BINDING seam (`Propagation.resolve`,
   ahead of both the agenda and the pure fast path — prefix keys and
   value leaves), the STATEMENT seam (`Package.withStored`, the one door
   `activate` and Disequality's direct park both pass; `Stored.terms()`
   names what an item speaks about), and the SUSPENSION seam
   (`Propagation.suspend`, BEFORE the ripeness test — watched is the
   body's declared read surface, and an upward-closed condition can
   pass without the watched terms being bound). Each check deep-walks
   first, so a pre-existing variable already bound to a value dissolves
   before any comparison — ground knowledge is spent, the free surface
   is what refuses — and a refusal names EVERY offending variable its
   event carries. (The general watermark detector — declared surfaces
   checked against actual capture — still serves §8.1's
   state-independent bodies and the assembler's fragment hygiene;
   undeclared reads off the relational vocabulary remain its territory,
   not this check's.)
2. **The idiom** — solve₁ → fold in the host language → seed solve₂
   with the result as a fact table (the row-set store is the carrier).
   Strata made explicit as solves; stratified aggregation and stratified
   negation-as-failure (count = 0) both fall out; the old
   findall-over-a-cold-table breakage disappears because a closed
   aggregate's sub-solve OWNS its tables.
3. **The division of labor** — recursive/monotone aggregation (shortest
   path, path counting, most-probable) is NOT this feature and never
   was: those are SEMIRING FOLDS and live inside the fixpoint as
   weighted inference (`solveBounded`/`solveClosed`). Semiring folds in
   the fixpoint; non-monotone folds at boundaries. Point users at
   `Weights` for the former, the idiom for the latter.

**The final simplification** (the human's question, August 2026: are all
aggregations semiring solves?): every aggregation is a ⊕-fold, and ⊕'s
IDEMPOTENCE decides where the fold may run — because a relational goal
proves the same answer several ways, and the fork is whether ⊕ cares.

- **⊕ idempotent** (min, max, exists): duplicate derivations fold to
  nothing, so proof-folds and answer-folds agree — pure `Weights`, today,
  including THROUGH recursion. These were never Aggregate's; they were
  rings wearing its name.
- **⊕ non-idempotent** (count, sum): the semantics forks, and the TYPE
  SYSTEM already polices it — over PROOFS is the counting ring
  (`solveBounded` refuses it: non-idempotent cannot stream a fixpoint;
  `solveClosed`'s star handles the acyclic-recursive case and DIVERGES
  HONESTLY on cycles, where the number genuinely does not exist); over
  ANSWERS is a fold over the deduped stream a finished solve already
  returns — the boundary idiom. SQL's COUNT(*) vs COUNT(DISTINCT), typed.
- **GROUP BY is table keys**: a weighted tabled call keyed on the group
  vars IS the grouped fold — `solveBounded` returns (term, fold) per
  answer term, one min per key in one pass. Non-idempotent per-key folds
  group at the boundary (`Collectors.groupingBy`).
- **argmax** is a selective ring carrying (value, witness) — an instance,
  not a feature. **findall** is an ordered (non-commutative) fold —
  boundary by nature.

The worked pairs (`Aggregate.count` counts the DISTINCT solutions of
its closed sub-solve — the staleness bug died with the closed form, the
proofs-conflation with the set-semantics fold; the boundary idiom is
the pinned oracle, and the two routes agree by test):

    // membero over [1, 2, 2]:
    Aggregate.count(x -> membero(x, l), n)   // n = 2 — answers, closed
    Weights.solve(g, product(COUNTING), …)
            .get(COUNTING)                   // 3 — proofs, honestly named, whole-solve
    g.solve(x).distinct().count()            // 2 — answers, at the boundary

    // solution identity is the whole tuple, never the payload:
    Aggregate.sum((s, v) -> product(s, v), total)
    // product(a, 10), product(b, 10) → 20, never 10

A NON-GROUND solution refuses the fold (`Reified.isGround`): a free
name denotes infinitely many distinct tuples, so no finite count
exists — and counting answer RECORDS instead would be
representation-sensitive (one region or two records for the same set
must not count differently). A ground solution with a residual witness
on a body local counts once: the witness is existential. "How much" of
an infinite region is a measure question, not a count — outside this
feature.

    // min over answers = min over proofs (idempotent), recursion-safe:
    price(item, x).and(Projection.project(x, v -> factor(MIN_PLUS, v)))
        → Weights.solve(…, product(MIN_PLUS), …)

    // cheapest per destination — the group IS the table key:
    Weights.solveBounded(route(a, dest), dest, boundedProduct(MIN_PLUS), …)

    // cyclic graph: answer-count exists, proof-count does not —
    reachable(1, y).solve(y).distinct().count()   // seals, then folds: correct
    // solveBounded(COUNTING) refused; solveClosed star diverges loudly:
    // the types refuse to invent the number Aggregate.count would have

So the feature dissolves: NOTHING new is built for any fold; the
closedness refusal survives (it guards the sub-solve's inputs, orthogonal
to the ring). The keep-or-retire call on `Aggregate`'s signatures is
DECIDED: the function shape stays as the closed boundary sugar; the
template-first forms are retired. The routing rule is
one sentence: DUPLICATE-INSENSITIVE → ring, anywhere; DUPLICATE-SENSITIVE
→ proofs mean rings-with-star, answers mean fold the stream.

**Expressivity check** (the human's deflation question — does the idiom
lose real programs?). Worked example: "a client's risk score is the
count of their late payments," correlated on the OPEN parameter
`client`:

- clients enumerable from data → solve₁ emits (client, payment), the
  host groups, the table seeds solve₂ — same answers, two solves;
- specific ground clients → per-client CLOSED sub-solves at the
  boundary (with `client` ground the aggregate has no open parameter);
- a genuinely FREE correlate ("which clients have score 3?" without
  enumerating clients from anywhere) was never computable in ANY
  design — you cannot count per key over keys nobody can list.

So nothing becomes unaskable; what changes is the SURFACE (one nested
relation becomes strata or a mode-restricted call). The recoverable
sugar, precisely: a relation form that REQUIRES its correlate ground
(refuse loudly on a free call), runs the per-key closed count, and
memoizes through tabling — nested syntax restored, zero new answers.

**The gated residual** — aggregates over branch-local CONSTRAINT STATE
(lookahead counting: "the value leaving the most feasible options";
open-set capacity sums mid-search). Only sound at the branch's enforce;
no current use needs it; the suspension design above is its dormant
blueprint. Its likelier future homes are not user aggregation at all:
search heuristics (the optimizer's accidental-complexity budget) and
global-constraint propagators (the store family).

**The pattern generalizes, and pldb got there first**: FactSource's
`pin()` (domain-layer.md) is the same FREEZE-AND-CERTIFY move for
substrates the engine does not own — a non-monotone read over a
changing substrate is only well-defined against a frozen input surface.
Three instances now: external epochs (`pin()`), internal branch
knowledge (the aggregate's ground surface), tabled extensions (the
seal). One carrier serves all their certificates: PIN STAMPS ARE EPOCH
FACTORS — an answer from a pinned source carries `t GIVEN source@epoch`
as an ordinary conjunct, memo validity becomes `leq` on the epoch
factor (cache invalidation as region containment, the check
`Call.subsumes` already runs), and cold execution's replay is `restate`
re-imposing the epoch condition. Phase-4 persistence (#75) inherits
this identification instead of rediscovering it. The epoch
representation is a HYPOTHESIS with a three-level carrier
(EpochRequirement → Footprint → EpochCondition, mirroring factor →
Residues → Condition — a lone footprint cannot carry ⊕, the same
lesson §4 records for residue maps); its receipt is unwritten, and what
it may buy is per-DERIVATION admissibility under another source world —
never that a table sealed under an older world is complete under a
newer one (cross-snapshot table reuse = recomputation or a delta
protocol). The tiers and details live in domain-layer.md §5.3;
fine-grained attribution is parked on §8.6's annotation seam.

## 9. Test map

- `ResiduesTest` / `ResiduesLawsTest` — the monoid: meet, containment,
  absorption flip, leq-reverses-accumulation.
- `ConditionTest` / `ConditionLawsTest` — the ring: absorption normal form,
  distributivity, the full bounded ladder.
- `JoinMapTest` / `JoinMapLawsTest` — the cell: strict ascent, the log,
  min-plus and Condition folds side by side, the healed seam.
- `SchedulingChaosTest` — the order-independence properties (entailment
  dedup, nested consumption, min-plus exactly-once, recursion) across 24
  randomized-scheduler seeds.
- `TabledUnderDomainsTest`, `TabledUnderNeqTest`, `WhodunnitTest` — TCLP
  end to end on the new carrier; `WeightedTablingTest`, `ClosedTablingTest`
  — the weight modes.
