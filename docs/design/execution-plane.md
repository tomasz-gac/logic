# The execution plane's target surface: walked theories, three doors, one probe

- **status**: DESIGN (August 2026) — the ratified target that stages E–G
  approximate. Stages E (absorb) and F (tabling over Theory) build toward
  it unchanged; stage G *is* this document, executed as its own gated
  design pass. No stage lands without its step-count pins.
- **charter**: the Factor/Theory/Atom arc's closing conversations — the
  rename-as-context-injection observation, the walked-invariant probe,
  and two withdrawn proposals whose post-mortems are §7.
- **links**: constraint-kernel.md (the as-shipped kernel this document
  retargets), nogood-store.md §5 (the depth-one ruling the Probe types),
  lattice-store.md (the probe as the one sanctioned bridge), condition.md
  §8.1 / #74 (the suspensions seam left open here).

## 0. The idea

State enters a factor in exactly one costume: a Renaming. The
chokepoint's three doors — resolve, activate, absorb — each walk the
content they admit (a prefix walks the affected residents; an entering
atom or theory walks at the door), so the resident theory is **always
walked**: its atoms already reflect every binding this branch has made.
Once that invariant holds, `normalize` stops needing a Package. What it
needs is the **changed names** (which slots the door touched — the
re-examination frontier, promoted from optimization hint to parameter)
and the **one question a store may ask the rest of the world** — the
Probe. Everything else the Package used to smuggle in has a better home:
walking happened at the door, collapses leave as data for the chokepoint
to mint, and quiescence is the door's guarantee, not the store's
discovery.

The shape is bought for the interface collapse — one door pipeline
replacing the stated/revise/wholesale-normalize triplication, custody
expressed in types instead of javadoc — not for speed. §7 records the
measurements that killed the speed claims.

## 1. The invariant

**The resident theory is walked**: no atom mentions a name the branch
has already bound. Maintainable because the doors are the only writes
and a logic variable binds at most once per branch — each atom is
rewritten at most once per watched name, and an entrant meets an
already-walked resident set, so the invariant is inductive. The
watched-name index routes each door to the affected atoms; unaffected
atoms are untouched.

Corollaries the arc already shipped without naming them: aliasing is
atom re-slotting (fusion at the collision — the `with` door); a
ground-keyed entry is decided knowledge (admitted → discharged; refused
→ ⊥, which is a legal plan value that execution reads as failure);
`normalize` verifies ground-keyed entries rather than skipping them.

## 2. The value plane (already built; restated for closure)

