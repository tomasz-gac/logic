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

	private static <T> Package extendNoCheck(Package s, LVar<T> lhs, Unifiable<T> rhs) {
		return s.put(lhs, rhs);
	}

	public static <T> Unifiable<T> walk(Package s, Unifiable<T> v) {
		return s.walk(v);
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

	private static <T> MFiber<Package> unify(
			Extender extend,
			Package s,
			Unifiable<T> lhs,
			Unifiable<T> rhs) {
		Unifiable<T> l = walk(s, lhs);
		Unifiable<T> r = walk(s, rhs);

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

	@SuppressWarnings("unchecked")
	public static <T> Option<LTree<T>> asLTree(Object v) {
		return v instanceof LTree ?
				Try.of(() -> (LTree<T>) v).toOption() :
				Option.none();
	}

	public static <T> MFiber<Package> unify(Package s, Unifiable<T> lhs, Unifiable<T> rhs) {
		return unify(MiniKanren::extend, s, lhs, rhs);
	}

	public static <T> MFiber<Package> unifyUnsafe(Package s, Unifiable<T> lhs, Unifiable<T> rhs) {
		return unify(MiniKanren::extendNoCheck, s, lhs, rhs);
	}

	public static <T> Fiber<Unifiable<T>> walkAll(Package s, Unifiable<T> u) {
		return done(walk(s, u))
				.flatMap(v -> v.asVar()
						.map(Fiber::<Unifiable<T>>done)
						.orElse(() -> v.asVal()
								.flatMap(MiniKanren::asIterable)
								.map(t -> walkIterable(s, t)))
						.orElse(() -> v.asVal()
								.flatMap(MiniKanren::<T>asLList)
								.filter(not(LList::isEmpty))
								.map(c -> Fiber.zip(
												defer(() -> walkAll(s, c.getHead())),
												defer(() -> walkAll(s, c.getTail())))
										.map(ht -> ht.apply(LList::of).get())
										.map(w -> Types.<T> castAs(w, Object.class).get())
										.map(LVal::lval)))
						.orElse(() -> v.asVal()
								.flatMap(MiniKanren::<T>asLTree)
								.filter(not(LTree::isEmpty))
								.map(c -> Fiber.zip(
												defer(() -> walkAll(s, c.getValue())),
												defer(() -> walkAll(s, c.getChildren())))
										.map(vc -> vc.apply(LTree::of).get())
										.map(w -> Types.<T> castAs(w, Object.class).get())
										.map(LVal::lval)))
						.orElse(() -> v.asVal()
								.flatMap(MiniKanren::tupleAsIterable)
								.map(t -> walkTuple(s, t)))
						.getOrElse(done(v)));
	}

	private static <T> Fiber<Unifiable<T>> walkIterable(Package s, Iterable<Object> iterable) {
		Collector<Object, ?, ?> collector = MiniKanren.getCollector(iterable)
				.getOrElseThrow(Exceptions.format(RuntimeException::new,
						"Unsupported iterable type: %s", iterable));

		return toJavaStream(iterable)
				.map(u -> walkAll(s, wrapUnifiable(u))
						.map(w -> (u instanceof Unifiable) ?
								w : w.asVal().get()))
				.reduce(done(new ArrayList<>()),
						(Fiber<ArrayList<Object>> acc, Fiber<Object> item) -> Fiber.zip(acc, item)
								.map(lr -> {
									lr._1.add(lr._2);
									return lr._1;
								}),
						Exceptions.throwingBiOp(UnsupportedOperationException::new))
				.map(r -> r.stream().collect(collector))
				.map(MiniKanren::<T>wrapUnifiable);
	}

	private static <T> Fiber<Unifiable<T>> walkTuple(Package s, Iterable<Object> tuple) {
		return toJavaStream(tuple)
				// walkAll accepts Unifiable,
				// but some elements may be regular types.
				// We're wrapping those types in a Val
				.map(e -> walkAll(s, wrapUnifiable(e))
						// Here, we unwrap the Unifiable,
						// if the original type was not Unifiable
						// so that we can reconstruct the original tuple
						.map(u -> e instanceof Unifiable ?
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
				.map(MiniKanren::castUnifiable);
	}

	public static <T> Fiber<Unifiable<T>> reify(Package s, Unifiable<T> item) {
		return walkAll(s, item)
				.flatMap(v -> reifyS(Package.empty(), v)
						.flatMap(rp -> walkAll(rp, v)));
	}

	@SuppressWarnings("unchecked")
	public static Fiber<Package> reifyS(Package s, Unifiable<?> val) {
		return walkAll(s, val)
				.flatMap(v -> v.asVar()
						.map(u -> extend(s, (LVar<Object>) u, LVar.lvar("_." + s.size())))
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
				.map(MiniKanren::wrapUnifiable)
				.reduce(done(s),
						(state, item) ->
								state.flatMap(s1 -> reifyS(s1, item)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	/**
	 * Like reify, but replaces unbound variables with fresh LVars instead of symbols.
	 * Used for tabling to copy terms without leaking variable context.
	 */
	public static <T> Fiber<Unifiable<T>> reifyVar(Package s, Unifiable<T> item) {
		return walkAll(s, item)
				.flatMap(v -> reifyVarS(Package.empty(), v)
						.flatMap(rp -> walkAll(rp, v)));
	}

	@SuppressWarnings("unchecked")
	private static Fiber<Package> reifyVarS(Package s, Unifiable<?> val) {
		return walkAll(s, val)
				.flatMap(v -> v.asVar()
						.map(u -> extend(s, (LVar<Object>) u, (Unifiable<Object>) LVar.lvar()))
						.map(Fiber::done)
						.orElse(() -> v.asVal()
								.flatMap(w -> asIterable(w)
										.orElse(() -> tupleAsIterable(w)))
								.map(it -> reifyVarIterable(s, it)))
						.orElse(() -> v.asVal()
								.flatMap(w -> Types.cast(w, LList.class))
								.map(llist -> reifyVarLList(s, llist)))
						.orElse(() -> v.asVal()
								.flatMap(w -> Types.cast(w, LTree.class))
								.map(ltree -> reifyVarLTree(s, ltree)))
						.getOrElse(done(s)));
	}

	private static Fiber<Package> reifyVarLList(Package s, LList<?> llist) {
		if (llist.isEmpty()) {
			return done(s);
		} else {
			return reifyVarS(s, llist.getHead())
					.flatMap(s1 -> reifyVarS(s1, llist.getTail()));
		}
	}

	private static Fiber<Package> reifyVarLTree(Package s, LTree<?> tree) {
		if (tree.isEmpty()) {
			return done(s);
		} else {
			return reifyVarS(s, tree.getValue())
					.flatMap(p -> reifyVarS(p, tree.getChildren()));
		}
	}

	private static Fiber<Package> reifyVarIterable(Package s, Iterable<Object> l) {
		return toJavaStream(l)
				.map(MiniKanren::wrapUnifiable)
				.reduce(done(s),
						(state, item) ->
								state.flatMap(s1 -> reifyVarS(s1, item)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	/**
	 * Check if two terms are alpha-equivalent (equivalent modulo variable renaming).
	 * Used for tabling to detect duplicate answer terms.
	 */
	public static <T> Fiber<Boolean> alphaEquiv(Unifiable<T> x, Unifiable<T> y, Package s) {
		return reify(s, x).flatMap(xReified ->
			reify(s, y).map(yReified ->
				xReified.equals(yReified)));
	}

	public static HashMap<LVar<?>, Unifiable<?>> prefixS(
			HashMap<LVar<?>, Unifiable<?>> s,
			HashMap<LVar<?>, Unifiable<?>> extendedS) {
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
	private static <T> Unifiable<T> castUnifiable(Object v) {
		return (Unifiable<T>) v;
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

	/**
	 * Check if a Unifiable contains any LVars at any nesting depth.
	 * Should only be called on a walked Unifiable.
	 * Uses Fiber to avoid stack overflow on deeply nested structures.
	 *
	 * @param u The Unifiable to check (should already be walked)
	 * @return Fiber that yields true if any LVars are found, false if ground
	 */
	public static <T> Fiber<Boolean> containsLVars(Unifiable<T> u) {
		return u.asVar()
				.map(v -> done(true))  // Found an LVar
				.orElse(() -> u.asVal()
						.flatMap(MiniKanren::asIterable)
						.map(MiniKanren::containsLVarsIterable))
				.orElse(() -> u.asVal()
						.flatMap(MiniKanren::<T>asLList)
						.filter(not(LList::isEmpty))
						.map(MiniKanren::containsLVarsLList))
				.orElse(() -> u.asVal()
						.flatMap(MiniKanren::<T>asLTree)
						.filter(not(LTree::isEmpty))
						.map(MiniKanren::containsLVarsLTree))
				.orElse(() -> u.asVal()
						.flatMap(MiniKanren::tupleAsIterable)
						.map(MiniKanren::containsLVarsTuple))
				.getOrElse(done(false));  // Not a var, not a nested structure
	}

	private static <T> Fiber<Boolean> containsLVarsLList(LList<T> llist) {
		if (llist.isEmpty()) {
			return done(false);
		} else {
			Unifiable<T> head = llist.getHead();
			Unifiable<LList<T>> tail = llist.getTail();

			if (head == null && tail == null) {
				return done(false);  // Empty list
			}

			Fiber<Boolean> headCheck = head != null ?
				defer(() -> containsLVars(head)) :
				done(false);

			return headCheck.flatMap(headHasLVars -> {
				if (headHasLVars) {
					return done(true);
				}
				if (tail != null) {
					return defer(() -> containsLVars(tail));
				}
				return done(false);
			});
		}
	}

	private static <T> Fiber<Boolean> containsLVarsLTree(LTree<T> tree) {
		if (tree.isEmpty()) {
			return done(false);
		} else {
			Unifiable<T> value = tree.getValue();
			Unifiable<LList<LTree<T>>> children = tree.getChildren();

			if (value == null && children == null) {
				return done(false);  // Empty tree
			}

			Fiber<Boolean> valueCheck = value != null ?
				defer(() -> containsLVars(value)) :
				done(false);

			return valueCheck.flatMap(valHasLVars -> {
				if (valHasLVars) {
					return done(true);
				}
				if (children != null) {
					return defer(() -> containsLVars(children));
				}
				return done(false);
			});
		}
	}

	private static Fiber<Boolean> containsLVarsIterable(Iterable<Object> iterable) {
		return toJavaStream(iterable)
				.map(item -> containsLVars(wrapUnifiable(item)))
				.reduce(done(false),
						(acc, itemFiber) -> acc.flatMap(found -> found ?
								done(true) :
								itemFiber),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	private static Fiber<Boolean> containsLVarsTuple(Iterable<Object> tuple) {
		return toJavaStream(tuple)
				.map(item -> containsLVars(wrapUnifiable(item)))
				.reduce(done(false),
						(acc, itemFiber) -> acc.flatMap(found -> found ?
								done(true) :
								itemFiber),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}
}