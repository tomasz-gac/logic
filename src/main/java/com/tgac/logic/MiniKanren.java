package com.tgac.logic;

import com.tgac.functional.Reference;
import com.tgac.functional.Streams;
import com.tgac.functional.exceptions.Exceptions;
import com.tgac.functional.recursion.MRecur;
import com.tgac.functional.recursion.Recur;
import com.tgac.functional.reflection.Types;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.Tuple4;
import io.vavr.Tuple5;
import io.vavr.Tuple6;
import io.vavr.Tuple7;
import io.vavr.Tuple8;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.collection.PriorityQueue;
import io.vavr.collection.Queue;
import io.vavr.collection.Seq;
import io.vavr.collection.Stream;
import io.vavr.collection.Tree;
import io.vavr.collection.TreeMap;
import io.vavr.collection.TreeSet;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.Arrays;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.StreamSupport;

import static com.tgac.functional.exceptions.Exceptions.throwingBiOp;
import static com.tgac.logic.LVal.lval;
import static com.tgac.functional.recursion.MRecur.mdone;
import static com.tgac.functional.recursion.MRecur.mrecur;
import static com.tgac.functional.recursion.MRecur.none;
import static com.tgac.functional.recursion.MRecur.ofRecur;
import static com.tgac.functional.recursion.Recur.done;
import static com.tgac.functional.recursion.Recur.recur;
import static io.vavr.Predicates.not;

