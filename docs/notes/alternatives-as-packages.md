# Alternatives held as maintained packages make verification incremental

- **status**: parked (August 2026 — the human's idea, landed on the same
  shelf and trigger as watched-revise: no honing without a payoff in
  evidence; this note records the better shelf occupant)
- **evidence held**: derivation. The census and profiler priced the
  problem it addresses (a trial is a ~33-step mini-solve, repeated per
  binding per alternative, re-deriving the same consequence closure
  each time); the mechanism itself is unmeasured.
- **imports**: constructive disjunction ⋯import (CLP lineage; already
  named in disjunction-store-pays-in-products' accounting, mixed record
  noted there); agreement move (named in the same note's verdict as
  shelved machinery).
- **obligations**: (1) design pass on the entailment check — with
  maintained worlds, "entailed" stops being "scratch unchanged" and
  becomes "this world's delta over base is empty", which is
  answers-as-diffs' representation; the check must be shown cheaper
  than the trial it replaces, or the idea dies; (2) the exclusion
  discipline as a law: worlds carry no disjunction store (rival-worlds
  semantics preserved verbatim from the trial's
  {@code withoutStore}), and stay settled — runs discarded at each
  application, suspensions refused; (3) if built, the same receipts as
  watched-revise: census-derived targets, step pins before/after on
  both benchmark poles; (4) memory sanity — k persistent
  structure-shared worlds per disjunct, measured not assumed.
- **links**: watched-revise.md (composes: a watch check is a cheaper
  gate than even an incremental application — the two attack different
  halves of the waste), disjunction-store-pays-in-products.md (the
  concession this fills the middle of, and the accounting that shelves
  both), docs/shelved/answers-as-diffs.md (the delta representation IS
  the entailment check), docs/design/lattice-store.md (the speculation
  tier is this idea generalized).

The economics note's concession mapped two corners: conde holds a
package per branch AND forks the search (fast per branch, exponential
branches); the store holds posting lists and does not fork (one branch,
but every ask about an alternative rebuilds its world from scratch —
impose on base, drain to quiescence, read the verdict, discard). This
note is the third corner: **hold packages, don't fork the search**.
Each alternative's postings are applied when the disjunct arrives, and
the resulting package — the alternative's hypothetical world — is kept
in the store. A binding reaching the store applies to each live world
incrementally: one resolve into an already-propagated package instead
of a from-scratch mini-solve. The consequence closure of an alternative
is derived once, at statement, and maintained — not re-derived per ask.

The verdicts fall out of the application itself. Refuted: the
incremental application fails — free detection, no separate ask.
Entailed: the world's delta over base has shrunk to empty — the
alternative no longer adds knowledge (the design-pass obligation).
Unit propagation gains the note's sharpest win: when the last rival
dies, the surviving world — accumulated consequences and all —
BECOMES the branch package. Today's unit imposition re-imposes the
survivor on base and re-derives everything it ever knew.

Beyond cutting the carrying cost, maintained worlds are the substrate
the agreement move needs: propagating what ALL alternatives agree on
into the base is a fold-meet over the alternatives' stores —
impossible over posting lists, near-mechanical over worlds. That is
the capability that would change the store's economics in kind
(propagation FROM undecided clauses) rather than in constant.

What it does NOT buy: the per-binding fan-out stays linear in live
alternatives — the constant shrinks, the shape doesn't — and the
workload question that shelved watched-revise is untouched: this too
pays only where deciders exist. Memory is spent deliberately (the
human's framing: we are optimizing scaling, not memory): k worlds per
disjunct, persistent and structure-shared with base.

Cheapest kill: show the entailment-by-delta check costing as much as
the trial it replaces (then the idea only helps refutation, and
watched-revise alone is the simpler shelf occupant); or show the
exclusion discipline leaky — worlds accumulating suspensions or runs
that cannot be discarded without changing answers.
