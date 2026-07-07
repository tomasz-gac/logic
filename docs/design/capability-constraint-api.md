# The capability constraint API — design and migration plan

**Status: Steps 1, 2 AND 3 IMPLEMENTED (July 2026, branch `capability-api`);
Step 3.5 (naming and structure, decided with Tom) and Step 4 (sweep) remain.** Implementation deviations
from this doc, all recorded in place: watch matching is `watches(state, changed)`
with CHAIN-INCLUSION (the changed variable may be the watched term, an alias link,
or the chain end — a plain live walk steps THROUGH a just-bound variable and misses
the primary match; found when projections stopped waking); `onPrefix` receives the
precomputed prefix delta and the live state (the oldPackage parameter died early);
the Neq→FD bridge stays call-shaped for now (data-shaping needs a vocabulary-growth
decision per rule B); `Verdict.run` defers via a transient PendingRuns store drained
after the OUTERMOST pass (statement-time runs splice inline at the goal's own
position); ground leq/addo constraints discharge exactly (the addTo all-bound guard
is gone with Constraint). `Constraint`, `buildWalkedConstraint`, `runConstraints`,
`remRun` and the `anyRelevantVar` family are deleted.

This was the concrete shape of
`constraint-propagation.md`'s Phase 3 — but its primary motivation is **type safety**,
not performance: restructure the constraint API so the breaking actions documented in
the machinery notes become unrepresentable, with the explicit worklist driver falling
out as a consequence. Do not build a bolt-on worklist without these types; doing the
worklist alone forfeits the only cheap chance to get the constraints-by-construction.

Prerequisite reading: `constraint-propagation.md` (§1.1 the existing machinery, §4 the
implemented Phases 1–2). This doc assumes the post-redesign state (chokepoint applies
prefixes centrally; cross-store wake; wake-on-narrowing; Neq→FD bridge).

---

## 1. Why: what is rule-enforced today that types can enforce instead

Today these contracts hold by convention, tests, and javadoc — each one has already
bitten at least once:

| Contract (rule today) | Historical bite |
|---|---|
| all bindings go through `StoreSupport.processPrefix`; never raw `extendS`/`MiniKanren.unify` in constraint code | pre-Phase-1a `resolveStorableDom` bypassed it → committed-choice unsoundness |
| store reactions never touch substitutions | pre-Phase-1b replace-clobbering |
| a woken constraint must re-park itself (`Constraint.addTo`) or it evaporates | protocol trap, currently survived only because every body re-runs `constraintOperation` |
| never wake on a no-change (equal-domain guard) | required for termination; guard lives in one method by discipline |
| `processPrefix` with a pair contradicting an existing binding is a silent no-op | discovered while documenting; callers must know to unify instead |
| trial unification must use a stripped package; `processPrefix` NPEs on a null store map | latent |
| narrow targets must be walked before `processDom` (a stale var object re-binds a bound variable) | bit during the chokepoint perf fix: `separateFDC`'s raw-target narrow violated the full-map contract, silently swallowed by the old merge, exposed by O(1) replace |

The redesign makes each of these either impossible to write or safe by default.

---

## 2. The target API

Java 8 throughout; "sealed" sets are closed by convention and package-private
constructors (no sealed types on 8; see §7).

### 2.1 `Prefix` — bindings as a value, mintable only by unification

```java
// unification becomes PURE: it computes what would be bound, applies nothing
Option<Prefix> MiniKanren.unify(Substitutions s, Term u, Term v);

// FD inference mints a checked single binding (none if x is already bound)
Option<Prefix> Prefix.binding(Package p, LVar<?> x, Term<?> v);
```

- `Prefix` construction is package-private; the two factories above are the only mints.
  A prefix is therefore *born valid*: it never contains a pair for an already-bound
  variable (the contradicting-rebinding no-op becomes unrepresentable).
- The only consumer is the chokepoint: `StoreSupport.processPrefix(Prefix)`.
  `Package.extendS`/`withSubstitutions` become inaccessible outside it.
