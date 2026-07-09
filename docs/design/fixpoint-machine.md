# The fixpoint machine — architecture note

**Status:** architecture principle + explicit guidance. This is NOT an implementation task.
Its two jobs are (a) give one shared vocabulary for the engine's fixpoint-based subsystems,
and (b) **stop a future implementer from prematurely merging them into one engine.** The
elegant unification below is seductive; read the recommendation (§4) before acting on it.
**Reviewed July 2026 (with Tom), after the narrowing instance was built: the recommendation
HELD — see §9 for what the intervening work taught, including the unification question asked
and answered again.**

Companion docs (the concrete instances):
- `docs/design/constraint-kernel.md` — the *narrowing* instance (historical record);
  `constraint-kernel.md` and `constraint-kernel.md` are the shape it
  actually shipped in.
- `docs/design/semiring-inference.md` — the *accumulating* instance (still planned).
- `docs/design/tabled-constraints.md` — the two instances MEETING (design sketch): what it
  costs to let tabling and constraints cooperate.

---

## 1. The pattern

Several things this engine does are, abstractly, the *same* computation: **iterate a
monotone operator on a complete lattice until it reaches a fixpoint** (Knaster–Tarski /
Kleene iteration).

- **Tabling today (Boolean):** the least fixpoint of the program's immediate-consequence
  operator over the lattice of answer sets. It grows the answer table until it stops changing.
- **Semiring-tabling (planned, `semiring-inference.md` §7):** the same iteration, but each
  table entry accumulates a semiring value instead of a bare set.
- **Constraint propagation (IMPLEMENTED, July 2026):** the greatest fixpoint of the
  narrowing operators over the lattice of variable domains. It shrinks domains until
  nothing narrows further. Shipped as the agenda drain in `ckanren/Propagation` with the
  store boundary of `constraint-kernel.md`.

That common shape is real, and it is a useful thing to *see*. It is not, by itself, a reason
to share code.

---

## 2. They are DUALS, not one computation

The two run in opposite directions, which is exactly why "just parameterize one engine" is
misleading:

| | Tabling / semiring | Constraint propagation |
|---|---|---|
| moves | **grows** information (adds answers) | **shrinks** possibility (removes values) |
| fixpoint | **least** (from ⊥, climb up) | **greatest** (from ⊤, descend) |
| lattice op | join / ⊕ | meet |
| keyed by | tabled call `(goal, args)` | variable |
| work unit | run a whole goal body (a sub-search) | apply one propagator (cheap, local) |
| level | across the whole search | within a node, before branching |
| integration | entangled with the CPS/fiber engine | self-contained narrowing loop |

There is a nuance that makes them abstractly identical: reverse the order on domains (treat
"more constrained" as "larger") and narrowing becomes *growing*, so propagation is also a
least fixpoint — in the reversed lattice. So the honest unification is "**least fixpoint of a
monotone operator on a lattice**, parameterized by (lattice, order, operator)." True — but
that abstraction is thin (see §3).

---

## 3. What is actually shared, and what is not

**Shared (thin):** a worklist, change-detection, and "iterate until nothing changes." Perhaps
50–100 lines.

**Not shared (where the real work is):**
- the **operators** — goal bodies vs. domain-narrowing rules;
- the **state** — answer tables (keyed by call) vs. domains (keyed by variable);
- the **granularity** — running a whole recursive sub-search vs. a cheap local narrowing;
- the **direction / termination** — grow-to-convergence (needs a star/closure for
  non-idempotent semirings) vs. shrink-to-quiescence (needs the descending-chain condition);
- the **integration** — tabling is woven into the CPS/fiber machinery (parked continuations);
  propagation is a self-contained loop inside a node.

And crucially, **the optimizations do not transfer.** AC-3's value is its variable→propagator
dependency index, which is meaningless to tabling (whose "propagators" are goal bodies keyed
by call). Tabling's value is memoizing sub-searches and parking continuations, which
propagation has no use for. The clever parts are client-specific; only the dumb loop is common.

---

## 4. Recommendation (the load-bearing part)

**Do NOT build a unified fixpoint driver up front.** Reasons:
1. The genuinely shared code is thin; the clients diverge on every axis that carries work.
2. Tabling is *working, integrated* code — re-expressing it on a generic driver is a large,
   risky refactor for the benefit of sharing a while-loop.
3. Neither optimization transfers, so a shared driver would be mostly type parameters and
   callbacks (all the real logic) around a trivial loop.
4. AC-3 and semiring-tabling **do not exist yet.** Unifying two imagined subsystems against a
   guessed-at abstraction is textbook premature generality.

Instead:
- Build **constraint AC-3** as its own focused thing (`constraint-kernel.md`). Its
  dependency index and narrowing loop want to be simple and fast, not generic.
- Build **semiring-tabling** by generalizing tabling's **existing** worklist/parking
  (`semiring-inference.md` §7), not by porting tabling onto something new.
- Extract a shared `Fixpoint` helper **only if**, after both exist, the worklist and
  change-detection code turn out to be genuinely duplicated and drifting. Bottom-up, driven
  by real duplication — never top-down from this note.

---

## 5. If it is ever extracted — the sketch

Provided only so it is concrete when/if §4's condition is met. Do not build this now.

