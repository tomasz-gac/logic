package com.tgac.logic.ckanren;
import com.tgac.functional.category.Unit;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.recursion.Recur;
import com.tgac.functional.step.Step;
import com.tgac.logic.Utils;
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
		Cont<Package, Unit> s = CKanren.unify(u, v)
				.apply(Package.empty());
		List<Integer> map = Utils.collect(s
				.map(p -> Recur.done(MiniKanren.walk(p, v))
						.map(v1 -> Utils.collect(CKanren.
										reify(p, v1))
								.stream()
								.collect(Collectors.toList()).get(0).get())
						.get()));
		Assertions.assertThat(map)
				.containsExactly(1);
	}
}