- **Atom** — the unit: `getFactorClass`, `name()`, `watched()` (the
  HELD collection; its equality is the kind's identity granularity),
  `payload()`, total `rename`. PartialOrder with structural default and
  sharp family overrides; kinds that digest slot-mates declare
  Semilattice (Imposition: same-target domain meet; Nogood: same-surface
  conjunct union).
- **Theory** — the plan-space value: meet = insert / fuse slot-mates /
  delete strictly dominated; leq = the covering order; slot, kind and
  watched-name indexes; `with` / `without` as the factor doors.
- **Coherence precondition (new, now law)**: Theory's leq and meet are
  defined for SAME-STATE theories or Any'd ones — the two contexts in
  which both sides' terms were walked against the same knowledge. Every
  comparison site today already respects it (same-package entailment
  checks; Any-space keys); this document promotes practice to contract.

## 3. The execution plane

```java
/** The one cross-store channel: the settled sibling product, self
 *  excluded, reachable only as a question. Constructed by the door. */
interface Probe {
    Fiber<Trial.Outcome> impose(Posting conjunct);
}

interface Factor<S extends Factor<S>> extends Packaged {
    Theory<S> theory();                      // the walked resident
    Fiber<S> rename(Renaming r);             // the ONE context door
    Fiber<Revision> normalize(Set<Term<?>> changed, Probe probe);
    <T> Goal enforce(Term<T> x);             // FAMILY-OWNED: answer-time policy
    <A> Term<A> reify(Term<A> t, Renaming answer);  // FAMILY-OWNED: rendering / guards
    boolean isEmpty();
}
```

`enforce` and `reify` stay family-owned. Labelling is policy, not
structure — FlatConstraints holds enumerable imposition atoms and
deliberately does not label, and labelling ORDER is where the heuristics
work lands (labelling-order-blind.md), so no generic default may freeze
it. A shared enforce skeleton (term walk, label-then-reexamine loop, the
completeness guard) becomes worth extracting when a second labelling
family exists (pldb's row-set store); the policy surface belongs to the
heuristics design. Reify likewise names two different jobs today —
residual rendering (nogoods) and an invariant guard (lattice) — and
stays per-family until that split is itself designed.

`normalize` is the global prune over the walked theory: same-store
cross-atom work (propagators, the subsumption sweep) plus cross-store
questions through the probe. Its `Revision` carries consequences as
DATA — collapses as (name, value) pairs, runs as degenerate
suspensions — and the chokepoint mints the Prefixes, because the
chokepoint owns binding creation.

The Probe is today's `state.withoutStore(self)` plus settle, typed down
from "a whole package" to "the one question you may ask it." What the
retyping buys: custody becomes structural (a store cannot wander a
package it never holds); the depth-one ruling is enforced by the
constructor (the driver builds the probe without the asking store — the
resident-recursion stays unrepresentable, now visibly); the settle moves
to its owner (the door guarantees quiescence; Verification stops calling
`Propagation.settled` itself). Sequential conjunct threading stays
inside `Trial` — the probe takes the conjunct whole.

## 4. The doors — one pipeline, three scopes

```
resolve(prefix):  apply prefix to substitutions
                  → per factor: rename(prefix-as-renaming)      [ingestion]
                  → normalize(prefix names, probe)              [prune]
                  → mint collapses, splice runs, recurse        [consequences]
activate(atom):   rename atom at entry → with() → normalize(its names, probe)
absorb(theory):   rename theory at entry → meet() → normalize(its names, probe)
```

The nogood store's lanes map without residue: binding-shaped conjuncts
decide from their own walked literals (`Trial.now`'s reads, pre-paid at
the door); store-shaped conjuncts go through the probe, where the trial
imposes, propagation runs, and sibling stores veto exactly as today.
The lattice store's update routing splits the same way: alias re-slot,
ground admit, and fusion are ingestion; propagator examination and the
cascade are the prune; collapse minting leaves as data.

## 5. What dies

| today | fate |
|---|---|
| `meet(S)`, `leq(S)`, `combine` | die — tabling compares theories; algebra lives in Theory only |
| `contains(Atom)` | dies into theory reads |
| Factor `split(vars)` | dies — theory-side |
| `normalize(Package)` | rename(full walk) + normalize(all names, probe) |
| `normalize(Prefix, Package)` | rename(prefix-as-renaming) + normalize(prefix names, probe) |
| `stated(Atom, Package)` | dies into the activate pipeline |
| `meet(Atom)` | absorbed into ingestion |
| Verification's settle call | moves to the door |
| update's Prefix minting | moves to the chokepoint |

## 6. The asterisks

1. **Quiescence is the door's obligation.** A probe is settled by
   construction; normalize is never invoked mid-agenda. This is where
   "state-free" earns its asterisk.
2. **Propagator bodies narrow** from `(terms, Package)` to reading their
   own walked theory, plus the probe should a body ever genuinely need a
   sibling (none does today). A real contract change, executed at G.
3. **Suspensions stay the driver's citizens** — outside this shape; the
   #74 seam (do their watched surfaces join the door discipline?) stays
   open and is not silently resolved here.
4. **Verification strength is a dial deliberately left at full.** The
   probe runs real propagation. The zero-propagation downgrade (decide a
   store-shaped literal by fuse/covering reads against the sibling
   theory) is sound and delay-safe — verdicts are monotone under binding
   growth, and labelling grounds every literal into the binding-shaped
   lane eventually — but weaker pruning can cost exponential search, so
   it is available only against a fixture receipt, never by default.

## 7. Lineage: the two proposals this shape survived

Recorded because the path is the argument (method.md).

- **Atom-level evaluation at crossings (killed before building).** An
  `Outcome`-bearing rename — atoms deciding themselves as they cross —
  audited out: semantic value zero (execution already decides
  everything, one normalize later), the normal-form claim vacuous
  (execution's invariant keeps decided atoms out of every comparison
  site), perf unmeasured, and the price was a second decision procedure
  owing an eternal agreement law to the first. The surviving residue is
  §1's discharge corollaries, which are the SAME procedure, relocated,
  with nothing left to agree with.
- **Always-walked as a performance claim (withdrawn on measurement).**
  The eager-vs-lazy framing was confused — an affected atom walks once
  per revision either way; the separable lever is watch-filtering.
  Measured on the scaled allDifferent fixture: 52,830 wholesale
  per-resident verifications, 37,110 genuinely affected — the skippable
  fraction is ~30% of a synchronous sub-second cost, not the ~80% the
  arithmetic promised, and the once-cited 76%-drain profile belonged to
  the deleted disjunctive store. The invariant stands on the interface
  collapse alone; every migration step is priced by the pins.

## 8. Vocabulary (proposed for ratification with this document)

Entering: **Factor / Theory / Atom** (the arc's ratified type triple,
owed their glossary lines), **covering** (Theory's leq — grade two of
the structural ⊂ covering ⊂ semantic tower), **slot** (collision key:
name + held watched collection), **fusion** (slot-mates combining via
their declared Semilattice), **subsumption deletion** (dominated atoms
drop; import — resolution provers' subsumption deletion, receipt owed
at entry), **Probe** (this document's §3; the lattice-store bridge,
typed), **walked theory** (§1's invariant). Retiring at stage H as
already planned: Store, Stored, ConstraintStore, prepend, revise,
restate-as-operation, Unknown → Name, Hole → Any.
