package com.tgac.logic.finitedomain;

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.functional.category.Nothing;
import com.tgac.logic.Utils;
import com.tgac.logic.ckanren.Constraint;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.TestAccess;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.Stream;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class ParametersTest {

	@Mock
	Goal accessor;

	@Test
	public void shouldNotBlowStackWhenProcessingPrefix() {
		HashMap<LVar<?>, Unifiable<?>> empty = HashMap.empty();

		HashMap<LVar<?>, Unifiable<?>> prefix = Stream.range(0, 10)
				.map(i -> Tuple.of(TestAccess.lvarUnsafe(), lval(i)))
				.foldLeft(empty,
						(m, t) -> m.put(t._1, t._2));

		Constraint constraint = Constraint.of(
				accessor,
				FiniteDomainConstraints.class,
				Arrays.asList(prefix.get()._1));

		Package[] box = new Package[1];
		FiniteDomainConstraints.empty()
				.prepend(constraint)
				.processPrefix(prefix)
				.apply(Package.of(HashMap.empty(),
						LinkedHashMap.of(FiniteDomainConstraints.class, FiniteDomainConstraints.empty())
				))
				.run(v -> {
					box[0] = v;
					return Nothing.nothing();
				}).get();
		System.out.println(box[0]);
	}

	@Test
	public void shouldForceAnswer() {
		Unifiable<Long> i = LVar.lvar();

		java.util.List<Package> collect = Utils.collect(EnforceConstraintsFD.forceAns(i)
				.apply(Package.empty().withStore(
						FiniteDomainConstraints.of(
								LinkedHashMap.<LVar<?>, Domain<?>> empty()
										.put(i.asVar().get(), EnumeratedDomain.range(0L, 10L)),
								HashSet.empty()))));

		System.out.println(collect);

		Assertions.assertThat(collect.stream()
						.map(p -> TestAccess.get(p, i.asVar().get()).get())
						.map(Unifiable::get)
						.collect(Collectors.toList()))
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldForceAnswerComposite() {
		Unifiable<Long> i = LVar.lvar();
		Unifiable<Long> j = LVar.lvar();

		java.util.List<Package> collect = Utils.collect(
				EnforceConstraintsFD.forceAns(lval(Tuple.of(i, j)))
						.apply(Package.empty().withStore(FiniteDomainConstraints.of(
								LinkedHashMap.<LVar<?>, Domain<?>> empty()
										.put(i.asVar().get(), EnumeratedDomain.range(0L, 3L))
										.put(j.asVar().get(), EnumeratedDomain.range(0L, 3L)),
								HashSet.empty()))));

		System.out.println(collect);

		java.util.List<Tuple2<Long, Long>> results = collect.stream()
				.map(p -> Tuple.of(TestAccess.get(p, i.asVar().get()).get(),
						TestAccess.get(p, j.asVar().get()).get()))
				.map(t -> t.map(Unifiable::get, Unifiable::get))
				.collect(Collectors.toList());

		System.out.println(results);
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
