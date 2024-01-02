package com.tgac.logic;

import com.tgac.functional.Exceptions;
import com.tgac.functional.recursion.Recur;
import io.vavr.collection.Array;
import io.vavr.collection.Map;
import io.vavr.collection.Stream;
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
import static com.tgac.functional.recursion.Recur.recur;
import static com.tgac.functional.recursion.Recur.zip;
import static com.tgac.logic.Incomplete.incomplete;

/**
 * @author TGa
 */
public interface Goal extends Function<Package, Stream<Package>> {

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
			Stream<Package> ss = apply(s);
			System.out.println("after " + name + ": " + ss.toJavaStream()
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
		public Stream<Package> apply(Package s) {
			return incomplete(() -> interleave(
					clauses.stream()
							.map(conjunction -> conjunction.apply(s))
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
							(l, r) -> zip(l, r).map(t -> t._1
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
					.reduce((acc, r) -> zip(acc, r)
							.map(lr -> lr.apply(java.util.stream.Stream::concat)))
					.map(r -> r.map(s -> s.toArray(Goal[]::new))
							.map(new Conjunction()::and)
							.map(Goal.class::cast))
					.orElseGet(() -> done(success()));
		}

		@Override
		public Stream<Package> apply(Package s) {
			return incomplete(() -> bind(Stream.of(s), Array.ofAll(clauses)));
		}

		@Override
		public String toString() {
			return "(" + clauses.stream().map(Objects::toString).collect(Collectors.joining(" && ")) + ")";
		}
	}

	static Goal conda(Goal... goals) {
		return a -> incomplete(() -> ifa(Array.of(goals).map(g -> g.apply(a))));
	}

	static Recur<Stream<Package>> ifa(Array<Stream<Package>> streams) {
		if (streams.isEmpty()) {
			return done(Stream.empty());
		} else {
			Stream<Package> a = streams.head();
			if (a instanceof Incomplete) {
				return ((Incomplete<Package>) a).getRest()
						.flatMap(s -> ifa(Array.of(s).appendAll(streams.tail())));
			} else if (a.isEmpty()) {
				return recur(() -> ifa(streams.tail()));
			} else {
				return done(a);
			}
		}
	}

	static Goal condu(Goal... goals) {
		return a -> incomplete(() -> ifu(Array.of(goals).map(g -> g.apply(a))));
	}

	static Recur<Stream<Package>> ifu(Array<Stream<Package>> streams) {
		if (streams.isEmpty()) {
			return done(Stream.empty());
		} else {
			Stream<Package> a = streams.head();
			if (a instanceof Incomplete) {
				return ((Incomplete<Package>) a).getRest()
						.flatMap(s -> ifu(Array.of(s).appendAll(streams.tail())));
			} else if (a.isEmpty()) {
				return recur(() -> ifu(streams.tail()));
			} else {
				return done(Stream.of(a.head()));
			}
		}
	}

	static Goal defer(Supplier<Goal> g) {
		return s -> g.get().apply(s);
	}

	static Goal success() {
		return Stream::of;
	}

	static Goal failure() {
		return s -> Stream.empty();
	}

	static <T> Goal unify(Unifiable<T> lhs, Unifiable<T> rhs) {
		return goal(s -> incomplete(() -> MiniKanren.unify(s, lhs, rhs)
				.getRecur().map(io.vavr.Value::toStream)))
				.named("unify");
	}

	static <T> Goal unifyNc(Unifiable<T> lhs, Unifiable<T> rhs) {
		return goal(s -> incomplete(() -> MiniKanren.unifyUnsafe(s, lhs, rhs)
				.getRecur().map(io.vavr.Value::toStream)))
				.named("unifyNc");
	}

	static <T> Goal separate(Unifiable<T> lhs, Unifiable<T> rhs) {
		return goal(a -> incomplete(() -> MiniKanren.separate(a, lhs, rhs)
				.getRecur().map(io.vavr.Value::toStream)))
				.named("separate");
	}

	default <T> java.util.stream.Stream<Unifiable<T>> solve(Unifiable<T> out) {
		return bind(Stream.of(Package.empty()), this).get()
				.map(s -> MiniKanren.reify(s, out).get())
				.toJavaStream();
	}

	default <T> Goal aggregate(Unifiable<T> var,
			Function<java.util.stream.Stream<Unifiable<T>>, Goal> f) {
		return s -> f.apply(bind(Stream.of(s), this).get()
						.map(s1 -> MiniKanren.reify(s1, var).get())
						.toJavaStream())
				.apply(s);
	}

	static <A> Recur<Stream<A>> interleave(Array<Stream<A>> lists) {
		if (lists.isEmpty()) {
			return done(Stream.empty());
		} else {
			Stream<A> fst = lists.head();
			Array<Stream<A>> rst = lists.tail();
			if (fst instanceof Incomplete) {
				// TODO : can this be somehow simplified to not require casting?
				return ((Incomplete<A>) fst).getRest()
						.flatMap(s -> interleave(rst.prepend(s)));
			} else if (fst.isEmpty()) {
				return recur(() -> interleave(rst));
			} else {
				return done(Stream.cons(fst.head(),
						() -> incomplete(() ->
								interleave(rst.append(fst.tail())))));
			}
		}
	}

	static Recur<Stream<Package>> bind(Stream<Package> s, Array<Goal> gs) {
		return gs.toJavaStream()
				.reduce(done(s), (subs, g) -> subs.flatMap(s1 -> bind(s1, g)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	static Recur<Stream<Package>> bind(Stream<Package> s, Goal g) {
		if (s instanceof Incomplete) {
			// TODO : can this be somehow simplified to not require casting?
			return ((Incomplete<Package>) s).getRest()
					.flatMap(s1 -> bind(s1, g));
		}
		if (s.isEmpty()) {
			return done(Stream.empty());
		} else {
			return interleave(Array.of(g.apply(s.head()), incomplete(() -> bind(s.tail(), g))));
		}
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
		public Stream<Package> apply(Package aPackage) {
			return goal.apply(aPackage);
		}
		@Override
		public String toString() {
			return name;
		}
	}
}
