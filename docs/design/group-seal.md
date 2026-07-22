# The group seal — sealing sleeper rings without a global lock

STATUS: SHIPPED (July 2026, `Region.groupSeal`, ~60 lines). The Tier 2 of
completion detection: with it, sealing is TOTAL for finite solves — every
semantically keys-final call event seals as early as its dependency
closure finishes, which is full SLG completion. Companion to
`table-completion.md` (the scheme this extends); read that first for the
vocabulary: call events, coats, cells, ledgers, sleeper edges, the
singleton seal rule.

## 1. The situation, domain-free

A Region is a FEED: a growing set of published items (the cell) plus the
work that publishes them (the ledger). A feed may be declared FINAL
(sealed) when nothing can ever publish to it again — an irreversible,
once-only declaration.

A feed's work is always in one of three states: RUNNING (counted by the
ledger), ASLEEP at some feed ("wake me when that feed publishes" —
recorded in the ledger as an outpost), or dead. Note the two-book
distinction this creates: a sleeping worker sits in the watched feed's
FOLLOWER LIST (who waits FOR that feed) and in its own feed's LEDGER
(whose work it IS). Finality only ever consults the second book —
followers waiting for me are their feeds' business, not an obstacle to
mine.

**THE PER-FEED RULE (Tier 1): a feed is final when its ledger is drained
— every unit of its work started has finished — and every piece of its
work still asleep sleeps either HOME or at an already-final feed.** The
two cases are safe for different reasons. Home: a worker asleep at its
own feed can only wake on a new item here, which only my own work could
publish, and the drained ledger says none exists — circularly impossible.
Already-final elsewhere: that feed will never publish again, so the
sleeper is dead where it lies. Any other configuration — my work asleep
at a live foreign feed — must refuse: if that feed published, my worker
would wake and my feed could grow.

Now take two feeds, A and B, each running a TRANSLATOR of the other:
whatever B publishes, a piece of A's work translates into A, and vice
versa. Both feeds exhaust their original material; the translations get
exchanged; both publishers finish. What remains: A's translator asleep at
B, B's translator asleep at A. Both ledgers are drained — no one is
running anywhere.

May we declare A final? The rule says no — A's translator sleeps at B:
not home, and B is not final. Symmetric refusal at B. No event will ever
fire again. A DEADLOCK OF SEALS: each feed's verdict waits on the
other's, both conservative, forever — even though it is OBVIOUS from
outside that neither can move first.

In tabling terms this is the cross-root ring —

    p(X) :- X=1 | q(X).      q(X) :- X=2 | p(X).
    query: conde( p(A), q(A) )

— both relations mastered independently, each body reading the other,
readers parked crosswise after the answers {1,2} are exchanged. (Never a
deadlock of the search: all answers were delivered; only the flags stay
false.)

## 2. The merge identification — one rule, applied to a composite

There is no second rule. Define the MERGE of a set S of feeds as the
virtual feed whose ledger is the sum of the members' ledgers, whose
sleepers are the union of theirs, and whose HOME is membership in S.
Then the group condition is the per-feed rule applied to merge(S),
verbatim: "the merged ledger is drained" = every member drained; "every
merged sleeper is home or at a final feed" = every sleeper inside S or
sealed. Tier 2 = Tier 1 ∘ merge.

The soundness argument transfers with the merge: a wake inside S needs a
new item in S, which needs running work in S — the merged drained ledger
rules it out; nothing OUTSIDE injects items, because publishing is billed
to the publisher's own feed (in tabling: the coat rule — an answer lands
in the cell of the call whose body derived it; a master's body is its own
region; each reader's work bills its own caller). Tier 1 is
|S| = 1; the two-feed ring is |S| = 2; SLG calls the general case
completing an SCC.

WHICH merge? The smallest one that makes all sleepers home: start from
the refusing feed and close under sleeper-targets — a fixpoint ascent in
the (finite, hence bounded) join-semilattice of feed sets, S₀ = {start},
Sₙ₊₁ = Sₙ ⊔ sleeperTargets(Sₙ). The closure computation is itself a
closure operator in the engine's own §3a vocabulary — the completion
detector is described by the same lattice machinery it certifies. The
walk in §3 is this ascent; the two-phase read is the price of evaluating
the merged rule WITHOUT materializing a merged ledger — constituents keep
their own monitors, and atomicity across them is reconstructed from
monotone snapshots instead of a merged lock. Eager merging (§4) and this
differ only in the merge's LIFETIME: at park time forever, versus at
refusal time for one rule-evaluation.

## 3. The algorithm

**Trigger.** Inside the ordinary seal cascade: the singleton rule refused,
but the region is drained and unsealed — so the only possible obstruction
is a foreign-unsealed sleeper. Attempt the group.

