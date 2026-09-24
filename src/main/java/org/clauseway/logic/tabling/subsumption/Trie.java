package org.clauseway.logic.tabling.subsumption;

// ABOUTME: A persistent path-trie node: a value and a map of children, rebuilt
// ABOUTME: along a key path on update; SubsumptionMap's discrimination carrier.

import io.vavr.collection.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import lombok.Value;

@Value
class Trie<K, V> {

	V value;
	HashMap<K, Trie<K, V>> children;

	static <K, V> Trie<K, V> of(V value) {
		return new Trie<>(value, HashMap.empty());
	}

	Trie<K, V> updateValue(UnaryOperator<V> update) {
		return new Trie<>(update.apply(value), children);
	}

	/**
	 * The trie with the node at {@code path} passed through {@code updater},
	 * minting nodes valued {@code absent} where the path leaves the trie. The
	 * walk and rebuild are iterative — depth is a serialized pattern's size.
	 */
	Trie<K, V> update(List<K> path, Supplier<V> absent, UnaryOperator<Trie<K, V>> updater) {
		ArrayList<Trie<K, V>> parents = new ArrayList<>(path.size());
		Trie<K, V> node = this;
		for (K key : path) {
			parents.add(node);
			node = node.children.getOrElse(key, null);
			if (node == null) {
				node = of(absent.get());
			}
		}
		Trie<K, V> rebuilt = updater.apply(node);
		for (int i = path.size() - 1; i >= 0; i--) {
			Trie<K, V> parent = parents.get(i);
			rebuilt = new Trie<>(parent.value, parent.children.put(path.get(i), rebuilt));
		}
		return rebuilt;
	}
}
