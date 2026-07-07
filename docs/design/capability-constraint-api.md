# The capability constraint API — design and migration plan

**Status:** design, NOT implemented. This is the concrete shape of
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
interface Propagator {
	/** Variables whose binding or narrowing re-runs this propagator. */
	Set<LVar<?>> watched();

	/** Re-examine against the current state. Reads anything, mutates nothing. */
	Verdict propagate(Package state);
}

// the closed verdict set:
Verdict.fail()                    // violated — kill the branch
Verdict.keep()                    // undecided — stay parked (THE DEFAULT-SAFE CASE)
Verdict.discharge()               // permanently satisfied — remove me
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
		return leq(u, v) ? Verdict.discharge() : Verdict.fail();
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

### 2.3 `Reaction` — store hooks return their factor, not the world

```java
interface ConstraintStore extends Store {
	/** React to newly applied bindings. Read anything; change only your own factor. */
	Reaction onPrefix(Prefix prefix, Package state);
	// enforceConstraints / reify / pendingPropagators as today (adapted)
}

Reaction.fail()
Reaction.unchanged()
Reaction.updated(Store myNewFactor)
Reaction.updated(Store myNewFactor, List<Inference> inferences)
```

A reaction physically cannot touch substitutions or another store's entry. The
chokepoint assembles the package from returned factors.

### 2.4 `Inference` — cross-factor information as data

The shared vocabulary of the system — the only way information crosses factors:

```java
Inference.bind(Prefix p)                  // substitutions grow (deferred unification)
Inference.narrow(LVar<?> x, Domain<?> d)  // x's domain shrinks
```

Exactly two variants, matching the two shared factors of the product lattice
(bindings grow; domains shrink). Everything else is a store's private state. Emitted
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

### 5.3 Projection — the honest wrinkle (DECISION NEEDED)
Projection's "constraints" are user goals — arbitrary, branching search. A `Verdict`
is a deterministic report and cannot express "explore two alternatives". Options:
1. **`Verdict.run(Goal)`** — the framework splices the goal into the search at the
   wake point. Keeps Projection in the framework; the escape hatch is explicit in the
   type instead of hidden inside "everything is a Goal".
2. **Evict Projection from the store framework** — it is a search trigger wearing a
   constraint costume; reimplement as a goal combinator over the wake mechanism.
Ask Tom before implementing either. Default assumption for sizing: option 1.

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
- **Step 3 — Prefix + the visibility lock.** `MiniKanren.unify` returns
  `Option<Prefix>` over a `Substitutions` view; `CKanren.unify` = mint + drive;
  Neq's trial unification inspects the prefix; delete `withoutConstraints`;
  make `extendS`/`withSubstitutions`/`Package.of`-with-substitutions inaccessible
  outside the chokepoint (see §7). After this step the scorecard's "unrepresentable"
  rows are actually unrepresentable.
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
