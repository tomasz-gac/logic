package com.tgac.logic.tabling.subsumption;

// ABOUTME: Term-indexed retrieval of stored calls whose pattern GENERALIZES a query:
// ABOUTME: a discrimination trie prunes candidates, Call.subsumes decides.

import com.tgac.functional.index.ImmutableIndex;
import com.tgac.logic.tabling.Call;
import com.tgac.logic.tabling.Tabled;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generalization retrieval over reified call patterns — the dual of an index
 * probe: stored keys are GENERAL (they carry holes), queries are specific, and
 * the answer is every stored value whose key subsumes the query.
 *
 * <p>One persistent trie per relation (relations are identity-keyed and never
 * holes). A stored pattern serializes to its preorder {@link Edge} path; the
 * QUERY is never serialized — the walk carries a worklist of query subterms,
 * so a stored {@link Edge.Hole} swallows one whole subterm by popping it, an
 * {@link Edge.Atom} matches the head by equality, and an {@link Edge.Branch}
 * unfolds the head's members onto the worklist ({@code Call.match}'s own
 * discipline). Candidates at exhausted paths pass {@link Call#subsumes},
 * which restores the precision the erased hole names gave up: the trie
 * prunes, subsumes decides.
 *
 * <p>Thread-safe: persistent tries behind CAS'd references — reads are
 * lock-free on a snapshot, writes retry.
 */
public final class SubsumptionMap<V> {

	private final ConcurrentHashMap<Tabled<?>, AtomicReference<ImmutableIndex<Edge, HashMap<Call, V>>>> roots =
			new ConcurrentHashMap<>();

	public void put(Call key, V value) {
		Array<Edge> path = flatten(key.getArguments());
		roots.computeIfAbsent(key.getRelation(),
						relation -> new AtomicReference<>(ImmutableIndex.of(HashMap.empty())))
				.updateAndGet(trie -> trie.withIndexAt(path, HashMap::empty,
						node -> node.updateValue(entries -> entries.put(key, value))));
	}

	/** Every stored value whose key subsumes {@code query}. */
	public java.util.List<V> subsumers(Call query) {
		java.util.List<V> result = new ArrayList<>();
		AtomicReference<ImmutableIndex<Edge, HashMap<Call, V>>> root = roots.get(query.getRelation());
		if (root == null) {
			return result;
		}
		ArrayDeque<Tuple2<ImmutableIndex<Edge, HashMap<Call, V>>, List<Term<?>>>> pending = new ArrayDeque<>();
		pending.push(Tuple.of(root.get(), List.of(query.getArguments())));
		while (!pending.isEmpty()) {
			Tuple2<ImmutableIndex<Edge, HashMap<Call, V>>, List<Term<?>>> state = pending.pop();
			ImmutableIndex<Edge, HashMap<Call, V>> node = state._1;
			List<Term<?>> terms = state._2;
			if (terms.isEmpty()) {
				for (Tuple2<Call, V> entry : node.getValue()) {
					if (entry._1.subsumes(query)) {
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
	private static Array<Edge> flatten(Term<?> arguments) {
		ArrayList<Edge> out = new ArrayList<>();
		ArrayDeque<Term<?>> pending = new ArrayDeque<>();
		pending.push(arguments);
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
