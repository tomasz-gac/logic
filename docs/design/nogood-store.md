# The nogood store — "not this" over literals, with "either-or" as its sibling

**STATUS: STAGES 1–2 BUILT (August 2026, the `note-store` branch:
`Literal`, `Nogood`, `Verification`, `Nogoods`, seventeen receipts plus
the laws-kit union-meet claim). The names are RATIFIED IMPORTS from
CP-SAT (the human's call, August 2026): a nogood is Stallman &
Sussman's record — "NOT all these simultaneously" — generalized; a
literal follows lazy clause generation's extension to constraint
literals; `Verification` keeps its working name. `notin`, `exclude`,
`either` remain placeholders until their stages build. This doc
supersedes its own earlier kernel: the cargo-typed store (parameterized
by a `Semilattice & PartialOrder` cargo) was the wrong seat for the
algebra — the value-level pair is how STORES answer, behind their
custody walls, never the nogood store's interface. Companions:
`condition.md` (§8.3 the imposition spectrum, §§8.5–8.6 the features
that land here), `lattice-store.md` (the value-store family),
`docs/notes/row-set-basis-to-core.md` (exclusion over row sets — one
nogood stating the positive membership constraint; no negative sibling
store, the human's stipulation).**

---

## 1. The kernel, in plain words

A nogood is a held negation: **"NOT all of these simultaneously"** — Neq's
record shape exactly, with literals for pairs. The store's bag holds
nogoods conjunctively (the package's own ∧); a nogood holds its literals
as born — `¬(l₁ ∧ … ∧ lₙ)` read directly as "at least one must fail to
hold"; nothing ever converts between the two spellings.

Verification is one mechanism with no cargo cases: **impose the nogood's
literals on a scratch copy of the package and read the run** —

- an imposition **fails** → the forbidden conjunction is refuted → the
  nogood is subsumed fully by the state → discard;
- an imposition **changes nothing** → that literal already holds →
  crossed off, lawful by monotonicity (knowledge only grows, so an
  entailed literal stays entailed);
- an imposition brings **new knowledge** → still owed → the literal
  survives as its ORIGINAL self.

No survivors → every literal already holds → the forbidden conjunction
is entailed → the branch fails. One survivor IS the plain negative
constraint on that literal — same representation, watching it. The
scratch is disposable: only the verdict classification ever leaves it
(see §5 for why nothing else may).

The nogood store interprets nothing. It does not know what a domain is,
what a prefix is, or how any store represents anything — imposition
routes the chokepoint, each store interprets its own slice, and the
verdicts come back Revision-shaped. That is the custody line the earlier
kernel crossed, and Neq is the proof the mechanism suffices: its
`verificationStep` is this trial hand-inlined for content that touches
only the substitution factor — trial-unification-fails = refuted, empty
delta = entailed, delta = the survivors.

## 2. Literals — the statement vocabulary, closed

A literal's content is a constraint, stated as data the chokepoint
already understands:

    Literal.bind(lhs, rhs)          // a unification literal; Prefix-shaped
                                    // at imposition time — the unifier
                                    // stays the only Prefix mint
    Literal.state(actuals, maker)   // a constraint statement as a
                                    // call-value — (actuals, template):
                                    // arguments as terms plus the owning
                                    // store's item maker (custody)
    Literal.absorb(actuals, maker)  // a whole Absorbable factor as a
                                    // call-value — the factor row;
                                    // FiniteDomain.in is its first builder

The statement literal is the (actuals, template) shape natively: the
item is generated at construction, identity delegates to the GENERATED
item (lawful under the named-schema contract — the maker is excluded),
and a renaming regenerates the item at the renamed actuals — so
transcription never needs the item's structure, `Stored` never widens,
and the crossings stay generic (see docs/notes/transcription-generifies.md).
The maker reads its variables through the actuals it is handed, never
lexical capture; ground data may close over. The literal's actuals are
a DECLARED SURFACE — what the general watermark detector will check
maker bodies against when it lands.

Three constructors, closed. (The factor row was reserved for the
sealed-Condition negation in the gated tail and arrived early — the
negation of an FD box needs it.) A program is not a literal — **negation of literals can never become
negation of programs**, structurally rather than by a runtime pin. What
`exclude` negates is `¬(c₁ ∧ … ∧ cₙ)` over atomic constraint literals:
one clause, the SAT-shaped fragment, nothing more.

A statement literal **asserts residence after landing**:
`Package.withStored` silently no-ops on an unregistered store and the
drain examines the orphaned item without complaint — a dropped statement
would read "unchanged", the irrecoverable direction (§4). The
imposition composes activate with the check: the item's store class
must be present in the delivered package, or refuse naming it.

## 3. The trial — Neq's signatures, contract for contract

`Verification.verify` is `verifyAndSimplify`: none = some nogood is
violated, the branch fails; otherwise the kept list — survivors
simplified, satisfied nogoods absent. `Verification.trial` is
`unifyConstraints`: none = subsumed fully, discard; empty = violated;
otherwise the surviving literals, entailed ones crossed off. The store
slice maps `verify` onto `Revision` in one line: none → fail, kept →
updated.

Sequential imposition threads bindings across literals sharing
variables — the jointness of Neq's whole-record trial, for free: with
x already 2, imposing x = y binds y in the scratch, and the following
y = 2 reads entailed.

Simplification means DROPPING entailed literals, never rewriting one:
survivors keep their original content. Reading a literal's "image" back
out of the scratch's updated factors is a projection in disguise and a
custody violation — the updated factor is the store's WHOLE factor,
resident knowledge mixed in, and re-noting it makes a different
constraint. (Neq's record-shrinkage is cargo-internal: its trial emits
its own remainder in its own language; nothing is read out of anyone's
store.) A `Projectable`-based extraction at the nogood's `terms()` may
return later with a better view; it is deliberately not tier zero.

## 4. The direction analysis and the imposition law

The two misreadings are not symmetric, and the design is built around
which one is fatal:

- **False "unchanged"** — content that ADDED knowledge read as
  already-the-case — crosses off a live literal, and on the last literal
  VETOES A SATISFIABLE BRANCH. Failure is absorbing; no later evidence
  resurrects a killed branch. Lost answers, irrecoverable.
  Unchanged-claims need proof.
- **False "updated"** — entailed content read as still-owed — delays
  the veto. Delay is sound in this architecture: nogoods re-verify on
  every revise, labelling binds through revise so the ground floor
  decides by answer time, and a nogood still live at rendering hits the
  wall. Updated-claims may be sloppy.

The change classifier is plain structural equality (reference-first),
sound because **every piece of solver knowledge lives IN the package**
— substitutions, factors, parked suspensions, tables — so a genuine
addition necessarily perturbs the structure. It errs only toward
"changed": bookkeeping growth and representation drift delay, never
kill. The two known ways to fake "unchanged" are both guarded: the
unregistered-store drop (§2's residence assertion) and the swallowing
store (the law's third clause, below).

Exactness rests on **the imposition law**, per store, testable at the
laws tier (the logic laws kit):

1. **Idempotence**: `impose(c, impose(c, p))` structurally equals
   `impose(c, p)` — the double-run receipt; catches eager renormalizers
   and non-no-op re-posts. Verification always runs on a settled
   package — quiescent, hence normalized — so under this law an
   entailed imposition cannot drift: its store's normalize re-runs on
   an already-normal factor and is identity.
2. **The ground floor**: content whose terms are all bound is decidable
   by evaluation — a structural no-op when it holds, failure when it
   does not. Every store can do this, and the veto's completeness rests
   on it: relational content (a stated x+y=z entailed only by composing
   resident propagators — invisible to any store) is SOUND WITH DELAY,
   decided when its arguments ground through the revise path.
3. **No silent swallowing**: non-entailed content must change the
   package or fail. A swallowing store is idempotent trivially, so this
   clause is not derivable from the first.

Early entailment detection beyond the floor — leq against the resident
value, the lattice stores' one-comparison answer for unary content — is
per-store capability and PURE OPTIMIZATION: it buys earliness on the
delay side, never soundness. There is no nogood-compatibility flag; a
store that honors the law participates, and the law is checked at build
time, not statement time.

## 5. Scratch discipline

- **Settled base**: the caller may sit mid-drain (absorption revises
  through the same trigger that queued more items), so verification
  COMPLETES the pending items on the scratch base first
  (`Propagation.settled` — items are constraint work and belong to the
  verdict; runs are search and stay with the real drain). Settle,
  don't strip: evaluating against a package with its agenda torn off
  runs on partial knowledge. A settle failure means the branch is
  doomed on the same items deterministically — report `Revision.fail`
  now and spare the real drain the recomputation.
- **Honest completion**: an imposition runs under the Exhaustion claim
  — it can wake suspension bodies resident in the scratch, and bodies
  are arbitrary goals that may spawn. Completion of a bare fiber is not
  exhaustion.
- **Forks read conservatively on both counts**: more than one delivered
  world means something forked — the literal stays owed (never a false
  cross-off) and later literals verify against the unthreaded scratch,
  where missed jointness only ever keeps more.
- **Depth one**: a scratch run that wakes the nogood store's own revise
  does not open scratches inside scratches — nogoods examined inside a
  scratch answer conservatively ("still owed", unproven). Every failure
  a scratch can witness is exact (meets, propagators, real
  imposition); the cap only converts deep would-be cross-offs into
  keeps, the delay direction, corrected at real imposition. Recursion
  would not buy completeness anyway: mutual waking forces an
  in-progress cut whose cycle answer is the same conservative
  "possible", the cost per wake is exponential in depth on the
  chokepoint's hot path, and the question deep scratches answer already
  has a fair, billed, chaos-tested home — the search itself. Depth is a
  tier with a measurable trigger (branches routinely dying at labelling
  that a deeper check would have killed early), not a principle.
- **Nothing leaves a scratch but the classification.** No factor
  extraction, no projection, no write-back (§3's warning).

## 6. No polarity: the sibling and free composition

The store is negative-only — every nogood is `¬(conjunction)`, the
verdict reading fixed, no polarity field anywhere. The two products
that motivated a polarity flag separate cleanly:

- **"Not this"** is this store: `notin` the singleton case, `exclude`
  the forbidden combination. Irreducible — there is no literal
  constructor for a complement; negation exists only as the trial's
  reading.
- **"Either-or" is a SIBLING store** (its own stage), sharing the trial
  machinery with the straight verdict reading. A single-alternative
  positive nogood is just the package — the front door collapses it;
  positive nogoods earn residence only at width two or more.

Composition replaces polarity mixing: a nogood is a `Stored`, so a
nogood-statement is a literal, so an either-alternative can CONTAIN an
exclusion — `either([state(nogood ¬p)], [q])` is `¬p ∨ q`, an implication
between constraints, by plain nesting. Both stores stay pure; the
algebra comes from stacking them. The agreement move (what all
surviving alternatives agree on holds now — the GAC precedent) is the
sibling's feature and travels with it.

## 7. The crossings — standing rulings for the tabling stage

A nogood is a **projectionless conjunct**: a clause of live-name
literals — the CNF twin of `Condition`'s DNF of anonymized
conjuncts. Projection enters only when nogoods ride keys and answers,
and the rulings from the pass stand:

- **Compound at the crossings.** A nogood projects as ONE factor of its
  conjunct, never eagerly distributed: distribution splits the ⊕, never
  the wrapper — polarity never dissolves (a `Domain` factor cannot say
  "not"; a box's complement is not a box). Unit pieces of a De-Morgan
  split stay wrapped as unit nogoods.
- **Distribution un-builds the feature downstream**: delivery streams
  per conjunct and each conjunct restates as its own consumer branch —
  the forks `either` exists to avoid. An exclusion never explores under
  EITHER representation — negative boxes denote infinite regions, never
  label; their finite exit is parasitic on a positive generator (veto,
  not generation) — so distributing one buys only duplicate delivery
  under overlapping filters.
- **Split transcribes nogoods wrapped**, canonically renamed, never
  simplified into another store's representation; subsumption on
  excluded boxes is ANTITONE (excluding a bigger box denotes a smaller
  region); enforcement of a negative is SUBTRACTION against the
  resident positive domain, and an exclusion-only answer reads
  non-ground (`Reified.isGround` false), refusing the fold —
  consistent with the aggregation ruling with no new rules.

## 8. Products and the staged build

```java
Exclusion.notin(x, box)                     // ¬(x ∈ B): one literal
Exclusion.exclude(FiniteDomain.in(x, boxA), // ¬(x∈A ∧ y∈B): the
        FiniteDomain.in(y, boxB))           // forbidden combination
Exclusion.exclude(Literal.bind(x, lval(3))) // x ≠ 3: Neq's degenerate shape
```

1. **The verification core — BUILT**: `Literal`, `Nogood`,
   `Verification`, the residence guard, seven receipts including the
   jointness and orphaned-statement pins.
2. **The store slice — BUILT**: the `ConstraintStore` faces; `normalize`
   = `verify` wrapped into `Revision` (none → fail, kept-equal →
   unchanged, else → updated); the `stated` override (the default reads
   unchanged and would miss a born-violated nogood); revise wholesale,
   Neq's pattern; the reify wall comparing through the canonical
   renaming. The owed decisions resolved: the classifier stayed plain
   equality over the settled base (§5's first bullet — the
   drain-observation seam was not taken), and the laws-kit tests live
   with the laws kit.
3. **The front doors**: `notin`/`exclude` compiling to literals.
   (`FiniteDomain.in`, the FD factor builder, arrived with stage 2.)
4. **Nogoods ride tabling**: the `Projectable` face under §7's rulings.
5. **The sibling**: `either` on the shared trial, straight reading,
   the agreement move, labelling leftovers as a `Conde` of impositions.
6. **Gated tail**: the ¬ operator (§8.5 — a sealed conjunct's region is
   factors plus bindings, literal-shaped already: the fragment extends
   by sealing, never by admitting programs); clause learning (§8.6 —
   learned nogoods are nogoods); the Neq re-seat, last, as hygiene — its
   verification is already this design's trial, so the re-seat is
   representation alignment only.

## 9. Risks, named

The wholesale re-verification cost on every revise (watched-literals
bookkeeping is the known mitigation — SAT's own scheme, arriving under
its own name — an optimization with Neq as the behavioral baseline); the classifier's reliance on the imposition law
(build-time tests, not runtime checks — a store violating the law
fails the laws tier, not the user's solve); scratch cost per
`Residues`-shaped content (one drain per check, priced against the
suite); and the standing doctrine — every stage prices against the
suite before it merges.
