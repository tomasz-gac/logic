package com.tgac.logic.ckanren;
import com.tgac.functional.recursion.Recur;
import com.tgac.functional.step.Step;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.tgac.logic.unification.LVal.lval;

@SuppressWarnings("ALL")
public class CKanrenTest {

	@Test
	public void shouldUnify() {
		Unifiable<Integer> u = LVar.lvar();
		Unifiable<Integer> v = lval(1);
		Step<Package> s = CKanren.unify(u, v)
				.apply(Package.empty());
		List<Integer> map = s
				.map(p -> Recur.done(MiniKanren.walk(p, v))
						.map(v1 -> CKanren.
								reify(p, v1)
								.stream()
								.collect(Collectors.toList()).get(0).get())
						.get())
				.stream()
				.collect(Collectors.toList());
		Assertions.assertThat(map)
				.containsExactly(1);
	}

}
