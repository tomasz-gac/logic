> **July 2026, final form:** suspensions are a KERNEL feature —
> `Propagation.suspend(watched, ripe, body)` with `Suspension` parked in a
> persistent driver-owned store, ripened on watched-chain binds, spliced via
> the run lane. `ripe` is a `Predicate<Substitutions>` (structurally scoped to
> shared knowledge) and must be monotone: adding bindings never falsifies it.
> Projection is a facade over `suspend`. Store-emitted search effects are
> `Revision.withSuspend` (degenerate always-ripe = a plain run); `Verdict.run`
> and `Revision.withRun` no longer exist. Where this doc says Verdict.run,
> read `suspend`/`withSuspend`.

# Suspensions — parked goals woken by bindings

**Status: the feature IS implemented — as `Verdict.run` (July 2026).** What §5
called the interim form turned out to be semantically complete once the two-phase
drain was pinned (runs collect during propagation, splice only after quiescence
with the agenda removed, execute against a quiesced store with fresh synchronous
drains inside; the hosting store's enforceConstraints is the force policy —
Projection's final-wake-then-throw is the loud default). A suspension today is a
propagator returning keep (parked) or run(goal) (resume), hosted by any
ConstraintStore:

    Propagator.of(HostStore.class, watchedTerms, state ->
            ready(state) ? Verdict.run(goal(state)) : Verdict.keep());

pldb's deferred lookups — the designated second customer — are expressible this way
directly and do NOT need the §6 extraction. The extraction is therefore DEMOTED to
an optional naming refactor (Verdict purity + a named registry instead of a vacuous
store); do it only if the hosting pattern proves confusing in practice. §§1–4 remain
the concept reference; §6's mechanics remain valid if the refactor ever happens.

---

## 1. The concept: a constraint is not a coroutine

The store framework hosts two things that only look alike:

- **Propagators** — deterministic, contracting reports: given the current state,
  narrow/verify/fail. Their confluence (order-independence of the fixpoint) rests on
  exactly this contract.
- **Suspended goals** — `Logic.project`'s "wait until these vars are ground, then run
  this arbitrary goal". The goal may **branch**, bind anything, run a sub-search. This
  is Prolog's `freeze`/`when` — a coroutine trigger wearing a constraint costume
  (today: `ProjectionConstraints`, a ConstraintStore with a vacuous identity reaction).

The distinction is not aesthetic. **An arbitrary goal must never run mid-fixpoint**:
the propagation drain's order-independence argument holds only for contracting
operations, and a branching goal spliced into the middle of a drain breaks it. Both
the interim and the final form must therefore *collect* ready goals during
propagation and *splice them only after quiescence*.

## 2. The API

```java
/** A parked goal with a readiness guard, woken when a watched variable is bound. */
interface Suspension {
	/** Variables whose binding should re-examine this suspension. */
	Set<LVar<?>> watched();

	/**
	 * Ready to run? some(goal) unparks and splices the goal into the search after
	 * the current propagation drain; none stays parked.
	 */
	Option<Goal> resume(Package state);

	/**
	 * Called at answer time (the enforceConstraints analogue) when still parked
	 * after a final resume attempt. Default: fail LOUDLY — an answer emitted while
	 * a suspension is pending is unsound (its goal never constrained anything).
	 * This is Projection's existing enforce policy ("Unbound variables during
	 * projection") generalized into the API. Kinds that can force themselves
	 * override: a deferred database lookup enumerates now; a safe negation with
	 * ground args checks now, with non-ground args it stays loud.
	 */
	default Option<Goal> force(Package state) {
		throw new IllegalStateException("unresolved suspension at answer time: " + this);
	}
}
```

Parked suspensions ride the package in their own registry — driver-visible but NOT a
`ConstraintStore` (no reaction, no reify hook; it is a third kind of store the driver
knows by name, next to the substitution and the constraint stores).

## 3. The driver phases

```
1. drain the propagation worklist to quiescence      // contracting, confluent
2. collect suspensions whose resume() is defined     // remove from the registry
3. splice their goals into the search, in park order // branching happens HERE
   (their execution may bind more → re-enter 1)
4. at reify: one final resume attempt, then force()  // loud by default
```

Park order makes the splice deterministic. Note the loop: a resumed goal that binds
variables re-enters propagation; termination comes from the search itself (a resumed
goal is ordinary search work, not propagation).

## 4. The reify policy — Projection's existing one, generalized

Projection already implements the loud policy: its `enforceConstraints` gives parked
projections one final wake and THROWS ("Unbound variables during projection") when
any remain — the same discipline as FD's constrained-variable-without-domain check.
`force`'s loud default is that policy lifted into the API, not a behaviour change;
kinds with a meaningful completion (lookup enumeration) override it instead of
inheriting the error.

## 5. The interim form (what Step 2 actually ships)

`Verdict.run(Goal)` — a fifth verdict. The driver collects run-goals during the
drain and splices after quiescence (the §1 requirement — this is NOT optional).
`ProjectionConstraints` survives with its projections as propagators returning
`keep` (not ground) or `run(goal)` (ground). This is behaviour-equivalent to the
final form; the difference is purely that the coroutine concept hides inside the
verdict set and a vacuous store.

## 6. The extraction (when and how)

**When:** at the arrival of the second customer — pldb's deferred lookups
(`pldb/docs/design/deferred-lookups.md`): a parked index lookup *enumerates facts* =
branches = is a Suspension, not a propagator (that doc predates this concept and
models them as Constraints — re-read it through this lens). Safe negation rides on
the same mechanism (`force` = loud on non-ground). The same second-customer rule
that earned `Narrowing` its existence applies: do not extract for one user.

**How (mechanical once the Step-2 driver exists):**
1. introduce `Suspension` + the registry store;
2. migrate `Logic.project` to park a Suspension; delete `ProjectionConstraints`;
3. move the driver's run-goal collection to the registry's resume phase (§3) —
   the collect-and-splice code already exists, it only changes its source;
4. remove `Verdict.run`, restoring `Verdict` to pure propagation outcomes
   (fail/keep/discharge/narrowed) and the confluence story to a one-line argument;
5. reify: wire `force` (loud default; project keeps it — identical to today's
   "Unbound variables during projection" throw, no behaviour change).

## 7. Non-goals

- General coroutines (wakes are binding-driven only; no time, no channels).
- Fairness games between spliced goals (park order, done).
- Building any of this before the second customer exists.
