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
										.map(LVal::lval)
										.map(MiniKanren::<T>castTerm)))
						.orElse(() -> v.asVal()
								.flatMap(MiniKanren::<T>asLTree)
								.filter(not(LTree::isEmpty))
								.map(c -> Fiber.zip(
												defer(() -> walkAll(s, c.getValue())),
												defer(() -> walkAll(s, c.getChildren())))
										.map(vc -> vc.apply(LTree::of).get())
										.map(w -> Types.<T> castAs(w, Object.class).get())
										.map(LVal::lval)
										.map(MiniKanren::<T>castTerm)))
						.orElse(() -> v.asVal()
								.flatMap(MiniKanren::tupleAsIterable)
								.map(t -> walkTuple(s, t)))
						.getOrElse(done(v)));
	}

	private static <T> Fiber<Term<T>> walkIterable(Package s, Iterable<Object> iterable) {
		Collector<Object, ?, ?> collector = MiniKanren.getCollector(iterable)
				.getOrElseThrow(Exceptions.format(RuntimeException::new,
						"Unsupported iterable type: %s", iterable));

		return toJavaStream(iterable)
				.map(u -> walkAll(s, wrapTerm(u))
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

	private static <T> Fiber<Term<T>> walkTuple(Package s, Iterable<Object> tuple) {
		return toJavaStream(tuple)
				// walkAll accepts Term,
				// but some elements may be regular types.
				// We're wrapping those types in a Val
				.map(e -> walkAll(s, wrapTerm(e))
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

	public static <T> Fiber<Term<T>> reify(Package s, Term<T> item) {
		return walkAll(s, item)
				.flatMap(v -> reifyS(Package.empty(), v)
						.flatMap(rp -> walkAll(rp, v)));
	}

	@SuppressWarnings("unchecked")
	public static Fiber<Package> reifyS(Package s, Term<?> val) {
		// shallow walk only: a deep walk would rebuild structures with rename
		// vars substituted in, and nested calls would rename the rename vars
		return done(walk(s, val))
				.flatMap(v -> v.asVar()
						// a var that walked to something else is already renamed
						.map(u -> u == val ?
								extend(s, (LVar<Object>) u, LVar.lvar("_." + s.size())) :
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
	 * Like reify, but replaces unbound variables with fresh LVars instead of symbols.
	 * Used for tabling to copy terms without leaking variable context.
	 */
	public static <T> Fiber<Term<T>> reifyVar(Package s, Term<T> item) {
		return walkAll(s, item)
				.flatMap(v -> reifyVarS(Package.empty(), v)
						.flatMap(rp -> walkAll(rp, v)));
	}

	@SuppressWarnings("unchecked")
	private static Fiber<Package> reifyVarS(Package s, Term<?> val) {
		// shallow walk only: a deep walk would rebuild structures with the
		// fresh replacements substituted in, and nested calls would refresh them again
		return done(walk(s, val))
				.flatMap(v -> v.asVar()
						// a var that walked to something else is already refreshed
						.map(u -> u == val ?
								extend(s, (LVar<Object>) u, (Term<Object>) LVar.lvar()) :
								s)
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
				.map(MiniKanren::wrapTerm)
				.reduce(done(s),
						(state, item) ->
								state.flatMap(s1 -> reifyVarS(s1, item)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	/**
	 * Check if two terms are alpha-equivalent (equivalent modulo variable renaming).
	 * Used for tabling to detect duplicate answer terms.
	 */
	public static <T> Fiber<Boolean> alphaEquiv(Term<T> x, Term<T> y, Package s) {
		return reify(s, x).flatMap(xReified ->
			reify(s, y).map(yReified ->
				structuralEquals(xReified, yReified)));
	}

	/**
	 * Structural equality on reified terms: variables compare by name, values
	 * compare recursively through collections, tuples, LLists and LTrees.
	 * Both terms must come from {@link #reify} (or {@link #reifyS}) so that
	 * variable names are canonical — only then does name equality mean
	 * alpha-equivalence.
	 */
	public static boolean structuralEquals(Term<?> a, Term<?> b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}
		if (a.asVar().isDefined() || b.asVar().isDefined()) {
			return a.asVar().isDefined() && b.asVar().isDefined()
					&& a.asVar().get().getName().equals(b.asVar().get().getName());
		}
		Object l = a.asVal().isDefined() ? a.asVal().get() : null;
		Object r = b.asVal().isDefined() ? b.asVal().get() : null;
		if (l == null || r == null) {
			return a.equals(b);
		}
		Option<LList<Object>> lAsLList = asLList(l);
		Option<LList<Object>> rAsLList = asLList(r);
		if (lAsLList.isDefined() && rAsLList.isDefined()) {
			return structuralEqualsLList(lAsLList.get(), rAsLList.get());
		}
		Option<LTree<Object>> lAsLTree = asLTree(l);
		Option<LTree<Object>> rAsLTree = asLTree(r);
		if (lAsLTree.isDefined() && rAsLTree.isDefined()) {
			return structuralEqualsLTree(lAsLTree.get(), rAsLTree.get());
		}
		Option<Iterable<Object>> lAsIterable = asIterable(l).orElse(() -> tupleAsIterable(l));
		Option<Iterable<Object>> rAsIterable = asIterable(r).orElse(() -> tupleAsIterable(r));
		if (lAsIterable.isDefined() && rAsIterable.isDefined()) {
			return structuralEqualsIterable(lAsIterable.get(), rAsIterable.get());
		}
		return l.equals(r);
	}

	private static boolean structuralEqualsLList(LList<?> l, LList<?> r) {
		if (l.isEmpty() || r.isEmpty()) {
			return l.isEmpty() && r.isEmpty();
		}
		return structuralEquals(l.getHead(), r.getHead())
				&& structuralEquals(l.getTail(), r.getTail());
	}

	private static boolean structuralEqualsLTree(LTree<?> l, LTree<?> r) {
		if (l.isEmpty() || r.isEmpty()) {
			return l.isEmpty() && r.isEmpty();
		}
		return structuralEquals(l.getValue(), r.getValue())
				&& structuralEquals(l.getChildren(), r.getChildren());
	}

	private static boolean structuralEqualsIterable(Iterable<Object> l, Iterable<Object> r) {
		java.util.Iterator<Object> li = l.iterator();
		java.util.Iterator<Object> ri = r.iterator();
		while (li.hasNext() && ri.hasNext()) {
			if (!structuralEquals(wrapTerm(li.next()), wrapTerm(ri.next()))) {
				return false;
			}
		}
		return li.hasNext() == ri.hasNext();
	}

	/**
	 * Hash code consistent with {@link #structuralEquals}: variables hash by
	 * name, values hash recursively through the same structures.
	 */
	public static int structuralHash(Term<?> u) {
		if (u == null) {
			return 0;
		}
		if (u.asVar().isDefined()) {
			return u.asVar().get().getName().hashCode();
		}
		Object v = u.asVal().isDefined() ? u.asVal().get() : null;
		if (v == null) {
			return 0;
		}
		Option<LList<Object>> asLList = asLList(v);
		if (asLList.isDefined()) {
			LList<Object> l = asLList.get();
			return l.isEmpty() ? 1 :
					31 * structuralHash(l.getHead()) + structuralHash(l.getTail());
		}
		Option<LTree<Object>> asLTree = asLTree(v);
		if (asLTree.isDefined()) {
			LTree<Object> t = asLTree.get();
			return t.isEmpty() ? 2 :
					31 * structuralHash(t.getValue()) + structuralHash(t.getChildren());
		}
		Option<Iterable<Object>> asIterable = asIterable(v).orElse(() -> tupleAsIterable(v));
		if (asIterable.isDefined()) {
			int result = 3;
			for (Object item : asIterable.get()) {
				result = 31 * result + structuralHash(wrapTerm(item));
			}
			return result;
		}
		return v.hashCode();
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

	/**
	 * Check if a Term contains any LVars at any nesting depth.
	 * Should only be called on a walked Term.
	 * Uses Fiber to avoid stack overflow on deeply nested structures.
	 *
	 * @param u The Term to check (should already be walked)
	 * @return Fiber that yields true if any LVars are found, false if ground
	 */
	public static <T> Fiber<Boolean> containsLVars(Term<T> u) {
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
			Term<T> head = llist.getHead();
			Term<LList<T>> tail = llist.getTail();

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
			Term<T> value = tree.getValue();
			Term<LList<LTree<T>>> children = tree.getChildren();

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
				.map(item -> containsLVars(wrapTerm(item)))
				.reduce(done(false),
						(acc, itemFiber) -> acc.flatMap(found -> found ?
								done(true) :
								itemFiber),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	private static Fiber<Boolean> containsLVarsTuple(Iterable<Object> tuple) {
		return toJavaStream(tuple)
				.map(item -> containsLVars(wrapTerm(item)))
				.reduce(done(false),
						(acc, itemFiber) -> acc.flatMap(found -> found ?
								done(true) :
								itemFiber),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}
}
