package com.tgac.logic;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.functional.category.Monad;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Utils {
	public static <T, C extends Monad<Cont<?, Nothing>, T>> List<T> collect(C cnt) {
		List<T> results = new ArrayList<>();
		// ground(): this test utility may legitimately run inside an outer
		// engine (nested pure collection) — a deliberately built engine
		new BreadthFirstScheduler<>(cnt.<Cont<T, Nothing>> cast()
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
