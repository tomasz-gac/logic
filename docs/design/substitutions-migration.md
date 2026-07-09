# The Substitutions migration — retyping the unifier onto the shared factor

**Status: Steps A and B IMPLEMENTED (July 2026, branch `substitutions`) — the
Substitutions class carries the consumer-derived operation set (interface
promotion deferred to the first second implementation, per YAGNI) and the
unifier is typed over it, with Package entries as thin adapters; the
Package.empty() trial-unification wrappers are gone. Steps C (decompose) and
D (representation, benchmark-gated) remain — its-own-session work: it touches the hottest path
in the engine. This completes the capability design's original `Substitutions`
sketch (its spec read `MiniKanren.unify(Substitutions s, …)`; see
`constraint-kernel.md` §7 for lineage), plus the
two follow-ons that surfaced while designing it: the kind-tagged decomposition
and the swappable-representation question.**

Prerequisite reading: `constraint-kernel.md` (the kernel as shipped, including
the `Substitutions` view).

---

## 1. Why

Three pressures, one migration:

1. **The landmine rule becomes a type.** "Raw `MiniKanren.unify` bypasses all
   constraint processing and is legitimate only inside the unifier" is today a
   javadoc contract. A unifier typed over `Substitutions` — a view with no
   route to any store — makes it physics: the unifier *cannot* see constraint
   state, so bypassing constraint processing is all it could ever do. Same move
   as `Prefix` (born valid), `Update` (notes can't leak), suspension ripeness
   (conditions can't read factors).
2. **Real warts delete.** Disequality's trial unification builds
   `Package.empty().withSubstitutions(s1)` wrappers purely to feed the
   unifier's over-wide signature; occursCheck/walk take a whole `Package` to
   read one map. All of that becomes direct calls on the view.
3. **The structure question wants one owner.** `members` (read-only traversal)
   was derived from `unify`'s decomposition and immediately caught drift twice
   (LTree missed; LList classified by Term instead of value). Sharing one
   `decompose` between the unifier and every traversal makes drift structurally
   impossible instead of pin-caught.

## 2. Step A — the `Substitutions` interface

Promote the shipped `Substitutions` view from a final class to an interface
whose operation set is read off the actual consumers (no speculative ops):

| operation | consumer |
|---|---|
| `Term<?> binding(LVar<?>)` | the chain step — `walk`'s loop body, `Watches` |
| `<T> Term<T> walk(Term<T>)` | everyone |
| `<T> Term<T> walkAll(Term<T>)` | ripeness, projection, reify path |
| `boolean isGround(Term<?>)` | suspension conditions |
| `Substitutions extend(LVar<?>, Term<?>)` | the unifier (today `Package.put`) |
| bulk extend (`Prefix.appliedTo`) | the chokepoint |
| `long size()` | reify's `_.N` numbering — a HIDDEN CONTRACT: renaming depends on it |
| iteration over bindings | Neq's map-level verification |

Contracts to pin BEFORE anything else changes:

- **Equality is representation-independent.** `Package` is a `@Value` over the
  map; two packages with the same bindings must stay equal regardless of
  backing. Pin with a test the moment a second implementation exists.
- **`size()` = number of bindings**, because reified variable numbering
  (`_.0`, `_.1`, …) derives from it and the answer-string pins assert it.
- Monotone: no operation removes a binding. (Backtracking is a different
  package, never a retraction.)

## 3. Step B — retype the unifier

`MiniKanren.unify`/`unifyPrefix`/`occursCheck`/`walk` internals take
`Substitutions` and thread it; `unifyPrefix` still mints `Prefix` via the
collecting Extender (unchanged). Callers:

- `CKanren.unify`: `MiniKanren.unifyPrefix(s.substitution(), u, v)`.
- Disequality trial unification: direct — the `Package.empty()` wrapper dance
  deletes; `unifyConstraints` threads `Substitutions` values.
- `EnforceConstraintsFD.unifyTerms`: same one-liner change.
- `reifyS` stays `Package`-shaped only if it must (it uses `extend` + `size`;
  it can move to the interface wholesale).

The Extender interface moves with it. Everything constraint-aware still enters
through `Propagation.resolve(Prefix)` — this migration changes who the unifier
*talks to*, not who may call it.

**Gate: the full suite plus the specific pins** — alpha-equivalence
(`shouldReify…Canonically`), rename numbering (LogicTest answer strings),
tabling variant checks, and the perf canary (`shouldReverse3` wall-clock; this
path regressed once before, commit 05c1398's history).

## 4. Step C — the kind-tagged decomposition

`unify`'s structural case is three branches (`unifyIterable`, `unifyLList`,
`unifyLTree`) plus `toIterable`; `members` is their read-only shadow. Replace
both with one owner:

```java
// in MiniKanren: the ONE place that knows what structure is
static Option<Decomposition> decompose(Term<?> v);
// Decomposition = (Kind kind, List<Term<?>> members)
// Kind = ITERABLE | TUPLE | LLIST | LTREE   — the CURRENT coarse classes, exactly
```

- `unify` structural case: decompose both; same kind AND same arity → zip-unify
  members; else fail. **Behavior preservation is the whole risk**: the current
  classes are coarse in ways that must survive — any-iterable unifies with
  any-iterable (List×Vector today), tuples arity-check by element count, empty
  `LList`/`LTree` fall through to value equality. Write the
  cross-container pins FIRST (List×Vector unifies; tuple×list of same arity
  does NOT; empty-vs-nonempty list fails).
- `members(v)` = `decompose(v).map(Decomposition::members)` — the derived
  shadow becomes the same object.
- `mapStructure` (the rebuilder) stays separate: rebuild needs the collector
  registry; traversal and reconstruction remain different verbs.

## 5. Step D (gated on measurement) — swappable representation

Byrd's observation: most substitution lists are small, so classic miniKanren
uses an association list — O(1) extension, zero hashing, free sharing. True for
his workloads; this repo's own history is the counterexample —
`shouldReverse3`-class derivations hold thousands of bindings per branch, and
an O(n) chain-step lookup rebuilds the 2s→20s regression class at the
representation level.

So: the INTERFACE ships (step A makes backing swappable in principle); no
second implementation ships without a benchmark. The interesting candidate is
the **adaptive representation** (Clojure's move: array-map under ~8–16
entries, promote to HAMT above) — it captures the small-subst win where Byrd
is right (fresh branches, which dominate a BFS frontier) without betraying
deep derivations. Decision procedure: a benchmark pitting HAMT / assoc /
adaptive on BOTH workload shapes (many small branches vs one deep derivation),
settled by numbers the way `SchedulerEquivalenceTest` settles scheduler
questions. Until then, vavr `HashMap` is the one implementation.

## 6. Order and sizing

A (interface + contracts + pins) → B (unifier retype) → C (decompose) → D
(never, until measured). A is small; B is mechanical but wide (every unifier
call site) with the perf canary watched; C is the risky one — hot path,
subtle class-preservation, pins first. One focused session for A+B; C its own
sitting. Each lands green on the full suite.

## 7. Non-goals

- No change to who may unify: `resolve(Prefix)` remains the only door for
  constraint-aware code.
- No triangular-vs-idempotent substitution redesign; walk chains stay.
- No representation swap without the §5 benchmark — an assoc list on vibes is
  how the 05c1398 class of regression comes back.
