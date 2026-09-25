package org.clauseway.logic.goals;

import static org.clauseway.logic.constraints.Constraints.unify;

import org.clauseway.functional.fibers.Cont;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.MiniKanren;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.functional.tuples.Function3;
import org.clauseway.functional.tuples.Function4;
import org.clauseway.functional.tuples.Function5;
import org.clauseway.functional.tuples.Function6;
import org.clauseway.functional.tuples.Function7;
import org.clauseway.functional.tuples.Function8;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple1;
import org.clauseway.functional.tuples.Tuple2;
import org.clauseway.functional.tuples.Tuple3;
import org.clauseway.functional.tuples.Tuple4;
import org.clauseway.functional.tuples.Tuple5;
import org.clauseway.functional.tuples.Tuple6;
import org.clauseway.functional.tuples.Tuple7;
import org.clauseway.functional.tuples.Tuple8;
import io.vavr.collection.List;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.util.function.BiFunction;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Matche {

	@SafeVarargs
	public static <A> Goal matche(Unifiable<A> u, Case<A> head, Case<A>... cases) {
		return Arrays.stream(cases)
				.map(c -> c.apply(u))
				.reduce(head.apply(u), Goal::or);
	}

	public interface Case<A> extends Function<Unifiable<A>, Goal> {
	}

	public static <A> Case<LList<A>> llist(Supplier<Goal> f) {
		return l -> Logic.<A> exist(a -> unify(l, LList.empty()).and(f.get()));
	}

	public static <A> Case<LList<A>> llist(Function<Unifiable<A>, Goal> f) {
		return l -> Logic.<A> exist(a -> unify(l, LList.of(a)).and(f.apply(a)));
	}

	public static <A> Case<LList<A>> llist(BiFunction<Unifiable<A>, Unifiable<LList<A>>, Goal> f) {
		return l -> Logic.<A, LList<A>> exist((a, d) -> unify(l, LList.of(a, d)).and(f.apply(a, d)));
	}

	public static <A> Case<LList<A>> llist(Function3<Unifiable<A>, Unifiable<A>, Unifiable<LList<A>>, Goal> f) {
		return l -> Logic.<A, A, LList<A>> exist((a, b, d) -> unify(l, LList.of(a, LList.of(b, d)))
				.and(f.apply(a, b, d)));
	}

	public static <A> Case<LList<A>> llist(Function4<Unifiable<A>, Unifiable<A>, Unifiable<A>, Unifiable<LList<A>>, Goal> f) {
		return l -> Logic.<A, A, A, LList<A>> exist((a, b, c, d) -> unify(l, LList.of(a, LList.of(b, LList.of(c, d))))
				.and(f.apply(a, b, c, d)));
	}

	// the callback speaks java.util.List; the vavr List above is the fold's own
	public static <A> Case<LList<A>> llist(int n, BiFunction<java.util.List<Unifiable<A>>, Unifiable<LList<A>>, Goal> f) {
		List<Unifiable<A>> elements = List.fill(n, LVar::lvar);
		return l -> elements.foldLeft(Tuple.of(l, Goal.success()), (lstAndGoal, a) -> {
			Unifiable<LList<A>> d = LVar.lvar();
			return Tuple.of(d, lstAndGoal._2.and(unify(lstAndGoal._1, LList.of(a, d))));
		}).apply((d, g) -> g.and(f.apply(elements.toJavaList(), d)));
	}

	public static <T1> Case<Tuple1<Unifiable<T1>>> tuple(Function<Unifiable<T1>, Goal> f) {
		return t -> Logic.<T1> exist(t1 ->
				unify(t, Tuple.of(t1))
						.and(f.apply(t1)));
	}

	public static <T1, T2> Case<Tuple2<
			Unifiable<T1>,
			Unifiable<T2>
			>> tuple(
			BiFunction<
					Unifiable<T1>,
					Unifiable<T2>,
					Goal> f) {
		return t -> Logic.<T1, T2> exist((t1, t2) ->
				unify(t, Tuple.of(t1, t2))
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
		return t -> Logic.<T1, T2, T3> exist((t1, t2, t3) ->
				unify(t, Tuple.of(t1, t2, t3))
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
		return t -> Logic.<T1, T2, T3, T4> exist((t1, t2, t3, t4) ->
				unify(t, Tuple.of(t1, t2, t3, t4))
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
		return t -> Logic.<T1, T2, T3, T4, T5> exist((t1, t2, t3, t4, t5) ->
				unify(t, Tuple.of(t1, t2, t3, t4, t5))
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
		return t -> Logic.<T1, T2, T3, T4, T5, T6> exist((t1, t2, t3, t4, t5, t6) ->
				unify(t, Tuple.of(t1, t2, t3, t4, t5, t6))
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
		return t -> Logic.<T1, T2, T3, T4, T5, T6, T7> exist((t1, t2, t3, t4, t5, t6, t7) ->
				unify(t, Tuple.of(t1, t2, t3, t4, t5, t6, t7))
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
		return t -> Logic.<T1, T2, T3, T4, T5, T6, T7, T8> exist((t1, t2, t3, t4, t5, t6, t7, t8) ->
				unify(t, Tuple.of(t1, t2, t3, t4, t5, t6, t7, t8))
						.and(f.apply(t1, t2, t3, t4, t5, t6, t7, t8)));
	}

	public static <T> Case<T> variable(Supplier<Goal> sup) {
		return u -> s -> Cont.defer(() ->
				MiniKanren.walkAll(s.substitution(), u)
						.map(v -> v.asVar()
								.map(__ -> sup.get())
								.orElseGet(Goal::failure)
								.apply(s)));
	}

	public static <T> Case<T> value(Function<T, Goal> f) {
		return u -> s -> Cont.defer(() ->
				MiniKanren.walkAll(s.substitution(), u)
						// isVal, not asVal presence: a NULL payload matches as a value
						.map(v -> (v.isVal() ? f.apply(v.get()) : Goal.failure())
								.apply(s)));
	}
}
