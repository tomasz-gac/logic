package com.tgac.logic.goals;

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.recursion.Recur.done;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.recursion.BFSEngine;
import com.tgac.functional.recursion.Engine;
import com.tgac.functional.recursion.ExecutorServiceEngine;
import com.tgac.functional.recursion.Recur;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author TGa
 */
public interface Goal extends Function<Package, Cont<Package, Nothing>> {
	ExecutorService THREAD_POOL = new ForkJoinPool();

	static Goal goal(Goal g) {
		return g;
	}

	default Goal and(Goal... goals) {
		return new Conjunction().and(this).and(goals);
	}

	default Goal or(Goal... goals) {
		return new Conde().or(this).or(goals);
	}

	default Goal orElse(Goal... goals){
		return new Condu().orElse(this).orElse(goals);
	}

	default Goal orElseFirst(Goal... goals){
		return new Condu().orElse(this).orElse(goals);
	}

	static Goal conde(Goal... goals) {
		return Arrays.stream(goals)
				.reduce(Goal::or)
				.orElseGet(Goal::failure);
	}

	static Goal condu(Goal... goals) {
		return Arrays.stream(goals)
				.reduce(Goal::orElse)
				.orElseGet(Goal::failure);
	}

	static Goal conda(Goal... goals) {
		return new Conda().orElseFirst(goals);
	}

	static Goal all(Goal... goals) {
		return new Conjunction().and(goals);
	}

	default Goal named(String name) {
		return NamedGoal.of(name, this);
	}

	default Recur<Goal> optimize() {
		return done(this);
	}

	default Goal debug(String name, Map<String, Unifiable<?>> vars) {
		return s -> {
			System.out.println("before " + name + ": " + printVars(s, vars));
			Cont<Package, Nothing> ss = apply(s);
			List<Package> items = new ArrayList<>();
			ss.run(p -> {
				items.add(p);
				return nothing();
			}).get();
			System.out.println("after " + name + ":" + items.stream()
					.map(s1 -> "\n- " + printVars(s1, vars))
					.collect(Collectors.joining("")));
			return ss;
		};
	}

	static Goal defer(Supplier<Goal> g) {
		return goal(s -> g.get().apply(s))
				.named("recursive call");
	}

	static Goal successIf(boolean bool) {
		return bool ?
				success() :
				failure();
	}

	static Goal success() {
		return goal(Cont::just)
				.named("success");
	}

	static Goal failure() {
		return goal(s -> k -> done(nothing()))
				.named("failure");
	}

	default <T> Stream<Unifiable<T>> solve(
			Unifiable<T> out,
			Function<Recur<Nothing>, Engine<Nothing>> factory) {
		Deque<Unifiable<T>> results = new LinkedBlockingDeque<>();

		Recur<Nothing> recur = apply(Package.empty())
				.flatMap(s -> CKanren.reify(s, out))
				.run(v -> {
					results.add(v);      // Push result to queue
					return nothing();         // Unit signal
				});
		Engine<Nothing> engine = factory.apply(recur);

		Spliterator<Unifiable<T>> spliterator = new Spliterator<Unifiable<T>>() {
			@Override
			public boolean tryAdvance(Consumer<? super Unifiable<T>> action) {
				while (results.isEmpty()) {
					if (engine.run(64, v -> {
					})) {
						while (!results.isEmpty()) {
							action.accept(results.poll());
						}
						return false; // Engine completed
					}
				}
				action.accept(results.poll());
				return true;
			}

			@Override
			public Spliterator<Unifiable<T>> trySplit() {
				return null;
			}

			@Override
			public long estimateSize() {
				return Long.MAX_VALUE;
			}

			@Override
			public int characteristics() {
				return Spliterator.ORDERED | Spliterator.NONNULL;
			}
		};

		return StreamSupport.stream(spliterator, false)
				.onClose(() -> {
					try {
						engine.close();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
	}

	default <T> Stream<Unifiable<T>> solveParallel(Unifiable<T> out) {
		return solve(out, r -> new ExecutorServiceEngine<>(r, THREAD_POOL));
	}

	default <T> Stream<Unifiable<T>> solve(Unifiable<T> out) {
		return solve(out, BFSEngine::new);
	}

	static String printVars(Package s, Map<String, Unifiable<?>> vars) {
		return vars.toJavaStream()
				.map(v -> v._1 + " : " + MiniKanren.walkAll(s, v._2).get().toString())
				.collect(Collectors.joining(", "));
	}

}
