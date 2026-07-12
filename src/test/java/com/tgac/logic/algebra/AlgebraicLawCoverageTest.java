package com.tgac.logic.algebra;

// ABOUTME: The logic twin of functional's coverage gate: every class implementing
// ABOUTME: an algebraic interface must have its laws registered — then they run.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Bottomed;
import com.tgac.functional.algebra.CommutativeMonoid;
import com.tgac.functional.algebra.JoinSemilattice;
import com.tgac.functional.algebra.Lattice;
import com.tgac.functional.algebra.MeetSemilattice;
import com.tgac.functional.algebra.Monoid;
import com.tgac.functional.algebra.Semiring;
import com.tgac.functional.algebra.laws.BottomedLaws;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.Empty;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.finitedomain.domains.Union;
import io.vavr.collection.Array;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Test;

public class AlgebraicLawCoverageTest {

	private static final Class<?>[] ALGEBRAS = {
			MeetSemilattice.class, JoinSemilattice.class, Lattice.class,
			Monoid.class, CommutativeMonoid.class, Semiring.class, Bottomed.class};

	private static Runnable domainLaws(List<Domain<Long>> featured) {
		return () -> {
			SemilatticeLaws.checkMeet(featured);
			BottomedLaws.check(featured);
		};
	}

	private static final Map<Class<?>, Runnable> LAWS = new LinkedHashMap<>();

	static {
		LAWS.put(EnumeratedDomain.class, domainLaws(Arrays.asList(
				EnumeratedDomain.of(Array.of(2L, 3L, 5L, 8L).map(Arithmetic::ofCasted)),
				EnumeratedDomain.of(Array.of(3L, 5L, 9L).map(Arithmetic::ofCasted)),
				EnumeratedDomain.of(Array.of(1L, 7L).map(Arithmetic::ofCasted)),
				Empty.instance())));
		LAWS.put(Interval.class, domainLaws(Arrays.asList(
				Interval.of(0L, 10L),
				Interval.of(3L, 6L),
				Interval.of(8L, 15L),
				Empty.instance())));
		LAWS.put(Singleton.class, domainLaws(Arrays.asList(
				Singleton.of(Arithmetic.ofCasted(5L)),
				Singleton.of(Arithmetic.ofCasted(9L)),
				Interval.of(3L, 6L),
				Empty.instance())));
		LAWS.put(Union.class, domainLaws(Arrays.asList(
				Interval.of(0L, 15L).difference(Interval.of(5L, 9L)),
				Interval.of(2L, 12L).difference(Interval.of(6L, 7L)),
				Interval.of(4L, 11L),
				Empty.instance())));
		LAWS.put(Empty.class, domainLaws(Arrays.asList(
				Empty.instance(),
				Interval.of(0L, 4L),
				EnumeratedDomain.of(Array.of(2L, 3L).map(Arithmetic::ofCasted)))));
	}

	@Test
	public void everyDeclaredAlgebraicInstanceHasItsLawsRegisteredAndPassing() throws IOException {
		List<Class<?>> instances = discoverImplementors();
		assertThat(instances).isNotEmpty();
		for (Class<?> instance : instances) {
			Runnable laws = LAWS.get(instance);
			assertThat(laws)
					.describedAs("no law registration for %s — add samples to "
							+ "AlgebraicLawCoverageTest.LAWS", instance.getName())
					.isNotNull();
			try {
				laws.run();
			} catch (AssertionError e) {
				throw new AssertionError(instance.getName() + ": " + e.getMessage(), e);
			}
		}
	}

	private static List<Class<?>> discoverImplementors() throws IOException {
		Path root = Paths.get("target", "classes");
		List<Class<?>> found = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(root)) {
			paths.filter(p -> p.toString().endsWith(".class"))
					.forEach(p -> {
						String name = root.relativize(p).toString()
								.replace(java.io.File.separatorChar, '.')
								.replaceAll("\\.class$", "");
						try {
							Class<?> c = Class.forName(name);
							if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) {
								return;
							}
							for (Class<?> algebra : ALGEBRAS) {
								if (algebra.isAssignableFrom(c)) {
									found.add(c);
									return;
								}
							}
						} catch (ClassNotFoundException | LinkageError e) {
							// unloadable class files are not algebraic instances
						}
					});
		}
		return found;
	}
}
