package com.tgac.logic;

import static com.tgac.functional.monad.Cont.suspend;
import static com.tgac.functional.recursion.Recur.done;

import com.tgac.functional.Exceptions;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.recursion.Engine;
import com.tgac.functional.recursion.Recur;
import com.tgac.functional.step.Cons;
import com.tgac.functional.step.Empty;
import com.tgac.functional.step.Incomplete;
import com.tgac.functional.step.Single;
import com.tgac.functional.step.Step;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.collection.Map;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
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
public interface Goal extends Function<Package, Cont<Package, Void>> {

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
			Cont<Package, Void> ss = apply(s);
			List<Package> items = new ArrayList<>();
			ss.run(p -> {
				items.add(p);
				return null;
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
		public Cont<Package, Void> apply(Package s) {
			return k -> Recur.interleave(
					clauses.stream()
							.map(g -> g.apply(s).apply(k))
							.collect(Collectors.toList()),
					__ -> {});
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
		public Cont<Package, Void> apply(Package s) {
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

			for (Goal g : goals) {
				g.apply(s).runRec(v -> {
					if (committed.compareAndSet(false, true)) {
						Cont<Package, Void> with = exit.with(v);
						return with.runRec(k);
					}
					return Recur.done(null);
				});
				if (committed.get())
					break;
			}

			return Recur.done(null);
		}));
	}

	static Recur<Step<Package>> ifu(Array<Step<Package>> streams) {
		if (streams.isEmpty()) {
			return done(Empty.instance());
		} else {
			return streams.head().accept(new Step.Visitor<Package, Recur<Step<Package>>>() {
				@Override
				public Recur<Step<Package>> visit(Empty<Package> empty) {
					return Recur.recur(() -> ifu(streams.tail()));
				}

				@Override
				public Recur<Step<Package>> visit(Incomplete<Package> inc) {
					return inc.getRest()
							.flatMap(s -> ifu(Array.of(s).appendAll(streams.tail())));
				}

				@Override
				public Recur<Step<Package>> visit(Single<Package> single) {
					return done(single);
				}

				@Override
				public Recur<Step<Package>> visit(Cons<Package> cons) {
					return done(cons);
				}
			});
		}
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
		return goal(s -> k -> k.apply(null))
				.named("failure");
	}

//	default <T> Stream<Unifiable<T>> solve(Unifiable<T> out) {
//		Deque<Unifiable<T>> results = new ArrayDeque<>();
//		return apply(Package.empty())
//				.flatMap(s -> CKanren.reify(s, out))
//				.run(v -> {
//					results.push(v);
//					return null;
//				}).toEngine()
//				.stream()
//				.peek(System.out::println)
//				.map(__ -> results.pollFirst());
//	}

	default <T> Stream<Unifiable<T>> solve(Unifiable<T> out) {
		Deque<Unifiable<T>> results = new ArrayDeque<>();

		Recur<Void> recur = apply(Package.empty())
				.flatMap(s -> CKanren.reify(s, out))
				.run(v -> {
					results.add(v);      // Push result to queue
					return null;         // Void signal
				});

		Engine<Void> engine = recur.toEngine();

		Spliterator<Unifiable<T>> spliterator = new Spliterator<Unifiable<T>>() {
			@Override
			public boolean tryAdvance(Consumer<? super Unifiable<T>> action) {
				while (results.isEmpty()) {
					if (engine.run(64, v -> {})) {
						if(!results.isEmpty()){
							action.accept(results.poll());
						}
						return false; // Engine completed
					}
				}
				action.accept(results.poll());
				return true;
			}

			@Override public Spliterator<Unifiable<T>> trySplit() { return null; }
			@Override public long estimateSize() { return Long.MAX_VALUE; }
			@Override public int characteristics() {
				return Spliterator.ORDERED | Spliterator.NONNULL;
			}
		};

		return StreamSupport.stream(spliterator, false);
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
		public Cont<Package, Void> apply(Package aPackage) {
			return goal.apply(aPackage);
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
