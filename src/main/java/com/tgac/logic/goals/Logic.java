package com.tgac.logic.goals;

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.goals.Matche.llist;
import static com.tgac.logic.goals.Matche.matche;
import static com.tgac.logic.ckanren.CKanren.unify;

import com.tgac.functional.Exceptions;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.recursion.Recur;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.Function3;
import io.vavr.Function4;
import io.vavr.Function5;
import io.vavr.Function6;
import io.vavr.Function7;
import io.vavr.Function8;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.collection.Array;
import io.vavr.collection.IndexedSeq;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Logic {
	public static <T> Goal appendo(
			Unifiable<LList<T>> first,
			Unifiable<LList<T>> second,
			Unifiable<LList<T>> both) {
		return matche(first, Matche.llist(() -> unify(second, both)))
				.or(matche(first,
						Matche.llist((a, d) -> Logic.<LList<T>> exist(res ->
								unify(both, LList.of(a, res))
										.and(defer(() -> appendo(d, second, res)))))))
				.named(formatLList(first) + " ++ " + formatLList(second) + " ≣ " + formatLList(both));
	}

	public static <T> String formatLList(Unifiable<LList<T>> first) {
		return first.asVar()
				.map(v -> "[" + v + "]")
				.getOrElse(() -> first.get().toString());
	}

	public static <A, B> Goal sameLengtho(Unifiable<LList<A>> lhs, Unifiable<LList<B>> rhs) {
		return matche(lhs, Matche.llist(() -> unify(rhs, LList.empty())))
				.or(matche(lhs,
						Matche.llist((_0, lTail) -> matche(rhs, Matche.llist((_1, rTail) ->
								defer(() -> sameLengtho(lTail, rTail)))))))
				.named("len(" + formatLList(lhs) + ") = len(" + formatLList(rhs) + ")");
	}

	public static <A> Goal membero(Unifiable<A> x, Unifiable<LList<A>> lst) {
		return matche(lst, Matche.llist((a, d) ->
				unify(a, x)
						.or(defer((() -> membero(x, d))))))
				.named(x + " ⊂ " + formatLList(lst));
	}

	public static Goal booleanGoal(
			Unifiable<Boolean> l, Unifiable<Boolean> r, Unifiable<Boolean> out,
			Array<Tuple3<Boolean, Boolean, Boolean>> table) {
		return table.map(b -> b
						.map(l::unifies, r::unifies, out::unifies).toSeq()
						.map(Goal.class::cast)
						.reduce(Goal::and))
				.reduce(Goal::or);
	}

	/**
	 * Logical conjunction -> AND
	 */
	public static Goal conjo(Unifiable<Boolean> l, Unifiable<Boolean> r, Unifiable<Boolean> out) {
		return booleanGoal(
				l, r, out,
				Array.of(
						Tuple.of(true, true, true),
						Tuple.of(true, false, false),
						Tuple.of(false, true, false),
						Tuple.of(false, false, false)));
	}

	/**
	 * Logical disjunction -> OR
	 */
	public static Goal disjo(Unifiable<Boolean> l, Unifiable<Boolean> r, Unifiable<Boolean> out) {
		return booleanGoal(
				l, r, out,
				Array.of(
						Tuple.of(true, true, true),
						Tuple.of(true, false, true),
						Tuple.of(false, true, true),
						Tuple.of(false, false, false)));
	}

	/**
	 * Logical negation
	 */
	public static Goal nego(Unifiable<Boolean> l, Unifiable<Boolean> r) {
		return l.unifies(true).and(r.unifiesNc(false))
				.or(l.unifies(false).and(r.unifies(true)));
	}

	public static Goal anyo(Unifiable<LList<Boolean>> lst, Unifiable<Boolean> out) {
		return matche(lst,
				llist(() -> out.unifies(false)),
				llist(a -> a.unifies(out)),
				llist((a, b, d) -> Logic.<Boolean> exist(c ->
						disjo(a, b, c)
								.and(defer(() -> anyo(LList.of(c, d), out))))));
	}

	public static Goal allo(Unifiable<LList<Boolean>> lst, Unifiable<Boolean> out) {
		return matche(lst,
				llist(() -> out.unifies(true)),
				llist(a -> a.unifies(out)),
				llist((a, b, d) -> Logic.<Boolean> exist(c ->
						conjo(a, b, c)
								.and(defer(() -> allo(LList.of(c, d), out))))));
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
		return s -> Cont.defer(() ->
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

	public static Goal projectMultiType(IndexedSeq<Unifiable<?>> goals, Function<IndexedSeq<Unifiable<Object>>, Goal> f) {
		return s -> Cont.defer(() ->
				goals.toJavaStream()
						.map(v -> MiniKanren.walkAll(s, v)
								.map(java.util.stream.Stream::of))
						.reduce((l, r) -> Recur.zip(l, r)
								.map(lr -> lr.apply(java.util.stream.Stream::concat)))
						.orElseGet(() -> Recur.done(java.util.stream.Stream.empty()))
						.map(u -> u.map(Unifiable::getObjectUnifiable)
								.collect(Array.collector()))
						.map(f)
						.map(g -> g.named("projected(" + g + ")"))
						.map(g -> g.apply(s)));
	}

	public static <T> Goal project(IndexedSeq<Unifiable<T>> values, Function<IndexedSeq<T>, Goal> f) {
		return s -> Cont.defer(() ->
				values.toJavaStream()
						.map(v -> MiniKanren.walkAll(s, v)
								.map(Stream::of))
						.reduce((l, r) -> Recur.zip(l, r)
								.map(lr -> lr.apply(Stream::concat)))
						.orElseGet(() -> Recur.done(Stream.empty()))
						.map(u -> u.map(Unifiable::getObjectUnifiable)
								.collect(Array.collector()))
						.map(u -> Option.of(u)
								.filter(v -> v.toJavaStream().allMatch(Unifiable::isVal))
								.getOrElseThrow(Exceptions.format(IllegalArgumentException::new, "Variable unbound during projection"))
								.map(Unifiable::get)
								.map(Types.<T> cast()))
						.map(f)
						.map(g -> g.named("projected(" + g + ")"))
						.map(g -> g.apply(s)));
	}
}
