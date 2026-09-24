package org.clauseway.logic.unification;

import static io.vavr.Predicates.not;
import static org.clauseway.functional.fibers.Fiber.defer;
import static org.clauseway.functional.fibers.Fiber.done;
import static org.clauseway.functional.fibers.MFiber.mdefer;
import static org.clauseway.functional.fibers.MFiber.mdone;
import static org.clauseway.functional.fibers.MFiber.none;
import static org.clauseway.logic.unification.LVal.lval;

import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.clauseway.functional.Exceptions;
import org.clauseway.functional.fibers.Fiber;
import org.clauseway.functional.fibers.MFiber;
import org.clauseway.functional.reflection.Types;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple2;

/**
 * @author TGa
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MiniKanren {

	private static <T> Option<Substitutions> extendNoCheck(Substitutions s, LVar<T> lhs, Term<T> rhs) {
		return Option.some(s.extend(lhs, rhs));
	}

	/**
	 * True when {@code x} occurs anywhere inside {@code v} under {@code s}: the
	 * walked term is inspected recursively through the same structural
	 * decomposition the unifier uses, so a binding that would close a cycle —
	 * directly or through earlier bindings — is detected before it is made.
	 */
	public static <T> Boolean occursCheck(Substitutions s, LVar<T> x, Term<T> v) {
		return occurs(s, x, v);
	}

	private static boolean occurs(Substitutions s, LVar<?> x, Term<?> v) {
		Term<?> walked = s.walk(v);
		if (walked.asVar().isDefined()) {
			return walked.asVar().get() == x;
		}
		for (Term<?> member : members(walked).getOrElse(Collections.emptyList())) {
			if (occurs(s, x, member)) {
				return true;
			}
		}
		return false;
	}

	/** none = the binding would close a cycle; the unification must fail. */
	static <T> Option<Substitutions> extend(Substitutions s, LVar<T> lhs, Term<T> rhs) {
		return occursCheck(s, lhs, rhs) ?
				Option.none() :
				extendNoCheck(s, lhs, rhs);
	}

	/**
	 * Renders a value for a trace label: a {@link Term} is deep-walked to its
	 * current bindings, anything else is printed as-is. Used by goal labels so a
	 * trace shows arguments fully substituted rather than as raw variable names.
	 */
	public static String format(Substitutions s, Object o) {
		return o instanceof Term ? walkAll(s, (Term<?>) o).ground().toString() : String.valueOf(o);
	}

	private interface Extender {
		<T> Option<Substitutions> apply(Substitutions s, LVar<T> lhs, Term<T> rhs);
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
					"Reified terms cannot re-enter unification: " + l + " ≡ " + r);
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
						.map(rVar -> extend.apply(s, lVar, rVar))
						.getOrElse(() -> extend.apply(s, lVar, r))
						.map(MFiber::mdone)
						.getOrElse(MFiber::none))
				.orElse(() -> r.asVar()
						.map(rVar -> extend.apply(s, rVar, l)
								.map(MFiber::mdone)
								.getOrElse(MFiber::none)))
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
	public static <T> Option<LList<T>> asLList(Object v) {
		return Option.of(v)
				.filter(LList.class::isInstance)
				.map(LList.class::cast);
	}

	@SuppressWarnings("unchecked")
	public static <T> Option<LTree<T>> asLTree(Object v) {
		return Option.of(v)
				.filter(LTree.class::isInstance)
				.map(LTree.class::cast);
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
		ArrayList<io.vavr.Tuple2<LVar<?>, Term<?>>> collected = new ArrayList<>();
		Extender collecting = new Extender() {
			@Override
			public <U> Option<Substitutions> apply(Substitutions p, LVar<U> l, Term<U> r) {
				return extend.apply(p, l, r)
						.map(extended -> {
							collected.add(new io.vavr.Tuple2<>(l, r));
							return extended;
						});
			}
		};
		return unify(collecting, s, lhs, rhs)
				.map(s1 -> new Prefix(HashMap.ofEntries(collected)));
	}

	public static <T> Fiber<Term<T>> walkAll(Substitutions s, Term<T> u) {
		return done(s.walk(u))
				.flatMap(v -> v.asVar()
						.map(Fiber::<Term<T>>done)
						.orElse(() -> MiniKanren.mapStructure(v, e -> walkAll(s, e)))
						.getOrElse(done(v)));
	}

	/** A term's one-level structural decomposition: its kind and a lazy view of its members. */
	@Value
	@RequiredArgsConstructor
	public static class Decomposition {
		public enum Kind {
			TUPLE, LLIST, LTREE
		}
		Kind kind;
		Iterable<Term<?>> members;
	}

	/**
	 * The ONE place that knows what structure is: a term's kind and members, one
	 * level deep, held lazily — no rebuild, no collector. Empty when the term is
	 * not structural (variables, plain values, empty LList/LTree — the empties
	 * are equality atoms). Structure is carried by the engine's own types —
	 * tuples through their structural contract, LList and LTree natively —
	 * and every foreign value, collections included, is an equality atom.
	 */
	static Option<Decomposition> decompose(Term<?> v) {
		if (!v.asVal().isDefined()) {
			return Option.none();
		}
		Object w = v.get();
		if (w == null) {
			// a null payload is an equality atom, never structure
			return Option.none();
		}
		return tupleAsIterable(w)
				.map(it -> new Decomposition(Decomposition.Kind.TUPLE, wrapAll(it)))
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
				.flatMap(Types.cast(Tuple.class))
				.map(t -> MiniKanren.<T> mapTuple(t, mapper))
				.orElse(() -> v.asVal()
						.flatMap(MiniKanren::<T>asLList)
						.filter(not(LList::isEmpty))
						.map(c -> Fiber.zip(
										defer(() -> mapper.apply(c.getHead().getObjectTerm())),
										defer(() -> mapper.apply(c.getTail().getObjectTerm())))
								.map(ht -> LList.of(ht._1, MiniKanren.castTerm(ht._2)).get())
								.map(w -> Types.<T> castAs(w, Object.class).get())
								.map(LVal::lval)
								.map(MiniKanren::<T>castTerm)))
				.orElse(() -> v.asVal()
						.flatMap(MiniKanren::<T>asLTree)
						.filter(not(LTree::isEmpty))
						.map(c -> Fiber.zip(
										defer(() -> mapper.apply(c.getValue().getObjectTerm())),
										defer(() -> mapper.apply(c.getChildren().getObjectTerm())))
								.map(vc -> LTree.of(vc._1, MiniKanren.castTerm(vc._2)).get())
								.map(w -> Types.<T> castAs(w, Object.class).get())
								.map(LVal::lval)
								.map(MiniKanren::<T>castTerm)));
	}

	private static <T> Fiber<Term<T>> mapTuple(
			Tuple tuple,
			Function<Term<Object>, Fiber<Term<Object>>> mapper) {
		return toJavaStream(tupleMembers(tuple))
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
				.map(tuple::withMembers)
				.map(LVal::lval)
				.map(MiniKanren::castTerm);
	}

	/**
	 * Convert a reified term back into a solver term: canonical anys become
	 * fresh variables — anys sharing a number share the variable — and ground
	 * structure is preserved. One minted seed, one walk.
	 */
	public static <T> Fiber<Unifiable<T>> instantiate(Reified<T> term) {
		return MiniKanren.instantiated(term).map(t -> (Unifiable<T>) t._1);
	}

	private static <T> Fiber<Tuple2<Term<T>, Map<Any<?>, LVar<?>>>> instantiated(Reified<T> term) {
		Map<Any<?>, LVar<?>> fresh = new LinkedHashMap<>();
		namesIn(term)
				.<Any<?>> flatMap(name -> name.asReified().toJavaStream())
				.forEach(any -> fresh.computeIfAbsent(any, miss -> (LVar<?>) LVar.lvar()));
		return walkAll(Substitutions.of(HashMap.ofAll(fresh)), term)
				.map(t -> Tuple.of(t, fresh));
	}

	/**
	 * Every NAME occurrence in the term — live vars and canonical anys —
	 * lazily streamed in traversal order: a short-circuiting consumer stops
	 * the scan early. Iterative, deep spines never recurse.
	 */
	public static Stream<Name<?>> namesIn(Term<?> term) {
		java.util.Deque<Term<?>> work = new java.util.ArrayDeque<>();
		work.push(term);
		return StreamSupport.stream(new Spliterators.AbstractSpliterator<Name<?>>(
				Long.MAX_VALUE, java.util.Spliterator.ORDERED | java.util.Spliterator.NONNULL) {
			@Override
			public boolean tryAdvance(java.util.function.Consumer<? super Name<?>> action) {
				while (!work.isEmpty()) {
					Term<?> current = work.pop();
					if (current.asName().isDefined()) {
						action.accept(current.asName().get());
						return true;
					}
					members(current).forEach(members -> members.forEach(work::push));
				}
				return false;
			}
		}, false);
	}

	public static <T> Fiber<Reified<T>> reify(Substitutions s, Term<T> item) {
		// after renaming, every node is an LVal or a Any — both Reified
		return walkAll(s, item)
				.flatMap(v -> reifyS(Substitutions.empty(), v)
						.flatMap(rp -> walkAll(rp, v)))
				.map(v -> (Reified<T>) v);
	}

	/**
	 * {@link #reify} plus the renaming it performed: each renamed var to its
	 * any. Anys are numbered by first occurrence and the map iterates in
	 * slot order — callers get the var↔slot correspondence as data, not by a
	 * parallel walk.
	 */
	public static <T> Fiber<Tuple2<Reified<T>, Map<LVar<?>, Any<?>>>> reifyWithAnys(
			Substitutions s, Term<T> item) {
		return walkAll(s, item)
				.flatMap(v -> reifyS(Substitutions.empty(), v)
						.flatMap(rp -> walkAll(rp, v)
								.map(reified -> Tuple.of((Reified<T>) reified, varsToAnys(rp)))));
	}

	/**
	 * {@link #instantiate} plus the minting it performed: each any to the
	 * fresh {@link LVar} standing where the term said {@code _.i} — the
	 * mirror of {@link #reifyWithAnys}, for callers that must re-impose
	 * slot-named knowledge (residues) onto the instantiation.
	 */
	public static <T> Fiber<Tuple2<Unifiable<T>, Map<Any<?>, LVar<?>>>> instantiateWithAnys(Reified<T> term) {
		return MiniKanren.instantiated(term).map(t -> t.map1(Types.cast()));
	}

	/** Invert the rename substitution into slot order: the var named {@code _.i} ↦ {@code _.i}. */
	private static Map<LVar<?>, Any<?>> varsToAnys(Substitutions renames) {
		LVar<?>[] slots = new LVar<?>[(int) renames.size()];
		for (io.vavr.Tuple2<Name<?>, Term<?>> entry : renames.map()) {
			// the rename pass binds live vars only, so the keys are LVars
			slots[((Any<?>) entry._2).getNumber()] = (LVar<?>) entry._1;
		}
		Map<LVar<?>, Any<?>> vars = new LinkedHashMap<>();
		for (int i = 0; i < slots.length; i++) {
			vars.put(slots[i], Any.of(i));
		}
		return vars;
	}

	public static Fiber<Substitutions> reifyS(Substitutions s, Term<?> val) {
		// shallow walk only: a deep walk would rebuild structures with rename
		// vars substituted in, and nested calls would rename the rename vars
		return done(s.walk(val))
				.flatMap(v -> v.asVar()
						// a var that walked to something else is already renamed
						.map(u -> u == val ?
								// a Any is an atom: no occurs check to fail here
								s.extend(u, Any.of((int) s.size())) :
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

	public static <A, B> Option<Tuple2<A, B>> zip(
			Option<A> a, Option<B> b) {
		return a.flatMap(av -> b.map(bv -> Tuple.of(av, bv)));
	}

	/**
	 * The tuple half of the structural gate, read through the structural
	 * contract: one row for every arity — decompose via {@code get}, rebuild
	 * via {@code withMembers} — so a new arity cannot be half-supported.
	 */
	public static Option<Iterable<Object>> tupleAsIterable(Object tuple) {
		return Types.cast(tuple, Tuple.class).map(MiniKanren::tupleMembers);
	}

	private static Iterable<Object> tupleMembers(Tuple t) {
		return () -> IntStream.rangeClosed(1, t.arity())
				.mapToObj(t::get)
				.iterator();
	}

	@SuppressWarnings("unchecked")
	private static <T> Term<T> castTerm(Object v) {
		return (Term<T>) v;
	}

	private static Stream<Object> toJavaStream(Iterable<Object> it) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it.iterator(), 0), false);
	}
}
