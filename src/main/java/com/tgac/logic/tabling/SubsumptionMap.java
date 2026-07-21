package com.tgac.logic.tabling;

// ABOUTME: Term-indexed retrieval of stored calls whose pattern GENERALIZES a query:
// ABOUTME: a discrimination trie prunes candidates, Call.subsumes decides.

import com.tgac.functional.index.ImmutableIndex;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.ReifiedVar;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Value;

/**
 * Generalization retrieval over reified call patterns — the dual of an index
 * probe: stored keys are GENERAL (they carry holes), queries are specific, and
 * the answer is every stored value whose key subsumes the query.
 *
 * <p>Keys flatten to preorder symbol paths over a shared persistent trie
 * ({@link ImmutableIndex}): the relation's identity, then per term node an
 * arity marker (composites), the term itself (equality atoms) or the one
 * {@code VAR} marker (holes — hole NAMES are deliberately erased, which makes
 * the trie an IMPERFECT filter: {@code p(X,X)} and {@code p(X,Y)} share a
 * path). The walk follows exact edges and stored-{@code VAR} edges (a hole
 * matches the query's whole subterm — the arity markers make subterm extents
 * computable), and every candidate then passes {@link Call#subsumes}, which
 * restores hole-consistency precision. The trie prunes; subsumes decides.
 *
 * <p>Thread-safe: one persistent trie behind a CAS'd reference — reads are
 * lock-free on the snapshot, writes retry.
 */
final class SubsumptionMap<V> {

	/** The one hole symbol — stored-side variable edges; names erased by design. */
	private static final Object VAR = new Object() {
		@Override
		public String toString() {
			return "VAR";
		}
	};

	/** A composite's child count; kinds are deliberately NOT distinguished — the post-filter is. */
	@Value
	private static class Arity {
		int size;
	}

	private final AtomicReference<ImmutableIndex<Object, HashMap<Call, V>>> root =
			new AtomicReference<>(ImmutableIndex.of(HashMap.empty()));

	void put(Call key, V value) {
		Array<Object> path = flatten(key);
		root.updateAndGet(idx -> idx.withIndexAt(path, HashMap::empty,
				node -> node.updateValue(entries -> entries.put(key, value))));
	}

	/** Every stored value whose key subsumes {@code query}. */
	List<V> subsumers(Call query) {
		ImmutableIndex<Object, HashMap<Call, V>> snapshot = root.get();
		Array<Object> path = flatten(query);
		int[] skip = subtermEnds(path);
		List<V> result = new ArrayList<>();
		ArrayDeque<ImmutableIndex<Object, HashMap<Call, V>>> nodes = new ArrayDeque<>();
		ArrayDeque<Integer> positions = new ArrayDeque<>();
		nodes.push(snapshot);
		positions.push(0);
		while (!nodes.isEmpty()) {
			ImmutableIndex<Object, HashMap<Call, V>> node = nodes.pop();
			int at = positions.pop();
			if (at == path.length()) {
				for (Tuple2<Call, V> entry : node.getValue()) {
					if (entry._1.subsumes(query)) {
						result.add(entry._2);
					}
				}
				continue;
			}
			Object symbol = path.get(at);
			// a stored hole covers the query's whole subterm — jump past it
			Option<ImmutableIndex<Object, HashMap<Call, V>>> viaHole = node.getLookup().get(VAR);
			if (viaHole.isDefined()) {
				nodes.push(viaHole.get());
				positions.push(skip[at]);
			}
			// exact structural edge; for a query hole the only exact edge IS the
			// hole edge (a stored concrete position cannot cover a hole), already
			// followed above
			if (symbol != VAR) {
				Option<ImmutableIndex<Object, HashMap<Call, V>>> viaSymbol = node.getLookup().get(symbol);
				if (viaSymbol.isDefined()) {
					nodes.push(viaSymbol.get());
					positions.push(at + 1);
				}
			}
		}
		return result;
	}

	/** Preorder symbol path: relation identity, then arity markers / atoms / VAR. */
	private static Array<Object> flatten(Call key) {
		ArrayList<Object> out = new ArrayList<>();
		out.add(key.getRelation());
		ArrayDeque<Term<?>> pending = new ArrayDeque<>();
		pending.push(key.getArguments());
		while (!pending.isEmpty()) {
			Term<?> term = pending.pop();
			if (term instanceof ReifiedVar) {
				out.add(VAR);
				continue;
			}
			Option<Iterable<Term<?>>> members = MiniKanren.members(term);
			if (members.isEmpty()) {
				out.add(term);
				continue;
			}
			ArrayList<Term<?>> children = new ArrayList<>();
			for (Term<?> child : members.get()) {
				children.add(child);
			}
			out.add(new Arity(children.size()));
			for (int i = children.size() - 1; i >= 0; i--) {
				pending.push(children.get(i));
			}
		}
		return Array.ofAll(out);
	}

	/**
	 * {@code ends[i]} = the index just past the subterm starting at {@code i} —
	 * what a stored hole skips. Backward pass: an atom ends at {@code i+1}, an
	 * arity node past its children's ends chained left to right.
	 */
	private static int[] subtermEnds(Array<Object> path) {
		int[] ends = new int[path.length()];
		for (int i = path.length() - 1; i >= 0; i--) {
			Object symbol = path.get(i);
			if (symbol instanceof Arity) {
				int end = i + 1;
				for (int child = 0; child < ((Arity) symbol).size; child++) {
					end = ends[end];
				}
				ends[i] = end;
			} else {
				ends[i] = i + 1;
			}
		}
		return ends;
	}
}