**Phase one — the closure walk.** BFS from the refusing region along
sleeper edges. Per region reached: skip if sealed (cannot wake anyone) or
already visited; check DRAINED — a running member ABORTS the whole
attempt; record the member's `started` count (the receipt for phase two);
push its sleeper-targets. The abort is cheap and self-healing: a running
member means the set is not finished YET, and that member's own finish
event fires the cascade again later, which re-attempts the group. The
retry is the existing event flow — no loop, no bookkeeping.

**Phase two — re-validation.** Re-read every member's `started` and demand
it UNCHANGED from the receipt. This closes the race a naive walk has:
while verifying A, a not-yet-visited B could publish an item that wakes
A's sleeper — A now runs, but A was already checked and passed. The
snapshot would be a fiction assembled from different moments.

Why re-reading `started` fixes it: `started` is MONOTONE. If every
member's count is identical at two reads, no work started anywhere in S
during the interval between them — the two reads bracket a spawn-free
window, so phase one's per-member checks, whenever each individually ran
inside that window, were all simultaneously true at its end. A consistent
global snapshot with NO nested monitors — the Dijkstra–Scholten
read-ordering trick lifted from one counter pair to a set, and the
direction principle doing concurrency work again: monotone quantities are
the only things you can snapshot piecewise and still trust.

(Why can nothing start between phase two and sealing? Starting work in S
requires a wake, which requires new growth in S, which requires running
work in S — and phase two just certified there has been none since before
phase one. The same circularity that justifies the singleton rule's
check-then-CAS gap justifies this one.)

**Sealing and hand-off.** CAS EVERY member's flag first, then announce:
the marking loop completes over the whole group before any member's
`onSealed` hook fires, so every hook observes the group fully sealed
(RegionTest pins it — this is what lets the closed mode's first-announced
hook solve the whole closure, and makes an unsealed closure member sighted
at a hook an invariant breach, which `Closed` converts to a loud throw).
A lost CAS means a racing group seal got there first; skip that member's
drain, no harm. Then drain every sealed member's parked sleepers. The dead sleepers feed
the ORDINARY cascade: each one's owner is awoken and rechecked, so a group
seal can unlock further singleton or group seals outside the set, exactly
like any other seal.

## 4. What it deliberately is not

- **Not merge-then-single-rule** (SLG-WAM's ASCC design: union regions at
  park time, apply the per-feed rule to the union). Coherent, and the
  obvious question — the answer is one asymmetry: SLEEPER EDGES ARE
  TRANSIENT, MERGES ARE PERMANENT. A reader that parks and later wakes
  leaves no trace; a union cannot be undone (un-merging is dynamic
  connectivity with deletions — much harder machinery). Under eager
  merging, transient cross-reads glue regions into blobs that can only
  seal collectively — a DAG workload sharing a sub-relation seals as one
  late lump instead of piece by piece — and LATE is exactly what the
  customers cannot afford: pricing and the negation gate want the
  earliest seal each event can justify. Nor does merging remove the
  atomicity problem: "the union is drained" still spans many ledgers —
  merge the counters physically and you need the nested monitors we
  banned; keep them separate and check at seal time, and you have
  rebuilt the walk. And merging cannot wait for refusal time, because
  discovering WHO to merge with means following sleeper edges — which is
  the walk again. The shipped algorithm is the proposal executed
  VIRTUALLY: it merges the closure for the duration of exactly one check
  (phase one assembles it, phase two makes the check atomic, the CAS
  seals it) and throws the merge away. If profiling ever shows walk
  churn, the eager design remains the documented fallback — over-merge
  is sound, just later.
- **Not a scheduler feature**: everything reads ledgers and flags — the
  same two structures the singleton rule reads. The scheduler never learns
  this exists.
- **Not able to over-seal**: an infinite relation in the closure never
  drains, so every walk touching it aborts forever, and its downstream
  correctly stays unsealed. Post-Tier-2, "unsealed" means exactly one
  thing: the closure genuinely is not finished.

## 5. Why it was ~60 lines

The reference implementation's completion detection is famously the
hardest part of the SLG-WAM. Here it is a static method on a generic
primitive, and the reason is lineage, not luck: park-as-data removed stack
freezing before this design began; detach-k — since superseded by the
anonymous master — made fiber-completion mean
body-exhausted; the coat (EnclosingCall) made billing state-carried and
thread-agnostic; the Region fusion made "drained", "sleeper edges" and
"seal" first-class on one value; and the two-edge graph made the criterion
STATABLE — at which point Tier 2 is the singleton rule quantified over a
closure, with monotone counters standing in for a global lock. The same
theorem, the same billing discipline, one for-loop wider.

Every one of those steps came out of comprehension pressure — the
machinery was rebuilt until it could be explained, and the explainable
version is the one in which the next theorem was a restatement. That is
method.md's loop closing on itself: naming until the invariants are
speakable is what made the hard theorem cheap.
