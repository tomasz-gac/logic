# A direct-style engine on Java 21 virtual threads — design sketch

**Status:** design sketch for a **separate, experimental Java 21 module** (`logic-vt`), NOT a
change to the current engine. The current engine is Java 8, trampolined, and works; this is an
*alternative* execution model, not a refactor of it. It is a research bet with one real trap
(§4). Do not touch the existing engine to pursue this — prototype it beside it.

---

## 1. The idea

The current engine is CPS + a trampolined free structure: `Goal = Package -> Cont<Package,
Nothing>`, driven by schedulers over `FiberStep`. The trampoline exists to get **stack safety**
(deep recursion without `StackOverflowError`) and **suspension** (parking) without threads.

Java 21 virtual threads give both of those *natively*: their stacks are growable/heap-allocated
(deep recursion is fine) and blocking a virtual thread is cheap (suspension is free). So you can
run the search in **direct recursive style** — a goal is an ordinary method that calls its
subgoals as ordinary calls — with **one logical branch per virtual thread**.

The payoff is that direct recursion means **the JVM call stack is the logical stack again**:
break inside `append` in the IDE and you see `append → append → append`, with locals and
step-in/step-out working natively. That is the clean answer to "debug a logic program with a
real debugger," which the trampolined engine can never give (its stack is interpreter plumbing).

---

## 2. What it buys

1. **Native JVM debugging** — real stacks per branch; IDE breakpoints/stepping just work.
2. **Delete the trampoline** — no `Done`/`Deferred`/`FlatMap`/`Forked` free structure; stack
   safety comes from the VT, so the core shrinks a lot.
3. **Simpler tabling** — a consumer waiting on a producer's answers is a blocking `queue.take()`
   on its VT, replacing the "parked continuations as data" machinery.
4. **Cheap real parallelism** — thousands of branches as VTs over work-stealing carriers.
5. **Natural cut / `once` / `ifte`** — with `StructuredTaskScope`, a disjunction is a scope and
   **cut = cancel the scope** (kill the sibling branches' threads). This is the cleanest known
   way to get a real cut, and it's why this pairs with the cut backlog item.

---

## 3. Execution model (how it actually works)

Direct style with blocking channels; the immutable `Package` is what makes it clean.

- A goal, given a `Package`, **produces** solutions by pushing packages onto an output
  channel (a bounded blocking queue). Success = push the current package; failure = push
  nothing.
- **Conjunction** `a ∧ b`: for each package `a` pushes, run `b` on it and forward `b`'s
  outputs. (bind / flatMap over the channel.)
- **Disjunction** `a ∨ b`: run `a` and `b` **each on its own virtual thread**, and **merge**
  their output channels into the caller's. This is the only place fairness is decided (§4).
- **Recursion within a branch is ordinary method calls** — that is what puts real frames on
  the VT's stack and makes it debuggable. A deep `append` recurses on the VT's growable stack;
  no trampoline needed.
- **Backtracking is free** because `Package` is immutable: each branch carries its own package,
  so "try another branch" is just exploring another package — there is no trail to undo. (Keep
  the persistent `Package`; do NOT introduce mutable state to "optimise" — it would destroy
  this property and the parallelism.)

Mental model: it's the classic "logic streams," but the immature-stream *thunks* are replaced
by *blocked virtual threads*, and demand (pulling from the output channel) drives the search.

---

## 4. The trap: fairness = completeness (READ THIS)

miniKanren's **completeness** comes from **fair interleaving of disjuncts** — a productive
infinite branch must not starve the others. In the current engine the schedulers guarantee this
(`BreadthFirstScheduler` promotes long-running buckets, etc.). In the VT model, **fairness moves
entirely into the disjunction merge in §3**:

- naive merge (drain one branch, then the next) is depth-first and **incomplete** — an infinite
  left branch hangs forever and the right branch's solutions are never seen;
- a **fair, bounded merge** (round-robin across branch channels, with backpressure so a fast
  producer blocks) preserves completeness.

So you are **re-implementing the fairness the current schedulers already provide**, and getting
it wrong means the engine silently returns fewer answers (or hangs) on exactly the recursive
programs logic programming exists for. This is the single hardest and most important part, and
it must be validated against the completeness tests (§7) before anything else is trusted.

Secondary costs:
- **Determinism goes.** The current schedulers can be deterministic/reproducible; VT scheduling
  is not. Debugging a non-deterministic search is its own pain, and test assertions on solution
  *order* will not hold.
- **Java 21-only ⇒ a second engine.** Everything downstream (pldb, cuisine, and the Java-8
  `logic`) stays on the trampolined engine. `logic-vt` is a parallel module, not a drop-in — two
  cores to maintain, or an eventual migration. Do not delete the Java-8 engine for this.

---

## 5. How the existing capabilities map

- **Debugging** — native IDE stepping (the whole point). Note: the box-model **tracer already
  handles read-only debugging well on Java 8**, so this engine's debugging win is specifically
  *native step-in/step-out*, a want, not a need. Weigh it accordingly.
- **Tabling** — blocking producer/consumer over channels; the tabled call's answers are a
  channel a consumer `take()`s. Simpler than parked continuations, but the fixpoint/left-
  recursion termination still has to be right (a consumer must not deadlock waiting on a
  producer that is waiting on it — the same master/slave subtlety the current tabling solved,
  now expressed with threads).
- **Cut** — `StructuredTaskScope`; cut cancels the scope. If you stay on Java 8 instead, cut is
  separate, harder work in the CPS engine.
- **Constraints / semirings** — orthogonal; they live in the `Package` and compose the same way.

---

## 6. Recommendation

Treat this as a **prototype-first research bet in a separate `logic-vt` module**, never as edits
to the working engine:
1. Build a **minimal** direct-style solver on VTs: `unify`, `∧`, `∨`, `fresh`, `defer`, and a
   fair bounded-channel merge. Nothing else.
2. **Validate completeness FIRST** (§7) — this is the go/no-go gate. If fair merge is hard to get
   right, stop; the rest is worthless without it.
3. Only then add tabling and a structured-concurrency cut, and confirm a native IDE stack shows
   `append → append`.
4. Decide, with data, whether native debugging + simpler tabling + natural cut justify a
   permanent second engine. If not, keep the Java-8 engine + the tracer + a CPS cut.

Do NOT start by porting the current engine. Do NOT let this touch Java-8 `logic`.

---

## 7. Acceptance / prototype gates

- **Completeness:** a goal with an infinite productive disjunct on one side and finite solutions
  on the other still yields the finite solutions (fair merge works). This is the gate.
- **Left-recursion under tabling terminates** (the same programs the current tabling handles).
- **Stack safety:** a million-deep recursive relation runs without `StackOverflowError` and with
  no trampoline in the code.
- **Native debugging:** an IDE breakpoint inside a recursive relation shows the real recursive
  call stack, not interpreter frames.
- **No mutable `Package`:** backtracking works purely by exploring alternative packages.

---

## 8. Non-goals

- Replacing or editing the Java-8 trampolined engine. This is additive and experimental.
- Shipping it to downstream consumers (they are Java 8).
- Doing it at all unless the completeness gate (§7) passes cleanly — an incomplete search engine
  is worse than none.
