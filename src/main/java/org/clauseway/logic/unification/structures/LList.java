package org.clauseway.logic.unification.structures;

import static org.clauseway.logic.constraints.Constraints.unify;
import static org.clauseway.logic.goals.Matche.llist;
import static org.clauseway.logic.goals.Matche.matche;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.vavr.Predicates.not;

import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Logic;
import org.clauseway.functional.tuples.Function3;
import org.clauseway.vavr.collection.Array;
import org.clauseway.vavr.control.Option;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.IntFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.clauseway.logic.unification.terms.LVal;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;

/**
 * @author TGa
 */

@Value
@RequiredArgsConstructor
public class LList<A> {
	Term<A> head;
	Term<LList<A>> tail;

	public static <A> Unifiable<LList<A>> empty() {
		return new LList<A>(null, null).asVal();
	}

	public static <A> Unifiable<LList<A>> of(Term<A> v) {
		return new LList<>(v, empty()).asVal();
	}

	public static <A> Unifiable<LList<A>> of(Term<A> v, Term<LList<A>> c) {
		return new LList<>(v, c).asVal();
	}

	@SafeVarargs
	public static <A> Unifiable<LList<A>> ofAll(A... vs) {
		return ofAll(Arrays.asList(vs));
	}

	public static <A> Unifiable<LList<A>> ofAll(List<A> items) {
		return ofAll(items.size(), i -> LVal.lval(items.get(i)));
	}

	@SafeVarargs
	public static <A> Unifiable<LList<A>> ofAll(Term<A>... items) {
		return ofAll(items.length, i -> items[i]);
	}

	public static <A> Unifiable<LList<A>> ofAll(int size, IntFunction<Term<A>> getter) {
		return IntStream.range(0, size)
				.map(i -> size - i - 1)
				.mapToObj(getter)
				.map(LList::of)
				.map(Unifiable::get)
				.reduce((acc, v) -> LList.of(v.head, LVal.lval(acc)).get())
				.map(LVal::lval)
				.orElseGet(LList::empty);
	}

	public boolean isEmpty() {
		return Objects.isNull(head) && Objects.isNull(tail);
	}

	public Unifiable<LList<A>> asVal() {
		return LVal.lval(this);
	}

	/** The proper prefix: elements in order, stopping at the first non-value tail. */
	public Stream<Term<A>> elements() {
		ArrayList<Term<A>> out = new ArrayList<>();
		Term<LList<A>> tail = this.asVal();
		while (tail.isVal() && !tail.get().isEmpty()) {
			out.add(tail.get().getHead());
			tail = tail.get().getTail();
		}
		return out.stream();
	}

	/** The dangling hole ending an improper list; empty when the list closes with (). */
	public Optional<Term<LList<A>>> openTail() {
		Term<LList<A>> tail = this.asVal();
		while (tail.isVal() && !tail.get().isEmpty()) {
			tail = tail.get().getTail();
		}
		return tail.isVal() ? Optional.<Term<LList<A>>> empty() : Optional.of(tail);
	}

	public Stream<A> toValueStream() {
		return elements()
				.map(Term::get);
	}

	public static <A> Collector<Term<A>, ?, Unifiable<LList<A>>> collector() {
		return Collector.<Term<A>, ArrayList<Term<A>>, Unifiable<LList<A>>> of(
				ArrayList::new,
				ArrayList::add,
				(lhs, rhs) -> {
					lhs.addAll(rhs);
					return lhs;
				}, l -> ofAll(l.size(), l::get));
	}

	public static <A, B> Goal map(
			Unifiable<LList<A>> lhs,
			Unifiable<LList<B>> rhs,
			BiFunction<Unifiable<A>, Unifiable<B>, Goal> relation) {
		return zipReduce(lhs, rhs, relation, Goal::and);
	}

	public static <A, B> Goal zipReduce(
			Unifiable<LList<A>> lhs,
			Unifiable<LList<B>> rhs,
			BiFunction<Unifiable<A>, Unifiable<B>, Goal> zip,
			BinaryOperator<Goal> reduce) {
		return unify(lhs, LList.empty()).and(unify(rhs, LList.empty()))
				.or(Logic.<A, LList<A>, B, LList<B>> exist(
						(lhsHead, lhsTail, rhsHead, rhsTail) ->
								unify(lhs, LList.of(lhsHead, lhsTail))
										.and(unify(rhs, LList.of(rhsHead, rhsTail)))
										.and(reduce.apply(
												zip.apply(lhsHead, rhsHead),
												Goal.defer(() -> zipReduce(lhsTail, rhsTail, zip, reduce))))));
	}

	public static <A> Goal foldRight(
			Unifiable<LList<A>> lst,
			Unifiable<A> init,
			Unifiable<A> reduced,
			// next = reducer(prev, current)
			Function3<Unifiable<A>, Unifiable<A>, Unifiable<A>, Goal> reducer) {
		Unifiable<A> next = lvar();
		return matche(lst,
				llist(() -> reduced.unifies(init)),
				llist((current, tail) ->
						Goal.defer(() -> foldRight(tail, init, next, reducer))
								.and(reducer.apply(reduced, next, current))));
	}

	public static <A> Goal foldLeft(
			Unifiable<LList<A>> lst,
			Unifiable<A> init,
			Unifiable<A> reduced,
			// next = reducer(prev, current)
			Function3<Unifiable<A>, Unifiable<A>, Unifiable<A>, Goal> reducer) {
		Unifiable<A> next = lvar();
		return matche(lst,
				llist(() -> reduced.unifies(init)),
				llist((current, tail) ->
						reducer.apply(next, init, current)
								.and(Goal.defer(() -> foldLeft(tail, next, reduced, reducer)))));
	}

	@Override
	public String toString() {
		String items = elements()
				.map(Objects::toString)
				.collect(Collectors.joining(", "));
		return String.format("(%s%s)",
				items,
				openTail()
						.map(tail -> " . " + tail)
						.orElse(""));
	}

}
