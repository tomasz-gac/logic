# Tabled constraints — the price list for merging the two fixpoint engines

**Status: STAGE 1 SHIPPED (July 2026) — tabled calls under FD domains run,
region-keyed; stages 2–4 remain design (AS-BUILT notes inline). This is the successor to `fixpoint-machine.md`'s
"don't merge the engines prematurely" caveat: it prices the merge so the
decision can be made deliberately when a use case pays for it. Until then, the
wall stands (see §2).**

Prerequisite reading: `fixpoint-machine.md` (the two-fixpoint mental model),
`constraint-kernel.md` (the store boundary this extends),
`tabling/Tabling.java`'s class javadoc (the master/slave protocol).

---

## 1. The two machines, in lattice terms

The engine runs one monotone-fixpoint machine in two directions:

- **Constraints descend.** A domain is an element of a lattice ordered by
  information ("smaller set = knows more"); propagators are monotone
  contracting operators; the agenda drain iterates them to a fixpoint; failure
  is ⊥, collapse is reaching an atom, the equal-domain guard is "descend
  strictly or stop". Finite descending chains give termination; monotonicity
  gives confluence (agenda order cannot change the answer set).
- **Tabling ascends.** A table's answer set only grows; new answers join in;
  the fixpoint is "no new answers this round". The ascending thing is the
  TABLE, not the search — search is the labor that feeds the ascent.

They meet in the middle at the `Package`. This doc is about what happens when
a tabled call executes under constraint knowledge, or produces answers that
carry it.

## 2. Today: the wall

Two guard pins keep the machines apart
(`TabledTest.shouldRejectTabledCallsUnderNonProjectableConstraints`,
`shouldRejectNonProjectableAnswerConstraints`): a tabled call under an
active constraint store throws, and a tabled answer whose variables carry
residue throws. The wall is loud, cheap and sound. AS BUILT (stage 1): the
CALL-side wall is now
PER-STORE — a non-empty store implementing `Projectable` participates (its
residue keys the call); one that cannot project still refuses loudly. AS
BUILT (stage 2): the ANSWER-side wall is per-store the same way — live
projectable knowledge RIDES the answer as its residues, spent bookkeeping
is waved through by `Projectable.discharged`, and only non-projectable
live knowledge still refuses. With FD and Neq both projectable, no
in-tree store is refusable — the pins keep the wall standing via a
test-local opaque store. It exists because the naive merge is SILENTLY
WRONG in three distinct ways (§3) — do not weaken the guards without
implementing this design.

## 3. Why the naive merge is unsound: three coupling points

Under constraints, both the things tabling equates stop being terms and
become REGIONS — a term plus the descending knowledge around it.

1. **Call time (completeness hazard).** Tabling asks "have I seen this call?"
   and answers by alpha-equivalence of the reified call. But
   `reachable(x), x ∈ {1..10}` and `reachable(x), x ∈ {1..3}` are alpha-equal
   terms and DIFFERENT regions. Reusing a table is sound only when the cached
   call's region CONTAINS the new call's region. A master that ran tight and
   a slave that arrives loose = the slave silently misses answers it is
   entitled to. Variant equality is correct exactly when every region is ⊤ —
   the condition the wall enforces. *(branching-as-data)* This is one
   continuum with the optimizer's variant analysis: a free arg is a ⊤
   region (one general entry), a bound arg a width-1 region (entry per
   value — the variant explosion), a call under `dom(x, 1..3)` a width-3
   region — partially-spent branching. The wall enforces "endpoints only";
   this design admits the middle; region containment is the generalization
   of the keyed-widening rule.
2. **Answer time (termination hazard).** Answers become regions too
   ("x, provided x ≠ 3"). Deduplication by alpha-equivalence must become
   deduplication by SUBSUMPTION (`x ∈ {1..5}` makes `x ∈ {2,3}` redundant but
   they are structurally distinct). Worse: tabling's termination argument is
   "finitely many distinct answers", and the ascending chain of answer-regions
   need not be finite — even with perfect subsumption-dedup, an infinite
   ANTICHAIN (infinitely many pairwise-incomparable regions, e.g. Neq minting
   `x≠1`, `x≠2`, …) ascends forever.
