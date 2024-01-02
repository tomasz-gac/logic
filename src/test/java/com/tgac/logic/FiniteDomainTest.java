package com.tgac.logic;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.Domain;
import com.tgac.logic.fd.EnforceConstraintsFD;
import com.tgac.logic.fd.EnumeratedInterval;
import com.tgac.logic.fd.FDSupport;
import com.tgac.logic.fd.ProcessPrefixFd;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.stream.Collectors;

import static com.tgac.logic.LVal.lval;
import static com.tgac.logic.LVar.lvar;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class FiniteDomainTest {

	@Mock
	Constraint constraint;

	static {
		FDSupport.useFD();
	}

	@Test
	public void shouldNotBlowStackWhenProcessingPrefix() {
		ProcessPrefixFd processPrefixFd = new ProcessPrefixFd();
		HashMap<LVar<?>, Unifiable<?>> empty = HashMap.empty();

		HashMap<LVar<?>, Unifiable<?>> prefix = Stream.range(0, 100_000)
				.map(i -> Tuple.of(new LVar<>(), lval(i)))
				.foldLeft(empty,
						(m, t) -> m.put(t._1, t._2));

		when(constraint.getArgs())
				.thenReturn(Array.of(prefix.get()._1));

		System.out.println(processPrefixFd.process(
						prefix,
						List.of(constraint))
				.get()
				.get()
				.apply(Package.of(HashMap.empty(), List.empty(), LinkedHashMap.empty(), List.empty()))
				.get());
	}

	@Test
	public void shouldForceAnswer() {
		Unifiable<Integer> i = lvar();

		java.util.List<Package> collect = EnforceConstraintsFD.forceAns(i)
				.apply(Package.of(HashMap.empty(), List.empty(),
						LinkedHashMap.<LVar<?>, Domain> empty()
								.put(i.asVar().get(), EnumeratedInterval.of(HashSet.range(0L, 10L))),
						List.empty()))
				.collect(Collectors.toList());

		System.out.println(collect);

		Assertions.assertThat(collect.stream()
						.map(p -> p.get(i.asVar().get()).get())
						.map(Unifiable::get)
						.collect(Collectors.toList()))
				.containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
	}

	@Test
	public void shouldForceAnswerComposite() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Package> collect = EnforceConstraintsFD.forceAns(lval(Tuple.of(i, j)))
				.apply(Package.of(HashMap.empty(), List.empty(),
						LinkedHashMap.<LVar<?>, Domain> empty()
								.put(i.asVar().get(), EnumeratedInterval.of(HashSet.range(0L, 3L)))
								.put(j.asVar().get(), EnumeratedInterval.of(HashSet.range(0L, 3L))),
						List.empty()))
				.collect(Collectors.toList());

		System.out.println(collect);

		java.util.List<Tuple2<Long, Long>> results = collect.stream()
				.map(p -> Tuple.of(p.get(i.asVar().get()).get(),
						p.get(j.asVar().get()).get()))
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
