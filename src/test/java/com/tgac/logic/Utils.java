package com.tgac.logic;

import com.tgac.functional.category.Monad;
import com.tgac.functional.category.Unit;
import com.tgac.functional.monad.Cont;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Utils {
	public static <T, C extends Monad<Cont<?, Unit>, T>> List<T> collect(C cnt) {
		List<T> results = new ArrayList<>();
		cnt.<Cont<T, Unit>> cast()
				.run(v -> {
					results.add(v);
					return Unit.unit();
				}).get();
		return results;
	}

	public static <T> List<T> collect(Stream<T> cnt) {
		return cnt.collect(Collectors.toList());
	}

	public static <T, C extends Monad<Cont<?, T>, T>> Stream<T> stream(C cnt) {
		return cnt.<Cont<T, T>> cast()
				.run(v -> v)
				.toEngine()
				.stream();
	}
}
