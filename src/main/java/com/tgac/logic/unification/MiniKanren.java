package com.tgac.logic.unification;

import static com.tgac.functional.fibers.Fiber.defer;
import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.functional.fibers.MFiber.mdefer;
import static com.tgac.functional.fibers.MFiber.mdone;
import static com.tgac.functional.fibers.MFiber.none;
import static com.tgac.logic.unification.LVal.lval;
import static io.vavr.Predicates.not;

import com.tgac.functional.Exceptions;
import com.tgac.functional.Reference;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.MFiber;
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
import java.util.Iterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

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

	private static <T> Substitutions extendNoCheck(Substitutions s, LVar<T> lhs, Term<T> rhs) {
		return s.extend(lhs, rhs);
	}

	public static <T> Boolean occursCheck(Substitutions s, LVar<T> x, Term<T> v) {
		return s.walk(v).asVar()
				.map(vv -> vv == x)
				.getOrElse(false);
	}

	static <T> Substitutions extend(Substitutions s, LVar<T> lhs, Term<T> rhs) {
		return occursCheck(s, lhs, rhs) ?
				s :
				extendNoCheck(s, lhs, rhs);
	}

	/**
	 * Renders a value for a trace label: a {@link Term} is deep-walked to its
	 * current bindings, anything else is printed as-is. Used by goal labels so a
	 * trace shows arguments fully substituted rather than as raw variable names.
	 */
	public static String format(Substitutions s, Object o) {
		return o instanceof Term ? walkAll(s, (Term<?>) o).get().toString() : String.valueOf(o);
	}

	private interface Extender {
		<T> Substitutions apply(Substitutions s, LVar<T> lhs, Term<T> rhs);
	}

	private static <T> MFiber<Substitutions> unify(
			Extender extend,
			Substitutions s,
			Term<T> lhs,
			Term<T> rhs) {
		Term<T> l = s.walk(lhs);
		Term<T> r = s.walk(rhs);

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
						// route through the extender even though two distinct walked
						// vars cannot fail the occurs check — prefix collection
						// observes every extension
						.map(rVar -> extend.apply(s, lVar, (Term<T>) rVar))
						.map(MFiber::mdone)
						.getOrElse(() -> mdone(extend.apply(s, lVar, r))))
				.orElse(() -> r.asVar()
						.map(rVar -> extend.apply(s, rVar, l))
						.map(MFiber::mdone))
				.orElse(() -> zip(decompose(l), decompose(r))
						.map(lr -> unifyDecomposed(extend, s, lr._1, lr._2)))
				.getOrElse(MFiber::none);
	}

	private static MFiber<Substitutions> unifyDecomposed(
			Extender extend,
			Substitutions s,
			Decomposition l,
			Decomposition r) {
		if (l.getKind() != r.getKind()) {
			return none();
		}
		switch (l.getKind()) {
			case LLIST:
			case LTREE:
				// always two members — (head, tail) / (value, children); shape
				// variance is resolved by recursion, never at this level
				Iterator<Term<?>> lm = l.getMembers().iterator();
				Iterator<Term<?>> rm = r.getMembers().iterator();
				Term<Object> lFirst = castTerm(lm.next());
				Term<Object> rFirst = castTerm(rm.next());
				Term<Object> lSecond = castTerm(lm.next());
				Term<Object> rSecond = castTerm(rm.next());
				return mdefer(() -> unify(extend, s, lFirst, rFirst))
						.flatMap(s1 -> unify(extend, s1, lSecond, rSecond));
			default:
				// single pass: zip while both sides last; a leftover on either
				// side is an arity mismatch, and the unrun chain is just dropped
				// (MFiber is lazy — nothing unifies until stepped)
				MFiber<Substitutions> state = mdone(s);
				Iterator<Term<?>> li = l.getMembers().iterator();
				Iterator<Term<?>> ri = r.getMembers().iterator();
				while (li.hasNext() && ri.hasNext()) {
					Term<Object> lt = castTerm(li.next());
					Term<Object> rt = castTerm(ri.next());
					state = state.flatMap(s1 -> unify(extend, s1, lt, rt));
				}
				return li.hasNext() || ri.hasNext() ? none() : state;
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

	public static <T> MFiber<Substitutions> unify(Substitutions s, Term<T> lhs, Term<T> rhs) {
		return unify(MiniKanren::extend, s, lhs, rhs);
	}

	/**
	 * Unification as a {@link Prefix} mint: computes exactly the newly added
	 * bindings without applying them — none = the terms cannot unify, an empty
	 * prefix = they already unify. The delta is collected as the unifier extends,
	 * so no post-hoc map diff is needed.
	 */
	public static <T> MFiber<Prefix> unifyPrefix(Substitutions s, Term<T> lhs, Term<T> rhs) {
		return unifyPrefix(MiniKanren::extend, s, lhs, rhs);
	}

	public static <T> MFiber<Prefix> unifyPrefixUnsafe(Substitutions s, Term<T> lhs, Term<T> rhs) {
		return unifyPrefix(MiniKanren::extendNoCheck, s, lhs, rhs);
	}

	private static <T> MFiber<Prefix> unifyPrefix(Extender extend, Substitutions s, Term<T> lhs, Term<T> rhs) {
		ArrayList<Tuple2<LVar<?>, Term<?>>> collected = new ArrayList<>();
		Extender collecting = new Extender() {
			@Override
			public <U> Substitutions apply(Substitutions p, LVar<U> l, Term<U> r) {
				Substitutions extended = extend.apply(p, l, r);
				if (extended != p) {
					collected.add(Tuple.of(l, r));
				}
				return extended;
			}
		};
		return unify(collecting, s, lhs, rhs)
				.map(s1 -> new Prefix(HashMap.ofEntries(collected)));
	}

	public static <T> Fiber<Term<T>> walkAll(Substitutions s, Term<T> u) {
		return done(s.walk(u))
				.flatMap(v -> v.asVar()
						.map(w -> Fiber.<Term<T>> done(w))
						.orElse(() -> MiniKanren.<T> mapStructure(v, e -> walkAll(s, e)))
						.getOrElse(done(v)));
	}

	/** A term's one-level structural decomposition: its kind and a lazy view of its members. */
	@Value
	@RequiredArgsConstructor
	public static class Decomposition {
		public enum Kind {
			ITERABLE, TUPLE, LLIST, LTREE
		}
		Kind kind;
		Iterable<Term<?>> members;
	}

	/**
	 * The ONE place that knows what structure is: a term's kind and members, one
	 * level deep, held lazily — no rebuild, no collector. Empty when the term is
	 * not structural (variables, plain values, empty LList/LTree — the empties
	 * are equality atoms). The kinds are deliberately coarse: any iterable
	 * matches any iterable; tuples are their own class, arity-checked by count.
	 */
	static Option<Decomposition> decompose(Term<?> v) {
		if (!v.asVal().isDefined()) {
			return Option.none();
		}
		Object w = v.get();
		return MiniKanren.<Object> asIterable(w)
				.map(it -> new Decomposition(Decomposition.Kind.ITERABLE, wrapAll(it)))
				.orElse(() -> tupleAsIterable(w)
						.map(it -> new Decomposition(Decomposition.Kind.TUPLE, wrapAll(it))))
				.orElse(() -> Types.cast(w, LList.class)
						.filter(x -> !x.isEmpty())
						.map(x -> new Decomposition(Decomposition.Kind.LLIST,
								Arrays.<Term<?>> asList(x.getHead(), x.getTail()))))
				.orElse(() -> Types.cast(w, LTree.class)
						.filter(t -> !t.isEmpty())
						.map(t -> new Decomposition(Decomposition.Kind.LTREE,
								Arrays.<Term<?>> asList(t.getValue(), t.getChildren()))));
	}

	private static Iterable<Term<?>> wrapAll(Iterable<Object> items) {
		return () -> {
			Iterator<Object> it = items.iterator();
			return new Iterator<Term<?>>() {
				@Override
				public boolean hasNext() {
					return it.hasNext();
				}

				@Override
				public Term<?> next() {
					return wrapTerm(it.next());
				}
			};
		};
	}

	/**
	 * The term's structural members — the same decomposition the unifier and
	 * walkAll recognize (collections, tuples, LList, LTree) — read-only: no
	 * rebuild, no collector needed. Empty when the term is not structural.
	 */
	public static Option<Iterable<Term<?>>> members(Term<?> v) {
		return decompose(v).map(Decomposition::getMembers);
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
		return instantiateTerm(term, new ConcurrentHashMap<>())
				.map(t -> (Unifiable<T>) t);
	}

	private static <T> Fiber<Term<T>> instantiateTerm(
			Term<T> u,
			ConcurrentMap<String, Term<Object>> fresh) {
		return u.asReified()
				.map(hole -> Fiber.<Term<T>> done((Term<T>) fresh.computeIfAbsent(
						hole.getName(), name -> LVar.lvar())))
				.orElse(() -> MiniKanren.<T> mapStructure(u, e -> instantiateTerm(e, fresh)))
				.getOrElse(done(u));
	}

	@SuppressWarnings("unchecked")
	public static <T> Fiber<Reified<T>> reify(Substitutions s, Term<T> item) {
		// after renaming, every node is an LVal or a ReifiedVar — both Reified
		return walkAll(s, item)
				.flatMap(v -> reifyS(Substitutions.empty(), v)
						.flatMap(rp -> walkAll(rp, v)))
				.map(v -> (Reified<T>) v);
	}

	@SuppressWarnings("unchecked")
	public static Fiber<Substitutions> reifyS(Substitutions s, Term<?> val) {
		// shallow walk only: a deep walk would rebuild structures with rename
		// vars substituted in, and nested calls would rename the rename vars
		return done(s.walk(val))
				.flatMap(v -> v.asVar()
						// a var that walked to something else is already renamed
						.map(u -> u == val ?
								extend(s, (LVar<Object>) u, ReifiedVar.of("_." + s.size())) :
								s)
						.map(Fiber::done)
						.orElse(() -> members(v)
								.map(ms -> reifyMembers(s, ms.iterator())))
						.getOrElse(done(s)));
	}

	private static Fiber<Substitutions> reifyMembers(Substitutions s, Iterator<Term<?>> members) {
		if (!members.hasNext()) {
			return done(s);
		}
		Term<?> head = members.next();
		return defer(() -> reifyS(s, head))
				.flatMap(s1 -> reifyMembers(s1, members));
	}

	/**
	 * Check if two terms are alpha-equivalent (equivalent modulo variable renaming).
	 * Reification numbers holes canonically and reified vars carry value
	 * equality by name, so plain equality on the reified forms decides it.
	 */
	public static <T> Fiber<Boolean> alphaEquiv(Term<T> x, Term<T> y, Substitutions s) {
		return reify(s, x).flatMap(xReified ->
				reify(s, y).map(xReified::equals));
	}

	public static <A, B> BiFunction<A, A, Tuple2<B, B>> applyOnBoth(Function<A, B> f) {
		return (a, b) -> Tuple.of(f.apply(a), f.apply(b));
	}

	public static <A, B> Option<Tuple2<A, B>> zip(Option<A> a, Option<B> b) {
		return a.flatMap(av -> b.map(bv -> Tuple.of(av, bv)));
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

	private static Stream<Object> toJavaStream(Iterable<Object> it) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it.iterator(), 0), false);
	}

	@SuppressWarnings("unchecked")
	private static <T> Iterable<Object> asIterable(Object item, Class<T> cls, Function<T, Iterable<?>> sequencer) {
		return cls.isInstance(item) ?
				(Iterable<Object>) sequencer.apply((T) item) :
				null;
	}
}