/**
 * @author TGa
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MiniKanren {
	@Value
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE, staticName = "of")
	public static class Substitutions {
		// substitutions
		HashMap<LVar<?>, Unifiable<?>> s;
		// constraints
		List<HashMap<LVar<?>, Unifiable<?>>> c;
		public static Substitutions empty() {
			return new Substitutions(HashMap.empty(), List.empty());
		}
		public static Substitutions ofS(HashMap<LVar<?>, Unifiable<?>> s) {
			return new Substitutions(s, List.empty());
		}

		<T> Substitutions put(LVar<T> key, Unifiable<T> value) {
			return Substitutions.of(s.put(key, value), c);
		}

		Substitutions putConstraint(HashMap<LVar<?>, Unifiable<?>> constraint) {
			return Substitutions.of(s, c.prepend(constraint));
		}

		@SuppressWarnings("unchecked")
		<T> Option<Unifiable<T>> get(LVar<T> v) {
			return s.get(v).map(w -> (Unifiable<T>) w);
		}

		public long size() {
			return s.size();
		}

		public Substitutions withoutConstraints() {
			return Substitutions.of(s, List.empty());
		}
	}

	private static final AtomicReference<Stream<Tuple2<Class<?>, Collector<?, ?, ?>>>> COLLECTORS =
			new AtomicReference<>(
					Stream.of(
							Tuple.of(Array.class, Array.collector()),
							Tuple.of(List.class, List.collector()),
							Tuple.of(PriorityQueue.class, PriorityQueue.collector()),
							Tuple.of(Queue.class, Queue.collector()),
							Tuple.of(Stream.class, Stream.collector()),
							Tuple.of(Vector.class, Vector.collector()),
							Tuple.of(HashSet.class, HashSet.collector()),
							Tuple.of(LinkedHashSet.class, LinkedHashSet.collector()),
							Tuple.of(TreeSet.class, TreeSet.collector()),
							Tuple.of(HashMap.class, HashMap.collector()),
							Tuple.of(LinkedHashMap.class, LinkedHashMap.collector()),
							Tuple.of(TreeMap.class, TreeMap.collector()),
							Tuple.of(Tree.class, Tree.collector()),
							Tuple.of(Option.class, optionCollector())));

	public static void addCollector(Class<?> cls, Collector<?, ?, ?> collector) {
		COLLECTORS.getAndUpdate(s -> s.append(Tuple.of(cls, collector)));
	}

	@SuppressWarnings("unchecked")
	private static Option<Collector<Object, ?, ?>> getCollector(Iterable<Object> v) {
		return COLLECTORS.get()
				.find(t -> t._1.isInstance(v))
				.map(Tuple2::_2)
				.map(c -> (Collector<Object, ?, ?>) c);
	}

	private static <T> Recur<Substitutions> extendNoCheck(Substitutions s, LVar<T> lhs, Unifiable<T> rhs) {
		return done(s.put(lhs, rhs));
	}

	public static <T> Recur<Unifiable<T>> walk(Substitutions s, Unifiable<T> v) {
		return v.asVar()
				.flatMap(s::get)
				.map(rhs -> rhs.asVar()
						.map(u -> recur(() -> walk(s, u)))
						.getOrElse(() -> done(rhs)))
				// it's important to return the same object
				// because we test with == to see if var is bound
				.getOrElse(() -> done(v));
	}

	public static <T> Recur<Boolean> occursCheck(Substitutions s, LVar<T> x, Unifiable<T> v) {
		return walk(s, v)
				.map(result -> result.asVar()
						.map(vv -> vv == x)
						.getOrElse(false));
	}

	public static <T> Recur<Substitutions> extend(Substitutions s, LVar<T> lhs, Unifiable<T> rhs) {
		return occursCheck(s, lhs, rhs)
				.flatMap(occurs -> occurs ? done(s) :
						extendNoCheck(s, lhs, rhs));
	}

	private interface Extender {
		<T> Recur<Substitutions> apply(Substitutions s, LVar<T> lhs, Unifiable<T> rhs);
	}

	private static <T> MRecur<Substitutions> unify(
			Extender extend,
			Substitutions s,
			Unifiable<T> lhs,
			Unifiable<T> rhs) {
		return ofRecur(Recur.zip(walk(s, lhs), walk(s, rhs)))
				.flatMap(results -> results
						// it's important to return the same object when l equals r
						// because we test with == to see if substitution already exists
						.apply((l, r) -> l.equals(r) ? mdone(s) :
								l.asVar().map(lVar -> r.asVar()
												.map(rVar -> extendNoCheck(s, lVar, rVar))
												.map(MRecur::ofRecur)
												.getOrElse(() -> ofRecur(extend.apply(s, lVar, r))))
										.orElse(() -> r.asVar()
												.map(rVar -> extend.apply(s, rVar, l))
												.map(MRecur::ofRecur))
										.orElse(() -> zip(l.asVal(), r.asVal())
												.flatMap(MiniKanren::toIterable)
												.map(lr -> unifyIterable(extend, s, lr._1, lr._2)))
										.orElse(() -> zip(l.asVal().flatMap(MiniKanren::<T>asLList),
												r.asVal().flatMap(MiniKanren::<T>asLList))
												.filter(lr -> !lr._1.isEmpty() && !lr._2.isEmpty())
												.map(lr -> mrecur(() -> unify(extend, s, lr._1.getHead(), lr._2.getHead()))
														.flatMap(s1 -> mrecur(() -> unify(extend, s1,
																Types.<Unifiable<T>> castAs(
																		lr._1.getTail(), Unifiable.class).get(),
																Types.<Unifiable<T>> castAs(
																		lr._2.getTail(), Unifiable.class).get())))))
										.getOrElse(MRecur::none)));
	}

	private static <T> MRecur<Substitutions> unifyIterable(Extender extender, Substitutions s, Iterable<Object> l, Iterable<Object> r) {
		if (!l.iterator().hasNext() && r.iterator().hasNext()) {
			return mdone(s);
		}
		if (toJavaStream(l).count() != toJavaStream(r).count()) {
			return none();
		} else {
			return Streams.zip(toJavaStream(l), toJavaStream(r), Tuple::of)
					// Because Tuples are treated as iterable
					// some of their elements may not be unifiable.
					// For those, we're wrapping them as Val to process them anyway
					.map(p -> p.map(applyOnBoth(MiniKanren::<T>wrapUnifiable)))
					.reduce(mdone(s),
							(state, unifiedItems) ->
									state.flatMap(s1 -> unify(extender, s1, unifiedItems._1, unifiedItems._2)),
							throwingBiOp(UnsupportedOperationException::new));
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> Unifiable<T> wrapUnifiable(Object v) {
		if (v instanceof Unifiable) {
			return (Unifiable<T>) v;
		} else {
			return (Unifiable<T>) lval(v);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> Option<Iterable<T>> asIterable(Object v) {
		if (v instanceof Iterable) {
			return Option.of((Iterable<T>) v);
		} else {
			return Option.none();
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> Option<LList<T>> asLList(Object v) {
		if (v instanceof LList) {
			return Option.of((LList<T>) v);
		} else {
			return Option.none();
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> Option<Option<T>> asOption(Object v) {
		if (v instanceof Option) {
			return Option.of((Option<T>) v);
		} else {
			return Option.none();
		}
	}

	public static <T> MRecur<Substitutions> unify(Substitutions s, Unifiable<T> lhs, Unifiable<T> rhs) {
		return unify(MiniKanren::extend, s, lhs, rhs)
				.flatMap(s1 -> verifyUnify(s1, s));
	}

	public static <T> MRecur<Substitutions> unifyUnsafe(Substitutions s, Unifiable<T> lhs, Unifiable<T> rhs) {
		return unify(MiniKanren::extendNoCheck, s, lhs, rhs)
				.flatMap(s1 -> verifyUnify(s1, s));
	}

	public static <T> Recur<Unifiable<T>> walkAll(Substitutions s, Unifiable<T> u) {
		return walk(s, u)
				.flatMap(v -> v.asVar()
						.map(Recur::<Unifiable<T>>done)
						.orElse(() -> v.asVal()
								.flatMap(MiniKanren::asIterable)
								.map(t -> walkIterable(s, t)))
						.orElse(() -> v.asVal()
								.flatMap(MiniKanren::<T>asLList)
								.filter(not(LList::isEmpty))
								.map(c -> Recur.zip(
												recur(() -> walkAll(s, c.getHead())),
												recur(() -> walkAll(s, c.getTail())))
										.map(ht -> ht.apply(LList::of).get())
										.map(w -> Types.<T> castAs(w, Object.class).get())
										.map(LVal::lval)))
						.orElse(() -> v.asVal()
								.flatMap(MiniKanren::tupleAsIterable)
								.flatMap(t -> walkTuple(s, t)))
						.getOrElse(done(v)));
	}

	private static <T> Recur<Unifiable<T>> walkIterable(Substitutions s, Iterable<Object> iterable) {
		Collector<Object, ?, ?> collector = MiniKanren.getCollector(iterable)
				.getOrElseThrow(Exceptions.format(RuntimeException::new,
						"Unsupported iterable type: %s", iterable));

		return toJavaStream(iterable)
				.map(u -> walkAll(s, wrapUnifiable(u))
						.map(w -> (u instanceof Unifiable) ?
								w : w.asVal().get())
						.map(Stream::of))
				.reduce((acc, item) -> Recur.zip(acc, item.map(Stream::head))
						.map(lr -> lr.apply(Stream::append)))
				.map(r -> r.map(str -> str.collect(collector))
						.map(MiniKanren::<T>wrapUnifiable))
				.orElseGet(() -> done(wrapUnifiable(iterable)));
	}

	private static <T> Option<Recur<Unifiable<T>>> walkTuple(Substitutions s, Iterable<Object> tuple) {
		return toJavaStream(tuple)
				// walkAll accepts Unifiable,
				// but some elements may be regular types.
				// We're wrapping those types in a Val
				.map(e -> walkAll(s, wrapUnifiable(e))
						// Here, we unwrap the Unifiable,
						// if the original type was not Unifiable
						// so that we can reconstruct the original tuple
						.map(u -> e instanceof Unifiable ?
								u : u.asVal().get())
						.map(Stream::of))
				.reduce((acc, item) -> Recur.zip(acc, item.map(Stream::head))
						.map(lr -> lr.apply(Stream::append)))
				.map(r -> r.map(stream -> stream
								.collect(Array.collector())
								.toJavaArray())
						.map(MiniKanren::tupleFromArray)
						.map(LVal::lval)
						.map(MiniKanren::<T>castUnifiable))
				.map(Option::of)
				.orElseGet(Option::none);
	}

	public static <T> Recur<Unifiable<T>> reify(Substitutions s, Unifiable<T> item) {
		return Recur.zip(
						walkAll(s.withoutConstraints(), item),
						walkAllConstraints(s.getC(), s))
				.flatMap(vc -> reifyS(Substitutions.empty(), vc._1)
						.map(vc::append))
				.map(vcr -> vcr
						.map1(v -> walkAll(vcr._3, v))
						.map2(c -> purify(c, vcr._3)
								.flatMap(pc -> removeSubsumed(pc, List.empty()))
								.flatMap(rc -> walkAllConstraints(rc, vcr._3))))
				.flatMap(vcr -> Recur.zip(vcr._1, vcr._2))
				.map(vc -> vc._2.isEmpty() ?
						vc._1 :
						vc.apply(Constrained::of));
	}

	private static Recur<List<HashMap<LVar<?>, Unifiable<?>>>> walkAllConstraints(
			List<HashMap<LVar<?>, Unifiable<?>>> constraints,
			Substitutions s) {
		return constraints.toJavaStream()
				.map(c -> walkAllConstraint(s, c)
						.map(java.util.stream.Stream::of))
				.reduce((l, r) -> Recur.zip(l, r)
						.map(lr -> lr.apply(java.util.stream.Stream::concat)))
				.orElseGet(() -> done(java.util.stream.Stream.empty()))
				.map(stream -> stream.collect(List.collector()));
	}
	private static Recur<HashMap<LVar<?>, Unifiable<?>>> walkAllConstraint(Substitutions s, HashMap<LVar<?>, Unifiable<?>> c) {
		return c.toJavaStream()
				.map(valSub -> valSub.map(
								val -> walkAll(s, val)
										// this should be right since lhs of a substitution is unbound and unique
										.map(u -> u.asVar().get()),
								sub -> walkAll(s, sub))
						.apply(Recur::zip))
				.reduce(done(HashMap.empty()),
						(acc, v) -> Recur.zip(acc, v)
								.map(ms -> ms.apply(HashMap::put)),
						throwingBiOp(UnsupportedOperationException::new));
	}

	@SuppressWarnings("unchecked")
	public static Recur<Substitutions> reifyS(Substitutions s, Unifiable<?> val) {
		return walkAll(s, val)
				.flatMap(v -> v.asVar()
						.map(u -> extend(s, (LVar<Object>) u, LVar.lvar("_." + s.size())))
						.orElse(() -> v.asVal()
								.flatMap(w -> asIterable(w)
										.orElse(() -> tupleAsIterable(w)))
								.map(it -> reifyIterable(s, it)))
						.orElse(() -> v.asVal()
								.flatMap(w -> Types.cast(w, LList.class))
								.map(LList -> reifyLList(s, LList)))
						.getOrElse(done(s)));
	}
	private static Recur<Substitutions> reifyLList(Substitutions s, LList<?> llist) {
		if (llist.isEmpty()) {
			return done(s);
		} else {
			return reifyS(s, llist.getHead())
					.flatMap(s1 -> reifyS(s1, llist.getTail()));
		}
	}

	private static Recur<Substitutions> reifyIterable(Substitutions s, Iterable<Object> l) {
		return toJavaStream(l)
				.map(MiniKanren::wrapUnifiable)
				.reduce(done(s),
						(state, item) ->
								state.flatMap(s1 -> reifyS(s1, item)),
						throwingBiOp(UnsupportedOperationException::new));
	}

	public static <T> MRecur<Substitutions> separate(Substitutions s, Unifiable<T> lhs, Unifiable<T> rhs) {
		return MRecur.of(unify(s.withoutConstraints(), lhs, rhs)
				.getRecur()
				.map(s1 -> verifySeparate(s1, s)));
	}

	private static Option<Substitutions> verifySeparate(Option<Substitutions> s, Substitutions a) {
		if (s.isEmpty()) {
			return Option.of(a);
		} else {
			Substitutions result = s.get();
			if (result.getS() == a.getS()) {
				return Option.none();
			} else {
				return Option.of(
						a.putConstraint(prefixS(a.getS(), result.getS())));
			}
		}
	}

	private static HashMap<LVar<?>, Unifiable<?>> prefixS(
			HashMap<LVar<?>, Unifiable<?>> s,
			HashMap<LVar<?>, Unifiable<?>> extendedS) {
		return extendedS.filter(kv -> !s.keySet().contains(kv._1));
	}

	private static MRecur<Substitutions> verifyUnify(Substitutions newSubstitutions, Substitutions a) {
		// substitutions haven't changed, so constraints are not violated
		if (newSubstitutions.getS() == a.getS()) {
			return mdone(a);
		} else {
			return verifyAndSimplifyConstraints(a.getC(), List.empty(), newSubstitutions.getS())
					.ifElse(
							c -> mdone(Substitutions.of(newSubstitutions.getS(), c)),
							MRecur::none);
		}
	}

	private static MRecur<List<HashMap<LVar<?>, Unifiable<?>>>> verifyAndSimplifyConstraints(
			List<HashMap<LVar<?>, Unifiable<?>>> constraints,
			List<HashMap<LVar<?>, Unifiable<?>>> newConstraints,
			HashMap<LVar<?>, Unifiable<?>> s) {
		if (constraints.isEmpty()) {
			return mdone(newConstraints);
		} else {
			return unifyConstraints(constraints.head(), s)
					.ifElse(
							nextS -> mdone(nextS)
									// if unification succeeds without extending s
									// then all simultaneous separateness constraints
									// are violated, so we fail the substitution
									.filter(s1 -> s != s1)
									// if unification succeeds by extending s,
									// then some simultaneous constraints are not violated,
									// and we append these constraints to list.
									// This way, we simplify constraint store on the fly
									.flatMap(s1 -> verifyAndSimplifyConstraints(
											constraints.tail(),
											newConstraints.append(prefixS(s, s1)),
											s)),
							// if unification fails, then constraint is redundant
							// because substitutions already contain bound values
							// that are consistent with it
							() -> verifyAndSimplifyConstraints(constraints.tail(), newConstraints, s));
		}
	}

	/**
	 * Checks whether all constraints unify within s simultaneously.
	 *
	 * @param simultaneousConstraints
	 * 		List of constraints that must be simultaneously true
	 * @param s
	 * 		current substitution map
	 * @return s after unification
	 */
	private static MRecur<HashMap<LVar<?>, Unifiable<?>>> unifyConstraints(
			HashMap<LVar<?>, Unifiable<?>> simultaneousConstraints,
			HashMap<LVar<?>, Unifiable<?>> s) {
		return simultaneousConstraints.toJavaStream()
				.map(t -> t.map(applyOnBoth(Unifiable::getObjectUnifiable)))
				.reduce(mdone(s),
						(acc, lr) -> acc.flatMap(s1 ->
								unify(Substitutions.ofS(s1), lr._1, lr._2)
										.map(Substitutions::getS)),
						throwingBiOp(UnsupportedOperationException::new));
	}

	private static Recur<List<HashMap<LVar<?>, Unifiable<?>>>> purify(
			List<HashMap<LVar<?>, Unifiable<?>>> c,
			Substitutions r) {
		return c.toJavaStream()
				.map(cc -> purifySingle(cc, r)
						.map(java.util.stream.Stream::of))
				.reduce((acc, pc) -> Recur.zip(acc, pc)
						.map(lr -> lr.apply(java.util.stream.Stream::concat)))
				.orElseGet(() -> done(java.util.stream.Stream.empty()))
				.map(str -> str
						.filter(not(HashMap::isEmpty))
						.collect(List.collector()));
	}

	private static Recur<HashMap<LVar<?>, Unifiable<?>>> purifySingle(
			HashMap<LVar<?>, Unifiable<?>> constraints,
			Substitutions r) {
		if (constraints.isEmpty()) {
			return done(HashMap.empty());
		} else {
			return Recur.zip(anyVar(constraints.head()._1, r),
							anyVar(constraints.head()._2, r))
					.map(lr -> lr.apply(Boolean::logicalOr))
					.flatMap(unbound -> unbound ?
							purifySingle(constraints.tail(), r) :
							purifySingle(constraints.tail(), r)
									.map(c -> constraints.head().apply(c::put)));
		}
	}

	/**
	 * Checks whether any item within v is unbound within r
	 */
	private static Recur<Boolean> anyVar(Unifiable<?> v, Substitutions r) {
		return v.asVar()
				.map(lvar -> walk(r, lvar)
						.map(rv -> rv == lvar))
				.getOrElse(done(false));
	}

	private static Recur<Boolean> isConstraintSubsumed(
			HashMap<LVar<?>, Unifiable<?>> constraints,
			List<HashMap<LVar<?>, Unifiable<?>>> accConstraints) {
		return accConstraints.toJavaStream()
				.reduce(done(false),
						(acc, v) -> acc.flatMap(r -> r ?
								done(true) :
								unifyConstraints(v, constraints)
										// TODO: perhaps == ?
										.filter(c -> c == constraints)
										.map(__ -> true)
										.resumeWith(() -> false)),
						throwingBiOp(UnsupportedOperationException::new));
	}

	private static Recur<List<HashMap<LVar<?>, Unifiable<?>>>> removeSubsumed(
			List<HashMap<LVar<?>, Unifiable<?>>> constraints,
			List<HashMap<LVar<?>, Unifiable<?>>> constraintAcc) {
		if (constraints.isEmpty()) {
			return done(constraintAcc);
		} else {
			return Recur.zip(
							// constraint subsumes another existing constraint
							isConstraintSubsumed(constraints.head(), constraintAcc),
							// constraint subsumed by previously processed
							isConstraintSubsumed(constraints.head(), constraints.tail()))
					.map(lr -> lr.apply(Boolean::logicalOr))
					.flatMap(isSubsumed -> isSubsumed ?
							// skip this constraint
							removeSubsumed(constraints.tail(), constraintAcc) :
							// add to result and process next
							removeSubsumed(
									constraints.tail(),
									constraintAcc.prepend(constraints.head())));
		}
	}

	public static <A, B> BiFunction<A, A, Tuple2<B, B>> applyOnBoth(Function<A, B> f) {
		return (a, b) -> Tuple.of(f.apply(a), f.apply(b));
	}

	public static <A, B> Option<Tuple2<A, B>> zip(Option<A> a, Option<B> b) {
		return a.flatMap(av -> b.map(bv -> Tuple.of(av, bv)));
	}

	private static <T> Option<Tuple2<Iterable<Object>, Iterable<Object>>> toIterable(Tuple2<T, T> lr) {
		return lr.apply(MiniKanren::asIterablePair)
				.orElse(() -> tuplesAsIterable(lr));
	}

	private static <T> Option<Tuple2<Iterable<Object>, Iterable<Object>>> asIterablePair(T lhs, T rhs) {
		return Tuple.of(lhs, rhs)
				.map(applyOnBoth(MiniKanren::asIterable))
				.apply(MiniKanren::zip);
	}

	private static Option<Tuple2<Iterable<Object>, Iterable<Object>>> tuplesAsIterable(Tuple2<?, ?> items) {
		return items
				.apply(applyOnBoth(MiniKanren::tupleAsIterable))
				.apply(MiniKanren::zip);
	}

	private static Option<Iterable<Object>> tupleAsIterable(Object tuple) {
		return asIterable(tuple, Tuple.class, Tuple::toSeq)
				.orElse(() -> asIterable(tuple, Tuple2.class, Tuple2::toSeq))
				.orElse(() -> asIterable(tuple, Tuple3.class, Tuple3::toSeq))
				.orElse(() -> asIterable(tuple, Tuple4.class, Tuple4::toSeq))
				.orElse(() -> asIterable(tuple, Tuple5.class, Tuple5::toSeq))
				.orElse(() -> asIterable(tuple, Tuple6.class, Tuple6::toSeq))
				.orElse(() -> asIterable(tuple, Tuple7.class, Tuple7::toSeq))
				.orElse(() -> asIterable(tuple, Tuple8.class, Tuple8::toSeq));
	}

	@SuppressWarnings("unchecked")
	private static <T> T tupleFromArray(Object... args) {
		switch (args.length) {
			case 1:
				return (T) Tuple.of(args[0]);
			case 2:
				return (T) Tuple.of(args[0], args[1]);
			case 3:
				return (T) Tuple.of(args[0], args[1], args[2]);
			case 4:
				return (T) Tuple.of(args[0], args[1], args[2], args[3]);
			case 5:
				return (T) Tuple.of(args[0], args[1], args[2], args[3], args[4]);
			case 6:
				return (T) Tuple.of(args[0], args[1], args[2], args[3], args[4], args[5]);
			case 7:
				return (T) Tuple.of(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
			case 8:
				return (T) Tuple.of(args[0], args[1], args[2], args[3], args[4], args[5], args[5], args[7]);
			default:
				throw new IllegalArgumentException("Tuple too long: " + Arrays.toString(args));
		}
	}

	private static <T> Collector<T, ?, Option<T>> optionCollector() {
		return Collector.of(
				Reference::<T>empty,
				Reference::set,
				throwingBiOp(IllegalArgumentException::new),
				r -> Option.of(r.get()));
	}

	@SuppressWarnings("unchecked")
	private static <T> Unifiable<T> castUnifiable(Object v) {
		return (Unifiable<T>) v;
	}

	private static java.util.stream.Stream<Object> toJavaStream(Iterable<Object> it) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it.iterator(), 0), false);
	}

	@SuppressWarnings("unchecked")
	private static <T> Option<Iterable<Object>> asIterable(Object item, Class<T> cls, Function<T, Seq<?>> sequencer) {
		return Types.cast(item, cls)
				.map(sequencer)
				.map(v -> (Iterable<Object>) v);
	}
}
