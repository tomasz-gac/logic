# The row-set constraint basis moves to core: one seam, two residences, three consumers

- **status**: argued — code inventory done (August 2026), nothing moved
- **evidence held**: derivation over read code — `Support` imports only
  logic's `Domain` plus vavr (moves verbatim); `TableConstraints.narrow`
  / `collapse` / `labelo` / `groundRecords` take candidates as
  arguments, already source-agnostic; the pldb coupling is exactly
  `TableBody(db, rel)` and the `posted(Database, Relation, …)` front
  door with `db.estimate` pricing
- **imports**: none new; leans on the co-store (lattice-store.md §4)
  and the level-set delivery (weighted-tclp.md §5), both already ratified
- **obligations**: (1) the extraction receipt — core store + a
  materialized-rows candidate source, pldb re-seated on the same store
  with its index-backed source, both suites green; (2) a neutral row
  type in core (pldb's `Fact` stays home); (3) the naming session
  entry — "table constraint" next to tabling's "table" in core is a
  collision, the gate settles the core store's name before the move
- **links**: negation-over-finite-goals.md (consumer under negation),
  lattice-store.md §4/§10 (the co-store slot — filled by NogoodConstraints
  composition per the stipulation, not by a sibling store),
  weighted-tclp.md §5
  (positive-face consumer: emit as level sets), condition.md (a learned
  nogood is a conditional answer for false), nogood-store.md (literals
  as the chokepoint the row set composes through)

## The claim

The row-set constraint machinery built in pldb (#61) is core
machinery with one pldb-shaped plug. `Support` (finite value set,
meet = intersection) and the whole propagation algebra — GAC narrowing
with transient projections and the store-only-when-shared rule,
singleton collapse to bindings, min-domain labelling, fail-first
row-wise enforce — are source-agnostic today; only the candidate
source (pldb: re-query the live index per wake) and the posting front
door know about `Database`. Extracting along that seam gives core a
row-set store fed by materialized rows, and pldb keeps its index-backed
source on the same store, losing nothing.

Two consumers want the core form, both as positive membership
(Support GAC): weighted TCLP's delivery — a level set IS a posted row
set ("emit as level-set constraints" is the pipeline's last stage) —
and pldb itself. NEGATION LEFT THIS CHAIN (the human's two-component
inversion, August 2026): `not(g)` posts one nogood per answer, the
answer's delta read as bind + factor literals, so it needs no store —
see negation-over-finite-goals.md. The compact form here (ONE nogood
stating the positive membership constraint, ¬(args ∈ answers)) remains
the recorded optimization should the per-answer form measure too slow
on wide tables, and the standing stipulation is unchanged either way:
NO negative row-set store gets built — the NogoodConstraints store is the
negative face, and the co-store slot lattice-store §4 reserves is
filled by composition, not by a sibling store. Learned nogoods keep
the same composition.

## The one real design decision: residence

pldb's rows deliberately live OUTSIDE the package — the data-boundary
doctrine, cold solves over pinned sources. All three new consumers
need rows INSIDE the package: a sealed answer set, a level set, and a
nogood set are answer content, and must survive transcription, replay,
and persistence — which the index-backed form structurally cannot.
Same algebra, two residence models: the seam covers propagation;
residence decides who owes Projectable and transcription faces
(package-resident core store owes them, boundary-resident pldb store
does not).

## The NogoodConstraints relation

Two compilations of the same negative knowledge, both nogoods: per-answer
(one nogood per row, bind literals) and compact (one nogood, one statement
literal of the positive membership constraint over the whole set — the
trial's three-way reading does the rest: membership fails on the
scratch = discharged, entailed = violated, narrows = owed). The literal
vocabulary stays the chokepoint — a row-set constraint is a `Stored`,
postable via a statement literal, so it composes with the verification
kernel unchanged. The known trade rides along: the nogood form is a lazy
veto, not an eager carve (the ¬¬C observation from
transcription-generifies.md) — exclusions filter at labelling.

The cheapest kill: attempt the extraction and find algebra that secretly
reads the Database — if `narrow`/`collapse`/`groundRecords` cannot run
on a materialized candidate list without pldb imports, the "one seam"
claim dies and the note records where the second seam is.
