package com.tgac.logic.finitedomain;

import static com.tgac.logic.unification.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.category.Nothing;
import com.tgac.logic.Utils;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.TestAccess;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.Stream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class ParametersTest {

	@Test
	public void shouldNotBlowStackWhenProcessingPrefix() {
		HashMap<LVar<?>, Term<?>> empty = HashMap.empty();

		HashMap<LVar<?>, Term<?>> prefix = Stream.range(0, 10)
				.map(i -> Tuple.of(TestAccess.lvarUnsafe(), lval(i)))
				.foldLeft(empty,
						(m, t) -> m.put(t._1, t._2));

		Propagator constraint = Propagator.of(
				FiniteDomainConstraints.class, "keep",
				Arrays.asList(prefix.get()._1),
				(watched, st) -> Verdict.keep());

		Package[] box = new Package[1];
		Package pkg = Package.of(HashMap.empty(),
				LinkedHashMap.of(FiniteDomainConstraints.class,
						FiniteDomainConstraints.empty().prepend(constraint)));
		Propagation.resolve(TestAccess.prefix(prefix))
				.apply(pkg)
				.run(v -> {
					box[0] = v;
					return Nothing.nothing();
				}).get();
		// processing a 10-association prefix completes without blowing the stack,
		// and the substitutions are applied
		assertThat(box[0]).isNotNull();
		assertThat(box[0].getSubstitutions().size()).isEqualTo(10);
	}

	@Test
	public void shouldForceAnswer() {
		Unifiable<Long> i = LVar.lvar();

		List<Package> collect = Utils.collect(EnforceConstraintsFD.forceAns(i)
				.apply(Package.empty().withStore(
						FiniteDomainConstraints.of(
								LinkedHashMap.<Term<?>, Domain<?>> empty()
										.put(i.asVar().get(), EnumeratedDomain.range(0L, 10L)),
								HashSet.empty()))));

		Assertions.assertThat(collect.stream()
						.map(p -> TestAccess.get(p, i.asVar().get()).get())
						.map(Term::get)
						.collect(Collectors.toList()))
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldForceAnswerComposite() {
		Unifiable<Long> i = LVar.lvar();
		Unifiable<Long> j = LVar.lvar();

		List<Package> collect = Utils.collect(
				EnforceConstraintsFD.forceAns(lval(Tuple.of(i, j)))
						.apply(Package.empty().withStore(FiniteDomainConstraints.of(
								LinkedHashMap.<Term<?>, Domain<?>> empty()
										.put(i.asVar().get(), EnumeratedDomain.range(0L, 3L))
										.put(j.asVar().get(), EnumeratedDomain.range(0L, 3L)),
								HashSet.empty()))));

		List<Tuple2<Long, Long>> results = collect.stream()
				.map(p -> Tuple.of(TestAccess.get(p, i.asVar().get()).get(),
						TestAccess.get(p, j.asVar().get()).get()))
				.map(t -> t.map(Term::get, Term::get))
				.collect(Collectors.toList());

		Assertions.assertThat(results)
				.containsExactlyInAnyOrder(
						Tuple.of(0L, 0L),
						Tuple.of(0L, 1L),
						Tuple.of(0L, 2L),
						Tuple.of(1L, 0L),
						Tuple.of(1L, 1L),
						Tuple.of(1L, 2L),
						Tuple.of(2L, 0L),
						Tuple.of(2L, 1L),
						Tuple.of(2L, 2L)
				);
	}
}
