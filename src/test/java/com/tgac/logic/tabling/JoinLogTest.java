package com.tgac.logic.tabling;

// ABOUTME: JoinLog's contract: fresh keys append in arrival order, known keys
// ABOUTME: fold by the semiring and grow only when the fold ascends.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import org.junit.Test;

public class JoinLogTest {

	private static JoinLog<String, Long> shortest(String key, long cost) {
		return JoinLog.<String, Long> empty(Semirings.MIN_PLUS).append(key, cost).get();
	}

	@Test
	public void anAscendingFoldMovesTheValueWithoutANewKey() {
		JoinLog<String, Long> log = shortest("d", 6L).join(shortest("d", 4L));

		// the key set did not grow, the knowledge did: min(6, 4) moved the value
		assertThat(log.size()).isEqualTo(1);
		assertThat(log.members.get("d").get()).isEqualTo(4L);
	}

	@Test
	public void anAbsorbedFoldChangesNothing() {
		JoinLog<String, Long> log = shortest("d", 4L).join(shortest("d", 6L));

		assertThat(log.members.get("d").get()).isEqualTo(4L);
	}

	@Test
	public void appendAbsorbedRefuses() {
		assertThat(shortest("d", 4L).append("d", 6L).isEmpty()).isTrue();
	}

	@Test
	public void freshKeysKeepArrivalOrderUnderTheIndex() {
		JoinLog<String, Long> log = shortest("a", 1L).append("b", 2L).get();

		assertThat(log.get(0)._1).isEqualTo("a");
		assertThat(log.get(1)._1).isEqualTo("b");
		assertThat(log.get(2)).isNull();
	}
}
