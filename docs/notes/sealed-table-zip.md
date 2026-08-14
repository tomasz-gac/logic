# A sealed table consumes as ONE disjunct — replay on the imposition spectrum

- **status**: argued — the human's design (August 2026), no code
- **evidence held**: derivation; the pieces rest on shipped machinery
  (Closed mode already waits for the seal; answers already cross as
  deltas through the posting rows' own rename; the trial's straight
  verdict reading is the sibling's, per nogood-store §6)
- **imports**: none new; "zip" is the human's conversational name for
  folding a sealed answer set into one Disjunct — glossary
  entry owed if it sticks
- **obligations**: (1) the disjunction store's design pass must cover the
  zip and the mode carve, not just the user door — PARTLY MET (Aug 14):
  DisjunctionConstraints shipped and the zip's construction is now
  fully derivable from shipped pieces, see the mechanical spec below;
  (2) MET (Aug 2026, disjunctive-store branch): the Projectable face
  shipped with three tabling receipts — disjuncts ride answers, decide
  at the consumer, enumerate at the caller's ground floor, and key the
  call; the zip has no remaining prerequisite besides deciding to
  build it; (3) the
  measurement, trigger-gated (the human's framing, August 2026): only
  if we ever decide to drop streaming — race drip-per-answer replay
  against wait-for-seal + one-record emit on real workloads; no
  scheduling and no promised speedup before (2) lands; (4) when built,
  the flattened clause owes CROSS-ANSWER absorption: each answer's
  Condition is an antichain internally, but flattening across answers
  can admit dominated alternatives (answer A's region covered by
  B's) — harmless (conde replay has the same redundancy; discharge
  catches some) but it is orConjunct's job done late on the store
  side, and the first concrete payer for the deferred
  alternative-subsumption dual
- **links**: condition.md §8.3 (the imposition spectrum this extends),
  nogood-store.md §6 (the disjunction sibling), finite-goal-tier.md (the
  tree this materializes), negation-over-finite-goals.md (the De
  Morgan dual), star-tabling.md (wait-mode — the natural zip site),
  tabled-constraints.md (replay as it stands)

## The claim

Fork ⟷ resident data was the imposition spectrum for CONSTRAINTS
(condition.md §8.3); table replay sits on the same dial. Today a
consumer of a sealed table forks per answer — the answer set's ∨
spent as search branching. With the disjunction store (nogoods' sibling:
a resident alternative of postings), a sealed table folds into ONE
disjunct — any(all(answer₁'s literals), all(answer₂'s literals), …),
each alternative the answer's delta read as literals, the SAME
transcription negation uses — and consumption stays in one branch.
Unit propagation crosses off alternatives as they refute; the last
survivor imposes; and the agreement move (what ALL surviving
alternatives agree on holds now) gets its first real consumer: the
hull of the remaining answers propagates before any commitment —
GAC's trick generalized to whole answer deltas.

The dual shapes, side by side: positive consumption = one
disjunct (∨ as data, zero forks until labelling); negative
consumption = n nogoods (∧ of ¬, zero forks ever). Both single-branch;
the table's disjunction pays as propagation instead of search.

The zip is also the finite tier made concrete: an any-of-alls tree
over posting literals is a FiniteGoal VALUE, so "tabling is
Goal → FiniteGoal" gains a carrier — the sealed table's zip IS the
function's output as data, and `not` is De Morgan on the tree,
closed inside the fragment.

## The seal asymmetry

The negative side streams; the positive zip cannot. A conjunction of
nogoods strengthens answer-by-answer — ¬Aᵢ is sound the moment answer
i arrives, and the seal gates only `not(g)`'s COMPLETION. A
disjunction that grows WEAKENS — anti-monotone, illegal — so the zip
is lawful only over a sealed table. This lands exactly on the shipped
mode split: Closed-mode consumers (already seal-waiting) can zip;
Streaming-mode consumers keep the fork replay.

## The trade, and who decides

A disjunct binds nothing until propagation prunes it to one
alternative or labelling expands it. Generate-and-test wins (the test
prunes alternatives before anything forks); generation-driven programs
starve until labelling. Fork-vs-zip is therefore the OPTIMIZER's
imposition-spectrum choice — licensed by confluence, guarded by
zombie labelling, §8.3's own rulings — with the mode carve as the
natural first cut (Closed zips, Streaming forks).

## The streaming question (the human's, August 2026)

The zip reopens a settled assumption: streaming-early tabling is no
longer a clear win in either direction. Depending on the disjunctive
store's speed, waiting for the seal and emitting one record could
beat dripping answers as they arise — but the comparison is only
honest once obligation (2) holds, and it stays unscheduled until a
real decision to drop streaming puts it on the table.

## The mechanical spec (Aug 14 — every piece shipped)

The nested disjunction — over answers, then over each answer's
Condition regions — collapses into ONE flat clause by ∨-associativity,
and Disjunct.of's flattening already performs the collapse:

    any( answers.flatMap(answer ->
        answer.condition.regions.map(region ->
            Posting.all( answer's binding delta as resolutions
                       ++ region.factors.map(Posting::absorb) ))) )

all through the caller-namespace renaming first — replay is rename ∘
absorb, per answer-region instead of per fork. Consuming a CONDITIONAL
answer is automatically correct: choosing an alternative imposes the
delta AND the region it was proven under. Nothing converts
representation at any level: outer ∨ → clause membership, inner ∧ →
Posting.all, factors → absorb, bindings → resolutions.
