package com.tgac.logic.unification;

import static com.tgac.functional.Exceptions.throwingBiOp;
import static com.tgac.functional.fibers.MFiber.mdone;
import static com.tgac.functional.fibers.MFiber.mdefer;
import static com.tgac.functional.fibers.MFiber.none;
import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.functional.fibers.Fiber.defer;
import static com.tgac.logic.unification.LVal.lval;
import static io.vavr.Predicates.not;

import com.tgac.functional.Exceptions;
import com.tgac.functional.Reference;
import com.tgac.functional.Streams;
import com.tgac.functional.fibers.MFiber;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.reflection.Types;
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
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.collection.PriorityQueue;
import io.vavr.collection.Queue;
import io.vavr.collection.Tree;
import io.vavr.collection.TreeMap;
import io.vavr.collection.TreeSet;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author TGa
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MiniKanren {

	private static final AtomicReference<List<Tuple2<Class<?>, Collector<?, ?, ?>>>> COLLECTORS =
			new AtomicReference<>(
					List.of(
							Tuple.of(Array.class, Array.collector()),
							Tuple.of(List.class, List.collector()),
							Tuple.of(PriorityQueue.class, PriorityQueue.collector()),
							Tuple.of(Queue.class, Queue.collector()),
							Tuple.of(io.vavr.collection.Stream.class, io.vavr.collection.Stream.collector()),
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

	private static <T> Package extendNoCheck(Package s, LVar<T> lhs, Term<T> rhs) {
		return s.put(lhs, rhs);
	}

	public static <T> Term<T> walk(Package s, Term<T> v) {
		return s.walk(v);
	}

	/**
	 * Renders a value for a trace label: a {@link Term} is walked to its current
	 * binding, anything else is printed as-is. Used by goal labels so a trace
	 * shows arguments substituted rather than as raw variable names.
	 */
	public static String format(Package s, Object o) {
		return o instanceof Term ? walk(s, (Term<?>) o).toString() : String.valueOf(o);
	}

	public static <T> Boolean occursCheck(Package s, LVar<T> x, Term<T> v) {
		return walk(s, v).asVar()
				.map(vv -> vv == x)
				.getOrElse(false);
	}

	public static <T> Package extend(Package s, LVar<T> lhs, Term<T> rhs) {
		return occursCheck(s, lhs, rhs) ?
				s :
				extendNoCheck(s, lhs, rhs);
	}

	private interface Extender {
		<T> Package apply(Package s, LVar<T> lhs, Term<T> rhs);
	}

	private static <T> MFiber<Package> unify(
			Extender extend,
			Package s,
			Term<T> lhs,
			Term<T> rhs) {
		Term<T> l = walk(s, lhs);
		Term<T> r = walk(s, rhs);

		// reified terms are solver output; meeting one here is a programming error
		// that the type system cannot catch when it is nested inside a value
		if (l.asReified().isDefined() || r.asReified().isDefined()) {
			throw new IllegalStateException(
					"Reified terms cannot re-enter unification: " + l + " ≣ " + r);
		}

		// it's important to return the same object when l equals r
		// because we test with == to see if substitution already exists
		if (l.equals(r)) {
			return mdone(s);
		}

		return l.asVar().map(lVar -> r.asVar()
						.map(rVar -> extendNoCheck(s, lVar, rVar))
						.map(MFiber::mdone)
						.getOrElse(() -> mdone(extend.apply(s, lVar, r))))
				.orElse(() -> r.asVar()
						.map(rVar -> extend.apply(s, rVar, l))
						.map(MFiber::mdone))
				.orElse(() -> zip(l.asVal(), r.asVal())
						.flatMap(MiniKanren::toIterable)
						.map(lr -> unifyIterable(extend, s, lr._1, lr._2)))
				.orElse(() -> zip(
						l.asVal().flatMap(MiniKanren::<T>asLList),
						r.asVal().flatMap(MiniKanren::<T>asLList))
						.filter(lr -> !lr._1.isEmpty() && !lr._2.isEmpty())
						.map(lr -> unifyLList(extend, s, lr._1, lr._2)))
				.orElse(() -> zip(
						l.asVal().flatMap(MiniKanren::<T>asLTree),
						r.asVal().flatMap(MiniKanren::<T>asLTree))
						.filter(lr -> !lr._1.isEmpty() && !lr._2.isEmpty())
						.map(lr -> unifyLTree(extend, s, lr._1, lr._2)))
				.getOrElse(MFiber::none);
	}

	private static <T> MFiber<Package> unifyLList(Extender extend, Package s, LList<T> l, LList<T> r) {
		return mdefer(() -> unify(extend, s, l.getHead(), r.getHead()))
				.flatMap(s1 -> unify(extend, s1, l.getTail(), r.getTail()));
	}

	private static <T> MFiber<Package> unifyLTree(Extender extend, Package s, LTree<T> l, LTree<T> r) {
		return mdefer(() -> unify(extend, s, l.getValue(), r.getValue()))
				.flatMap(s1 -> unify(extend, s1, l.getChildren(), r.getChildren()));
	}

	private static <T> MFiber<Package> unifyIterable(Extender extender, Package s, Iterable<Object> l, Iterable<Object> r) {
		if (!l.iterator().hasNext() && r.iterator().hasNext()) {
			return mdone(s);
		}
		if (toJavaStream(l).count() != toJavaStream(r).count()) {
			return none();
		} else {
			return Streams.zip(toJavaStream(l), toJavaStream(r), Tuple::of)
					// Because Tuples are treated as iterable
					// some of their elements may not be terms.
					// For those, we're wrapping them as Val to process them anyway
					.map(p -> p.map(applyOnBoth(MiniKanren::<T>wrapTerm)))
					.reduce(mdone(s),
							(state, unifiedItems) ->
									state.flatMap(s1 -> unify(extender, s1, unifiedItems._1, unifiedItems._2)),
							throwingBiOp(UnsupportedOperationException::new));
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> Term<T> wrapTerm(Object v) {
		if (v instanceof Term) {
			return (Term<T>) v;
		} else {
			return (Term<T>) lval(v);
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

	@SuppressWarnings("unchecked")
	public static <T> Option<LTree<T>> asLTree(Object v) {
		return v instanceof LTree ?
				Try.of(() -> (LTree<T>) v).toOption() :
				Option.none();
	}

	public static <T> MFiber<Package> unify(Package s, Term<T> lhs, Term<T> rhs) {
		return unify(MiniKanren::extend, s, lhs, rhs);
	}

	public static <T> MFiber<Package> unifyUnsafe(Package s, Term<T> lhs, Term<T> rhs) {
		return unify(MiniKanren::extendNoCheck, s, lhs, rhs);
	}

	public static <T> Fiber<Term<T>> walkAll(Package s, Term<T> u) {
		return done(walk(s, u))
				.flatMap(v -> v.asVar()
						.map(w -> Fiber.<Term<T>> done(w))
						.orElse(() -> MiniKanren.<T> mapStructure(v, e -> walkAll(s, e)))
						.getOrElse(done(v)));
	}

	/**
	 * Rebuild the structure of a term (collection, LList, LTree or tuple)
	 * with each component passed through the mapper. Empty when the term
	 * is not structural.
	 */
	private static <T> Option<Fiber<Term<T>>> mapStructure(
			Term<T> v,
			Function<Term<Object>, Fiber<Term<Object>>> mapper) {
		return v.asVal()
				.flatMap(MiniKanren::asIterable)
				.map(t -> MiniKanren.<T> mapIterable(t, mapper))
				.orElse(() -> v.asVal()
						.flatMap(MiniKanren::<T>asLList)
						.filter(not(LList::isEmpty))
						.map(c -> Fiber.zip(
										defer(() -> mapper.apply(c.getHead().getObjectTerm())),
										defer(() -> mapper.apply(c.getTail().getObjectTerm())))
								.map(ht -> LList.of(ht._1, MiniKanren.<LList<Object>> castTerm(ht._2)).get())
								.map(w -> Types.<T> castAs(w, Object.class).get())
								.map(LVal::lval)
								.map(MiniKanren::<T>castTerm)))
				.orElse(() -> v.asVal()
						.flatMap(MiniKanren::<T>asLTree)
						.filter(not(LTree::isEmpty))
						.map(c -> Fiber.zip(
										defer(() -> mapper.apply(c.getValue().getObjectTerm())),
										defer(() -> mapper.apply(c.getChildren().getObjectTerm())))
								.map(vc -> LTree.of(vc._1, MiniKanren.<LList<LTree<Object>>> castTerm(vc._2)).get())
								.map(w -> Types.<T> castAs(w, Object.class).get())
								.map(LVal::lval)
								.map(MiniKanren::<T>castTerm)))
				.orElse(() -> v.asVal()
						.flatMap(MiniKanren::tupleAsIterable)
						.map(t -> MiniKanren.<T> mapTuple(t, mapper)));
	}

	private static <T> Fiber<Term<T>> mapIterable(
			Iterable<Object> iterable,
			Function<Term<Object>, Fiber<Term<Object>>> mapper) {
		Collector<Object, ?, ?> collector = MiniKanren.getCollector(iterable)
				.getOrElseThrow(Exceptions.format(RuntimeException::new,
						"Unsupported iterable type: %s", iterable));

		return toJavaStream(iterable)
				.map(u -> mapper.apply(wrapTerm(u))
						.map(w -> (u instanceof Term) ?
								w : w.asVal().get()))
				.reduce(done(new ArrayList<>()),
						(Fiber<ArrayList<Object>> acc, Fiber<Object> item) -> Fiber.zip(acc, item)
								.map(lr -> {
									lr._1.add(lr._2);
									return lr._1;
								}),
						Exceptions.throwingBiOp(UnsupportedOperationException::new))
				.map(r -> r.stream().collect(collector))
				.map(MiniKanren::<T>wrapTerm);
	}

	private static <T> Fiber<Term<T>> mapTuple(
			Iterable<Object> tuple,
			Function<Term<Object>, Fiber<Term<Object>>> mapper) {
		return toJavaStream(tuple)
				// the mapper accepts Term,
				// but some elements may be regular types.
				// We're wrapping those types in a Val
				.map(e -> mapper.apply(wrapTerm(e))
						// Here, we unwrap the Term,
						// if the original type was not Term
						// so that we can reconstruct the original tuple
						.map(u -> e instanceof Term ?
								u : u.asVal().get()))
				.reduce(
						done(new ArrayList<>()),
						(acc, item) -> Fiber.zip(acc, item)
								.map(lr -> {
									lr._1.add(lr._2);
									return lr._1;
								}),
						Exceptions.throwingBiOp(UnsupportedOperationException::new))
				.map(ArrayList::toArray)
				.map(MiniKanren::tupleFromArray)
				.map(LVal::lval)
				.map(MiniKanren::castTerm);
	}

	/**
	 * Convert a reified term back into a solver term: canonical holes become
	 * fresh variables — holes sharing a name share the variable — and ground
	 * structure is preserved.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Fiber<Unifiable<T>> instantiate(Reified<T> term) {
		return instantiateTerm(term, new java.util.concurrent.ConcurrentHashMap<>())
				.map(t -> (Unifiable<T>) t);
	}

	private static <T> Fiber<Term<T>> instantiateTerm(
			Term<T> u,
			java.util.concurrent.ConcurrentMap<String, Term<Object>> fresh) {
		return u.asReified()
				.map(hole -> Fiber.<Term<T>> done((Term<T>) fresh.computeIfAbsent(
						hole.getName(), name -> LVar.lvar())))
				.orElse(() -> MiniKanren.<T> mapStructure(u, e -> instantiateTerm(e, fresh)))
				.getOrElse(done(u));
	}

	@SuppressWarnings("unchecked")
	public static <T> Fiber<Reified<T>> reify(Package s, Term<T> item) {
		// after renaming, every node is an LVal or a ReifiedVar — both Reified
		return walkAll(s, item)
				.flatMap(v -> reifyS(Package.empty(), v)
						.flatMap(rp -> walkAll(rp, v)))
				.map(v -> (Reified<T>) v);
	}

	@SuppressWarnings("unchecked")
	public static Fiber<Package> reifyS(Package s, Term<?> val) {
		// shallow walk only: a deep walk would rebuild structures with rename
		// vars substituted in, and nested calls would rename the rename vars
		return done(walk(s, val))
				.flatMap(v -> v.asVar()
						// a var that walked to something else is already renamed
						.map(u -> u == val ?
								extend(s, (LVar<Object>) u, ReifiedVar.of("_." + s.size())) :
								s)
						.map(Fiber::done)
						.orElse(() -> v.asVal()
								.flatMap(w -> asIterable(w)
										.orElse(() -> tupleAsIterable(w)))
								.map(it -> reifyIterable(s, it)))
						.orElse(() -> v.asVal()
								.flatMap(w -> Types.cast(w, LList.class))
								.map(llist -> reifyLList(s, llist)))
						.orElse(() -> v.asVal()
								.flatMap(w -> Types.cast(w, LTree.class))
								.map(ltree -> reifyLTree(s, ltree)))
						.getOrElse(done(s)));
	}

	private static Fiber<Package> reifyLList(Package s, LList<?> llist) {
		if (llist.isEmpty()) {
			return done(s);
		} else {
			return reifyS(s, llist.getHead())
					.flatMap(s1 -> reifyS(s1, llist.getTail()));
		}
	}

	private static Fiber<Package> reifyLTree(Package s, LTree<?> tree) {
		if (tree.isEmpty()) {
			return done(s);
		} else {
			return reifyS(s, tree.getValue())
					.flatMap(p -> reifyS(p, tree.getChildren()));
		}
	}

	private static Fiber<Package> reifyIterable(Package s, Iterable<Object> l) {
		return toJavaStream(l)
				.map(MiniKanren::wrapTerm)
				.reduce(done(s),
						(state, item) ->
								state.flatMap(s1 -> reifyS(s1, item)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	/**
	 * Check if two terms are alpha-equivalent (equivalent modulo variable renaming).
	 * Reification numbers holes canonically and reified vars carry value
	 * equality by name, so plain equality on the reified forms decides it.
	 */
	public static <T> Fiber<Boolean> alphaEquiv(Term<T> x, Term<T> y, Package s) {
		return reify(s, x).flatMap(xReified ->
			reify(s, y).map(xReified::equals));
	}

	public static HashMap<LVar<?>, Term<?>> prefixS(
			HashMap<LVar<?>, Term<?>> s,
			HashMap<LVar<?>, Term<?>> extendedS) {
		return extendedS.removeAll(s.keysIterator());
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
		Iterable<Object> result = asIterable(tuple, Tuple1.class, t -> Collections.singletonList(t._1));
		if (result != null) {
			return Option.of(result);
		}
		result = asIterable(tuple, Tuple2.class, t -> Arrays.asList(t._1, t._2));
		if (result != null) {
			return Option.of(result);
		}
		result = asIterable(tuple, Tuple3.class, t -> Arrays.asList(t._1, t._2, t._3));
		if (result != null) {
			return Option.of(result);
		}
		result = asIterable(tuple, Tuple4.class, t -> Arrays.asList(t._1, t._2, t._3, t._4));
		if (result != null) {
			return Option.of(result);
		}
		result = asIterable(tuple, Tuple5.class, t -> Arrays.asList(t._1, t._2, t._3, t._4, t._5));
		if (result != null) {
			return Option.of(result);
		}
		result = asIterable(tuple, Tuple6.class, t -> Arrays.asList(t._1, t._2, t._3, t._4, t._5, t._6));
		if (result != null) {
			return Option.of(result);
		}
		result = asIterable(tuple, Tuple7.class, t -> Arrays.asList(t._1, t._2, t._3, t._4, t._5, t._6, t._7));
		if (result != null) {
			return Option.of(result);
		}
		result = asIterable(tuple, Tuple8.class, t -> Arrays.asList(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8));
		if (result != null) {
			return Option.of(result);
		} else {
			return Option.none();
		}
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
				Exceptions.throwingBiOp(IllegalArgumentException::new),
				r -> Option.of(r.get()));
	}

	@SuppressWarnings("unchecked")
	private static <T> Term<T> castTerm(Object v) {
		return (Term<T>) v;
	}

	private static java.util.stream.Stream<Object> toJavaStream(Iterable<Object> it) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it.iterator(), 0), false);
	}

	@SuppressWarnings("unchecked")
	private static <T> Iterable<Object> asIterable(Object item, Class<T> cls, Function<T, Iterable<?>> sequencer) {
		return cls.isInstance(item) ?
				(Iterable<Object>) sequencer.apply((T) item) :
				null;
	}
}
