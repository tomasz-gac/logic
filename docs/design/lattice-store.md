# The lattice store — DESIGN (July 2026, nothing built)

The generalization the single-sorted refactor already performed without
saying so: `FiniteDomainConstraints` is "a lattice store" — a map from
names to values of a meet-semilattice, plus named propagators, plus the
boundary algebra — with the finite-domain specifics confined to a few
seams. This doc records the extraction (`LatticeStore<L>`), its dual
sibling (the co-store, generalizing Neq), the instance catalogue, the
theorems that license what the store family promises, the speculation
tier built once over the abstraction, and the cross-store doctrine.
Status: DESIGN. Sequencing is §9 — the extraction happens INSIDE #61,
per the second-implementation rule; nothing here is speculative build.

## 1. The claim

After Term-keying, value-equal named propagators, and the boundary
algebra (split/rename/normalize + project derived), the FD store's code
demands of `Domain` only: MeetSemilattice + Bottomed, a membership test,
an optional point-collapse, an optional enumeration, and a stabilization
guard. Everything else — the name→value map, pointwise meet, slotwise
leq, the propagator kernel (named, value-equal, watched, cascaded,
deduped), revise/normalize/absorb, canonical marshal, tabling and caveat
integration — never mentions finite domains. That residue IS the generic
store; a new value domain costs its lattice and its propagator library
and inherits keys, conditional answers, seeding, splitting, persistence
and the speculation tier without integration code.

