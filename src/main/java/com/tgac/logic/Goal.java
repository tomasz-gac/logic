package com.tgac.logic;

import com.tgac.functional.Exceptions;
import com.tgac.functional.recursion.Recur;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.step.Cons;
import com.tgac.logic.step.Empty;
import com.tgac.logic.step.Incomplete;
import com.tgac.logic.step.Single;
import com.tgac.logic.step.Step;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.collection.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.tgac.functional.recursion.Recur.done;

/**
 * @author TGa
 */
public interface Goal extends Function<Package, Step<Package>> {

	static Goal goal(Goal g) {
		return g;
	}

	default Conjunction and(Goal... goals) {
		return new Conjunction().and(this).and(goals);
	}

	default Goal or(Goal... goals) {
		return new Disjunction().or(this).or(goals);
	}

	static Disjunction conde(Goal... goals) {
		return new Disjunction().or(goals);
	}

	static Conjunction all(Goal... goals) {
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
			Step<Package> ss = apply(s);
			System.out.println("after " + name + ": " + ss.stream()
					.map(s1 -> printVars(s1, vars))
					.collect(Collectors.joining("\n")));
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
		public Step<Package> apply(Package s) {
			return Incomplete.of(() -> interleave(
					clauses.stream()
							.map(conjunction ->
									conjunction.apply(s))
							.collect(Array.collector())));
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
		public Step<Package> apply(Package s) {
			return Incomplete.of(() -> bind(Single.of(s), Array.ofAll(clauses)));
		}

		@Override
		public String toString() {
			return "(" + clauses.stream().map(Objects::toString).collect(Collectors.joining(" && ")) + ")";
		}
	}

	static Goal conda(Goal... goals) {
		return goal(a -> Incomplete.of(() -> ifa(Array.of(goals).map(g -> g.apply(a)))))
				.named("first(" +
						Arrays.stream(goals).map(Objects::toString)
								.collect(Collectors.joining(" ||| ")) +
						")");
	}

	static Recur<Step<Package>> ifa(Array<Step<Package>> streams) {
		if (streams.isEmpty()) {
			return done(Empty.instance());
		} else {
			Step<Package> a = streams.head();
			return streams.head()
					.accept(new Step.Visitor<Package, Recur<Step<Package>>>() {
						@Override
						public Recur<Step<Package>> visit(Empty<Package> empty) {
							return Recur.recur(() -> ifa(streams.tail()));
						}
						@Override
						public Recur<Step<Package>> visit(Incomplete<Package> inc) {
							return inc.getRest()
									.flatMap(s -> ifa(Array.of(s).appendAll(streams.tail())));
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

	static Goal condu(Goal... goals) {
		return goal(a -> Step.incomplete(() -> ifu(Array.of(goals).map(g -> g.apply(a)))))
				.named(Arrays.stream(goals).map(Objects::toString)
						.collect(Collectors.joining(" ||| ")));
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
				public Recur<Step<Package>> visit(com.tgac.logic.step.Incomplete<Package> inc) {
					return inc.getRest()
							.flatMap(s -> ifu(Array.of(s).appendAll(streams.tail())));
				}
				@Override
				public Recur<Step<Package>> visit(Single<Package> single) {
					return done(single);
				}
				@Override
				public Recur<Step<Package>> visit(Cons<Package> cons) {
					return done(Single.of(cons.getHead()));
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
		return goal(Single::of)
				.named("success");
	}

	static Goal failure() {
		return goal(s -> Empty.instance())
				.named("failure");
	}

	default <T> java.util.stream.Stream<Unifiable<T>> solve(Unifiable<T> out) {
		return bind(Single.of(Package.empty()), this).get()
				.flatMap(s -> CKanren.reify(s, out))
				.stream();
	}

	default <T> Goal aggregate(Unifiable<T> var,
			Function<java.util.stream.Stream<Unifiable<T>>, Goal> f) {
		return s -> f.apply(bind(Single.of(s), this).get()
						.flatMap(s1 -> CKanren.reify(s1, var))
						.stream())
				.apply(s);
	}

	static <A> Recur<Step<A>> interleave(Array<Step<A>> lists) {
		if (lists.isEmpty()) {
			return done(Empty.instance());
		} else {
			return lists.head().interleave(lists.tail());
		}
		//		Step<A> fst = lists.head();
		//		Array<Step<A>> rst = lists.tail();
		//		return fst.accept(new Step.Visitor<A, Recur<Step<A>>>() {
		//			@Override
		//			public Recur<Step<A>> visit(Empty<A> empty) {
		//				return Recur.recur(() -> interleave(rst));
		//			}
		//			@Override
		//			public Recur<Step<A>> visit(Incomplete<A> inc) {
		//				return inc.getRest()
		//						.flatMap(s -> interleave(rst.prepend(
		//								(s instanceof Incomplete) ?
		//										((Incomplete<A>) s).getOrEvaluate() :
		//										s)));
		//			}
		//			@Override
		//			public Recur<Step<A>> visit(Single<A> single) {
		//				return done(Cons.of(single.getHead(),
		//						Step.incomplete(() -> interleave(rst))));
		//			}
		//			@Override
		//			public Recur<Step<A>> visit(Cons<A> cons) {
		//				return done(Cons.of(cons.getHead(),
		//						Step.incomplete(() -> interleave(rst.append(cons.getTail())))));
		//			}
		//		});
	}

	static Recur<Step<Package>> bind(Step<Package> s, Array<Goal> gs) {
		return gs.toJavaStream()
				.reduce(done(s), (subs, g) -> subs.flatMap(s1 -> bind(s1, g)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	static Recur<Step<Package>> bind(Step<Package> s, Goal g) {
		return s.accept(new Step.Visitor<Package, Recur<Step<Package>>>() {
			@Override
			public Recur<Step<Package>> visit(Empty<Package> empty) {
				return done(Empty.instance());
			}
			@Override
			public Recur<Step<Package>> visit(Incomplete<Package> inc) {
				return inc.getRest()
						.flatMap(s -> bind(s, g));
			}
			@Override
			public Recur<Step<Package>> visit(Single<Package> single) {
				return done(g.apply(single.getHead()));
			}
			@Override
			public Recur<Step<Package>> visit(Cons<Package> cons) {
				return interleave(Array.of(
						g.apply(cons.getHead()),
						Incomplete.of(() -> bind(cons.getTail(), g))
				));
			}
		});
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
		public Step<Package> apply(Package aPackage) {
			return goal.apply(aPackage);
		}
		@Override
		public String toString() {
			return name;
		}
	}
}
