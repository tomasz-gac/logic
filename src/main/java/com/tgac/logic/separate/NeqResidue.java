package com.tgac.logic.separate;

// ABOUTME: Neq's projected knowledge about a var list: disequality records
// ABOUTME: transcribed onto positional slots — data all the way, replayed by copy.

import com.tgac.logic.constraints.store.Residue;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * The disequality store's residue over a positional var list. Each record is
 * one disequality — "NOT all these bindings simultaneously" — transcribed as
 * slot → forbidden term, with every projected var renamed to its canonical
 * {@link com.tgac.logic.unification.Hole}. Records are DATA over slots, no
 * store lineage in them: independent same-shaped contexts project to equal
 * residues, and replay copies the record onto the target vars — each
 * consumption its own instance, nothing shared to capture.
 *
 * <p>Entailment is record containment: more disequalities cut the region
 * smaller, so {@code this ⊑ other} iff this holds every record other holds.
 * A record entailing another without being equal (x≠5 entails ¬(x=5∧y=3))
 * reads as incomparable — conservative, costs reuse, never soundness.
 *
 * <p>TERMINATION note ({@link com.tgac.logic.constraints.store.Projectable}):
 * record sets over unbounded values do NOT form a finite lattice — tabling
 * under ever-growing disequality contexts is the author's responsibility,
 * like tabling any unbounded generator.
 */
@Value(staticConstructor = "of")
public class NeqResidue implements Residue<NeqResidue> {

	HashSet<HashMap<Integer, Term<?>>> records;

	@Override
	public boolean leq(NeqResidue other) {
		return records.containsAll(other.records);
	}

	/** Each record replays through the public statement entry: one
	 * {@code separate} over the paired (slot var, forbidden term) lists, so a
	 * replayed record re-verifies against the target state like a fresh
	 * disequality — already-violated records fail the branch silently. */
	@Override
	public Goal restate(List<Unifiable<?>> vars) {
		Goal all = Goal.success();
		for (HashMap<Integer, Term<?>> record : records) {
			all = Conjunction.of(all, restateRecord(record, vars));
		}
		return all;
	}

	private static Goal restateRecord(HashMap<Integer, Term<?>> record, List<Unifiable<?>> vars) {
		return p -> {
			List<Object> lhs = new ArrayList<>();
			List<Object> rhs = new ArrayList<>();
			for (Tuple2<Integer, Term<?>> pair : record) {
				lhs.add(vars.get(pair._1));
				rhs.add(MiniKanren.instantiate(pair._2, vars).get());
			}
			return Disequality.<List<Object>> separate(LVal.lval(lhs), LVal.lval(rhs)).apply(p);
		};
	}
}
