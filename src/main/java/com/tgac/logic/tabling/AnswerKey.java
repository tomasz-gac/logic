package com.tgac.logic.tabling;

// ABOUTME: One cached answer's identity: the reified term, the vars its holes
// ABOUTME: name, and the per-store factors it holds under — term GIVEN delta.

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A cached answer: {@code term} holds GIVEN {@code residues} — the answer's
 * normalized store factors, in the master's own variable names.
 * {@code holeVars.get(i)} is the body var the term names {@code _.i}: the
 * seed correspondence replay needs to rename the factors onto a
 * consumption's instantiated holes (unseeded factor vars mint fresh — the
 * existential). A ground answer has no residues and equality collapses to
 * term alpha-equivalence. Residues compare within an entry only — one
 * master, one lineage, shared vars — which is exactly what dedup needs.
 * {@code holeVars} is replay plumbing, not identity: alpha-equivalent
 * answers from different disjuncts name their holes with different branch
 * vars, and each stored answer replays with its OWN consistent
 * (holeVars, residues) pair.
 */
@Value
@EqualsAndHashCode(of = {"term", "residues"})
public class AnswerKey {
	Reified<?> term;
	List<LVar<?>> holeVars;
	Map<Class<?>, Object> residues;

	public static AnswerKey of(Reified<?> term) {
		return new AnswerKey(term, Collections.emptyList(), HashMap.empty());
	}

	public static AnswerKey of(Reified<?> term, List<LVar<?>> holeVars, Map<Class<?>, Object> residues) {
		return new AnswerKey(term, holeVars, residues);
	}

	/**
	 * {@code a ⊑ b} pointwise over store classes, absent = ⊤: every class b
	 * knows about, a must know at least as strongly. The containment check
	 * behind entry reuse (a caller may use an entry whose region covers its
	 * own) and answer dedup (a narrower answer is redundant under a wider).
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static boolean residuesLeq(Map<Class<?>, Object> a, Map<Class<?>, Object> b) {
		for (Tuple2<Class<?>, Object> knowledge : b) {
			Object mine = a.getOrElse(knowledge._1, null);
			if (mine == null || !((PartialOrder) mine).leq((PartialOrder) knowledge._2)) {
				return false;
			}
		}
		return true;
	}
}