## 2. Anatomy: the capability record

    LatticeStore<L extends MeetSemilattice<L> & Bottomed>

    per-L capabilities:
      contains(L, value)        REQUIRED — revise's verification
      asPoint(L) → Option<v>    optional — collapse to inferred binding
      stream(L)  → values       optional — labelling/enforce (finite only)
      stabilized(L, L') policy  REQUIRED — the termination guard:
                                exact equality (finite descent) or
                                ε/widening (infinite chains — reals)
      width(L)   → estimate     optional — the optimizer tier's metric (§7)
      difference(L, L)          needed by the co-store's unit propagation

The capability record is the ADMISSION TEST: it sorts every candidate
domain in one glance (sets: everything; reals: no stream; labels: no
stream, no point, pure meet) and it is where an instance's hazards are
declared rather than discovered.

## 3. The instance catalogue

By cost tier; each entry: why wanted, what it costs, its hazard.

**Nearly free (a Domain implementation only):**
- **General enums** — lift EnumeratedDomain's Arithmetic requirement;
  any finite value set (states, roles, categories); booleans become the
  2-element case, unit propagation falls out of the ordinary wake
  machinery. Needed by pldb columns immediately.
- **Sets** — CP set variables: L = [must ⊆ S ⊆ may], meet =
  (must ∪, may ∩); propagators ⊆/∪/∩ + a cardinality bridge to FD.
  Tags, group memberships, role sets.
- **Versions** — semver ranges as interval lattices; with the row-set
  store this is dependency resolution (package managers are constraint
  solvers).
- **Security labels** — clearance lattices; labels meet along
  derivations; answers gain "classified ≤ L" caveats. Information-flow
  control as a store; thematic fit with domain-layer.md's example.

**A propagator library away:**
- **Strings, modest** — length as an FD int (a store bridge THROUGH a
  shared variable, not a data bridge), known prefix/suffix, charset;
  enough for validation caveats and LIKE pushdown. The full
  regular-language lattice has infinite descent and wants widening —
  parked.
- **Difference constraints (DBMs)** — the relational domain over
  x − y ≤ c; the store factor is a difference-bound matrix, propagation
  is incremental shortest paths; subsumes pairwise precedence reasoning
  GLOBALLY. The scheduler's real upgrade. The architecture already
  admits joint factors (the row-set precedent).
- **Units/dimensions** — a small lattice threading addo (same unit) and
  mulo (unit product); catches a whole bug class.

**Policy work required:**
- **Reals** — interval domains with OUTWARD rounding; propagators are
  the existing interval arithmetic lifted over Arithmetic; no stream
  (reals cannot label — and need not: conditional answers carry the
  interval as the caveat; the store whose enforcement is impossible is
  the store the caveat machinery was built for); no exact stabilization
  (ε-policy or widening — the one research-adjacent hazard; wants a
  declared precision, not cleverness). Answer shape: narrowed boxes PLUS
  the carried coupling — the box is the hull, the constraint keeps the
  answer exact (x ∈ [2.0,3.8] ∧ y ∈ [2.8,4.6] ∧ add(x,y,6.6)).
- **Time** — mostly NOT a new store: instants are the long instance,
  windows its intervals, precedence the existing leq propagators;
  domain-layer.md's validity/lease caveats land natively. Allen's
  relation algebra is an optional later lattice.

**The extraction driver:**
- **Row-set store (#61)** — L = powerset-of-rows, meet = intersection,
  contains = membership, stream = rows, propagators = per-column
  projections (GAC, affordable for extensional constraints — §6). The
  SECOND INSTANCE that forces the abstraction honestly; FD re-seats as
  the first.

## 4. The co-store: negative knowledge generalized

Neq's data is join-shaped — records accumulate by union while the
denoted region shrinks; negation flips the lattice (data→meaning is
antitone). The general citizen of that flipped side:

    record:  ¬( x ∈ L₁  ∧  y ∈ L₂  ∧  z = t )     — an EXCLUDED BOX,
    store:   a set of records; meaning = complement of their union

De Morgan: a record IS a clause of negated lattice literals
(¬A ∨ ¬B ∨ ¬C); the store is a CLAUSE STORE, and its natural inference
is unit propagation — when all components but one are entailed by
current knowledge, the last fires its complement into its variable via
`difference`. Consequences:

- **Not a wrapper over LatticeStore.** The positive store lives in the
  product lattice ∏L (one value per name, pointwise meet); the co-store
  lives in the FREE join-semilattice over boxes (finite unions of
  products). A joint exclusion is irreducibly joint — ¬(x∈A ∧ y∈B) is
  no per-name assignment — so no complement/dual wrapper represents it.
  Single-name exclusions don't need the store at all (domain
  difference). Two small generics, sharing the component lattices, the
  boundary algebra (meet = record union, leq = containment, split by
  support — Neq passes this gauntlet today), and the law kits.
- **The build is a LIFT of Neq, not a new store**: widen record pairs
  from (name, term) to (name, L), generalize verifyAndSimplify from
  unification-based to leq/disjoint-based, add unit propagation. Neq
  becomes the point-lattice instance. Maintenance delta ≈ zero: Neq is
  the co-store today, in point-flavored form.
- **Producers**: exclusion FACTS (deny lists, SQL EXCLUDE constraints —
  "no overlapping bookings" is ¬(room=r ∧ overlap) with an enum side
  and a time side: the scheduler's resource constraint as data);
  CONSTRUCTIVE NEGATION (a negated goal over a SEALED entry returns the
  complement region as records — negation that answers with constraints
  instead of silence); someday, learned nogoods (§10).
- **The dropped Neq→FD bridge returns as native inference**: unit
  propagation of a generalized record IS the bridge's deduction
  (x∈{4,5} ∧ x≠5 ⊢ x=4), no longer bolted pairwise but the store's own
  law. (Foreclosure honored: no pairwise bridge code exists; see §7.)
- **Hazards inherited from Neq**: record sets grow without bound (the
  antichain — the finiteness gate stays the author's responsibility;
  widening is the upgrade); full GAC over clauses is expensive — unit
  propagation is the honest first tier; cross-lattice literals in one
  record work on paper and wait for a second use case.

## 5. The theorems (what licenses what)

Assembled from lattice.md's license table and the tabling-reuse line;
recorded here because the store family's promises rest on them.

- **Seeding (answers carry across monotone growth)**: the positive
  fragment (facts, ∨=∪, ∧=join, ∃, recursion=lfp) is a composition of
  monotone maps on the ⊆-lattice; Knaster–Tarski makes the fixpoint
  monotone in the base facts. B ⊆ B′ ⇒ F(B) ⊆ F(B′): old answers are
  members of the new set. Idempotence (x⊕x=x) makes re-derivation
  harmless — the dedup insert-guard is idempotence made operational.
  Object-level negation (Neq/co-store caveats) is INSIDE answer
  elements, invisible to ⊆ — caveated answers seed soundly. The bane is
  negation-AS-FAILURE (claims about fact-base completeness), which is
  antitone and seal-gated.
- **Folding (accumulators carry)**: an aggregate is incrementally
  maintainable iff it factors as a commutative monoid homomorphism
  h(B ⊎ Δ) = h(B) · h(Δ). max/min: idempotent folds (duplicates
  harmless); sum/count: non-idempotent (event identity in terms is
  load-bearing); median/mode: not folds — recompute-only. Aggregates
  may never ride seeding: their answers are REPLACED, not extended —
  the value embeds into the answer lattice as incomparable points
  (monotone into (ℕ,≤), non-monotone into ⊆).
- **The misfit census** (what doesn't fit a semilattice store, by
  broken law): non-idempotent combination (resources, costs,
  probabilities) → the SEMIRING side — the weight package's
  jurisdiction, the soft-CSP/c-semiring boundary from the literature;
  order-sensitive combination (true cut) → Barriers, or reify priority
  as data and re-enter the lattice; retraction → epochs, not stores;
  strategies (QCSP, games) → genuinely outside — an external oracle via
  Fiber.external. Global constraints and fuzzy/possibilistic combos are
  false alarms (propagator intelligence / idempotent min-max). The
  boundary is the idempotence line the quotient tower already drew.

## 6. Consistency strength and the speculation tier

**Cascade-to-quiescence is the fixpoint EXECUTOR; GAC is a pruning
CONTRACT.** Chaotic iteration makes the mutual fixpoint of the installed
propagators unique and order-independent; it says nothing about their
strength. Bounds reasoning on x·y=6 over {1..6} prunes nothing; GAC
(every value has a supporting tuple) prunes to {1,2,3,6}. Strength is
chosen per constraint shape: tables → full GAC (support = a row —
affordable; the row-set store's contract); arithmetic → bounds/hull
(the classic trade); alldifferent-class → dedicated algorithms if a
workload pays. Even all-GAC ≠ global consistency (the pigeonhole
island: locally content, jointly dead) — the remainder is search's job.

**Speculation: the consistency hierarchy is a budget ladder.** GAC
speculates within one constraint (a support is a micro-branch); SAC
probes one variable against the WHOLE store (assume x=v in a sandbox,
cascade, wipeout ⇒ prune v); shaving is SAC at bounds (the scheduling
workhorse); search is speculation with commitment. Soundness is proof by
cases: anything true in every branch of an exhaustive split is true
outright — the JOIN of branch outcomes; ACI licenses merging probes in
any order, hence in parallel. Constructive disjunction is the same move
for ∨: sandbox each disjunct, join, continue in ONE search node.

**Economics (why probing is rationed, never default):** a probe and a
search branch cost the SAME cascade; the difference is what is bought.
A branch buys the whole subtree under the assumption; a probe buys only
the cascade and discards. And the goods differ in kind: search's
refutation is path-local control flow (re-discovered per context, up to
2^k times); a probe's refutation folds back as a DOMAIN FACT — global,
compounding, multiplicative. Probing is profitable exactly where
converting a refutation into data has resale value: probe cost |D|·cascade
vs expected subtree savings. Hence: opt-in tier, bounds-first
(shaving), targeted by width × watcher-count, run at the root and
sparingly. The engine is an unusually good host: persistent packages
make sandboxes free (branch, cascade, inspect, discard — no trailing),
and a probe is implementable as an ordinary named propagator — no
kernel change.

**The pigeonhole falls to one probe round**: assume z₁=1 → z₂=2 → z₃
wiped; both values die; the island that only labelling could kill dies
at speculation. The canonical advertisement.

## 7. Cross-store doctrine

The kernel's earned rule stands: **cross-store consequences ride
bindings, full stop** — pairwise data bridges were built, measured,
dropped, and are foreclosed. The generic tier honors the rule twice:

1. **The probe is the universal, sanctioned bridge.** A probe is a
   binding through the chokepoint in a sandbox; EVERY store reacts by
   its ordinary revise; a wipeout from any store prunes the probed
   value in its own. The foreclosed Neq→FD inference (x∈{4,5} ∧ x≠5 ⊢
   x=4) returns generically, for every store pair at once, priced by
   probe budget instead of hidden in bespoke plumbing.
2. **Optimization is shared MEASUREMENT, not shared data.** The width
   capability (§2) moves metrics — never knowledge — to the driver:
   first-fail variable selection across all stores' views of a name;
   probe targeting (width × watchers); agenda cost policy (cheap
   propagators first, declared per store class); store-sighted pricing
   generalized beyond FD, feeding the join-ordering optimizer alongside
   FactSource's count(pattern). Custody preserved: stores understand
   their own state; the framework combines judgments it does not
   inspect.

This section is the PROPOSED SCOPE OF #63 (the post-TCLP optimization
slot): width + first-fail, probe/shaving as an opt-in propagator,
agenda policy, generalized pricing — all gated on #61's extraction.

## 8. Negative facts and tabling reuse (cross-ref)

The reuse stratification (domain-layer.md's write path relies on it):

| stratum | example | reuse across pins |
|---|---|---|
| monotone core | closures, joins, caveated answers (Neq incl.) | seed freely |
| antitone FILTER facts | deny lists, revocations, exclusions AS DELIVERY FILTERS | cache untouched; reload the filter store (bulk absorb) |
| NAF + aggregates crust | "no X exists", counts, in-derivation pruning | seal-gated; recompute per pin |

The middle stratum is the co-store's operational role in pldb: exclusion
facts load as caller-side records; tabled entries stay WIDE and
constraint-free (keys unfragmented); the containment law serves the
narrower caller; consumption's chokepoint verification fails denied
deliveries — shipped machinery at point granularity today. A new denial
is a filter change, not a cache invalidation. The boundary that must be
DECLARED per negative table: filter-at-delivery (cache-friendly) vs
prunes-the-derivation ("paths avoiding denied nodes" — genuinely
antitone in the cached relation; crust rules apply).

## 9. Sequencing

1. **Extract inside #61** (the second-implementation rule): LatticeStore
   carved from FDC; FD re-seats; row-set lands as the forcing instance
   with GAC-in-normalize and absorb as its front door.
2. **Co-store lift** from Neq when its first producer lands (exclusion
   facts in pldb, or the scheduler's resource constraints).
3. **Instances by use case, never speculatively**: enums (pldb columns —
   likely first), sets, versions, labels; DBM when the scheduler
   returns; reals when a measurement domain arrives (with the declared
   ε); strings-modest for validation caveats.
4. **#63 as §7**: width, first-fail, shaving, agenda policy, pricing.
5. Laws: one parametrized kit run per instance (meet laws + capability
   contracts); the split law _1 ∧ _2 = this pinned per store.

## 10. Non-goals

- **No CDCL**: reason-tracking (an implication graph through
  propagation) is kernel-wide provenance work; backjumping assumes a
  chronological stack the fiber scheduler doesn't have. The co-store is
  the VESSEL learned nogoods would pour into (monotone, cross-branch by
  the Table mechanism) — the vessel is cheap, the learning is not.
  Industrial SAT-class needs go to a solver via Fiber.external.
- **No QCSP/strategy domains** — outside the region semantics entirely.
- **No default-on probing** — §6's economics; opt-in, budgeted,
  targeted.
- **No pairwise store bridges, ever** — §7's doctrine; the probe and
  shared variables (the string-length bridge pattern) are the sanctioned
  channels.
- **No speculative instances** — the catalogue is a menu, not a
  backlog; each row waits for its use case.

## 11. Where knowledge lives

constraint-kernel.md (store contracts, the chokepoint, absorb) ·
lattice.md (the license table §5a, the quotient tower, speculation §5c —
the theory this doc instantiates) · tabled-constraints.md (regions,
witnesses, the containment law §8 leans on) · domain-layer.md (caveats,
epochs, the write path; §8's strata serve it) · semiring-inference.md
(the other side of the idempotence boundary — soft/weighted constraints'
jurisdiction) · the dropped-bridge lineage lives in
constraint-kernel.md §7's foreclosure record.
