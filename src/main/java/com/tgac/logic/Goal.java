package com.tgac.logic;

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.monad.Cont.suspend;
import static com.tgac.functional.recursion.Recur.done;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.recursion.BFSEngine;
import com.tgac.functional.recursion.Engine;
import com.tgac.functional.recursion.ExecutorServiceEngine;
import com.tgac.functional.recursion.MapBFSEngine;
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
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

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
		return new Disjunction().or(this).or(goals);
	}

	static Goal conde(Goal... goals) {
		return Arrays.stream(goals)
				.reduce(Goal::or)
				.orElseGet(Goal::failure);
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

	@Value
	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	class Disjunction implements Goal {
		List<Goal> clauses = new ArrayList<>();

		@Override
		public Disjunction or(Goal... goals) {
			if (goals.length == 0) {
				return this;
			} else if (goals.length == 1) {
				clauses.add(goals[0]);
				return this;
			} else {
				clauses.add(new Conjunction().and(goals));
				return this;
			}
		}

		@Override
		public Cont<Package, Nothing> apply(Package s) {
			return k -> Recur.forEach(
					clauses.stream()
							.map(g -> g.apply(s).apply(k))
							.collect(Collectors.toList()),
					_0 -> {
					});
		}

		@Override
		public Recur<Goal> optimize() {
			return clauses.stream()
					.map(Goal::optimize)
					.map(v -> v.map(g ->
							g instanceof Disjunction ?
									((Disjunction) g).clauses.stream() :
									java.util.stream.Stream.of(g)))
					.reduce(done(new Disjunction()),
							(l, r) -> Recur.zip(l, r).map(t -> t._1
									.or(t._2.toArray(Goal[]::new))),
							Exceptions.throwingBiOp(UnsupportedOperationException::new));
		}

		@Override
		public String toString() {
			return "(" + clauses.stream()
					.map(Objects::toString)
					.collect(Collectors.joining(" || ")) + ")";
		}
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	class Conjunction implements Goal {
		List<Goal> clauses = new ArrayList<>();

		public Conjunction and(Goal... goals) {
			clauses.addAll(Arrays.asList(goals));
			return this;
		}

		@Override
		public Recur<Goal> optimize() {
			return clauses.stream()
					.map(Goal::optimize)
					.map(v -> v.map(g -> g instanceof Conjunction ?
							((Conjunction) g).clauses.stream() :
							java.util.stream.Stream.of(g)))
					.reduce((acc, r) -> Recur.zip(acc, r)
							.map(lr -> lr.apply(java.util.stream.Stream::concat)))
					.map(r -> r.map(s -> s.toArray(Goal[]::new))
							.map(new Conjunction()::and)
							.map(Goal.class::cast))
					.orElseGet(() -> done(success()));
		}

		@Override
		public Cont<Package, Nothing> apply(Package s) {
			return clauses.stream()
					.reduce(suspend(k -> k.apply(s)),
							Cont::flatMap,
							Exceptions.throwingBiOp(UnsupportedOperationException::new));
		}

		@Override
		public String toString() {
			return "(" + clauses.stream().map(Objects::toString).collect(Collectors.joining(" && ")) + ")";
		}
	}

	static Goal condu(Goal... goals) {
		return s -> Cont.callCC(exit -> Cont.suspend(k -> {
			AtomicBoolean committed = new AtomicBoolean(false);
			List<Package> results = new ArrayList<>();
			return Arrays.stream(goals)
					.reduce(Recur.done(nothing()),
							(acc, g) -> acc.flatMap(_0 ->
									g.apply(s).run(s1 -> {
										results.add(s1);
										return nothing();
									}).flatMap(_1 -> {
										if (committed.get() || results.isEmpty()) {
											return done(nothing());
										}
										committed.set(true);
										return results.stream()
												.map(exit::<Package>with)
												.map(c -> c.runRec(k))
												.reduce(done(nothing()),
														(l, r) -> l.flatMap(_2 -> r));
									})),
							Exceptions.throwingBiOp(UnsupportedOperationException::new));
		}));
	}

	static Goal conda(Goal... goals) {
		return s -> Cont.callCC(exit -> Cont.suspend(k -> {
			AtomicBoolean committed = new AtomicBoolean(false);
			return Arrays.stream(goals)
					.reduce(
							Recur.<Nothing> done(Nothing.nothing()),
							(acc, g) -> acc.flatMap(__ -> {
								Recur<Nothing> collected = g.apply(s).runRec(s1 -> {
									if (committed.compareAndSet(false, true)) {
										return exit.<Package> with(s1).runRec(k);
									}
									return Recur.done(Nothing.nothing()); // ignore subsequent solutions
								});
								return collected.map(___ -> Nothing.nothing()); // don’t emit past this point
							}),
							(a, b) -> a.flatMap(__ -> b) // never called, but required
					);
		}));
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
						e.printStackTrace(System.err);
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

	@Value
	@RequiredArgsConstructor(staticName = "of")
	class NamedGoal implements Goal {
		String name;
		Goal goal;

		@Override
		public Cont<Package, Nothing> apply(Package aPackage) {
			return goal.apply(aPackage);
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
