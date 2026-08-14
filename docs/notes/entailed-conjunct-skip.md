# An entailed conjunct is skippable at plan time

- **status**: hunch, recorded on the human's instruction (Aug 14) —
  deliberately NOT built; the optimizer stays untouched.
- **evidence held**: derivation only. The trial decides three ways, and
  the optimizer currently consumes one and a half of them: doomed
  (refuted-if-Done) prices 0 and kills the segment; everything else
  prices 1. The third verdict is unexploited: a conjunct ENTAILED at
  plan time must be priced 1 (it succeeds exactly once — pricing alone
  cannot express "free"), but the planner could DROP it from the
  segment entirely — its imposition is a no-op, and entailment is
  monotone-permanent, so entailed-at-plan-time stays entailed at every
  later state. Sound unconditionally, same license as the disjunction
  store's discharge.
- **imports**: none new; the trial's verdicts, the pricing seat.
- **obligations** (if ever built): (1) `Trial.entailed(posting, p)` —
  isDone && isEntailed, the doom oracle's dual, same guard; (2) the
  skip lives where `OrderingOptimizer.price` consults
  `Bounded.answers(bound)` — one more verdict at the same seat,
  dropping the conjunct from the rebuilt segment; (3) a receipt
  mirroring deadPosts: an entailed post vanishes from the plan and the
  step count shows it. Cheapest kill: the half-blind pass — the
  optimizer prices against layer-boundary state, so mid-layer
  entailments are invisible and the win may be too rare to price.
- **links**: the trial's three-way (constraints/Trial), nogood-store §4
  (the doomed seam this extends), #116 (entailed-exact is the licensing
  law), weighted-disjunction-fold.md (the same verdict driving the
  summand — entailment as "contributes nothing new" in two seats).

One sentence: the doom check reads the trial's refuted verdict at the
pricing seat; this reads the entailed verdict at the same seat, and the
payoff is a dropped conjunct instead of a zeroed segment. TRIGGER: a
profile showing entailed postings actually occurring in real plans (the
half-blind pass makes this an open question), or the laws kit (#116)
landing entailed-exact — at which point the skip is a one-afternoon cut
with its license already proven.
