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
import com.tgac.logic.finitedomain.domains.Empty;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.finitedomain.domains.Union;
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

	private static final Runnable DOMAIN_LAWS = () -> {
		List<Domain<Long>> xs = Arrays.asList(
				EnumeratedDomain.range(0L, 11L),
				EnumeratedDomain.range(3L, 7L),
				EnumeratedDomain.range(8L, 15L),
				EnumeratedDomain.range(0L, 0L));
		SemilatticeLaws.checkMeet(xs);
		BottomedLaws.check(xs);
	};

	private static final Map<Class<?>, Runnable> LAWS = new LinkedHashMap<>();

	static {
		LAWS.put(EnumeratedDomain.class, DOMAIN_LAWS);
		LAWS.put(Empty.class, DOMAIN_LAWS);
		LAWS.put(Interval.class, DOMAIN_LAWS);
		LAWS.put(Singleton.class, DOMAIN_LAWS);
		LAWS.put(Union.class, DOMAIN_LAWS);
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
