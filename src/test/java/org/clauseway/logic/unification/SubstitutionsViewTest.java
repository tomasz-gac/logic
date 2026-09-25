package org.clauseway.logic.unification;

// ABOUTME: Pins the Substitutions interface faces: the java-Map factory, the
// ABOUTME: pair-iteration and toMap reads, and equality over bindings.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.clauseway.functional.tuples.Tuple2;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.terms.Name;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.junit.Test;

public class SubstitutionsViewTest {

	@Test
	public void theJavaMapFactoryRoundTripsThroughTheReadFaces() {
		Unifiable<Integer> x = LVar.lvar();
		Map<Name<?>, Term<?>> seed = new LinkedHashMap<>();
		seed.put(x.asName().get(), lval(1));

		Substitutions s = Substitutions.of(seed);
		assertThat(s.size()).isEqualTo(1);
		assertThat(s.binding(x.asName().get())).isEqualTo(lval(1));
		assertThat(s.toMap()).isEqualTo(seed);

		int seen = 0;
		for (Tuple2<Name<?>, Term<?>> binding : s.bindings()) {
			assertThat(binding._1).isEqualTo(x.asName().get());
			assertThat(binding._2).isEqualTo(lval(1));
			seen++;
		}
		assertThat(seen).isEqualTo(1);
	}

	@Test
	public void equalityIsTheBindingSet() {
		Unifiable<Integer> x = LVar.lvar();
		Substitutions grown = Substitutions.empty().extend(x.asVar().get(), lval(2));
		Map<Name<?>, Term<?>> same = new LinkedHashMap<>();
		same.put(x.asVar().get(), lval(2));

		assertThat(grown).isEqualTo(Substitutions.of(same));
		assertThat(grown).isNotEqualTo(Substitutions.empty());
	}
}