3. **Consumption time (the easy one).** Replaying a cached answer into a
   consumer is the MEET of the consumer's state with the answer's region —
   which is what `resolve` already does. Soundness at consumption is nearly
   free, PROVIDED the answer's residue is actually re-imposed (§5.3); the
   silent-generalization failure the second guard test pins is exactly what
   happens when it isn't. *(branching-as-data)* Consumption is easy BECAUSE
   it is data→data: `restate` moves deferred branching from table-data back
   into store-data at ZERO branches — the fifth move of the optimizer's
   conversion table ("transfer"), and the cheapest. A constrained answer
   consumes at order 1 (a post) where a ground answer materializes
   1-per-answer: `x ∈ {1..5}` replays as one knowledge injection instead of
   five branches. TCLP is defer-materialization applied to the table's own
   contents.

## 4. The key insight: the order decomposes per store

Everything hard above reduces to one operation: ENTAILMENT — `region A ⊑
region B`. And the `Package` being a product lattice means the order is
POINTWISE: A entails B iff every factor of A entails its counterpart in B.
So no cross-domain vocabulary is needed. Comparison is intra-store business;
the driver-side fold ANDs opaque per-store verdicts — the same custody
principle as the store boundary itself (stores understand their own state;
the framework combines answers it does not inspect). Pointwise is per-STORE,
not per-variable: a store's own factor may be irreducibly JOINT (the row-set
store's residue is a relation over the call vars) — native, not a violation.

**Known, accepted incompleteness:** the pointwise product order approximates
semantic entailment from below. Example: A = `x ∈ {1..5}` (FD) ∧ `x ≠ 3`
(Neq) and B = `x ∈ {1,2,4,5}` (FD, Neq empty) denote the SAME region, but
pointwise neither entails the other (A's FD factor is looser, its Neq factor
tighter). Cross-domain reasoning could see through this; the fold cannot. The
cost is only a missed cache hit — recomputation, never wrong answers. That is
the right trade for a decomposed API.

### 4.1 The term slot: normalize what has a solved form

*(Tom's question, July 2026: why is the substitutions factor privileged —
`x = 5` lands INSIDE the key term while `x ∈ {1,2}` rides ALONGSIDE as a
residue? Isn't that Byrd's unification-primacy leaking into the design?)*

Not primacy — Substitutions is a lattice like the other factors (join =
unify). The real property is that equality is the one constraint whose
SOLVED FORM is expressible in the term syntax itself: a binding set has an
MGU, and applying it (walking) rewrites a constrained term into an
equivalent PLAIN term. Domains and disequalities have no term that denotes
them, so their knowledge must ride next to the term as data. The law is
"denotable knowledge lives in the term, everything else rides as residues"
— and the engine already applies it dynamically: the moment FD knowledge
becomes term-expressible (a singleton collapse) it migrates through the
chokepoint into the substitutions factor and thence into any term that
walks it. The key just inherits that law.

