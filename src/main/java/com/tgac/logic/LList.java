package com.tgac.logic;

import io.vavr.Predicates;
import io.vavr.collection.Array;
import io.vavr.collection.IndexedSeq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.IntFunction;
import java.util.stream.*;

import static com.tgac.logic.Goal.defer;
import static com.tgac.logic.LVal.lval;

/**
 * @author TGa
 */

@Value
@RequiredArgsConstructor
public class LList<A> {
	Unifiable<A> head;
	Unifiable<LList<A>> tail;

	public static <A> Unifiable<LList<A>> empty() {
		return new LList<A>(null, null).asVal();
	}

	public static <A> Unifiable<LList<A>> of(Unifiable<A> v) {
		return new LList<>(v, empty()).asVal();
	}

	public static <A> Unifiable<LList<A>> of(Unifiable<A> v, Unifiable<LList<A>> c) {
		return new LList<>(v, c).asVal();
	}

	@SafeVarargs
	public static <A> Unifiable<LList<A>> ofAll(A... vs) {
		return ofAll(Array.of(vs));
	}

	public static <A> Unifiable<LList<A>> ofAll(IndexedSeq<A> items) {
		return ofAll(items.size(), i -> lval(items.get(i)));
	}

	public static <A> Unifiable<LList<A>> ofAll(List<A> items) {
		return ofAll(items.size(), i -> lval(items.get(i)));
	}

	@SafeVarargs
	public static <A> Unifiable<LList<A>> ofAll(Unifiable<A>... items) {
		return ofAll(items.length, i -> items[i]);
	}

	public static <A> Unifiable<LList<A>> ofAll(int size, IntFunction<Unifiable<A>> getter) {
		return IntStream.range(0, size)
				.map(i -> size - i - 1)
				.mapToObj(getter)
				.map(LList::of)
				.map(Unifiable::get)
				.reduce((acc, v) -> LList.of(v.head, lval(acc)).get())
				.map(LVal::lval)
				.orElseGet(LList::empty);
	}
	public boolean isEmpty() {
		return Objects.isNull(head) && Objects.isNull(tail);
	}

	public Unifiable<LList<A>> asVal() {
		return lval(this);
	}

	public Stream<Either<LVar<LList<A>>, Unifiable<A>>> stream() {
		return StreamSupport.stream(
				Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED),
				false);
	}

	public Stream<A> toValueStream() {
		return stream()
				.map(Either::get)
				.map(Unifiable::get);
	}

	public static <A> Collector<Unifiable<A>, ?, Unifiable<LList<A>>> collector() {
		return Collector.<Unifiable<A>, ArrayList<Unifiable<A>>, Unifiable<LList<A>>> of(
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
		return lhs.unify(LList.empty()).and(rhs.unify(LList.empty()))
				.or(Goals.<A, LList<A>, B, LList<B>> exist(
						(lhsHead, lhsTail, rhsHead, rhsTail) ->
								lhs.unify(LList.of(lhsHead, lhsTail))
										.and(rhs.unify(LList.of(rhsHead, rhsTail)))
										.and(reduce.apply(
												zip.apply(lhsHead, rhsHead),
												defer(() -> zipReduce(lhsTail, rhsTail, zip, reduce))))));
	}

	@Override
	public String toString() {
		List<Either<LVar<LList<A>>, Unifiable<A>>> items =
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
						.filter(Predicates.not(List::isEmpty))
						.map(i -> i.get(items.size() - 1))
						.map(tail -> tail.fold(l -> " . " + l,
								r -> (delimitedItems.isEmpty() ?
										"" : ", ") + r))
						.getOrElse(""));
	}

	public Iterator<Either<LVar<LList<A>>, Unifiable<A>>> iterator() {
		Unifiable<LList<A>> that = this.asVal();
		return new Iterator<Either<LVar<LList<A>>, Unifiable<A>>>() {
			private Unifiable<LList<A>> tail = that;
			@Override
			public boolean hasNext() {
				return Objects.nonNull(tail) &&
						(tail.asVar().isDefined() ||
								!tail.asVal().filter(LList::isEmpty).isDefined());
			}
			@Override
			public Either<LVar<LList<A>>, Unifiable<A>> next() {
				Either<LVar<LList<A>>, Unifiable<A>> item =
						tail.asVal()
								.map(LList::getHead)
								.map(Either::<LVar<LList<A>>, Unifiable<A>>right)
								.getOrElse(() -> tail.asVar()
										.map(Either::<LVar<LList<A>>, Unifiable<A>>left)
										.get());
				tail = tail.asVal()
						.map(LList::getTail)
						.getOrElse(() -> null);
				return item;
			}
		};
	}
}