- Trial unification (disequality's "would these unify, and how?") inspects the
  `Prefix` without applying it — which is what Neq already does (the prefix IS its
  record). `withoutConstraints` and null store maps disappear entirely.

### 2.2 `Propagator` + `Verdict` — constraint bodies report, the driver administers

Today the outcome trichotomy is smeared across `remRun` (remove), the body re-running
`constraintOperation` (re-park), and `addTo`'s guard (discharge). The framework cannot
observe outcomes, and forgetting to re-park silently drops a constraint.

```java
interface Propagator extends Stored {          // Stored: routes park/remove to its store
	/**
	 * Watched variables resolved against the LIVE state. Today Constraint's walked
	 * args are a cache kept fresh only by the re-park side effect of
	 * remove-and-rerun; once the framework owns parking (keep = untouched), that
	 * cache would go stale under aliasing (x bound to y must wake x's watchers on
	 * y's changes). Walking at match time makes freshness structural. Constraint is
	 * then DELETED — it was a Propagator plus this cache plus Stored routing.
	 */
	Set<LVar<?>> watched(Package state);

	/** Re-examine against the current state. Reads anything, mutates nothing. */
	Verdict propagate(Package state);
}

// the closed verdict set:
Verdict.fail()                    // violated — kill the branch
Verdict.keep()                    // undecided — stay parked (THE DEFAULT-SAFE CASE)
Verdict.subsumed()                // entailed, can never be violated again — remove me
Verdict.narrowed(List<Inference>) // stay parked AND apply these inferences
Verdict.run(Goal)                 // escape hatch for search-triggering stores (§5.3)
```

The framework keeps the propagator parked unless told otherwise: **forgetting is now
the safe outcome** (`keep`), not silent evaporation. Walked-args freshness comes from
walking inside `propagate` instead of re-parking walked copies.

`leq(x,y)` under this API (same math as today's `leqFD`):

```java
public Verdict propagate(Package s) {
	Term<T> u = walk(s, x), v = walk(s, y);
	if (u.isVal() && v.isVal())
		return leq(u, v) ? Verdict.subsumed() : Verdict.fail();
	Option<Domain<T>> du = domainOf(s, u), dv = domainOf(s, v);
	if (!du.isDefined() || !dv.isDefined())
		return Verdict.keep();                       // was letDomain's silent skip
	Domain<T> u2 = du.get().atMost(dv.get().max());
	Domain<T> v2 = dv.get().atLeast(du.get().min());
	if (u2.isEmpty() || v2.isEmpty())
		return Verdict.fail();
	return Verdict.narrowed(updates(u, u2, v, v2));  // driver diffs/dedups
}
```

### 2.3 `Revision` — store hooks return their factor, not the world

```java
interface ConstraintStore extends Store {
	/** Revise against newly applied bindings. Read anything; change only your own factor. */
	Revision revise(Prefix prefix, Package state);
	// enforce / reify / pendingPropagators as today (adapted)
}

Revision.fail()
Revision.unchanged()
Revision.updated(Store myNewFactor)
Revision.updated(Store myNewFactor, List<Inference> inferences)
```

A revision physically cannot touch substitutions or another store's entry. The
chokepoint assembles the package from returned factors.

### 2.4 `Inference` — cross-factor information as data

The shared vocabulary of the system — the only way information crosses factors:

```java
Inference.bind(Prefix p)                        // substitutions grow (deferred unification)
Inference.narrow(Term<?> target, Narrowing n)   // what the target may be shrinks
```

Exactly two variants, matching the two shared factors of the product lattice
(bindings grow; attributions shrink). `Narrowing` is the minimal vocabulary seam —
one method, "apply to a target" — implemented by the finite-domain `Domain`
hierarchy, whose lattice/arithmetic machinery stays in `finitedomain`; the
dependency is one-way (finitedomain → ckanren). Everything else is a store's
private state. Emitted
by both `Reaction`s and `Verdict.narrowed`; applied only by the driver. This replaces
call-shaped bridges: the Neq→FD bridge stops being `Disequality` calling
`FiniteDomain.excludeFromDomain` (a named, cross-package dependency) and becomes Neq
returning `updated(storeWithoutRecord, [narrow(x, dMinusV)])` — the coupling turns
data-shaped and the public facade seam is deleted.

### 2.5 The driver — the explicit worklist the types force into existence

```java
// conceptual; replaces StoreSupport.processPrefix's reaction/wake composition
Result drive(Prefix initial, Package p) {
	Deque<Inference> pending = queue(Inference.bind(initial));
	while (!pending.isEmpty()) {
		Inference i = pending.pop();
		if (i is bind(pfx)) {
			pfx = revalidate(pfx, p);                       // drop already-bound identical pairs;
			if (contradicts(pfx, p)) return FAIL;           // CONTRADICTION IS LOUD, not a merge no-op
			p = p.extendS(pfx);                             // 1. apply — the only spot
			for (ConstraintStore cs : stores(p)) {          // 2. reactions
				Reaction r = cs.onPrefix(pfx, p);
				if (r.failed()) return FAIL;
				p = p.putStore(r.factor());
				pending.addAll(r.inferences());             // deferred, not recursive
			}
			enqueue wakes for vars of pfx;                  // 3. watchers
		} else if (i is narrow(x, d)) {
			Domain cur = domainOf(p, x) REQUIRED;           // unroutable → FAIL (custody rule, §4C)
			Domain next = cur.intersect(d);
			if (next.isEmpty()) return FAIL;
			if (next.equals(cur)) continue;                 // the equal-guard, in ONE place
			if (next.isSingleton()) pending.add(Inference.bind(Prefix.binding(p, x, value(next))));
			else { p = storeDomain(p, x, next); enqueue wakes for x; }
		} else /* wake(x) */ {
			for (Propagator c : watching(p, x)) {
				switch (c.propagate(p)) {
					FAIL       → return FAIL;
					KEEP       → nothing (still parked);
					DISCHARGE  → p = removePropagator(p, c);
					NARROWED(u)→ pending.addAll(u);
					RUN(g)     → splice g into the search (§5.3);
				}
			}
		}
	}
	return p;
}
```

Termination is visible on one screen: every iteration binds a variable (finitely
many), strictly shrinks a domain (descending chain condition, enforced by the
equal-guard), removes a propagator, or no-ops on a drained queue. Today the same
argument requires tracing recursion through five methods. Because the queue is
inspectable, the driver can also dedup identical narrowings and detect contradictory
inferences from different stores in one pass — both silent today.

---

## 3. The scorecard

| Breakage mode (from the machinery notes) | After this refactor |
|---|---|
| bypass the chokepoint | unrepresentable (no public apply; `Prefix` mint-controlled) |
| contradicting rebinding = silent no-op | unrepresentable at mint; loud at drive |
| null store map NPE | concept deleted (trial unify inspects `Prefix`) |
| reaction touches substitutions / other stores | unrepresentable (`Reaction` signature) |
| forget to re-park → constraint evaporates | default-safe (`keep`; framework owns parking) |
| manual or no-change wake → livelock | unrepresentable (one `narrow` entry owns guard + wake; `runConstraints` gone) |
| non-contracting domain update | mostly closed: `Domain`'s public ops are contraction-only; the driver intersects rather than replaces |
| wrong arithmetic (`mulIntervals` class) | NOT closed — no type system catches bad math; completeness tests remain the defence |
| termination beyond the guard (DCC) | NOT closed — semantic; but the argument localizes to the driver |

---

## 4. The composition model (why Inference does not break composability)

The interdependence between stores is *semantic* and pre-existing: two factors
describe the same variables whether or not they talk. The design choices are only
where the intersection is computed — never (labeling generate-and-test), pairwise
call-shaped bridges (N², named dependencies — what `excludeFromDomain` is today), or
mediated data-shaped messages (this design). The substitution has always been a
mediated cross-store channel — it is why the system composes at all; `Inference` is
the same mechanism with a second, weaker message kind. Precedent: Nelson–Oppen theory
combination — independent decision procedures cooperating solely by exchanging
equalities over shared variables. `bind` is equality propagation; `narrow` is theory
propagation.

Three rules keep it composable — violating any of them reintroduces the entanglement:

- **A. Bridges are optimizations, never obligations.** Every store must be sound
  alone; removing any other store degrades performance, never correctness. (The
  current bridge already obeys this: Neq keeps records when no domain exists.)
- **B. No names, only vocabulary.** Messages address *factors* (`bind` →
  substitutions; `narrow` → the domain of x), never stores. Growing the vocabulary is
  a system-wide design decision with a very high bar — `bind` is the default channel;
  `narrow` exists only because domains are a second shared factor; a third kind needs
  this doc amended first.
- **C. Custody transfer, never information loss.** A store that discharges its record
  *in exchange for* an emitted inference has handed off custody: if the inference
  cannot be routed (no domain store, no domain for x), the driver must FAIL the
  reaction that emitted it — never drop the message — otherwise the constraint is
  silently lost. (Today the emitter checks routability itself via `getDom`; in the
  data-shaped design the check moves to the driver and MUST be loud.)

---

## 5. Mapping the existing stores

### 5.1 Neq — the easiest
`verifyUnify`'s trichotomy (violated / discharged / simplified) is already
verdict-shaped at store level: `onPrefix` returns `fail()` or
`updated(simplifiedStore)`. The bridge becomes
`updated(storeWithoutRecord, [narrow(x, dMinusV)])` — and, unlike today,
the record-creation-time-only limitation lifts: the check can also run when a domain
appears later, because routability is the driver's job (rule C).
`pendingPropagators` stays empty (wholesale-verify protocol, unchanged).

### 5.2 FD — the split
Its `onPrefix` reaction is the domain-membership check of newly bound values
(`unchanged()`/`fail()`). Its arithmetic constraints (`leqFD`, `addoFD`, `mulFD`,
`separateFDC`, `copyDom`) become `Propagator`s — mechanical rewrites of the existing
bodies (§2.2's leq example). `constraintOperation`, `remRun`, `Constraint.addTo`,
`buildWalkedConstraint` are deleted. `resolveStorableDom`/`updateVarDomain` dissolve
into the driver's `narrow` case. Labeling (`EnforceConstraintsFD`) emits
`Inference.bind` per candidate instead of calling `processPrefix`.

### 5.3 Projection — DECIDED (Tom, July 2026): option 1 now, extraction later
Projection's "constraints" are user goals — arbitrary, branching search. A `Verdict`
is a deterministic report and cannot express "explore two alternatives".

**Decision:** implement `Verdict.run(Goal)` — with one NON-OPTIONAL driver
requirement: run-goals are COLLECTED during the propagation drain and spliced into
the search only AFTER quiescence. An arbitrary goal run mid-fixpoint breaks the
drain's confluence (it is not contracting). The projections become propagators
returning `keep` (not ground) or `run(goal)` (ground).

The concept this hides — a parked coroutine, distinct from a constraint — is
specified in `suspensions.md`, including the mechanical extraction (delete
`ProjectionConstraints`, restore `Verdict` to pure propagation outcomes) to be done
when the second customer arrives (pldb's deferred lookups are Suspensions, not
propagators).

---

## 6. Migration plan (each step lands green on the full suite + pins)

Inside-out, so each step is independently shippable and the visibility lock comes
last (lock the door after the furniture is arranged):

- **Step 1 — Verdict/Propagator for FD bodies.** Introduce `Propagator`/`Verdict` +
  an adapter that wraps a `Propagator` as today's `Constraint` goal (re-park on
  `keep`, remove on `discharge`, etc.). Convert `leqFD`, `addoFD`, `mulFD`,
  `separateFDC`, `copyDom` one by one. Protocol unchanged; the re-park trap dies for
  converted bodies. Lowest risk, immediately useful.
- **Step 2 — Reaction/Inference + the driver.** Change `ConstraintStore.processPrefix`
  to `onPrefix(Prefix, Package) → Reaction`; rewrite `StoreSupport.processPrefix` as
  the §2.5 loop; delete `remRun`/`addTo`/`constraintOperation`/the adapter;
  data-shape the Neq→FD bridge and delete `FiniteDomain.excludeFromDomain`. Resolve
  §5.3 first (Tom decision). This is the big step — treat it like Phase 1/2: pin
  tests first (contradiction-between-inferences is loud; unroutable inference fails
  the emitting reaction; dedup of identical narrowings).
- **Step 2.5 — the explicit agenda: IMPLEMENTED (July 2026).** Items are Bind(delta)
  and Wake(term) (narrow inferences apply inline — bounded once their cascades
  append); the run lane replaced PendingRuns; `enqueue` is the single propagation
  entry (present → append, absent → install + drain + splice); `drain` pops one
  item per deferred step; phase 2 removes the agenda before chaining runs. Original
  rationale below. The
  implemented fixpoint is RECURSION-HIDDEN: a pass is linear, and cascades re-enter
  the chokepoint (collapse → nested pass; narrowing → inline wake; bind → nested
  pass), trampolined by the continuation substrate — stack-safe but uninspectable.
  To make it literal, generalize PendingRuns into an Agenda store (work items:
  Inference, Wake(term), Run(goal)); the re-entry points APPEND instead of
  recursing, and the outermost chokepoint drains one item at a time
  (`drain = pop.apply(s).and(drain)` — a flat loop through the trampoline). Buys:
  cascade-wide dedup and contradiction detection (today per-verdict only), a single
  quiescence point (the outermost-marker trick for runs disappears), and an
  inspectable agenda for propagation tracing.

  **The producer map** — during a drain, appends come from exactly three paths,
  all inside applyOneItem: (1) VERDICTS — a Wake runs parked propagators; narrowed
  appends Narrow/Bind items, run collects a Run; (2) REACTIONS — a Bind runs every
  store's onPrefix, whose inferences are appends; (3) DOMAIN OUTCOMES — a Narrow
  strictly shrinking appends Wake(x), collapsing appends Bind{x→v} (a binding
  minted by propagation, no unification involved). Unification is NEVER a producer
  — it is always a trigger. The driver applies Bind items primitively (revalidated
  delta, direct extension); do NOT route them through unify — that would put a
  trigger inside the loop.

  **Two-phase drain (Tom's catch: constraints inside projections).** Phase 1
  propagates Bind/Narrow/Wake to quiescence with the agenda present; Run goals are
  only collected. Phase 2 REMOVES the agenda and chains the collected runs as
  plain goals (exactly today's PendingRuns drain). A spliced run therefore
  executes with NO agenda: every trigger it hits — unification or constraint
  statement — starts a fresh synchronous drain that quiesces before the run's
  next conjunct. Do NOT let runs execute with the agenda present: their
  constraint statements would QUEUE instead of propagating synchronously, and a
  committed-choice construct inside the projected goal could commit on
  un-propagated state — the Phase-1 unsoundness family, one level up. Runs
  spawning constraints spawning runs is ordinary search (may diverge; fairness
  applies); propagation between runs always quiesces (DCC).

  **HARD REQUIREMENT (Tom): the drain must NOT be a native Java loop.** Each
  iteration must pass through a deferred step (`Goal.defer`), so one propagation
  item ≈ one scheduler step and the trampoline yields between items — exactly the
  granularity the recursive implementation gets from its Cont.defer points. A
  native while-loop would make an entire cascade a single scheduler step: a bugged
  (DCC-violating) cascade would hog the CPU, fair schedulers could not interleave
  other branches, and bottom-avoidance would be lost. Written correctly, a
  divergent cascade behaves exactly as today under every scheduler — and becomes
  diagnosable (agenda-size watermark; propagation tracing), which the opaque
  recursion is not.

- **Step 3 — Prefix + the visibility lock.** Consolidation note: prefix-vs-
  substitutions revalidation exists twice — `Disequality.verificationStep` (prefix
  forbidden: holds→fail, clashes→discharge, open→simplify) and
  `StoreSupport.applyBind` (prefix asserted: holds→drop, clashes→fail, open→bind).
  Same trichotomy, dual polarity, because a Neq record and a Bind delta are both
  prefix maps. The Prefix type is the natural home for one shared
  `unifyPrefix(prefix, substitutions) → Trichotomy` consumed by both readings and
  by trial unification. IMPLEMENTED, with deviations: the mint is
  `MiniKanren.unifyPrefix(Package, Term, Term) → MFiber<Prefix>` — a collecting
  `Extender` records each extension as the unifier makes it, so the delta costs
  O(delta) and the post-hoc `prefixS` full-map diff is DELETED (the O(n)-per-unify
  class of the perf landmine is gone, not just avoided). Gotcha found by the suite:
  the unifier's var–var aliasing arm called `extendNoCheck` directly and bypassed
  the threaded extender — collection missed every alias binding (`u=v` through
  structure yielded an empty prefix) until it was routed through `extend.apply`.
  `Prefix` construction is package-private in `unification`; the two mints are the
  unifier and the checked `Prefix.binding(Package, LVar, Term)` (none when bound —
  the silent-no-op trap made unrepresentable). `Prefix.revalidate` is the shared
  asserted-polarity trichotomy consumed by `applyBind`; Disequality's forbidden
  polarity reads the same delta through `unifyConstraints`, which now returns the
  collected delta directly (empty = violated, none = redundant) instead of an
  extended map for callers to diff. Deleted doors: `Package.extendS`,
  `StoreSupport.withoutConstraints`, `Disequality.verifySeparate` +
  `VerificationResult`, `MiniKanren.prefixS`. Residual (§7): `withSubstitutions`
  stays public — Java 8 has no way to scope it to the chokepoint across packages;
  it is the documented door; its three remaining callers are the chokepoint's
  two `Prefix.appliedTo` applications and Disequality's bare trial-package seed. After this step the scorecard's "unrepresentable"
  rows are unrepresentable up to that one documented residual.
- **Step 3.5 — naming and structure (decided with Tom, July 2026).** The refactor
  introduced six new nouns; this step settles their names against the literature,
  dissolves the `StoreSupport` grab-bag, and makes type ownership structural.
  Provenance audit: `Propagator` (Gecode/Schulte-Tack; Radul-Sussman), `Prefix`
  (cKanren's prefix-S), `Agenda` (AC-3 / propagator networks), `Narrowing`
  (narrowing operators, interval CP) are literature-grounded and stay. `Verdict`,
  `Reaction`, `Inference` were coined here; `Verdict` and `Inference` stay
  (`Status`/`Outcome` are anemic; `Tell` (Saraswat ask/tell) is opaque without CCP
  background), `Reaction` goes. Four commits, suite green after each:
  1. **Renames.** `Reaction` → `Revision` (the value IS the store's revised
     factor; AC-3's REVISE); `ConstraintStore.onPrefix` → `revise` (verb→noun,
     self-linking); `Verdict.discharge` → `subsumed` (Gecode `ES_SUBSUMED`);
     `ConstraintStore.enforceConstraints` → `enforce` (the `Constraints` suffix is
     noise on an interface named `Constraint*`). Rewrite the two javadocs that
     still QUOTE the cKanren paper as evergreen contracts citing it as provenance.
     Explicitly rejected: `ConstraintStore` → `PropagatorStore` (wrong for Neq —
     it holds no propagators; wholesale records participate through `revise`) and
     → `ConstraintDomain` (overloads "domain" against `finitedomain.Domain`).
  2. **`Inference` becomes pure data.** Add `match(bindHandler, narrowHandler)`;
     the driver interprets inferences (as it already does verdicts and revisions)
     instead of `Inference.Bind.toGoal` calling UP into the driver's
     `enqueueBind`. `toGoal` dies. This unknots the data→driver cycle that would
     otherwise cut across the package split in commit 4.
  3. **`StoreSupport` dissolves.** It is three things sharing a namespace and a
     name that says nothing: (a) store-map edits (`withConstraint`,
     `withoutConstraint`, `getConstraintStore`, `updateC`) → methods on `Package`
     (which already owns `putStore`/`withoutStore`; `Store`/`Stored` live in
     `unification`, so no new dependency); (b) hook folds (`enforceConstraints`,
     `reify`) → `CKanren`, next to their only caller (the reification path);
     `isAssociated` → its caller (Disequality's purify) under a truthful name
     (its javadoc still apologizes "Original name: anyVar"); (c) the engine
     (`resolve`, `enqueue*`, `drain`, `wake`, `activate`, `interpret`,
     `pendingPropagators`) merges WITH `Agenda` into one class: **`Propagation`**
     — data and its only interpreter in one file. `Agenda.Item` gains polymorphic
     `apply()` (`Bind.apply` absorbs `applyBind`; `Wake.apply` wraps `wake`),
     killing the instanceof dispatch — safe because nesting keeps the whole
     interpreter in one class, preserving the closed-data-one-interpreter design
     language. INVARIANT to preserve: `drain` pops ONE item per deferred step
     (scheduler fairness / bottom-avoidance).
  4. **Package moves.** Layered, acyclic (commit 2 is what makes this true):
     `ckanren.propagator` = `Propagator`, `Verdict`, `Inference`, `Narrowing`
     (bottom; `Inference` lives here as the price of acyclicity — both levels
     emit inferences, so it must sit below both); `ckanren.store` =
     `ConstraintStore`, `Revision` (depends on `propagator`: `pendingPropagators`,
     `Revision.updated` carries inferences); `ckanren` root = `CKanren` +
     `Propagation` (the driver, depends on both). The layering states a design
     fact the flat package hid: the store protocol knows about propagators; the
     propagator protocol has no idea stores exist. Add `package-info.java` per
     package. (Subpackage named `propagator`, not `propagation` — that word now
     names the driver class.)

  Deferred, recorded here so it isn't relitigated: the Verdict/Revision mirror is
  two types for a reason (unit of scheduling vs unit of persistence; each level
  can only affect what it owns — merging would re-open the doors this API
  closed). BUT the mirror exists because Neq predates propagators. Rewriting Neq
  as one propagator per record (watched terms = the record's vars; verdicts:
  `subsumed` when redundant, `fail` when violated, plus a new `replace` verdict
  for simplification) would leave `revise` without a real implementor — one
  protocol, one response type, store level shrinks to container duties
  (hold/enforce/reify). Feature work with risk; backlog, not now.
- **Step 4 — sweep.** Update `constraint-propagation.md` (Phase 3 = done, this way),
  the machinery doc's contracts section (rules become signatures), CLAUDE.md, and the
  chokepoint javadoc.

Sizing honestly: Step 1 is days; Step 2 is the propagation-redesign-sized effort
(expect it to surface latent bugs the same way Phase 2 surfaced `mulIntervals`);
Step 3 is mechanical but wide (every `MiniKanren.unify` caller). Do not start Step 2
without the same discipline as Phases 1–2: failing pins first, one commit per move,
full suite green each time.

## 7. Java 8 caveats

- No sealed types: `Verdict`/`Reaction`/`Inference` are closed by package-private
  constructors + static factories; document the closed set.
- No module-level friends: the capability boundary is package-privacy, which forces
  co-locating the applier with `Package` — either move the driver into
  `com.tgac.logic.unification`, or give `Package` a package-private
  `apply(Prefix)` that a single bridge class in `unification` exposes to `ckanren`.
  Decide at Step 3; prefer moving the driver (one class) over widening `Package`'s
  API.
- `Substitutions` as a type can be a thin read-only view over the existing
  `HashMap<LVar<?>, Term<?>>` — no data-structure change.

## 8. Acceptance tests

1. All existing suites + `PropagationPinTest` + `NeqFdBridgeTest` green after every
   step (the machinery's behaviour must not change — this is an API refactor).
2. New pins for Step 2: two stores emitting contradictory `bind`s in one pass →
   loud failure (today: silent merge-keeps-first); an inference emitted with custody
   transfer but unroutable → the emitting reaction fails (rule C); identical
   narrowings from two sources → applied once.
3. Step 3: compilation itself is the test — the forbidden calls no longer exist to
   be written. Verify `git grep extendS` outside the chokepoint returns nothing.

## 9. Non-goals

- Performance. The worklist may or may not beat the recursion; measure after Step 2,
  optimize only if it matters. The motivation here is the scorecard, not speed.
- Growing the Inference vocabulary beyond `bind`/`narrow` (rule B).
- Solving Projection's identity crisis beyond the §5.3 decision.
- Touching the search/scheduler substrate, `Package` immutability, or the reify
  pipeline's structure.