Nor is the positional frame minted by the substitution store: slot i = the
i-th free variable of the walked call pattern, first-occurrence order.
Every store — substitutions included — reports against that one frame; a
`Hole` is what a slot looks like WHEN IT OCCURS INSIDE A TERM (the key's
`_.1`, a Neq record's forbidden term). Slots and holes are one
canonicalization written in two places. Holes carry three loads:

- **alpha-normal keys** — renaming by first occurrence turns
  alpha-equivalence into structural equality, so lookup is a hash hit and
  `SubsumptionMap` can index patterns at all;
- **the ∀-binder marking** — a cached answer is quantified over exactly its
  holes, and replay renames exactly those, every consumption (losing this
  marking WAS the variable-capture bug, §5.1);
- **shared cross-store coordinates** — the residue slot spaces.

The rejected alternative is coherent and MORE uniform: key on the UNWALKED
pattern plus a Herbrand residue ("slot 0 = 5") and match entries by
entailment across every factor alike. The current scheme is exactly the
optimization that uniform design admits: normalize what has a normal form
(hash hit), entail only what doesn't (subsumption scan). Tabling does not
assume equality's primacy; it exploits equality's solvability.

**The layer beneath solvability** (Tom, July 2026 — the full account is
lattice.md §5a, "the junction, per store"): the term syntax has no ∨, so
"has a term normal form" = "disjunction-free". Substitutions is
disjunction-free by architecture — unitary unification never creates
alternatives, and every explicit ∨ (`conde`) exports to the search's ⊕ —
so the store keeps its normal form because non-normalizable knowledge
leaves as ALTERNATE ANSWERS. Domains and disequalities are compressed ⊕
(finite and cofinite respectively) and so cannot be terms; they ride as
residues. The term/residue boundary sits at "where unification stays
unitary" and would move with the theory.

**The residue predates TCLP** — as display. `NeqConstraints.reify`'s
`Constrained` output IS `(term, residue)` rendered for a human: purify
hole-renames the records against the answer frame and DROPS every record
touching an unprojected var — `project`'s widening-drop by another name.
The `=/=` display was a conditional answer that could only be read; stage
2 made the same object replayable (answers-as-diffs' "one operation
behind reification"). Re-expressing `reify` over `project` is recorded
future unification work.

## 5. The design: three intra-domain hooks

`Propagation` does not change at all — tabling does not route through the
driver (`Table` is a plain store). The whole feature is `ConstraintStore`
growing three OPTIONAL hooks, plus logic in `Tabling`:

### 5.1 `project(vars, state, renaming) → Residue`

"My knowledge about these variables, canonically renamed" — an opaque,
store-specific value. Half of this exists: `reify` already renders residue
against a rename substitution; this is its data-shaped sibling. AS BUILT: POSITIONAL instead of renamed —
`project(List<LVar>)`, residue slot i = the hole the key names `_.i`
(`reifyWithHoles` derives the order from the one rename pass), so no renaming
machinery crosses the store boundary. `project` of the empty list is ⊤, and
⊤ residues stay OUT of keys — calls under irrelevant knowledge stay
constraint-free variants. THE CONTRACT (third revision — Tom's:
parameter over flag over throw): `project(vars, wideningAllowed)`
TRANSCRIBES everything expressible — domains AND wholly-covered couplings
alike; permission to widen is not an instruction to widen. The parameter
governs only the INEXPRESSIBLE REMAINDER (a coupling escaping to an
unsupplied local): dropped silently when allowed, THROWN when exactness
was demanded — the parameter carries the boundary's context INTO the
store, which is what legitimizes the throw (the earlier objection was
that the store lacks the context to refuse; now the demand is explicit).
The store still never learns WHICH side it serves — only the strength
demanded. `isWidened` is GONE: no flag, no advisory metadata, no equality
exclusions. Couplings are carried as the LIVE PROPAGATOR OBJECTS plus a
watched-var → slot map — no factory recipes, no shape tokens, no custom
equality; default object identity is the membership base. The residue
restates itself (`Residue.restate`), and replay is a RENAMING — a
conditional answer is ∀-quantified over its holes, so every consumption
instantiates a fresh copy of the conditions. Propagator bodies are
POSITIONAL (`(watched, pkg) → Verdict`, no lexical capture), so
answer-side replay registers the propagator REBUILT over the fresh holes
(`Propagator.watching`); the call side is an identity renaming and
re-activates the live object ITSELF — which is what lets a recursive
call project the same object and land in its creator's entry
(`recursionUnderACarriedCouplingSharesItsEntry`). The superseded
alias-unify replay (fifth revision retired it) was VARIABLE CAPTURE:
two consumptions of one answer in a query welded onto the shared
original vars and only the diagonal survived
(`twoConsumptionsOfACoupledAnswerAreIndependent` pins the fix).

### 5.2 `entails(mine, other) → boolean`

The intra-domain order — and NOT actually a new word *(July 2026,
Residue = Domain)*: entailment is FREE from the meet — `A ⊑ B` iff
`A.intersect(B).equals(A)` — and the kernel has computed exactly this all
along: `DomainUpdate`'s equal-domain termination guard IS the entailment
test `dom ⊒ previous`. For FD the whole §5 hook set is exposure, not
machinery: Residue IS `Domain` values (project), `restate` is the public
`dom` factory, `answers` is the width `Bounded` wants — one object serves
TCLP and the optimizer. The §5.5 gate then reads structurally: a store
participates iff its knowledge factors into Domain-like lattice values
(meet → entailment, width → pricing, statement form → restate); Neq fails
for lack of exactly this. Follow-up (same conversation): with `Lattice<L>`
   F-bounded on the VALUES (`Domain implements Lattice<Domain>`), this
   hook is SUBSUMED — comparison is value-side, written once in the
   driver's fold; only `project` (and conditionally `restate`) remain
   store-side. See `lattice.md` §6. This is also the deferred `Lattice<L>`'s
adoption moment ("adoption not rewrite, when a customer exists" — the
customer arrived twice at once); Domain is the prototype instance. For FD: domain-wise ⊆.
For Neq: record-set implication (hard in general; see §6). Reflexive,
transitive; `entails` need not be complete (a conservative `false` costs
reuse, not soundness). AS BUILT: subsumed exactly as predicted — residues are
`PartialOrder` values (leq ALONE; the meet is not demanded, since consumers
only compare — and `Comparable` was rejected: a total order cannot express
incomparable regions). One driver-side `leq` is the whole fold. The
TERMINATION gate was DROPPED AS A TYPE (Tom's ruling): infinite residue
antichains are program misconstruction, exactly like tabling an unbounded
generator — never statically rejected elsewhere. Finite-lattice-ness is a
documented per-store SUFFICIENT CONDITION; `Projectable` is the SOUNDNESS
gate (unprojected knowledge cannot be keyed); Neq is admissible whenever
someone gives it project/restate over its record-set meet-semilattice.

### 5.3 `restate(residue) → Goal`

Turn my residue back into statements through the normal public entries
(`resolve`/`activate`/`narrowed`), so a consumer replaying a cached answer
re-imposes its guards — the meet-at-consumption. Without this hook, cached
answers silently generalize (the second guard test's scenario). AS BUILT:
restate is ALSO the call-entry hook — the master runs FROM THE KEY (the
caller's constraint stores stripped: absence is ⊤, posting re-registers; the
key's residues restated ahead of the body), so the cache holds exactly the
region the key names and every caller, the first included, filters at
consumption by its own state. The master-from-key pin: two callers share a
key, one privately coupled — the cache must hold the key's answers, not the
coupling's subset.

### 5.4 What Tabling does with them

- **Key** = (reified args, map storeClass → residue), residues projected onto
  the call variables at call time.
- **Call matching, stage 1**: exact residue equality. Sound, complete per
  key, least reuse, trivially terminating in the key space iff residues over
  fixed vars are finitely many.
- **Call matching, stage 2 (optional upgrade)**: pointwise-⊑ subsumption —
  reuse an entry when its call region contains the new call's; the consumer's
  own tighter state filters answers at consumption for free.
- **Answers** = (reified term, map storeClass → residue); replay =
  instantiate + `restate` each residue. Dedup by pointwise entailment.
- AS BUILT (stage 1): `Call` carries `(relation, reified args, storeClass →
  residue)` with exact residue equality; subsumptive reuse is
  CONSTRAINT-FREE-ONLY — positional slot spaces do not align across
  different hole counts, so region containment between constrained calls
  waits for stage 3's correspondence machinery.
- STAGE 2 DECIDES (Tom): matching is ENTAILMENT, not equality — use ANY
  entry (open or sealed alike; joining a wider in-progress entry is sound
  by the subset property, mid-stream) with the same relation, Herbrand
  args-subsumption, and `caller.residue ⊑ entry.residue` (the containment
  law verbatim: caller knows at least as much, entry's region covers
  caller's); else mint. Exact equality survives as the hash fast path
  (same store state ⇒ identical residue ⇒ equal key). Carried-coupling
  entailment is IDENTITY-CONSERVATIVE: down a recursion the store holds
  the LITERAL SAME propagator object (posted once, persists until
  discharged), so recursive variants under one constraint context share
  their entry — the reuse TCLP exists for — while two INDEPENDENT posts
  of a same-shaped coupling are incomparable and recompute: a conservative
  false costs reuse, never soundness. This unifies variant matching, the
  sealed-subsumer path and the old constraint-free-only rule under one
  containment check, and it is stage 3 arriving early in conservative
  form; semantic coupling-entailment (shape tokens) is the optional later
  strengthening, not a prerequisite. Racing minters may create comparable
  entries — benign, both truthful.

### 5.5 The termination gate

Entailment enables subsumption; it does not bound ascent. Participation must
be gated per store on a declared property: **"my residues over a fixed
variable set form a finite lattice"** (equivalently: no infinite antichains).

- FD qualifies: residues are domain assignments over a finite universe.
- Neq does NOT: records range over an unbounded value space (`x≠1`, `x≠2`, …
  is an infinite antichain). Neq participation requires a WIDENING (collapse
  record sets above some size to ⊤, trading deduction for termination) —
  a separate design decision, not assumed here.

AS BUILT (July 2026): Neq PARTICIPATES, without the widening — records
transcribe positionally (LHS var → slot, RHS terms hole-renamed: pure data,
lineage-free, so independent same-shaped contexts project EQUAL residues
and share entries — `TabledUnderNeqTest`). The finite-lattice gate is NOT
enforced as a type: `Projectable`'s javadoc declares termination the
author's responsibility, exactly like tabling an unbounded generator. The
widening remains the upgrade if a workload hits the antichain.

A store that declines the gate keeps today's wall; the guard tests become
per-store rather than global.

*(branching-as-data)* The gate is an instance of the direction rule
(`fixpoint-machine.md` §10) — and the danger analysis above omits the
symmetric BENEFIT: answers-as-data also SHORTENS the ascending chain (one
region-answer subsumes many ground answers → fewer entries → earlier
completion), and completion is what turns a tabled call into the
optimizer's exact pricing oracle. Regions can lengthen the ascent
(antichains) or shorten it (subsumption); the finite-lattice gate is
exactly the line between the two cases. Also note the API convergence: the
hook set here (project/entails/restate) and the optimizer's store
capabilities (answers/Forcing) are one per-store,
driver-folds-opaque-verdicts family — a `Residue` that knows its WIDTH
serves TCLP keys, subsumption dedup, AND the pricing of consuming that
answer. Design them together when either is built. (The
suspension≡consumer triple survives the merge unchanged: consumers of
constrained entries still wake on an upward-closed condition over table
growth.)

## 6. Staging, if ever implemented

1. **DONE (July 2026).** Hooks + FD-only, exact-equality keys, unconstrained
   answers still rejected. (`TabledUnderDomainsTest` pins it.)
2. **DONE (July 2026).** Constrained ANSWERS for FD (fourth revision built,
   fifth corrected replay; the recipe/flag/alias drafts are superseded):
   `project(vars, wideningAllowed)`
   transcribes all expressible knowledge — domains plus covered couplings
   as (LIVE PROPAGATOR OBJECT, watched-var → slot map), default identity,
   no recipes, no flags, no custom equality. ALWAYS-PORT-ALL holds: one
   projection rides both boundaries; the call side passes
   wideningAllowed=true (escapes drop — caller-private, sound by
   containment, filtered at consumption), the answer side demands
   exactness (escape to a body-local THROWS at produce: label the local or
   lift it into the answer). Master restate is an identity renaming — it
   re-activates the carried objects themselves; answer replay
   instantiates fresh holes and registers the propagators REBUILT over
   them (§5.1 — the alias-unify draft was variable capture). Delivery
   itself is goal-shaped: consume unifies args through `Constraints.unify`
   (the chokepoint), so the caller's stores revise on delivery like any
   other statement. Answer DEDUP is the leq insert-guard:
   append-only, a new (term, residues) answer joins iff no existing
   same-term answer's residue entails it — no retraction (delivered
   answers stand; indexes stay monotone), no equality anywhere
   (PartialOrder suffices; identity is the membership base within an
   entry). `discharged` is the ground-answer fast path. Purely-local
   DOMAINS have a decidable witness and drop. Matching is entailment
   (§5.4). Closed/star mode is excluded — orthogonal concern, refused
   loudly until designed.
3. Pointwise-⊑ call subsumption — landed early with stage 2 in conservative
   form (§5.4: entailment matching, open-entry joining); the optional
   strengthenings (shape tokens, cross-hole-count alignment) wait for a
   workload that misses them.
4. Neq — SHIPPED UNWIDENED (July 2026, §5.5 AS BUILT): transcribed record
   residues, termination the author's responsibility. The widening remains
   the upgrade, still gated on a motivating use case.

Each stage lands green with the guard tests refined, not deleted.

## 7. What this is for (the use case that would pay)

Memoizing pruned subproblems: a tabled recursive relation whose calls carry
domains caches "the answers to this subproblem GIVEN this region" — e.g.
partial schedules keyed by their temporal bounds, reachability under
resource windows, grammar/analysis fixpoints with value constraints. This is
TCLP (tabled constraint logic programming — XSB's TCLP, Mod-TCLP of Arias &
Carro), and the hook set in §5 is deliberately the same shape Mod-TCLP
requires of a pluggable domain: projection + entailment (+ our restate,
which they fold into answer resumption). The lattice reading and the
literature agree on the interface; that convergence is the main evidence
this design is the right shape.

## 8. Non-goals

- No cross-domain semantic entailment (§4's accepted incompleteness).
- No changes to `Propagation`, the agenda, or the store boundary's three
  triggers — this composes beside them.
- No general assert/retract: tabling (and `constrain`-mode queries, see the
  pldb notes) assumes facts and rules are immutable per solve. Dynamic
  programs break monotonicity and are out of scope everywhere in this engine.
