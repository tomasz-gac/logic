package com.tgac.logic.tabling;

// ABOUTME: One cached answer's identity: the reified term plus the per-store
// ABOUTME: residues it holds under — a conditional answer is term GIVEN region.

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import lombok.Value;

/**
 * A cached answer: {@code term} holds GIVEN {@code residues} (storeClass →
 * residue over the term's holes, positional). A ground answer has no
 * residues — equality collapses to term alpha-equivalence, the pre-stage-2
 * identity. Residue equality within an entry is the store objects' identity
 * semantics (one master, one store lineage), which is exactly what dedup
 * needs; ACROSS entries answer keys are never compared.
 */
@Value
public class AnswerKey {
	Reified<?> term;
	Map<Class<?>, Object> residues;

	public static AnswerKey of(Reified<?> term) {
		return new AnswerKey(term, HashMap.empty());
	}

	public static AnswerKey of(Reified<?> term, Map<Class<?>, Object> residues) {
		return new AnswerKey(term, residues);
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
