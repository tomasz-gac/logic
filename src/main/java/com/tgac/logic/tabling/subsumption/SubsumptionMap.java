package com.tgac.logic.tabling.subsumption;

// ABOUTME: Term-indexed retrieval of stored patterns that GENERALIZE a query:
// ABOUTME: a discrimination trie prunes candidates, Subsumption.subsumes decides.

import com.tgac.functional.index.ImmutableIndex;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.ReifiedVar;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Value;

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

	/**
	 * One live hypothesis of the walk: some stored prefix led to {@code node},
	 * and {@code remaining} are the query subterms its patterns must still cover.
	 */
	@Value
	private static class State<V> {
		ImmutableIndex<Edge, HashMap<Term<?>, V>> node;
		List<Term<?>> remaining;
	}

	/** Every stored value whose pattern subsumes {@code query}. */
	public java.util.List<V> subsumers(Term<?> query) {
		java.util.List<V> result = new ArrayList<>();
		ArrayDeque<State<V>> pending = new ArrayDeque<>();
		pending.push(new State<>(root.get(), List.of(query)));
		while (!pending.isEmpty()) {
			State<V> state = pending.pop();
			ImmutableIndex<Edge, HashMap<Term<?>, V>> node = state.getNode();
			List<Term<?>> terms = state.getRemaining();
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
					.forEach(child -> pending.push(new State<>(child, rest)));
			if (head instanceof ReifiedVar) {
				// only a hole covers a hole — a stored concrete position cannot
				continue;
			}
			Option<Iterable<Term<?>>> members = MiniKanren.members(head);
			if (members.isEmpty()) {
				node.getLookup().get(new Edge.Atom(head))
						.forEach(child -> pending.push(new State<>(child, rest)));
			} else {
				ArrayList<Term<?>> children = childrenOf(members.get());
				List<Term<?>> unfolded = rest.prependAll(children);
				node.getLookup().get(new Edge.Branch(children.size()))
						.forEach(child -> pending.push(new State<>(child, unfolded)));
			}
		}
		return result;
	}

	/** A stored pattern's preorder edge path — the only side that serializes. */
	private static Array<Edge> flatten(Term<?> pattern) {
		ArrayList<Edge> out = new ArrayList<>();
		List<Term<?>> pending = List.of(pattern);
		while (!pending.isEmpty()) {
			Term<?> term = pending.head();
			pending = pending.tail();
			if (term instanceof ReifiedVar) {
				out.add(Edge.Hole.HOLE);
				continue;
			}
			Option<Iterable<Term<?>>> members = MiniKanren.members(term);
			if (members.isEmpty()) {
				out.add(new Edge.Atom(term));
				continue;
			}
			ArrayList<Term<?>> children = childrenOf(members.get());
			out.add(new Edge.Branch(children.size()));
			pending = pending.prependAll(children);
		}
		return Array.ofAll(out);
	}

	/** One pass over the lazy members into a local buffer — size and order in one go. */
	private static ArrayList<Term<?>> childrenOf(Iterable<Term<?>> members) {
		ArrayList<Term<?>> children = new ArrayList<>();
		for (Term<?> child : members) {
			children.add(child);
		}
		return children;
	}
}
