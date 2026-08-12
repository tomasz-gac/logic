# Postings are the store language — every reader consumes the language, not the store

- **status**: argued — the human's unification (August 2026), no code;
  the display corner is ruled (toString delegation, ¬ as the sign —
  the Neq-kill design) and the transcription corner is built
- **evidence held**: derivation over shipped machinery — Posting.rename
  and Transcribable (the engine-reader corner, built); Resolution
  crossing as binds (the floor already speaks the language); the
  named-schema contract + rebuildable propagators #65 (the registry
  mechanism, built for replay); Residues.about/all (the split this
  ladder rides, adopted)
- **imports**: none new; "restate" extends the adopted term (answers as
  self-restating factors) from answers to the store face
- **obligations**: (1) `restate` and its round-trip law —
  absorb(restate(F)) ≡ F modulo normalize — into the laws kit (#116)
  beside the imposition law; (2) the FactSource probe: express a posted
  lookup's constraints as postings and compile the pushable half to a
  WHERE clause — the N+M receipt; (3) the marshal round-trip law
  (unmarshal ∘ marshal = identity on the posting) with the payload slot
  and registry-name stability answered; (4) the depth-of-projection
  policy on `about` (below) — decide per caller, don't inherit;
  (5) rulings owed: restate's home (Projectable vs its own capability),
  the payload slot's acceptance, depth as parameter vs fixed
- **links**: nogood-store.md §2 (the vocabulary), constraint-kernel.md
  §2 (the doors that construct it), transcription-generifies.md (the
  claim this materializes), condition.md §8.1 (Residues, about/all),
  domain-layer.md (FactSource, pin stamps), negation-over-finite-goals.md
  (the ∃-elimination lesson depth option (c) reuses), sealed-table-zip.md
  (compression residences — this note is the expression side)

## The claim

The engine has one recurring boundary problem wearing four coats:
compressed knowledge leaving a live Package must be re-expressed for
its READER. The unification (the human's, August 2026): stores speak
Posting — the chokepoint's closed statement vocabulary — and every
reader consumes the language, never the store:

| reader | operation | consumes | status |
|---|---|---|---|
| engine, another lineage | tabling crossings | renamed postings | built |
| human | solve boundary | ground values, else `¬(...)` strings | ruled |
| external query engine | FactSource pushdown | compiled predicates | unbuilt |
| engine, across time | persistence (#75) | marshalled postings | unbuilt |

Tabling and solve produce the SAME object — answer sets as Package
images per call pattern — and differ only in boundary policy per
reader: tabling transcribes (stays compressed), solve exits (expands
by enforce, expresses by reify). A FactSource is the input dual — a
table whose master runs in the external source — and the memo store
makes the two literally one object (a persisted table IS a fact
source with a pin stamp).

## restate: the store face, with its law

`restate(): Posting` — a factor expressed as its own statements,
joined by `all`. The law: **absorb(restate(F)) ≡ F**, modulo normalize
(the imposition law's idempotence clause licenses the modulo) —
restate is imposition's inverse, checkable in the laws kit. Per store
it is near-dictation: a lattice store restates as one `Imposition` per
entry plus one `stated(propagator)` per parked propagator; Nogoods as
one `stated(nogood)` each; the Substitutions floor already speaks it
(a Prefix crosses as binds — Resolution.rename today).

Three dissolutions the moment restate exists: DISPLAY generalizes
(every store's residual rendering = restate-about-the-rendered-names
`.toString()`; FD's converges on its current format — Imposition
already prints "x ⊂ {…}"); per-store RENAME gains a generic default
(restate → Posting.rename → rebuild; direct renames survive as
perf overrides where pins care); the MARSHAL format is the language
plus a registry.

## The serialization face and the registry

`Stored` grows `name` beside `terms()` (it already has the store
class); serialization carries (storeClass, name, terms, PAYLOAD) —
the payload slot is the honest gap in name+terms: value-carrying items
(an Imposition's Domain) serialize store-owned data, not just names.
Deserialization is a REGISTRY stores register builders into; bodies
NEVER cross — they rebuild by name (the named-schema contract is the
enabler; #65 built the mechanism; persistence finally pays for it).
Registry names must be stable across versions — the schema-validity
twin of the pin stamps' data-validity. The same registry serves
pushdown: an adapter's name→predicate table ("add" → a+b=c) is the
marshal registry's SQL twin.

## The fidelity ladder is the remainder policy

Both splits are lossless factorings — by NAMES (`about`: covered,
remainder) and by VOCABULARY (pushable, residual). Readers differ
only in what they do with the remainder:

- **keep it** — transcription, marshal (`all`: exact);
- **post-filter with it** — pushdown (the residual runs engine-side);
- **drop it** — display (purify; lossy but honest — `isGround=false`
  guards the folds).

The pushdown contract, named now because its violation is silent:
**every posting is either compiled or residualized, never dropped** —
a swallowed constraint at a fact source is the false-unchanged
failure mode wearing an adapter costume. Unknown dialects cost
post-filtering, never correctness; that is what makes N+M workable
where N×M store-specific inspection was not.

## The solve factoring (the human's lean, August 2026)

`solveSymbolic` as the underlying operation — answers as (term,
Residues.all), the c-table shape, never refusing — with regular
`solve` DELEGATING to it plus enumeration (enforce), which refuses on
infinite. Not required now; recorded so the symbolic mode arrives as
the factoring, not a bolt-on. The near-term slice is display only:
residuals attach through the promoted `Constrained` carrier and print
via the postings' own toString (the ¬ format, prune-invisible).

## Depth of projection — the open work

`about(vars)` under strict-name splitting (covered iff every touched
name is supplied) sends coupled knowledge to the remainder — a
constraint tying a query var to an unqueried one will not push down.
Three depths, different callers:

- (a) **strict names + remainder** — pushdown's natural depth (what
  couldn't compile anyway post-filters);
- (b) **transitive coupling closure** — tabling keys' depth (widen
  soundly-but-priced; the toll-gate ruling);
- (c) **∃-eliminate off-vars where finite** — the negation lesson's
  labelling path: exact, priced.

No single pick: `about` grows a depth policy per caller. This is the
note's main open design question.
