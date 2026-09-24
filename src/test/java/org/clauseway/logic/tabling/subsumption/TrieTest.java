package org.clauseway.logic.tabling.subsumption;

// ABOUTME: Pins the persistent path-trie under SubsumptionMap: path creation with
// ABOUTME: absent-fill, sibling isolation, leaf update, and snapshot persistence.

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class TrieTest {

	private static Trie<Integer, String> at(Trie<Integer, String> trie, Integer... keys) {
		for (Integer key : keys) {
			trie = trie.getChildren().get(key).get();
		}
		return trie;
	}

	@Test
	public void updateAtPathCreatesMissingNodesWithAbsentValue() {
		Trie<Integer, String> trie = Trie.<Integer, String> of("root")
				.update(Arrays.asList(1, 2, 3), () -> "absent", n -> n.updateValue(v -> "leaf"));
		assertThat(at(trie, 1, 2, 3).getValue()).isEqualTo("leaf");
		assertThat(at(trie, 1).getValue()).isEqualTo("absent");
		assertThat(at(trie, 1, 2).getValue()).isEqualTo("absent");
		assertThat(trie.getValue()).isEqualTo("root");
	}

	@Test
	public void siblingPathsStayIsolated() {
		Trie<Integer, String> trie = Trie.<Integer, String> of("root")
				.update(Arrays.asList(1, 2), () -> "absent", n -> n.updateValue(v -> "w"))
				.update(Arrays.asList(1, 3), () -> "absent", n -> n.updateValue(v -> "q"));
		assertThat(at(trie, 1, 2).getValue()).isEqualTo("w");
		assertThat(at(trie, 1, 3).getValue()).isEqualTo("q");
	}

	@Test
	public void updateAtExistingLeafKeepsItsChildren() {
		Trie<Integer, String> trie = Trie.<Integer, String> of("root")
				.update(Arrays.asList(1, 2, 3), () -> "absent", n -> n.updateValue(v -> "deep"))
				.update(Arrays.asList(1, 2), () -> "absent", n -> n.updateValue(v -> "mid"));
		assertThat(at(trie, 1, 2).getValue()).isEqualTo("mid");
		assertThat(at(trie, 1, 2, 3).getValue()).isEqualTo("deep");
	}

	@Test
	public void emptyPathUpdatesTheRoot() {
		Trie<Integer, String> trie = Trie.<Integer, String> of("root")
				.update(Collections.emptyList(), () -> "absent", n -> n.updateValue(v -> "updated"));
		assertThat(trie.getValue()).isEqualTo("updated");
		assertThat(trie.getChildren().isEmpty()).isTrue();
	}

	@Test
	public void updateMintsANewTrieAndLeavesTheSnapshotUnchanged() {
		Trie<Integer, String> before = Trie.<Integer, String> of("root")
				.update(Arrays.asList(1, 2), () -> "absent", n -> n.updateValue(v -> "w"));
		Trie<Integer, String> after = before
				.update(Arrays.asList(1, 2), () -> "absent", n -> n.updateValue(v -> "overwritten"));
		assertThat(at(before, 1, 2).getValue()).isEqualTo("w");
		assertThat(at(after, 1, 2).getValue()).isEqualTo("overwritten");
	}

	@Test
	public void deepPathsRebuildWithoutRecursion() {
		java.util.List<Integer> path = new java.util.ArrayList<>();
		for (int i = 0; i < 100_000; i++) {
			path.add(i);
		}
		Trie<Integer, String> trie = Trie.<Integer, String> of("root")
				.update(path, () -> "absent", n -> n.updateValue(v -> "leaf"));
		Trie<Integer, String> node = trie;
		for (Integer key : path) {
			node = node.getChildren().get(key).get();
		}
		assertThat(node.getValue()).isEqualTo("leaf");
	}
}
