# Glossary — every term the docs are allowed to use

**How to rate** (the human's pass): edit the checkbox on each line —
`[x]` solid (I hold this), `[~]` fuzzy (re-explain when touched),
`[?]` don't recognize it. Unrated = unreviewed. A `[~]` or `[?]` next to
a term is a standing work order: the next doc or conversation touching it
starts with a plain-words re-explanation.

**The rule this file enforces**: docs may only use terms listed here.
A new term enters by explicit adoption — proposed in conversation,
ratified by the human, then added with its one-liner. Imported theory
(anything with a literature name) is marked ⋯import and must trace to a
theorem-import receipt before a doc leans on it. Renaming an existing
concept requires retiring its old entry, never aliasing.

Sections run from oldest/most-settled to newest/most-speculative, so a
rating pass can stop at any section boundary and still be useful.

---

## 1. Core engine

- [x] **Goal** — a function `Package → Cont<Package, Nothing>`; success calls the continuation, failure stays silent. *(CLAUDE.md)*
- [x] **Package** — the immutable solver state: substitutions + stores; branching is free because each branch keeps its own. *(CLAUDE.md)*
- [x] **Cont** — the stack-safe continuation monad goals are built from. *(functional README)*
- [x] **Term / Unifiable / Reified** — structural root / solver input / solver output; `instantiate` is the only Reified→Unifiable bridge. *(CLAUDE.md)*
- [x] **LVar / LVal** — logic variable / wrapped value. *(unification/)*
- [x] **Hole** — a canonical slot name (`_.n`); what a slot looks like inside a term; carries alpha-normal keys and the ∀-binder marking. *(tabled-constraints §4.1)*
- [x] **Unknown** — the one name type: what a substitution may key and a walk may chase. `LVar` (live, identity) and `Hole` (canonical, numbered) implement it; the unifier still binds only LVars — Prefix keeps the privilege. *(unification/)*
- [x] **walk / walkAll** — resolve a term through substitutions, shallow / deep. *(unification/)*
- [x] **reify** — render an answer against canonical names; constrained stores attach their residuals. *(unification/)*
- [x] **alpha-equivalence** — equality of reified terms up to variable naming; plain `equals` on `Reified`. *(unification/)*
- [x] **Prefix** — a checked set of new bindings; mintable only by the unifier or `Prefix.binding`. *(unification/)*
- [x] **the chokepoint** — `Propagation.resolve`: the ONLY way substitutions grow in constraint-aware code; two coequal reasons — the veto (any store may fail the branch) and the wake (the only way other stores hear at all; bypass fails silently). *(constraint-kernel)*

## 2. Fibers and completion (the substrate)

- [x] **Fiber** — a computation describing its own control flow; recursion as heap data, pausable, forkable. *(functional README)*
- [x] **Frame** — one runnable unit a scheduler steps; owns its local trampoline. *(fibers/interpreter)*
- [x] **scheduler / driver** — a policy over one step interpreter deciding which frame steps next; swapping never changes the answer set (for pure programs). *(functional README)*
- [x] **Channel** — a monotone value plus the workforce producing it; growth is semilattice join, an absorbed delta refuses, growth wakes parked waiters. *(emit.md)*
- [x] **Scope** — the workforce ledger: two monotone counters (started/finished) plus parked-sleeper records; quiescence seals it. *(completion.md)*
- [x] **seal** — the computed moment no work can ever grow a channel again; completes every waiting consumer with the final value. *(completion.md)*
- [x] **group seal** — sealing a ring of mutually-waiting scopes together; the closure walk with a two-phase counter snapshot. *(group-seal.md)*
- [x] **produce / claim** — claimed production: a compare-and-swap where the first claimant's body becomes the channel's workforce; losers no-op or run an alternative. *(emit.md)*
- [x] **Emitter** — the one typed door for emission, minted by produce. *(emit.md)*
- [x] **await** — park a frame at a channel until a readiness predicate holds of the grown value; predicate must be upward-closed. *(await.md)*
- [x] **drained / exhaustion** — observing that a sub-computation finished without (or with) delivering; how failure-as-silence becomes observable. *(await.md)*
- [~] **strand refusal** — a drive out of work with a consumer still parked refuses loudly and NAMES the channel it starved at. *(completion.md)*
- [x] **"the ledger is the work"** — billing by construction: the counters are not tracking work, they are what work is. *(completion.md)*
- [x] **ambient scope** — the frame's current workforce, inherited at every fork: assigned once at claim, it reaches any helper at any depth with no passing — work bills to frames because packages branch (a package-threaded ledger would fork with the world and never agree). *(table-completion)*
- [x] **run lane** — where woken suspension bodies go: spliced AFTER the drain quiesces, so growth happens between fixpoints, never inside one. *(condition.md §8.1)*
- [x] **Worklist** — drain-to-quiescence as a fiber, so long cascades stay fairly stepped. *(functional)*
- [x] **chaos harness / RandomizedScheduler** — seeded random frame choice; order-independence becomes a testable property across seeds. *(schedulers/)*
- [x] **fairness valve / promotion** — the UNFAIR scheduler's pour: a long-running shallowest bucket merges downward at the priced threshold — part of the tuned search shape, not just a rescue; honest BFS keeps only the crash hatch (a dead level pours after 10k no-progress steps). *(UnfairBreadthFirstScheduler)*

## 3. Constraint kernel

- [x] **stores are branches as data** — the founding sentence: a constraint store is a compressed set of branches; finite compression exits by EXPANSION (enforce/labelling), infinite compression exits by EXPRESSION (reify/`Constrained`); the compression is also what the optimizer and the crossings move around. *(condition.md)*
- [x] **Constrained** — the rendered answer-with-residuals carrier: reify's output when expressed infinities ride the term. *(separate/)*
- [x] **ConstraintStore** — a store with the two triggers (revise, stated) answered by `Fiber<Revision>`; may read anything, may swap only its OWN factor. *(constraint-kernel)*
- [x] **revise** — bindings arrived; the store's COMPLETE reaction (custody, own watchers, own cascade). *(constraint-kernel)*
- [x] **absorb** — meet a whole factor into the resident store + queue normalize; the bulk statement entry. *(constraint-kernel)*
- [~] **normalize (store)** — re-establish normal form after a meet brought foreign knowledge; may fail. *(constraint-kernel)*
- [x] **Revision** — a store's answer: unchanged / fail / updated(own factor + consequences: inferred prefixes, runs). *(constraint-kernel)*
- [?] **custody** — a store understands only its own state; the driver combines verdicts it does not inspect. *(constraint-kernel)*
- [x] **factor** — one store's slice of the package's knowledge product. *(constraint-kernel)*
- [~] **Watches** — the shared chain matcher: which items wake on which terms. *(constraints/store)*
- [x] **suspension** — a parked (terms, ripeness, body) triple owned by the DRIVER, not a store: Propagation parks it, ripens it after bindings, splices the body into the run lane. *(condition.md §8.1)*
- [x] **ripeness** — the condition under which a suspension's body may run (e.g. deep-groundness). *(projection/)*
- [x] **labelling** — enumerate remaining domain values as branches at answer time; deferred materialization's endpoint — the finite door's mechanism. *(finitedomain/)*
- [x] **enforce** — the FINITE exit: per-answer commit at the end of a branch, where compressed branching expands — FD labels, projections fail if unrun; the branch's own seal. *(ConstraintStore)*
- [x] **narrowing wake** — constraint bodies wake when domains shrink, not only on bindings. *(CLAUDE.md landmines)*
- [~] **trial unification** — Neq's check: try the forbidden unification on the side and observe. *(separate/)*

## 4. Algebra and capabilities

- [~] **Semilattice** — ONE idempotent-commutative-associative op (`combine`) and the accumulation order it induces; deliberately direction-unnamed (meet and join are the domain's readings). *(functional algebra)*
- [x] **absorbedBy** — "I contribute nothing other lacks": combine(x,y)=y; an order, but which entailment reading it carries depends on direction. *(Semilattice)*
- [x] **PartialOrder / leq** — the entailment order alone: a ⊑ b = "a knows at least as much"; the direction commitment a bare Semilattice withholds. *(functional algebra)*
- [x] **meet / join** — narrow by conjunction / widen by union; every store accumulates by meet; join is the second face, exposed as a projection when needed. *(lattice.md)*
- [x] **Domain\<L\>** — what a LatticeStore requires of a per-name value: meet, order, membership, collapse-to-point, stabilization — the admission test as a capability record. *(lattice/)*
- [~] **Absorbable** — the arrival capability: pure `meet` + `normalize`; store-level Semilattice+PartialOrder. *(constraints/store)*
- [x] **Projectable** — the departure capability: `split` (lossless factoring over vars) + `rename`; project = split∘canonical-rename. Participation in tabling requires it. *(constraints/store)*
- [~] **Renaming** — one final class: a seed map `Unknown → Term` plus an optional mint-on-miss; `apply` is one walkAll under the seed. Factories: `of` (seed only, misses pass through), `minting` (fresh name per miss — the existential), `restating` (Hole-keyed targets). Callers build the seed; Residues' resolution is the main one. *(constraints/store)*
- [~] **Semiring** — (⊕ merge alternatives, ⊗ chain steps, 0, 1); distributivity is the rearrangement license — the law that makes per-arrival delivery inside a fixpoint equal final-value delivery. *(functional algebra)*
- [x] **IdempotentSemiring** — a⊕a=a: dedup is lawful, at-least-once delivery safe. *(functional algebra)*
- [x] **BoundedSemiring** — 1⊕a=1 (top absorbs) hence a\*=1: cyclic streaming terminates. *(functional algebra)*
- [x] **ClosedSemiring** — has a real Kleene star; cycles solved analytically. *(functional algebra)*
- [~] **SuperiorSemiring** — selective + superior: best-first commitment (Dijkstra's pop) is legal. *(functional algebra)*
- [~] **law kits / coverage gate** — property tests per algebra claim; the build fails if any implementor lacks a claiming law test. *(functional-laws)*

## 5. Tabling

- [x] **Table / TableEntry** — the per-solve call→entry map / one call's notebook: its answer cell and its production ledger. *(tabling/)*
- [~] **Call** — the cache key: relation identity + reified args + residues — the call's REGION, not just its pattern; keys differ ⟹ entries differ, and reuse is one-directional (wide serves narrow, never the reverse — the cache is a function of the region the master ran from). *(tabling/)*
- [x] **anonymous master** — the body runs as the entry's workforce, belonging to no caller; selected by the produce CAS. *(table-completion)*
- [x] **reader / consumer** — a caller reading the cell by cursor; parks at the channel when caught up. *(tabling/)*
- [x] **answer cell** — the entry's channel value: one JoinMap from answer terms to ring values. *(condition.md §5)*
- [x] **JoinMap** — the cell carrier: `order` (terms, append-only), `members` (⊕-folds), `log` (arrivals that ascended). *(condition.md §5)*
- [x] **ascent log** — the delta journal: every arrival that moved a value, one event kind; inside readers cursor it as fixpoint fuel — the per-key diff computed once at the writer, whose position doubles as the delivery ledger (what let the delivered-set die). *(condition.md §5)*
- [x] **strict ascent** — append folds by ⊕ and grows only if the value moved; no move = no wake = the termination signal. *(JoinMap)*
- [x] **completion detection** — Dijkstra–Scholten over the scope counters + sleeper edges; a caller can never seal ahead of a call it depends on. *(table-completion)*
- [x] **subsumptive reuse** — a sealed (or open) wider entry serves narrower calls through consume's unification filter; sound by the subset property. *(tabled-constraints §5.4)*
- [~] **keyed widening / Barrier** — the call pattern is the table key, so no optimizer may move binders across it: a binder pushed in narrows what the body derives while the key still claims the wide region — Q8's narrow-serves-wide poison, manufactured by the optimizer. *(optimizer)*
- [x] **∞→exact transition** — an incomplete entry prices MAX; a completed one prices its exact count. *(Tabling.tabledOrder)*
- [x] **InBody / inside vs outside reader** — inside a tabled body = fixpoint fuel, streams every ascent; outside = receives only final facts. *(condition.md §6)*
- [x] **finality** — the delivery theorem: a value at ⊕'s top (1⊕a=1) is final on arrival and streams; anything below waits for the seal. *(condition.md §6)*
- [~] **existential witness** — a body-local variable riding an answer's residues whole; minted fresh per consumption. *(tabled-constraints §6)*
- [~] **master-from-key** — the body runs from the key's region (caller stores stripped, key residues restated), so the cache holds exactly what the key names. *(tabled-constraints §5.3)*
- [~] **TablingMode** — the algorithm plugged into the shared skeleton: cellSemiring / bodyState / absorb / capture / caughtUp. *(tabling/)*
- [x] **Streaming (plain / weighted)** — fold values during explore, hand out by finality; plain's cell is the constraint ring, weighted's the weight ring. *(tabling/)*
- [~] **Closed / star tabling** — explore for structure under a presence-valued cell, record base/edge into a DependencyGraph, solve x = A\*⊗b at the seal, replay reader chains. *(star-tabling)*
- [x] **replay = rename ∘ absorb** — a cached answer is ∀-quantified over its holes; every consumption instantiates a fresh copy; alias-replay was the variable-capture bug. *(tabled-constraints §5.1)*

## 6. The constraint ring (the July–August unification)

- [~] **Residues** — ONE region of constraint knowledge: per-store factors conjoined; the ⊗-monoid (meet, TRUE); leq = containment, narrower entails wider. *(condition.md §3)*
- [x] **about / all / restate** — Residues' three doors, each speaking pairs of (reified image, factors): `about(world, anchor)` extracts the knowledge touching the anchor, `all(world, anchor)` extracts the whole normalized delta (existential witnesses included), `restate(image, factors, anchor)` re-states an extraction into a consumer world under one shared minting Renaming — the existential's scope. *(tabling/Residues)*
- [x] **conjunct** — one Residues value inside a Condition; one derivation's region. *(condition.md)*
- [~] **Condition** — a term's proven space: a DNF of Residues kept in absorption normal form; ⊕ = region union, ⊗ = cross-meet, 1 = TRUE (ground), bounded. *(condition.md §4)*
- [~] **absorption normal form** — a ∨ (a∧b) = a: dominated conjuncts drop, dominating newcomers evict; subsumption dedup IS this law. *(condition.md §4)*
- [~] **the crossings** — project (call side) / normalize (answer side) / restate (imposition under a renaming): how a conjunct enters and leaves packages; owned by Residues. *(condition.md §3)*
- [?] **the healed seam** — a ground arrival absorbs every conditional version of its term (1 ⊕ C = 1). *(condition.md §7)*
- [~] **homes / crossings / admissions** — the orientation: one object (a region), several homes, lossless moves between them, a growing guest list of factor kinds. *(condition.md §0)*
- [?] **c-tables / PosBool** ⋯import — the literature name for conditional answers and their boolean-condition semiring. *(condition.md §2)*
- [?] **two-level laws** — monoid laws on Residues, ring laws on Condition on top; a raw conjunct cannot carry ⊕. *(condition.md §4)*

## 7. Weights

- [x] **factor(ring, w)** — multiply a weight into the derivation's running value. *(Weights)*
- [x] **SemiringStore** — the product-of-rings value riding the package; the lazy rail's carrier. *(weight/)*
- [x] **solveBounded / solveClosed** — weighted tabling: stream through the fixpoint (bounded rings) / capture structure and star-solve at the seal (closed rings). *(star-tabling)*
- [~] **proofs vs answers fork** — non-idempotent folds (count, sum) mean two different numbers: over derivations (rings) or over the deduped answer set (boundary folds); idempotent folds don't care. *(condition.md §8.7)*
- [x] **boundary idiom / solve→fold→seed** — aggregate over a FINISHED solve's stream in the host language, seed the next solve with the result as a fact table; strata as explicit solves. *(condition.md §8.7)*
- [x] **closedness refusal** — today's Aggregate must refuse any sub-goal capturing a pre-existing variable; the correctness fix that survived four deflations. *(condition.md §8.7)*
- [~] **birth watermark** — record the variable counter at a boundary; anything older, undeclared and unground that leaked into a sub-result refuses loudly, named. *(condition.md §8.7)*
- [~] **GROUP BY is table keys** — a weighted tabled call keyed on the group vars IS the grouped fold. *(condition.md §8.7)*

## 8. Research vocabulary (condition.md §8, note-store, domain-layer — the newest layer)

- [x] **two evaluators / eager and lazy rail** — the kernel evaluates conditions by propagation (prunes now); the weight path carries them inertly (collects, pays at finality); one ring, two residences. *(condition.md §8.3)*
- [~] **imposition spectrum** — fork ⟷ resident data (disjunctive store) ⟷ carried value: three homes for a constraint, chosen by price. *(condition.md §8.3)*
- [~] **plan space** — the imposition choice belongs to the optimizer: licensed by confluence + the two-evaluators identity, guarded by ∞→exact and zombie labelling. *(condition.md §8.3)*
- [~] **toll gate** — a lifted goal crosses the tabling barrier as DATA through the crossings (into the key or caller-private); filters migrate cleanly, binders widen keys soundly-but-priced. *(condition.md §8.3)*
- [~] **the Projectable equivalence** — resident-in-a-store and riding-as-a-value are one object: the crossings are lossless conversion operators (no retraction; relocate the imposition point instead). *(condition.md §8.3)*
- [~] **note / escape / four moves** — the note store's mechanism: "at least one escape holds"; cross off / enforce the last / fail on empty / discard when satisfied. *(note-store)*
- [~] **NoteStore\<V\>** — one store parameterized by escape cargo (`Semilattice & PartialOrder`); Prefix = Neq re-seated, Domain = notin/exclude, Residues = or-without-forking. *(note-store)*
- [~] **scratch-copy check (excursion)** — is a whole pack still possible? absorb it into a scratch package and watch propagation; drained observes failure; verdicts tier at the run-lane boundary. *(note-store §4)*
- [~] **agreement move (lift)** — what ALL surviving escapes agree on holds now; needs an opt-in join; hull joins sound; the deduction forking can never make. *(note-store §4)*
- [?] **polarity** — "avoid this" vs "be in this": the two escape questions swap answers; a flag, not a machine. *(note-store §2)*
- [~] **co-store** — the negative-box configuration of the note store (Neq generalized: forbid boxes instead of points). *(note-store / lattice-store)*
- [~] **Neq = ¬Condition** — a record set is a negated DNF (records are clauses); FD+Neq are the finite–cofinite complement-closed pair. *(condition.md §8.5)*
- [~] **constructive negation** ⋯import — negation computed as the complement of a SEALED answer region, delivered as constraints; "negation is the ultimate outside reader". *(condition.md §8.5)*
- [~] **clause learning / tabling the failures** ⋯import — a learned nogood is "⊥ GIVEN R"; ¬R is a co-store clause; nogoods are born at 1 and stream; the fair scheduler prunes the frontier instead of backjumping. *(condition.md §8.6)*
- [?] **annotation seam** — PARKED: explanations, source attribution and provenance as one ring threaded through the kernel's meets; three customers, first payer builds it. *(condition.md §8.6)*
- [~] **suspensions as factors / debt certificate** — a suspension enters conditions as (name, actuals); restate re-imposes the obligation, not its consequences; bodies must be state-independent. *(condition.md §8.1)*
- [x] **(actuals, template)** — the assembler's call-value = the suspension transcription: arguments as terms + a body-maker whose closure is excluded from identity. *(assembler / §8.1)*
- [~] **two rings, one value shape** — "what I've PROVEN" (bounded answer ring) vs "what to RUN" (closed program ring; star = defineRecursive = minting the name); solve-to-seal connects them. *(condition.md §8.1)*
- [~] **equivalence inflation** — the named failure mode: "can model" silently becoming "is". *(method.md)*
- [~] **theorem import receipt** — hypotheses, enforcement points, evidence, and the NOT-PURCHASED list recorded before a named result is leaned on. *(method.md)*
- [~] **evidence types** — derive / falsify / expose / measure / demonstrate: match the verb to the evidence held; never silently promote. *(method.md)*
- [~] **mid-solve pin** — a runtime invariant checked once per event at the boundary that owns it, refusing loudly with names (the canary family). *(note-store §5)*

## 9. Data boundary (domain-layer)

- [x] **FactSource** — the external read seam: pin / enumerate / estimate / supportedModes; multi-relation with a rich bound pattern (Database.get's shape promoted). *(domain-layer §4)*
- [x] **landing design** — fetched rows become pldb Facts in the solve-local Database; lookups over them post as table constraints: propagation over external data, GAC-style in-memory joins. *(domain-layer §4.1)*
- [?] **fetch-coverage ledger** — records which (source, relation, snapshot, pattern) regions were COMPLETELY enumerated; facts answer matches, coverage answers completeness. *(domain-layer §4.1)*
- [x] **pin() / snapshot** — the source-owned token for a consistent solve-scoped view; the freeze half of freeze-and-certify. *(domain-layer §5)*
- [x] **snapshot vector** — {source → token} attached to results; descriptive; no universal ≥ across token types. *(domain-layer §5.3)*
- [~] **epoch carrier hypothesis** — EpochRequirement → Footprint → EpochCondition (mirroring factor → Residues → Condition); an EXPERIMENT gated on a receipt; buys per-derivation admissibility, never table completeness. *(domain-layer §5.3)*
- [x] **table constraint / row-set store** — returned rows as a narrowing domain (Support lattice, GAC propagator, labelo); shipped in pldb. *(pldb table-constraints)*
- [~] **conditional answer** — (term, Condition, snapshot): the domain-facing result; check by restating, list with policy, compare by containment. *(domain-layer §6)*
- [~] **caveated authorization** — the driving vertical slice: recursive grants with conditions over real data. *(domain-layer §7)*
- [~] **wave execution** — poll → materialize → pin → cold solve → commit outputs; correctness = observational equivalence to a fresh cold solve at that snapshot. *(domain-layer §9)*

## 10. Method

- [x] **comprehension veto** — "if I don't get it, it's not designed properly"; explanation is a proof obligation and the code changes until it can be given. *(method.md)*
- [x] **adversarial deflation** — every proposal attacked before it ships; downgrades are wins. *(method.md)*
- [x] **negative witness** — a theory that cannot say "this is NOT an instance" explains nothing. *(method.md)*
- [x] **honest ledger** — benefits stated with their tense: banked vs promissory-with-customer. *(method.md)*
- [x] **shelve with a trigger** — every deferral names the event that reopens it; a shelf without a trigger is a graveyard. *(method.md)*
- [~] **run the loop backwards** — once a structure closes (laws green, faces named), new capability is derived from its missing operations rather than smelled in code. *(method.md)*
- [x] **schedule as adversary** — order-dependence is a broken law made observable; the chaos harness is law-testing at the system level. *(method.md)*
- [~] **a lawful seam is a demand letter** — a substrate whose types carry laws issues debt to every layer above it; order bugs are the unpaid installments. *(method.md)*
- [x] **close the design when it lands** — implementation is incomplete until its document graduates: status, links, deviations, re-shelved remainders. *(method.md)*
