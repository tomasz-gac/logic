package com.tgac.logic;

import com.tgac.functional.Exceptions;
import com.tgac.functional.Reference;
import com.tgac.functional.Streams;
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
import io.vavr.control.Try;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.StreamSupport;

import static com.tgac.functional.Exceptions.throwingBiOp;
import static com.tgac.functional.recursion.Recur.done;
import static com.tgac.functional.recursion.Recur.recur;
import static com.tgac.logic.LVal.lval;
import static io.vavr.Predicates.not;

/**
 * @author TGa
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MiniKanren {

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

	private static <T> Package extendNoCheck(Package s, LVar<T> lhs, Unifiable<T> rhs) {
		return s.put(lhs, rhs);
	}

	public static <T> Unifiable<T> walk(Package s, Unifiable<T> v) {
		if (v.asVal().isDefined()) {
			return v;
		}
		if (s.get(v.getVar()).isEmpty()) {
			// it's important to return the same object
			// because we test with == to see if var is bound
			return v;
		}
		Unifiable<T> result = v;
		while (s.get(result.getVar()).isDefined()) {
			result = s.get(result.getVar()).get();
			if (result.asVal().isDefined()) {
				break;
			}
		}
		return result;
	}

	public static <T> Boolean occursCheck(Package s, LVar<T> x, Unifiable<T> v) {
		return walk(s, v).asVar()
				.map(vv -> vv == x)
				.getOrElse(false);
	}

	public static <T> Package extend(Package s, LVar<T> lhs, Unifiable<T> rhs) {
		return occursCheck(s, lhs, rhs) ?
				s :
				extendNoCheck(s, lhs, rhs);
	}

	private interface Extender {
		<T> Package apply(Package s, LVar<T> lhs, Unifiable<T> rhs);
	}

	private static <T> Option<Package> unify(
			Extender extend,
			Package s,
			Unifiable<T> lhs,
			Unifiable<T> rhs) {

		Unifiable<T> l = walk(s, lhs);
		Unifiable<T> r = walk(s, rhs);
		if (l.equals(r)) {
			// it's important to return the same object when l equals r
			// because we test with == to see if substitution already exists
			return Option.of(s);
		}

		return l.asVar().map(lVar -> r.asVar()
						.map(rVar -> extendNoCheck(s, lVar, rVar))
						.map(Option::of)
						.getOrElse(() -> Option.of(extend.apply(s, lVar, r))))
				.orElse(() -> r.asVar()
						.map(rVar -> extend.apply(s, rVar, l))
						.map(Option::of))
				.orElse(() -> zip(l.asVal(), r.asVal())
						.flatMap(MiniKanren::toIterable)
						.map(lr -> unifyIterable(extend, s, lr._1, lr._2)))
				.orElse(() -> zip(
						l.asVal().flatMap(MiniKanren::<T>asLList),
						r.asVal().flatMap(MiniKanren::<T>asLList))
						.filter(lr -> !lr._1.isEmpty() && !lr._2.isEmpty())
						.map(lr -> unifyLList2(extend, s, lr._1, lr._2)))
				.getOrElse(Option::none);
	}

	private static <T> Option<Package> unifyLList2(
			Extender extend,
			Package s,
			LList<T> lhs, LList<T> rhs) {
		Package currentState = s;
		while (true) {
			if (lhs.isEmpty() || rhs.isEmpty()) {
				return unify(extend, currentState, lval(lhs), lval(rhs));
			}
			// unify heads
			Option<Package> tmp = unify(extend, currentState, lhs.getHead(), rhs.getHead());
			if (tmp.isEmpty()) {
				return Option.none();
			}
			currentState = tmp.get();

			Unifiable<LList<T>> lTail = walk(currentState, lhs.getTail());
			Unifiable<LList<T>> rTail = walk(currentState, rhs.getTail());
			if (lTail.asVar().isDefined()) {
				return unify(extend, currentState, lTail, rTail);
			}

			if (rTail.asVar().isDefined()) {
				return unify(extend, currentState, lTail, rTail);
			}

			lhs = lTail.get();
			rhs = rTail.get();
		}
	}

	private static <T> Option<Package> unifyIterable(Extender extender, Package s, Iterable<Object> l, Iterable<Object> r) {
		if (!l.iterator().hasNext() && r.iterator().hasNext()) {
			return Option.of(s);
		}
		if (toJavaStream(l).count() != toJavaStream(r).count()) {
			return Option.none();
		} else {
			return Streams.zip(toJavaStream(l), toJavaStream(r), Tuple::of)
					// Because Tuples are treated as iterable
					// some of their elements may not be unifiable.
					// For those, we're wrapping them as Val to process them anyway
					.map(p -> p.map(applyOnBoth(MiniKanren::<T>wrapUnifiable)))
					.reduce(Option.of(s),
							(state, unifiedItems) ->
									state.flatMap(s1 -> unify(extender, s1, unifiedItems._1, unifiedItems._2)),
							throwingBiOp(UnsupportedOperationException::new));
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> Unifiable<T> wrapUnifiable(Object v) {
		if (v instanceof Unifiable) {
			return (Unifiable<T>) v;
		} else {
			return (Unifiable<T>) lval(v);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> Option<Iterable<T>> asIterable(Object v) {
		return v instanceof Iterable ?
				Try.of(() -> (Iterable<T>) v).toOption() :
				Option.none();
	}

	@SuppressWarnings("unchecked")
	public static <T> Option<LList<T>> asLList(Object v) {
		return v instanceof LList ?
				Try.of(() -> (LList<T>) v).toOption() :
				Option.none();
	}

	public static <T> Option<Package> unify(Package s, Unifiable<T> lhs, Unifiable<T> rhs) {
		return unify(MiniKanren::extend, s, lhs, rhs)
				.flatMap(s1 -> verifyUnify(s1, s));
	}

	public static <T> Option<Package> unifyUnsafe(Package s, Unifiable<T> lhs, Unifiable<T> rhs) {
		return unify(MiniKanren::extendNoCheck, s, lhs, rhs)
				.flatMap(s1 -> verifyUnify(s1, s));
	}

	public static <T> Recur<Unifiable<T>> walkAll(Package s, Unifiable<T> u) {
		return done(walk(s, u))
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

	private static <T> Recur<Unifiable<T>> walkIterable(Package s, Iterable<Object> iterable) {
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

	private static <T> Option<Recur<Unifiable<T>>> walkTuple(Package s, Iterable<Object> tuple) {
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

	public static <T> Recur<Unifiable<T>> reify(Package s, Unifiable<T> item) {
		return Recur.zip(
						walkAll(s.withoutConstraints(), item),
						walkAllConstraints(s.getSConstraints(), s))
				.flatMap(vc -> reifyS(Package.empty(), vc._1)
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
			Package s) {
		return constraints.toJavaStream()
				.map(c -> walkAllConstraint(s, c)
						.map(java.util.stream.Stream::of))
				.reduce((l, r) -> Recur.zip(l, r)
						.map(lr -> lr.apply(java.util.stream.Stream::concat)))
				.orElseGet(() -> done(java.util.stream.Stream.empty()))
				.map(stream -> stream.collect(List.collector()));
	}
	private static Recur<HashMap<LVar<?>, Unifiable<?>>> walkAllConstraint(Package s, HashMap<LVar<?>, Unifiable<?>> c) {
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
	public static Recur<Package> reifyS(Package s, Unifiable<?> val) {
		return walkAll(s, val)
				.flatMap(v -> v.asVar()
						.map(u -> extend(s, (LVar<Object>) u, LVar.lvar("_." + s.size())))
						.map(Recur::done)
						.orElse(() -> v.asVal()
								.flatMap(w -> asIterable(w)
										.orElse(() -> tupleAsIterable(w)))
								.map(it -> reifyIterable(s, it)))
						.orElse(() -> v.asVal()
								.flatMap(w -> Types.cast(w, LList.class))
								.map(llist -> reifyLList(s, llist)))
						.getOrElse(done(s)));
	}
	private static Recur<Package> reifyLList(Package s, LList<?> llist) {
		if (llist.isEmpty()) {
			return done(s);
		} else {
			return reifyS(s, llist.getHead())
					.flatMap(s1 -> reifyS(s1, llist.getTail()));
		}
	}

	private static Recur<Package> reifyIterable(Package s, Iterable<Object> l) {
		return toJavaStream(l)
				.map(MiniKanren::wrapUnifiable)
				.reduce(done(s),
						(state, item) ->
								state.flatMap(s1 -> reifyS(s1, item)),
						throwingBiOp(UnsupportedOperationException::new));
	}

	public static <T> Option<Package> separate(Package s, Unifiable<T> lhs, Unifiable<T> rhs) {
		return verifySeparate(
				unify(s.withoutConstraints(), lhs, rhs),
				s);
	}

	private static Option<Package> verifySeparate(Option<Package> s, Package a) {
		if (s.isEmpty()) {
			return Option.of(a);
		} else {
			Package result = s.get();
			if (result.getSubstitutions() == a.getSubstitutions()) {
				return Option.none();
			} else {
				return Option.of(
						a.putSepConstraint(prefixS(a.getSubstitutions(), result.getSubstitutions())));
			}
		}
	}

	public static HashMap<LVar<?>, Unifiable<?>> prefixS(
			HashMap<LVar<?>, Unifiable<?>> s,
			HashMap<LVar<?>, Unifiable<?>> extendedS) {
		return extendedS.filter(kv -> !s.keySet().contains(kv._1));
	}

	private static Option<Package> verifyUnify(Package newPackage, Package a) {
		// substitutions haven't changed, so constraints are not violated
		if (newPackage.getSubstitutions() == a.getSubstitutions()) {
			return Option.of(a);
		} else {
			return verifyAndSimplifyConstraints(a.getSConstraints(), List.empty(), newPackage.getSubstitutions())
					// TODO : verify
					.map(c -> Package.of(newPackage.getSubstitutions(), c, a.getDomains(), a.getConstraints()));
		}
	}

	private static Option<List<HashMap<LVar<?>, Unifiable<?>>>> verifyAndSimplifyConstraints(
			List<HashMap<LVar<?>, Unifiable<?>>> constraints,
			List<HashMap<LVar<?>, Unifiable<?>>> newConstraints,
			HashMap<LVar<?>, Unifiable<?>> s) {
		if (constraints.isEmpty()) {
			return Option.of(newConstraints);
		} else {
			return constraints.toJavaStream()
					.reduce(Option.of(Tuple.of(s, newConstraints)),
							(acc, c) -> acc.flatMap(stateAndNewConstraints ->
									verificationStep(stateAndNewConstraints._1, stateAndNewConstraints._2, c)),
							throwingBiOp(UnsupportedOperationException::new))
					.map(acc -> acc._2);
		}
	}

	private static Option<Tuple2<
			HashMap<LVar<?>, Unifiable<?>>,
			List<HashMap<LVar<?>, Unifiable<?>>>>> verificationStep(
			HashMap<LVar<?>, Unifiable<?>> s,
			List<HashMap<LVar<?>, Unifiable<?>>> newConstraints,
			HashMap<LVar<?>, Unifiable<?>> constraint) {
		Option<HashMap<LVar<?>, Unifiable<?>>> unification = unifyConstraints(constraint, s);
		if (unification.isDefined()) {
			return unification
					// if unification succeeds without extending s
					// then all simultaneous separateness constraints
					// are violated, so we fail the substitution
					.filter(s1 -> s != s1)
					// if unification succeeds by extending s,
					// then some simultaneous constraints are not violated,
					// and we append these constraints to list.
					// This way, we simplify constraint store on the fly
					.map(s1 -> Tuple.of(s, newConstraints.append(prefixS(s, s1))));
		} else {
			// if unification fails, then constraint is redundant
			// because substitutions already contain bound values
			// that are consistent with it
			return Option.of(Tuple.of(s, newConstraints));
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
	private static Option<HashMap<LVar<?>, Unifiable<?>>> unifyConstraints(
			HashMap<LVar<?>, Unifiable<?>> simultaneousConstraints,
			HashMap<LVar<?>, Unifiable<?>> s) {
		return simultaneousConstraints.toJavaStream()
				.map(t -> t.map(applyOnBoth(Unifiable::getObjectUnifiable)))
				.reduce(Option.of(s),
						(acc, lr) -> acc.flatMap(s1 ->
								// This cannot recurse deeply via verifyUnify
								// because there are no constraints in the package
								unify(Package.empty().extendS(s1), lr._1, lr._2)
										.map(Package::getSubstitutions)),
						throwingBiOp(UnsupportedOperationException::new));
	}

	private static Recur<List<HashMap<LVar<?>, Unifiable<?>>>> purify(
			List<HashMap<LVar<?>, Unifiable<?>>> c,
			Package r) {
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
			Package r) {
		if (constraints.isEmpty()) {
			return done(HashMap.empty());
		} else {
			return Recur.zip(done(anyVar(constraints.head()._1, r)),
							done(anyVar(constraints.head()._2, r)))
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
	public static Boolean anyVar(Unifiable<?> v, Package r) {
		return v.asVar()
				.map(lvar -> walk(r, lvar) == lvar)
				.getOrElse(done(false));
	}

	private static Boolean isConstraintSubsumed(
			HashMap<LVar<?>, Unifiable<?>> constraints,
			List<HashMap<LVar<?>, Unifiable<?>>> accConstraints) {
		return accConstraints.toJavaStream()
				.reduce(false,
						(r, v) -> r || unifyConstraints(v, constraints)
								.filter(c -> c == constraints)
								.map(__ -> true)
								.getOrElse(() -> false),
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
							done(isConstraintSubsumed(constraints.head(), constraintAcc)),
							// constraint subsumed by previously processed
							done(isConstraintSubsumed(constraints.head(), constraints.tail())))
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

	public static Option<Iterable<Object>> tupleAsIterable(Object tuple) {
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
	public static <T> T tupleFromArray(Object... args) {
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