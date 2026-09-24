package org.clauseway.logic;

import org.clauseway.functional.Nothing;
import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import org.clauseway.functional.fibers.Cont;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Utils {
	public static <T> List<T> collect(Cont<T, Nothing> cnt) {
		List<T> results = new ArrayList<>();
		// a HOST: the collected computation may itself use the ground() door,
		// so this harness builds its engine explicitly instead of grounding
		new BreadthFirstScheduler<>(cnt
				.run(v -> {
					results.add(v);
					return Nothing.nothing();
				})).get();
		return results;
	}

	public static <T> List<T> collect(Stream<T> cnt) {
		return cnt.collect(Collectors.toList());
	}
}
