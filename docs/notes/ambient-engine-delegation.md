# The ambient engine: closed — the fence beat the unification

- **status**: CLOSED (Aug 2026). Killed by the human's channel-ownership
  alternative (engine-owned-channels.md): the corruption class behind
  the grounding saga was two engines SHARING state, and fencing the
  sharing (channels stamped by their driving engine, loud refusal on a
  second) achieves the safety goal without unifying the engines. The
  ambient plan's price — re-entrant drive surgery per scheduler,
  busy-frame exclusion, a depth budget, a self-demand detector — bought
  one capability beyond safety: a demand that steps foreign frames to
  produce what it awaits. That capability had no customer; the fence
  makes it impossible instead of supported, which is the honest form
  of "no customer".
- **evidence held**: the analysis below, kept because it transfers.
  The layering result (Cont owns COUNT — k is a heap lambda, applied
  once per clause; the engine owns ORDER — parks are engine-visible
  citizens because wake order is data-chosen and a stack resumes
  LIFO-only) and the compounding law (a demand's caller keeps its rest
  on the JVM stack, so recursion THROUGH a nested drive nests one loop
  per outstanding demand — the human's fib counterexample) both
  survive the closure and govern the successor: they are why the
  nested engine is a boundary door and why the pure population
  de-fiberizes rather than demands.
- **links**: engine-owned-channels.md (the successor and the ruled
  plan), eager-flatmap-contract.md (the three-door contract; the
  successor kills two doors by deletion),
  shelved/virtual-threads-engine.md (one stack PER suspension — the
  other resolution of the same one-shot limit).

## The kept counterexample (fib; scopes any nested-drive door)

    int fib(int n) {
        if (n == 1 || n == 2) { return 1; }
        else { return offer(() -> fib(n - 1)) + offer(() - > fib(n - 2)); }
    }

A nested drive cannot return until the demanded closure completes, and
that closure demands again BEFORE completing — nested-incomplete all
the way down: n closure frames each holding a live rest, interleaved
with n drive-loop layers. Leaf-ness, not sequentiality and not
parking, is the discriminator: a demand is flat only if the demanded
work issues no demands of its own from the JVM stack, or keeps its
recursion in fiber structure behind one rim. There is no graceful
degradation past a depth limit — the caller's rest is a stack frame
and cannot be spilled to the heap (the one-shot limit).
