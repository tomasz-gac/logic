# The note store — "not this" over postings, with "either-or" as its sibling

**STATUS: DESIGN, KERNEL REDESIGNED; THE VERIFICATION CORE IS BUILT
(August 2026, the `note-store` branch: `Posting`, `Note`,
`Verification`, six receipts). Every name — `Posting`, `Note`,
`Verification`, `notin`, `exclude`, `either` — is a PLACEHOLDER for the
human's naming call, which gates everything past the core. This doc
supersedes its own earlier kernel: the cargo-typed store
(`NoteStore<V extends Semilattice & PartialOrder>`) was the wrong seat
for the algebra — the value-level pair is how STORES answer, behind
their custody walls, never the note store's interface. Companions:
`condition.md` (§8.3 the imposition spectrum, §§8.5–8.6 the features
that land here), `lattice-store.md` (the value-store family).**

---

## 1. The kernel, in plain words

A note is a held negation: **"NOT all of these simultaneously"** — Neq's
record shape exactly, with postings for pairs. The store's bag holds
notes conjunctively (the package's own ∧); a note holds its postings as
the escape list it is born as — `¬(p₁ ∧ … ∧ pₙ)` read directly as "at
least one must fail to hold"; nothing ever converts between the two
spellings.

Verification is one mechanism with no cargo cases: **impose the note's
postings on a scratch copy of the package and read the run** —

- an imposition **fails** → the forbidden conjunction is refuted → the
  note is subsumed fully by the state → discard;
- an imposition **changes nothing** → that posting already holds →
  crossed off, lawful by monotonicity (knowledge only grows, so an
  entailed posting stays entailed);
- an imposition brings **new knowledge** → still owed → the posting
  survives as its ORIGINAL self.

No survivors → every posting already holds → the forbidden conjunction
is entailed → the branch fails. One survivor IS the plain negative
constraint on that posting — same representation, watching it. The
scratch is disposable: only the verdict classification ever leaves it
(see §5 for why nothing else may).

The note store interprets nothing. It does not know what a domain is,
what a prefix is, or how any store represents anything — imposition
routes the chokepoint, each store interprets its own slice, and the
verdicts come back Revision-shaped. That is the custody line the earlier
kernel crossed, and Neq is the proof the mechanism suffices: its
`verificationStep` is this trial hand-inlined for content that touches
only the substitution factor — trial-unification-fails = refuted, empty
delta = entailed, delta = the survivors.

## 2. Postings — the statement vocabulary, closed

An escape's content is a constraint, stated as data the chokepoint
already understands:

    Posting.bind(lhs, rhs)          // a unification literal; Prefix-shaped
                                    // at imposition time — the unifier
                                    // stays the only Prefix mint
    Posting.state(actuals, maker)   // a constraint statement as a
                                    // call-value — (actuals, template):
                                    // arguments as terms plus the owning
                                    // store's item maker (custody)

The statement posting is the (actuals, template) shape natively: the
item is generated at construction, identity delegates to the GENERATED
item (lawful under the named-schema contract — the maker is excluded),
and a renaming regenerates the item at the renamed actuals — so
transcription never needs the item's structure, `Stored` never widens,
and the crossings stay generic (see docs/notes/transcription-generifies.md).
The maker reads its variables through the actuals it is handed, never
lexical capture; ground data may close over. The posting's actuals are
a DECLARED SURFACE — what the general watermark detector will check
maker bodies against when it lands.

Two constructors, closed; a reserved third row (a whole `Absorbable`
factor) waits for the sealed-Condition negation in the gated tail. A
program is not a posting — **negation of postings can never become
negation of programs**, structurally rather than by a runtime pin. What
`exclude` negates is `¬(c₁ ∧ … ∧ cₙ)` over atomic constraint postings:
one clause, the SAT-shaped fragment, nothing more.

A statement posting **asserts residence after landing**:
`Package.withStored` silently no-ops on an unregistered store and the
drain examines the orphaned item without complaint — a dropped statement
would read "unchanged", the irrecoverable direction (§4). The
imposition composes activate with the check: the item's store class
must be present in the delivered package, or refuse naming it.

## 3. The trial — Neq's signatures, contract for contract

`Verification.verify` is `verifyAndSimplify`: none = some note is
violated, the branch fails; otherwise the kept list — survivors
simplified, satisfied notes absent. `Verification.trial` is
`unifyConstraints`: none = subsumed fully, discard; empty = violated;
otherwise the surviving postings, entailed ones crossed off. The store
slice maps `verify` onto `Revision` in one line: none → fail, kept →
updated.

Sequential imposition threads bindings across postings sharing
variables — the jointness of Neq's whole-record trial, for free: with
x already 2, imposing x = y binds y in the scratch, and the following
y = 2 reads entailed.

Simplification means DROPPING entailed postings, never rewriting one:
survivors keep their original content. Reading a posting's "image" back
out of the scratch's updated factors is a projection in disguise and a
custody violation — the updated factor is the store's WHOLE factor,
resident knowledge mixed in, and re-noting it makes a different
constraint. (Neq's record-shrinkage is cargo-internal: its trial emits
its own remainder in its own language; nothing is read out of anyone's
store.) A `Projectable`-based extraction at the note's `terms()` may
return later with a better view; it is deliberately not tier zero.

## 4. The direction analysis and the imposition law

The two misreadings are not symmetric, and the design is built around
which one is fatal:

- **False "unchanged"** — content that ADDED knowledge read as
  already-the-case — crosses off a real escape, and on the last escape
  VETOES A SATISFIABLE BRANCH. Failure is absorbing; no later evidence
  resurrects a killed branch. Lost answers, irrecoverable.
  Unchanged-claims need proof.
- **False "updated"** — entailed content read as still-owed — delays
  the veto. Delay is sound in this architecture: notes re-verify on
  every revise, labelling binds through revise so the ground floor
  decides by answer time, and a note still live at rendering hits the
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
   and non-no-op re-posts. Verification always runs on a post-drain
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
delay side, never soundness. There is no note-compatibility flag; a
store that honors the law participates, and the law is checked at build
time, not statement time.

## 5. Scratch discipline

- **Honest completion**: an imposition runs under the Exhaustion claim
  — it can wake suspension bodies resident in the scratch, and bodies
  are arbitrary goals that may spawn. Completion of a bare fiber is not
  exhaustion.
- **Forks read conservatively on both counts**: more than one delivered
  world means something forked — the posting stays owed (never a false
  cross-off) and later postings verify against the unthreaded scratch,
  where missed jointness only ever keeps more.
- **Depth one**: a scratch run that wakes the note store's own revise
  does not open scratches inside scratches — notes examined inside a
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

The store is negative-only — every note is `¬(conjunction)`, the
verdict reading fixed, no polarity field anywhere. The two products
that motivated a polarity flag separate cleanly:

- **"Not this"** is this store: `notin` the singleton case, `exclude`
  the forbidden combination. Irreducible — there is no posting
  constructor for a complement; negation exists only as the trial's
  reading.
- **"Either-or" is a SIBLING store** (its own stage), sharing the trial
  machinery with the straight verdict reading. A single-alternative
  positive note is just the package — the front door collapses it;
  positive notes earn residence only at width two or more.

Composition replaces polarity mixing: a note is a `Stored`, so a
note-statement is a posting, so an either-alternative can CONTAIN an
exclusion — `either([state(note ¬p)], [q])` is `¬p ∨ q`, an implication
between constraints, by plain nesting. Both stores stay pure; the
algebra comes from stacking them. The agreement move (what all
surviving alternatives agree on holds now — the GAC precedent) is the
sibling's feature and travels with it.

## 7. The crossings — standing rulings for the tabling stage

A note is a **projectionless conjunct**: a clause whose literals are
live-name postings — the CNF twin of `Condition`'s DNF of anonymized
conjuncts. Projection enters only when notes ride keys and answers,
and the rulings from the pass stand:

- **Compound at the crossings.** A note projects as ONE factor of its
  conjunct, never eagerly distributed: distribution splits the ⊕, never
  the wrapper — polarity never dissolves (a `Domain` factor cannot say
  "not"; a box's complement is not a box). Unit pieces of a De-Morgan
  split stay wrapped as unit notes.
- **Distribution un-builds the feature downstream**: delivery streams
  per conjunct and each conjunct restates as its own consumer branch —
  the forks `either` exists to avoid. An exclusion never explores under
  EITHER representation — negative boxes denote infinite regions, never
  label; their finite exit is parasitic on a positive generator (veto,
  not generation) — so distributing one buys only duplicate delivery
  under overlapping filters.
- **Split transcribes notes wrapped**, canonically renamed, never
  simplified into another store's representation; subsumption on
  excluded boxes is ANTITONE (excluding a bigger box denotes a smaller
  region); enforcement of a negative is SUBTRACTION against the
  resident positive domain, and an exclusion-only answer reads
  non-ground (`Reified.isGround` false), refusing the fold —
  consistent with the aggregation ruling with no new rules.

## 8. Products and the staged build

```java
Exclusion.notin(x, box)                     // ¬(x ∈ B): one posting
Exclusion.exclude(FiniteDomain.in(x, boxA), // ¬(x∈A ∧ y∈B): the
        FiniteDomain.in(y, boxB))           // forbidden combination
Exclusion.exclude(Posting.bind(x, lval(3))) // x ≠ 3: Neq's degenerate shape
```

1. **The verification core — BUILT**: `Posting`, `Note`,
   `Verification`, the residence guard, six receipts including the
   jointness and orphaned-statement pins.
2. **The store slice**: the `ConstraintStore` faces; `normalize` =
   `verify` wrapped into `Revision` (none → fail, kept → updated);
   revise wholesale, Neq's pattern. Decisions owed here: the classifier
   refinement (component-wise reference comparison, or the
   drain-observation seam — the latter touches Propagation, the
   human's call) and the laws-kit tests landing per store.
3. **The front doors**: `notin`/`exclude` compiling to postings; the
   FD statement builder (`FiniteDomain.in` returning the item as data).
4. **Notes ride tabling**: the `Projectable` face under §7's rulings.
5. **The sibling**: `either` on the shared trial, straight reading,
   the agreement move, labelling leftovers as a `Conde` of impositions.
6. **Gated tail**: the ¬ operator (§8.5 — a sealed conjunct's region is
   factors plus bindings, posting-shaped already: the fragment extends
   by sealing, never by admitting programs); clause learning (§8.6 —
   learned nogoods are notes); the Neq re-seat, last, as hygiene — its
   verification is already this design's trial, so the re-seat is
   representation alignment only.

## 9. Risks, named

The wholesale re-verification cost on every revise (watched-escapes
bookkeeping is the known mitigation, an optimization with Neq as the
behavioral baseline); the classifier's reliance on the imposition law
(build-time tests, not runtime checks — a store violating the law
fails the laws tier, not the user's solve); scratch cost per
`Residues`-shaped content (one drain per check, priced against the
suite); and the standing doctrine — every stage prices against the
suite before it merges.
