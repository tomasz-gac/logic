package com.tgac.logic.ckanren;

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.Utils;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Test;

@SuppressWarnings("ALL")
public class CKanrenTest {

	@Test
	public void shouldUnify() {
		Unifiable<Integer> u = LVar.lvar();
		Unifiable<Integer> v = lval(1);
		Cont<Package, Nothing> s = CKanren.unify(u, v)
				.apply(Package.empty());
		List<Integer> map = Utils.collect(s
				.map(p -> Fiber.done(MiniKanren.walk(p, v))
						.map(v1 -> Utils.collect(CKanren.
										reify(p, v1))
								.stream()
								.collect(Collectors.toList()).get(0).get())
						.get()));
		Assertions.assertThat(map)
				.containsExactly(1);
	}
}