```java
// A generic monotone-fixpoint driver. Clients supply the state lattice, the operators,
// and change-detection; the driver owns the worklist and the "iterate to no change" loop.
interface Fixpoint<State> {
    // Run pending operators until the state stops changing (a fixpoint), or return
    // failure (bottom). Monotonicity of the operators is the caller's obligation and is
    // what guarantees termination + order-independence.
    Optional<State> solve();
}
```

Constraint-AC-3 would instantiate `State` = domain tuples with meet + a variable→propagator
index; semiring-tabling would instantiate `State` = answer tables with ⊕ + a call→consumer
index. They would be two *clients* of the driver, not one a special case of the other.

---

## 6. Why keep this note even though it says "don't build it"

The shared *mental model* earns its keep by keeping both concrete designs honest — it is a
review checklist, not code:
- Propagators MUST be monotone/contracting, or the fixpoint neither exists nor is
  order-independent (this is why the constraint fix works at all).
- Non-idempotent semirings (counting, probability) need a **star/closure or convergence
  story** for cyclic programs — the same "does the fixpoint exist?" question, in the other
  direction.
- Both need cheap **change-detection**; the persistent `Package` (reference equality on
  immutable sub-maps) is the tool for it in both.
- One vocabulary — monotone operator, complete lattice, least/greatest fixpoint, worklist —
  lets one reviewer reason about both subsystems.

---

## 7. When unification WOULD be right (so the door isn't nailed shut)

- **Greenfield:** if the engine were being designed from scratch with both requirements known
  up front, a unified fixpoint core could be a clean foundation.
- **Proven duplication:** once both subsystems exist, if the worklist/change-detection code is
  copy-pasted and diverging, extract §5's helper then.

Neither condition holds today (not greenfield; and as of July 2026 BOTH loops exist — the
agenda drain and tabling — and share no code to deduplicate, see §9). So the answer today
is: **shared model, separate code.**

---

## 8. Non-goals

- A unified fixpoint driver, built up front or retrofitted onto working tabling. (§4.)

---

## 9. What building the narrowing instance taught (July 2026 review)

The propagation engine now exists, built separately per §4, and the experience upgrades
several of this note's predictions to observations — recorded here so the next "shouldn't
we unify?" conversation starts where this one ended.

- **"Only the dumb loop is common" is now inspectable fact.** The agenda drain is a
  synchronous, Package-local, confluent loop over cheap operators (one item per deferred
  step); tabling is a cross-branch coordination protocol woven through parked
  continuations, where the ascending machine's worklist IS the search scheduler's frame
  queue. They share a mental model and zero lines. An extraction today would delete no
  duplication, and an abstraction that deletes nothing is speculation (YAGNI).
- **The third customer is not a lattice.** Sharpening §6's star/closure remark: semiring
  ⊕ for counting, probability and provenance is NON-IDEMPOTENT — two derivations of the
  same answer must count twice, where a lattice join would collapse them. Idempotence is a
  lattice law; its absence changes the convergence theory (star/closure, ω-continuity),
  not just the parameters. A "lattice fixpoint engine" built now would be a roof the very
  next tenant cannot live under; the abstraction honest enough for all three instances —
  ordered algebra plus a convergence policy — is a while-loop with type parameters.
- **The instances couple through vocabulary, not through a shared engine.** The
  interesting interaction — tabling and constraints cooperating — happens entirely at the
  boundary: call keys carrying constraint regions, per-store entailment, residue restated
  at consumption (`tabled-constraints.md`). Notably the coupling can CREATE termination:
  a descending region can bound an ascending machine's novelty (`nat(x)` diverges under
  plain tabling; `nat(x), x ∈ {1..10}` is finite). None of that needs merged internals.
- **The termination story unified conceptually without unifying code.** Plain tabling's
  termination condition ("finitely many distinct answers/subgoals") is the special case
  of "no infinite antichain in the answer/call order" where the order is discrete
  (variant equality). Constraints enrich the order on the same product space
  (term × region): comparability adds subsumption-dedup, incomparability adds novelty.
  Call-side widening is sound in both coordinates (XSB's subgoal abstraction is the
  term-coordinate instance; dropping call residue is the region-coordinate instance);
  answer-side widening is unsound in both. One theory, two coordinates, still two engines.
- **A leaf-level `Worklist` WAS extracted (July 2026, `functional/fibers/Worklist`)** —
  under §4's own bottom-up rule, at the moment the store-owned cascades would have
  duplicated it: a fiber-stepped drain-to-quiescence loop (one item per deferred
  step). It is explicitly NOT the §5 driver: the agenda and tabling remain their
  own machines; `Worklist` is a brick stores use INSIDE their hooks.
- **A user-facing `Lattice<L>` interface was considered and DEFERRED (Tom, July 2026).**
  The right-sized unification, if ever wanted, is a small interface (meet, order/entails,
  bottom, optionally enumerable atoms) with the generic abstract-domains store as its
  first paying customer, adopted — not migrated to — by `Domain` and the TCLP hooks.
  Deferred until such a customer is real; recorded so it is proposed as adoption, never
  as an engine rewrite. The §4 verdict on the engine merge is unchanged and, per the
  above, stronger.
