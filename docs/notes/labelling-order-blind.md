# FD labelling is order-blind; first-fail plus computing propagators is the fix

- **status**: argued (Aug 2026, the human's observation after the
  scheduling benchmark — "watched-literal-like, but within the FD
  store"); nothing built, one audit owed before design closes.
- **evidence held**: code — `EnforceConstraintsFD.enforceConstraints`
  labels `getDomains().keySet()` in REGISTRATION ORDER, no heuristic;
  per variable a Conde over its domain. The mitigating grace is real:
  the domain is read from the CURRENT state at that variable's turn,
  so narrowing from already-labelled variables is respected. The
  blindness is purely the order — a determined variable enumerated
  before its determiners spawns its full domain in branches that the
  propagator then prunes backwards (the human's x+y=z: N³-flavored
  instead of ground-two-compute-one).
- **imports**: ⋯import first-fail / smallest-domain-first dynamic
  variable ordering (Haralick & Elliott 1980, the standard CSP
  heuristic) — receipt owed when built.
- **obligations**: (1) the AUDIT, first and cheap: per FD propagator
  DIRECTION, which of three levels does it implement — INFERS from
  domains (dom(z) ∩= dom(x) ⊕ dom(y), narrowing from the other two
  DOMAINS with nothing ground), COMPUTES on ground (z from ground
  x, y only), or merely CHECKS ground tuples? The design closes only
  after this is known. (2) tier 1: replace the fixed keySet iteration
  with a dynamic smallest-current-domain choice per step — no
  semantics, no custody, no trial, FD-internal. (3) tier 2, per the
  audit: lift every direction to the INFERENCE level — domain-from-
  domains is the strong form and strictly subsumes compute-on-ground
  (singleton inputs infer singleton outputs), and it fires DURING
  search, not just at labelling — narrowing z the moment x and y
  narrow, feeding first-fail better ordering signal and the
  comparison fast path more decided alternatives. (4) the pin: the
  scheduling benchmark both lanes (first-fail is enforce-side,
  lane-agnostic — the job-shop's every `end` is `start + 1`,
  functionally determined, and registration order may be enumerating
  them).
- **links**: disjunction-store-pays-in-products.md (the benchmark
  this would move on both poles), couplings-are-syntactic.md (the
  sibling ceiling — this note is about labelling order, that one
  about order comparison), constraint-kernel.md (FD's toolkit owns
  all of this — no seam outside the store is touched).

## The claim

Labelling enumerates variables whose values are FUNCTIONS of
already-labelled ones, because the iteration order is registration
order. The human's trick — ground the two smallest domains, compute
the third — decomposes into two classic pieces, and the first
subsumes most of the second:

1. **First-fail**: choose the variable with the smallest LIVE domain,
   re-chosen dynamically each step. This subsumes ground-two-compute-
   one wherever propagators compute: once x and y ground, a computing
   addo narrows z to a singleton → smallest domain → chosen next →
   one branch. Watched-literals' cousin: don't enumerate what is
   about to be derived.
2. **Inferring propagators**: the residual, in its strong form (the
   human's addition): each direction of a functional triple narrows
   its variable from the other two's DOMAINS — dom(z) ∩= dom(x) ⊕
   dom(y), dom(x) ∩= dom(z) ⊖ dom(y) — with nothing ground. This
   strictly subsumes compute-on-ground (singletons in, singleton
   out) and acts DURING search: every narrowing anywhere in the
   triple propagates to the other two immediately, which is what
   makes first-fail's ordering signal honest (a determined variable
   is visibly small the moment its determiners shrink, not only
   after they ground) and what feeds the disjunct fast path more
   refuted alternatives earlier. A check-only direction leaves the
   domain wide and the determination invisible to everything
   downstream.

Cheapest kill: the audit finds every propagator direction already at
the INFERENCE level AND a first-fail prototype fails to move the
benchmark pins — then the order was not the bottleneck and this note
closes as refuted by measurement.
