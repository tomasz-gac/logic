package com.tgac.logic.tabling.subsumption;

// ABOUTME: Term-indexed retrieval of stored patterns that GENERALIZE a query:
// ABOUTME: a discrimination trie prunes candidates, Subsumption.subsumes decides.

import com.tgac.functional.index.ImmutableIndex;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.ReifiedVar;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generalization retrieval over reified patterns — the dual of an index probe:
 * stored keys are GENERAL (they carry holes), queries are specific, and the
 * answer is every stored value whose pattern subsumes the query. ONE key
 * space: partitioning (per relation, per rule body, …) is the caller's
 * concern, which is what keeps this reusable across its customers — tabling's
 * sealed-subsumer lookup today, the optimizer's plan reuse later.
 *
 * <p>A stored pattern serializes to its preorder {@link Edge} path over one
 * persistent trie ({@link ImmutableIndex}); alpha-equal patterns are one key
 * (reified equality — last put wins). The QUERY is never serialized: the walk
 * carries a worklist of query subterms, so a stored {@link Edge.Hole}
 * swallows one whole subterm by popping it, an {@link Edge.Atom} matches the
 * head by equality, and an {@link Edge.Branch} unfolds the head's members
 * onto the worklist. Candidates at exhausted paths pass
 * {@link Subsumption#subsumes}, which restores the precision the erased hole
 * names gave up: the trie prunes, subsumes decides.
 *
 * <p>Thread-safe: the persistent trie behind a CAS'd reference — reads are
 * lock-free on a snapshot, writes retry.
 */
public final class SubsumptionMap<V> {

	private final AtomicReference<ImmutableIndex<Edge, HashMap<Term<?>, V>>> root =
			new AtomicReference<>(ImmutableIndex.of(HashMap.empty()));

	public void put(Term<?> pattern, V value) {
		Array<Edge> path = flatten(pattern);
		root.updateAndGet(trie -> trie.withIndexAt(path, HashMap::empty,
				node -> node.updateValue(entries -> entries.put(pattern, value))));
	}

	/** Every stored value whose pattern subsumes {@code query}. */
	public java.util.List<V> subsumers(Term<?> query) {
		java.util.List<V> result = new ArrayList<>();
		ArrayDeque<Tuple2<ImmutableIndex<Edge, HashMap<Term<?>, V>>, List<Term<?>>>> pending = new ArrayDeque<>();
		pending.push(Tuple.of(root.get(), List.of(query)));
		while (!pending.isEmpty()) {
			Tuple2<ImmutableIndex<Edge, HashMap<Term<?>, V>>, List<Term<?>>> state = pending.pop();
			ImmutableIndex<Edge, HashMap<Term<?>, V>> node = state._1;
			List<Term<?>> terms = state._2;
			if (terms.isEmpty()) {
				for (Tuple2<Term<?>, V> entry : node.getValue()) {
					if (Subsumption.subsumes(entry._1, query)) {
						result.add(entry._2);
					}
				}
				continue;
			}
			Term<?> head = terms.head();
			List<Term<?>> rest = terms.tail();
			// a stored hole covers the head wholesale — pop it
			node.getLookup().get(Edge.Hole.HOLE)
					.forEach(child -> pending.push(Tuple.of(child, rest)));
			if (head instanceof ReifiedVar) {
				// only a hole covers a hole — a stored concrete position cannot
				continue;
			}
			Option<Iterable<Term<?>>> members = MiniKanren.members(head);
			if (members.isEmpty()) {
				node.getLookup().get(new Edge.Atom(head))
						.forEach(child -> pending.push(Tuple.of(child, rest)));
			} else {
				List<Term<?>> unfolded = rest.prependAll(members.get());
				node.getLookup().get(new Edge.Branch(count(members.get())))
						.forEach(child -> pending.push(Tuple.of(child, unfolded)));
			}
		}
		return result;
	}

	/** A stored pattern's preorder edge path — the only side that serializes. */
	private static Array<Edge> flatten(Term<?> pattern) {
		ArrayList<Edge> out = new ArrayList<>();
		ArrayDeque<Term<?>> pending = new ArrayDeque<>();
		pending.push(pattern);
		while (!pending.isEmpty()) {
			Term<?> term = pending.pop();
			if (term instanceof ReifiedVar) {
				out.add(Edge.Hole.HOLE);
				continue;
			}
			Option<Iterable<Term<?>>> members = MiniKanren.members(term);
			if (members.isEmpty()) {
				out.add(new Edge.Atom(term));
				continue;
			}
			ArrayList<Term<?>> children = new ArrayList<>();
			for (Term<?> child : members.get()) {
				children.add(child);
			}
			out.add(new Edge.Branch(children.size()));
			for (int i = children.size() - 1; i >= 0; i--) {
				pending.push(children.get(i));
			}
		}
		return Array.ofAll(out);
	}

	private static int count(Iterable<Term<?>> members) {
		int n = 0;
		for (Term<?> ignored : members) {
			n++;
		}
		return n;
	}
}
