package com.tgac.logic.unification;

import static com.tgac.logic.ckanren.CKanren.unify;
import static com.tgac.logic.goals.Matche.llist;
import static com.tgac.logic.goals.Matche.matche;
import static com.tgac.logic.unification.LVar.lvar;
import static io.vavr.Predicates.not;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import io.vavr.Function3;
import io.vavr.collection.Array;
import io.vavr.collection.IndexedSeq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.IntFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.Value;

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
		return ofAll(Array.of(vs));
	}

	public static <A> Unifiable<LList<A>> ofAll(IndexedSeq<A> items) {
		return ofAll(items.size(), i -> LVal.lval(items.get(i)));
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

	public Stream<Either<Term<LList<A>>, Term<A>>> stream() {
		return StreamSupport.stream(
				Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED),
				false);
	}

	public Stream<A> toValueStream() {
		return stream()
				.map(Either::get)
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

	public static <A> Goal lasto(
			Unifiable<LList<A>> lst,
			Unifiable<A> last) {
		return matche(lst,
				llist((a) -> last.unifies(a)),
				llist((a, b, d) ->
						Goal.defer(() -> lasto(LList.of(b, d), last))));
	}

	@Override
	public String toString() {
		List<Either<Term<LList<A>>, Term<A>>> items =
				stream()
						.collect(Collectors.toList());
		String delimitedItems = IntStream.range(0, items.size() - 1)
				.mapToObj(items::get)
				.map(Either::get)
				.map(Objects::toString)
				.collect(Collectors.joining(", "));
		return String.format("(%s%s)",
				delimitedItems,
				Option.of(items)
						.filter(not(List::isEmpty))
						.map(i -> i.get(items.size() - 1))
						.map(tail -> tail.fold(l -> " . " + l,
								r -> (delimitedItems.isEmpty() ?
										"" : ", ") + r))
						.getOrElse(""));
	}

	public Iterator<Either<Term<LList<A>>, Term<A>>> iterator() {
		Term<LList<A>> that = this.asVal();
		return new Iterator<Either<Term<LList<A>>, Term<A>>>() {
			private Term<LList<A>> tail = that;

			@Override
			public boolean hasNext() {
				return Objects.nonNull(tail) &&
						!tail.asVal().filter(LList::isEmpty).isDefined();
			}

			@Override
			public Either<Term<LList<A>>, Term<A>> next() {
				// a non-val tail is a dangling hole: the list is improper
				Either<Term<LList<A>>, Term<A>> item =
						tail.asVal()
								.map(LList::getHead)
								.map(Either::<Term<LList<A>>, Term<A>>right)
								.getOrElse(() -> Either.left(tail));
				tail = tail.asVal()
						.map(LList::getTail)
						.getOrElse(() -> null);
				return item;
			}
		};
	}
}
