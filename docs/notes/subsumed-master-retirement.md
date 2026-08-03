# A master whose entry becomes subsumed is doing redundant work — retire it

- **status**: hunch
- **evidence held**: none (derivation sketch only)
- **imports**: subsumptive reuse, subset property, master-from-key, replay = rename ∘ absorb, strict ascent
- **obligations**: (1) redirect soundness mid-production — a narrow reader switching to the wide entry needs replay-from-zero through its filter, which redelivers; safe under idempotent ⊕, WRONG under counting (double-count) — the safe subset may be retire-only-before-first-answer; (2) measure the waste on real programs before building anything; (3) completion detection — the narrow entry's sleeper edges must survive the redirect or the ledger loses dependency information.
- **links**: would graduate into tabled-constraints.md §5.4

When a narrow entry (say `p(X,5)` under `X ∈ {1..3}`) has a live master and a
wider entry for the same relation later appears, the narrow master's remaining
exploration is redundant: every answer it can still derive is also derivable by
the wide master (subset property), and every reader it serves could be served
wide-to-narrow through the consumption filter. Today both masters run to
completion — sound, but duplicated exploration over the narrower region.

The idea: on detecting a live subsumer, retire the narrow master and redirect
its readers to the wide entry. What this does NOT buy: the narrow answers were
never "unused" (the entry's own readers consume them) — the waste is duplicate
derivation work, not wasted answers. Cheapest kill: obligation (1)'s
non-idempotent wall — if redirect-mid-flight can't be made delivery-exact, the
idea shrinks to retiring masters that haven't produced yet, and the measured
waste (2) may not justify even that.
