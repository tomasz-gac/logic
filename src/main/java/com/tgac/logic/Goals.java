package com.tgac.logic;

import com.tgac.functional.Exceptions;
import com.tgac.functional.recursion.Recur;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.Function3;
import io.vavr.Function4;
import io.vavr.Function5;
import io.vavr.Function6;
import io.vavr.Function7;
import io.vavr.Function8;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.Tuple4;
import io.vavr.Tuple5;
import io.vavr.Tuple6;
import io.vavr.Tuple7;
import io.vavr.Tuple8;
import io.vavr.collection.Array;
import io.vavr.collection.IndexedSeq;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Try;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.tgac.functional.recursion.Recur.done;
import static com.tgac.logic.Goal.defer;
import static com.tgac.logic.Goal.failure;
import static com.tgac.logic.Goal.goal;
import static com.tgac.logic.Incomplete.incomplete;
@SuppressWarnings("Convert2MethodRef")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Goals {

	public static Goal firsto(Goal... goals) {
		return goal(s -> Arrays.stream(goals)
				.map(g -> g.apply(s))
				.filter(s1 -> Try.of(() -> s1.toOption().get()).isSuccess())
				.findFirst()
				.orElseGet(Stream::empty))
				.named("firsto(" + Arrays.stream(goals)
						.map(Objects::toString)
						.collect(Collectors.joining(", ")) + ")");
	}

	public static <T> Goal appendo(
			Unifiable<LList<T>> first,
			Unifiable<LList<T>> second,
			Unifiable<LList<T>> both) {
		return matche(first, llist(() -> second.unify(both)))
				.or(matche(first,
						llist((a, d) -> Goals.<LList<T>> exist(res ->
								both.unify(LList.of(a, res))
										.and(defer(() -> appendo(d, second, res)))))))
				.named(formatLList(first) + " + " + formatLList(second) + " ≣ " + formatLList(both));
	}
	private static <T> String formatLList(Unifiable<LList<T>> first) {
		return first.asVar().map(v -> "[" + v + "]").getOrElse(() -> first.get().toString());
	}

	public static <A> Goal sameLengtho(Unifiable<LList<A>> lhs, Unifiable<LList<A>> rhs) {
		return matche(lhs, llist(() -> rhs.unify(LList.empty())))
				.or(matche(lhs,
						llist((_0, lTail) -> matche(rhs, llist((_1, rTail) ->
								defer(() -> sameLengtho(lTail, rTail)))))))
				.named("len(" + formatLList(lhs) + ") = len(" + formatLList(rhs) + ")");
	}

	public static <A> Goal membero(Unifiable<A> x, Unifiable<LList<A>> lst) {
		return matche(lst, llist((a, d) ->
				a.unify(x)
						.or(defer((() -> membero(x, d))))))
				.named(x + " ⊂ " + formatLList(lst));
	}

	public static <T> Goal rembero(Unifiable<LList<T>> ls, Unifiable<T> x, Unifiable<LList<T>> out) {
		return matche(ls, llist(() -> out.unify(LList.empty())))
				.or(matche(ls, llist((a, d) ->
						x.unify(a)
								.and(out.unify(d)))))
				.or(matche(ls, llist((a, d) -> Goals.<LList<T>> exist(res ->
						a.separate(x)
								.and(out.unify(LList.of(a, res)))
								.and(defer(() -> rembero(d, x, res)))))))
				.named(x + " ⊄ " + formatLList(ls) + " ≣ " + out);
	}

	public static <A> Goal distincto(Unifiable<LList<A>> distinct) {
		return matche(distinct,
				llist(() -> Goal.success()),
				llist(a -> Goal.success()),
				llist((a, b, d) -> a.separate(b)
						.and(defer(() -> distincto(LList.of(a, d))))
						.and(defer(() -> distincto(LList.of(b, d))))))
				.named("distincto(" + distinct + ")");
	}

	@SafeVarargs
	public static <A> Goal matche(Unifiable<A> u, Case<A> head, Case<A>... cases) {
		return Arrays.stream(cases)
				.map(c -> c.apply(u))
				.reduce(head.apply(u), Goal::or);
	}

	public interface Case<A> extends Function<Unifiable<A>, Goal> {
	}

	public static <A> Case<LList<A>> llist(Supplier<Goal> f) {
		return l -> Goals.<A> exist(a -> l.unify(LList.empty()).and(f.get()));
	}

	public static <A> Case<LList<A>> llist(Function1<Unifiable<A>, Goal> f) {
		return l -> Goals.<A> exist(a -> l.unify(LList.of(a)).and(f.apply(a)));
	}

	public static <A> Case<LList<A>> llist(Function2<Unifiable<A>, Unifiable<LList<A>>, Goal> f) {
		return l -> Goals.<A, LList<A>> exist((a, d) -> l.unify(LList.of(a, d)).and(f.apply(a, d)));
	}

	public static <A> Case<LList<A>> llist(Function3<Unifiable<A>, Unifiable<A>, Unifiable<LList<A>>, Goal> f) {
		return l -> Goals.<A, A, LList<A>> exist((a, b, d) -> l.unify(LList.of(a, LList.of(b, d)))
				.and(f.apply(a, b, d)));
	}
	public static <A> Case<LList<A>> llist(Function4<Unifiable<A>, Unifiable<A>, Unifiable<A>, Unifiable<LList<A>>, Goal> f) {
		return l -> Goals.<A, A, A, LList<A>> exist((a, b, c, d) -> l.unify(LList.of(a, LList.of(b, LList.of(c, d))))
				.and(f.apply(a, b, c, d)));
	}

	public static <A> Case<LList<A>> llist(int n, Function2<List<Unifiable<A>>, Unifiable<LList<A>>, Goal> f) {
		List<Unifiable<A>> elements = List.fill(n, LVar::lvar);
		return l -> elements.foldLeft(Tuple.of(l, Goal.success()), (lstAndGoal, a) -> {
			Unifiable<LList<A>> d = LVar.lvar();
			return Tuple.of(d, lstAndGoal._2.and(lstAndGoal._1.unify(LList.of(a, d))));
		}).apply((d, g) -> g.and(f.apply(elements, d)));
	}

	public static <T1> Case<Tuple1<Unifiable<T1>>> tuple(Function1<Unifiable<T1>, Goal> f) {
		return t -> Goals.<T1> exist(t1 ->
				t.unify(Tuple.of(t1))
						.and(f.apply(t1)));
	}

	public static <T1, T2> Case<Tuple2<
			Unifiable<T1>,
			Unifiable<T2>
			>> tuple(
			Function2<
					Unifiable<T1>,
					Unifiable<T2>,
					Goal> f) {
		return t -> Goals.<T1, T2> exist((t1, t2) ->
				t.unify(Tuple.of(t1, t2))
						.and(f.apply(t1, t2)));
	}

	public static <T1, T2, T3> Case<Tuple3<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>
			>> tuple(
			Function3<
					Unifiable<T1>,
					Unifiable<T2>,
					Unifiable<T3>,
					Goal> f) {
		return t -> Goals.<T1, T2, T3> exist((t1, t2, t3) ->
				t.unify(Tuple.of(t1, t2, t3))
						.and(f.apply(t1, t2, t3)));
	}

	public static <T1, T2, T3, T4> Case<Tuple4<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Unifiable<T4>
			>> tuple(
			Function4<
					Unifiable<T1>,
					Unifiable<T2>,
					Unifiable<T3>,
					Unifiable<T4>,
					Goal> f) {
		return t -> Goals.<T1, T2, T3, T4> exist((t1, t2, t3, t4) ->
				t.unify(Tuple.of(t1, t2, t3, t4))
						.and(f.apply(t1, t2, t3, t4)));
	}

	public static <T1, T2, T3, T4, T5> Case<Tuple5<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Unifiable<T4>,
			Unifiable<T5>
			>> tuple(
			Function5<
					Unifiable<T1>,
					Unifiable<T2>,
					Unifiable<T3>,
					Unifiable<T4>,
					Unifiable<T5>,
					Goal> f) {
		return t -> Goals.<T1, T2, T3, T4, T5> exist((t1, t2, t3, t4, t5) ->
				t.unify(Tuple.of(t1, t2, t3, t4, t5))
						.and(f.apply(t1, t2, t3, t4, t5)));
	}

	public static <T1, T2, T3, T4, T5, T6> Case<Tuple6<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Unifiable<T4>,
			Unifiable<T5>,
			Unifiable<T6>
			>> tuple(
			Function6<
					Unifiable<T1>,
					Unifiable<T2>,
					Unifiable<T3>,
					Unifiable<T4>,
					Unifiable<T5>,
					Unifiable<T6>,
					Goal> f) {
		return t -> Goals.<T1, T2, T3, T4, T5, T6> exist((t1, t2, t3, t4, t5, t6) ->
				t.unify(Tuple.of(t1, t2, t3, t4, t5, t6))
						.and(f.apply(t1, t2, t3, t4, t5, t6)));
	}

	public static <T1, T2, T3, T4, T5, T6, T7> Case<Tuple7<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Unifiable<T4>,
			Unifiable<T5>,
			Unifiable<T6>,
			Unifiable<T7>
			>> tuple(
			Function7<
					Unifiable<T1>,
					Unifiable<T2>,
					Unifiable<T3>,
					Unifiable<T4>,
					Unifiable<T5>,
					Unifiable<T6>,
					Unifiable<T7>,
					Goal> f) {
		return t -> Goals.<T1, T2, T3, T4, T5, T6, T7> exist((t1, t2, t3, t4, t5, t6, t7) ->
				t.unify(Tuple.of(t1, t2, t3, t4, t5, t6, t7))
						.and(f.apply(t1, t2, t3, t4, t5, t6, t7)));
	}

	public static <T1, T2, T3, T4, T5, T6, T7, T8> Case<Tuple8<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Unifiable<T4>,
			Unifiable<T5>,
			Unifiable<T6>,
			Unifiable<T7>,
			Unifiable<T8>
			>> tuple(
			Function8<
					Unifiable<T1>,
					Unifiable<T2>,
					Unifiable<T3>,
					Unifiable<T4>,
					Unifiable<T5>,
					Unifiable<T6>,
					Unifiable<T7>,
					Unifiable<T8>,
					Goal> f) {
		return t -> Goals.<T1, T2, T3, T4, T5, T6, T7, T8> exist((t1, t2, t3, t4, t5, t6, t7, t8) ->
				t.unify(Tuple.of(t1, t2, t3, t4, t5, t6, t7, t8))
						.and(f.apply(t1, t2, t3, t4, t5, t6, t7, t8)));
	}

	public static <T> Case<T> lvar(Supplier<Goal> sup) {
		return u -> s -> incomplete(() ->
				MiniKanren.walkAll(s, u)
						.map(v -> v.asVar()
								.map(__ -> sup.get())
								.getOrElse(() -> failure())
								.apply(s)));
	}

	public static <T> Case<T> lval(Function1<T, Goal> f) {
		return u -> s -> incomplete(() ->
				MiniKanren.walkAll(s, u)
						.map(v -> v.asVal()
								.map(f)
								.getOrElse(() -> failure())
								.apply(s)));
	}

	public static <T1> Goal exist(Function1<Unifiable<T1>, Goal> f) {
		return f.apply(LVar.lvar());
	}

	public static <T1, T2> Goal exist(Function2<
			Unifiable<T1>,
			Unifiable<T2>,
			Goal> f) {
		return f.apply(LVar.lvar(), LVar.lvar());
	}

	public static <T1, T2, T3> Goal exist(Function3<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Goal> f) {
		return f.apply(LVar.lvar(), LVar.lvar(), LVar.lvar());
	}

	public static <T1, T2, T3, T4> Goal exist(Function4<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Unifiable<T4>,
			Goal> f) {
		return f.apply(LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar());
	}

	public static <T1, T2, T3, T4, T5> Goal exist(Function5<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Unifiable<T4>,
			Unifiable<T5>,
			Goal> f) {
		return f.apply(LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar());
	}

	public static <T1, T2, T3, T4, T5, T6> Goal exist(Function6<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Unifiable<T4>,
			Unifiable<T5>,
			Unifiable<T6>,
			Goal> f) {
		return f.apply(LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar());
	}

	public static <T1, T2, T3, T4, T5, T6, T7> Goal exist(Function7<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Unifiable<T4>,
			Unifiable<T5>,
			Unifiable<T6>,
			Unifiable<T7>,
			Goal> f) {
		return f.apply(LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar());
	}

	public static <T1, T2, T3, T4, T5, T6, T7, T8> Goal exist(Function8<
			Unifiable<T1>,
			Unifiable<T2>,
			Unifiable<T3>,
			Unifiable<T4>,
			Unifiable<T5>,
			Unifiable<T6>,
			Unifiable<T7>,
			Unifiable<T8>,
			Goal> f) {
		return f.apply(LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar(), LVar.lvar());
	}

	public static <T1> Goal project(Unifiable<T1> v1, Function1<T1, Goal> f) {
		return s -> incomplete(() ->
				MiniKanren.walkAll(s, v1)
						.map(v -> v.asVal()
								.map(f)
								.map(g -> g.named("projected(" + g + ")"))
								.map(g -> g.apply(s))
								.getOrElseThrow(Exceptions.format(IllegalArgumentException::new, "Cannot project %s. No value bound.", v))));
	}

	public static <T1, T2> Goal project(Unifiable<T1> v1, Unifiable<T2> v2, Function2<T1, T2, Goal> f) {
		return project(v1, a -> project(v2, x -> f.apply(a, x)));
	}

	public static <T1, T2, T3> Goal project(
			Unifiable<T1> v1, Unifiable<T2> v2, Unifiable<T3> v3,
			Function3<T1, T2, T3, Goal> f) {
		return project(v1, v2, (a, b) -> project(v3, c -> f.apply(a, b, c)));
	}

	public static <T1, T2, T3, T4> Goal project(
			Unifiable<T1> v1, Unifiable<T2> v2, Unifiable<T3> v3, Unifiable<T4> v4,
			Function4<T1, T2, T3, T4, Goal> f) {
		return project(v1, v2, v3, (a, b, c) -> project(v4, x -> f.apply(a, b, c, x)));
	}

	public static <T1, T2, T3, T4, T5> Goal project(
			Unifiable<T1> v1, Unifiable<T2> v2, Unifiable<T3> v3, Unifiable<T4> v4, Unifiable<T5> v5,
			Function5<T1, T2, T3, T4, T5, Goal> f) {
		return project(v1, v2, v3, v4, (a, b, c, d) -> project(v5, x -> f.apply(a, b, c, d, x)));
	}

	public static <T1, T2, T3, T4, T5, T6> Goal project(
			Unifiable<T1> v1, Unifiable<T2> v2, Unifiable<T3> v3,
			Unifiable<T4> v4, Unifiable<T5> v5, Unifiable<T6> v6,
			Function6<T1, T2, T3, T4, T5, T6, Goal> f) {
		return project(v1, v2, v3, v4, v5, (a, b, c, d, e) -> project(v6, x -> f.apply(a, b, c, d, e, x)));
	}

	public static <T1, T2, T3, T4, T5, T6, T7> Goal project(
			Unifiable<T1> v1, Unifiable<T2> v2, Unifiable<T3> v3,
			Unifiable<T4> v4, Unifiable<T5> v5, Unifiable<T6> v6,
			Unifiable<T7> v7,
			Function7<T1, T2, T3, T4, T5, T6, T7, Goal> f) {
		return project(v1, v2, v3, v4, v5, v6, (a, b, c, d, e, g) -> project(v7, x -> f.apply(a, b, c, d, e, g, x)));
	}

	public static <T1, T2, T3, T4, T5, T6, T7, T8> Goal project(
			Unifiable<T1> v1, Unifiable<T2> v2, Unifiable<T3> v3,
			Unifiable<T4> v4, Unifiable<T5> v5, Unifiable<T6> v6,
			Unifiable<T7> v7, Unifiable<T8> v8,
			Function8<T1, T2, T3, T4, T5, T6, T7, T8, Goal> f) {
		return project(v1, v2, v3, v4, v5, v6, v7,
				(a, b, c, d, e, g, h) -> project(v8, x -> f.apply(a, b, c, d, e, g, h, x)));
	}

	public static Goal project(IndexedSeq<Unifiable<?>> goals, Function<IndexedSeq<Unifiable<Object>>, Goal> f) {
		return s -> incomplete(() ->
				goals.toJavaStream()
						.map(v -> MiniKanren.walkAll(s, v)
								.map(java.util.stream.Stream::of))
						.reduce((l, r) -> Recur.zip(l, r)
								.map(lr -> lr.apply(java.util.stream.Stream::concat)))
						.orElseGet(() -> done(java.util.stream.Stream.empty()))
						.map(u -> u.map(Unifiable::getObjectUnifiable)
								.collect(Array.collector()))
						.map(f::apply)
						.map(g -> g.named("projected(" + g + ")"))
						.map(g -> g.apply(s)));
	}
}
