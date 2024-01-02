package com.tgac.logic;
import com.tgac.logic.cKanren.CKanren;
import io.vavr.collection.Stream;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import static com.tgac.logic.LVal.lval;
import static com.tgac.logic.LVar.lvar;

@SuppressWarnings("ALL")
public class CKanrenTest {

	@Test
	public void shouldUnify() {
		Unifiable<Integer> u = lvar();
		Unifiable<Integer> v = lval(1);
		Stream<Package> s = CKanren.unify(u, v)
				.apply(Package.empty());
		Assertions.assertThat(
						s.map(p -> MiniKanren.walk(p, v)
								.map(v1 -> CKanren.
										reify(p, v1).get()).get()))
				.containsExactly(lval(1));
	}

}
