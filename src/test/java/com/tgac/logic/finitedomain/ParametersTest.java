package com.tgac.logic.finitedomain;
import com.tgac.logic.ckanren.Constraint;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.TestAccess;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.stream.Collectors;

import static com.tgac.logic.unification.LVal.lval;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class ParametersTest {

	@Mock
	PackageAccessor accessor;

	@Test
	public void shouldNotBlowStackWhenProcessingPrefix() {
		HashMap<LVar<?>, Unifiable<?>> empty = HashMap.empty();

		HashMap<LVar<?>, Unifiable<?>> prefix = Stream.range(0, 10)
				.map(i -> Tuple.of(TestAccess.lvarUnsafe(), lval(i)))
				.foldLeft(empty,
						(m, t) -> m.put(t._1, t._2));

		Constraint constraint = Constraint.of(
				accessor, Array.of(prefix.get()._1));

		System.out.println(FiniteDomainConstraints.empty()
				.prepend(constraint)
				.processPrefix(prefix)
				.apply(Package.of(HashMap.empty(),
						FiniteDomainConstraints.empty()))
				.get());
	}

	@Test
	public void shouldForceAnswer() {
		Unifiable<Long> i = LVar.lvar();

		java.util.List<Package> collect = EnforceConstraintsFD.forceAns(i)
				.apply(Package.of(HashMap.empty(),
						FiniteDomainConstraints.of(
								LinkedHashMap.<LVar<?>, Domain<?>> empty()
										.put(i.asVar().get(), EnumeratedDomain.range(0L, 10L)),
								List.empty())))
				.collect(Collectors.toList());

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

		java.util.List<Package> collect = EnforceConstraintsFD.forceAns(lval(Tuple.of(i, j)))
				.apply(Package.of(HashMap.empty(),
						FiniteDomainConstraints.of(
								LinkedHashMap.<LVar<?>, Domain<?>> empty()
										.put(i.asVar().get(), EnumeratedDomain.range(0L, 3L))
										.put(j.asVar().get(), EnumeratedDomain.range(0L, 3L)),
								List.empty())))
				.collect(Collectors.toList());

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
