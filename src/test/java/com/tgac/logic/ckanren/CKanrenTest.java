package com.tgac.logic.ckanren;
import com.tgac.functional.recursion.Recur;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Stream;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

@SuppressWarnings("ALL")
public class CKanrenTest {

	@Test
	public void shouldUnify() {
		Unifiable<Integer> u = lvar();
		Unifiable<Integer> v = lval(1);
		Stream<Package> s = CKanren.unify(u, v)
				.apply(Package.empty());
		Assertions.assertThat(
						s.map(p -> Recur.done(MiniKanren.walk(p, v))
								.map(v1 -> CKanren.
										reify(p, v1).get()).get()))
				.containsExactly(lval(1));
	}

}
