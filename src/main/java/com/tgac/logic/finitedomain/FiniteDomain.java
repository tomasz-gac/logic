package com.tgac.logic.finitedomain;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FiniteDomain {

	/** The membership {@code u ∈ d} through the store's imposition door. */
	@SuppressWarnings("unchecked")
	public static <T> Posting dom(Unifiable<T> u, Domain<T> d) {
		return FiniteDomainConstraints.empty().impose(u, (Domain<Object>) d);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static long cmpOrder(Substitutions s, Term<?> l, Term<?> r, IntPredicate satisfied) {
		Term<?> lw = s.walk(l);
		Term<?> rw = s.walk(r);
		if (lw.asVal().isDefined() && rw.asVal().isDefined() && lw.get() instanceof Comparable) {
			return satisfied.test(((Comparable) lw.get()).compareTo(rw.get())) ? 1 : 0;
		}
		return 1;
	}

	private static <T> Option<Array<VarWithDomain<T>>> letDomain(Package p, Array<? extends Term<T>> us) {
		return Option.of(us.toJavaStream()
						.map(u -> p.walk(u))
						.flatMap(v -> v.asVal()
								.map(val -> VarWithDomain.of(v, Singleton.of(Arithmetic.ofCasted(v.get()))))
								.map(Stream::of)
								.getOrElse(() -> FiniteDomainConstraints.getDom(p, v.getVar())
										.map(d -> VarWithDomain.of(v, d))
										.toJavaStream()))
						.collect(Array.collector()))
				.filter(uds -> uds.size() == us.size());
	}

	private static <T> Propagator prop(String name, Array<Unifiable<T>> us,
			BiFunction<Array<? extends Term<?>>, Package, Verdict> body) {
		return Propagator.of(FiniteDomainConstraints.class, name, us, body);
	}

	/**
	 * Posting-time entry: the propagator watching its terms as a statement —
	 * parked in the FD store at imposition, first examination queued, wakes
	 * re-examining the same parked object (constraint-kernel.md).
	 */
	private static Posting fdPosting(Propagator item, Predicate<Package> doomed) {
		return Propagation.activate(item, FiniteDomainConstraints::register, doomed);
	}

	@SuppressWarnings("unchecked")
	private static <T> Array<? extends Term<T>> typed(Array<? extends Term<?>> watched) {
		return (Array<? extends Term<T>>) watched;
	}

	private static <T> BiFunction<Array<? extends Term<?>>, Package, Verdict> gated(
			Function<Array<VarWithDomain<T>>, Verdict> verdict) {
		return (watched, s) -> letDomain(s, FiniteDomain.<T> typed(watched))
				.filter(uds -> uds.toJavaStream()
						.noneMatch(ud -> ud.getDomain().isEmpty()))
				.map(verdict)
				.getOrElse(Verdict::keep);
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	static class VarWithDomain<T> {
		Term<T> unifiable;
		Domain<?> domain;

		@SuppressWarnings("unchecked")
		public <U> Domain<U> getDomain() {
			return (Domain<U>) domain;
		}
	}

	public static <T> Posting leq(Unifiable<T> less, Unifiable<T> more) {
		return fdPosting(leqProp(less, more),
				p -> cmpOrder(p.substitution(), less, more, c -> c <= 0) == 0);
	}

	public static <T> Posting lss(Unifiable<T> less, Unifiable<T> more) {
		return Posting.all(
				p -> cmpOrder(p.substitution(), less, more, c -> c < 0) == 0,
				fdPosting(leqProp(less, more), p -> false),
				separate(less, more));
	}

	public static <T> Posting gtr(Unifiable<T> more, Unifiable<T> less) {
		return Posting.all(
				p -> cmpOrder(p.substitution(), more, less, c -> c > 0) == 0,
				fdPosting(leqProp(less, more), p -> false),
				separate(more, less));
	}

	public static <T> Posting geq(Unifiable<T> more, Unifiable<T> less) {
		return fdPosting(leqProp(less, more),
				p -> cmpOrder(p.substitution(), more, less, c -> c >= 0) == 0);
	}

	private static <T> Propagator leqProp(Unifiable<T> less, Unifiable<T> more) {
		return prop("leq",
				Array.of(less, more),
				FiniteDomain.<T> gated(vds ->
						Tuple.of(vds.get(0), vds.get(1))
								.apply(FiniteDomain::leqVerdict)));
	}

	private static <T> Verdict leqVerdict(VarWithDomain<T> lss, VarWithDomain<T> mor) {
		Domain<T> lessDom = lss.<T> getDomain().atMost(mor.<T> getDomain().max());
		Domain<T> moreDom = mor.<T> getDomain().atLeast(lss.<T> getDomain().min());
		if (lessDom.isEmpty() || moreDom.isEmpty()) {
			return Verdict.fail();
		}
		if (lss.getUnifiable().isVal() && mor.getUnifiable().isVal()) {
			// ground and consistent: nothing left to watch
			return Verdict.subsumed();
		}
		return Verdict.update((state, store) -> DomainUpdate.narrowAll(state,
				(FiniteDomainConstraints) store,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(lss.getUnifiable(), lessDom),
						VarWithDomain.of(mor.getUnifiable(), moreDom))));
	}

	public static <T> Posting addo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return addoFD(a, b, c);
	}

	public static <T> Posting subtracto(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return addoFD(c, b, a);
	}

	static <T> Posting addoFD(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return fdPosting(prop("add",
				Array.of(a, b, rhs),
				FiniteDomain.<T> gated(vds ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) ->
										addVerdict(u, v, w,
												u.<T> getDomain().min(), v.<T> getDomain().min(), w.<T> getDomain().min(),
												u.<T> getDomain().max(), v.<T> getDomain().max(), w.<T> getDomain().max())))),
				p -> false);
	}

	private static <T> Verdict addVerdict(
			VarWithDomain<T> u, VarWithDomain<T> v, VarWithDomain<T> w,
			Arithmetic<T> uMin, Arithmetic<T> vMin, Arithmetic<T> wMin,
			Arithmetic<T> uMax, Arithmetic<T> vMax, Arithmetic<T> wMax) {

		if (u.getUnifiable().isVal() && v.getUnifiable().isVal() && w.getUnifiable().isVal()) {
			// ground: check the sum exactly, nothing left to watch
			return uMin.add(vMin).compareTo(wMin) == 0 ? Verdict.subsumed() : Verdict.fail();
		}

		Interval<T> wi = Interval.of(
				uMin.add(vMin),
				uMax.add(vMax.next()));

		Interval<T> vi = Interval.of(
				wMin.subtract(uMax),
				wMax.subtract(uMin).next());

		Interval<T> ui = Interval.of(
				wMin.subtract(vMax),
				wMax.subtract(vMin).next());

		return Verdict.update((state, store) -> DomainUpdate.narrowAll(state,
				(FiniteDomainConstraints) store,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(w.getUnifiable(), wi),
						VarWithDomain.of(v.getUnifiable(), vi),
						VarWithDomain.of(u.getUnifiable(), ui))));
	}

	public static <T> Posting multo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return mulFD(a, b, c);
	}

	public static <T> Posting divo(Unifiable<T> divided, Unifiable<T> divisor, Unifiable<T> result) {
		return multo(result, divisor, divided);
	}

	static <T> Posting mulFD(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return fdPosting(prop("mul",
				Array.of(a, b, rhs),
				FiniteDomain.<T> gated(vds ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) ->
										mulVerdict(u, v, w,
												u.<T> getDomain().min(), v.<T> getDomain().min(), w.<T> getDomain().min(),
												u.<T> getDomain().max(), v.<T> getDomain().max(), w.<T> getDomain().max())))),
				p -> false);
	}

	private static <T> Verdict mulVerdict(
			VarWithDomain<T> u, VarWithDomain<T> v, VarWithDomain<T> w,
			Arithmetic<T> uMin, Arithmetic<T> vMin, Arithmetic<T> wMin,
			Arithmetic<T> uMax, Arithmetic<T> vMax, Arithmetic<T> wMax) {
		// all are numbers -> check multiplication
		if (uMin.equals(uMax) && vMin.equals(vMax) && wMin.equals(wMax)) {
			return uMin.mul(vMin).compareTo(wMin) == 0 ? Verdict.subsumed() : Verdict.fail();
		}

		// some are numbers -> do nothing until all generated
		if (uMin.equals(uMax) || vMin.equals(vMax) || wMin.equals(wMax)) {
			return Verdict.keep();
		}

		// Trim domains
		Domain<T> wi, ui, vi;

		Array<Tuple2<Arithmetic<T>, Arithmetic<T>>> uvPerm = Array.of(
				Tuple.of(uMin, vMin),
				Tuple.of(uMax, vMin),
				Tuple.of(uMin, vMax),
				Tuple.of(uMax, vMax));

		wi = Interval.normalized(
						minResult(Arithmetic::mul, uvPerm),
						maxResult(Arithmetic::mul, uvPerm))
				.intersect(w.getDomain());

		// result is zero, so we cannot infer any u or v bounds information
		if (wi.min().equals(wi.max()) && wi.min().isZero()) {
			Domain<T> wiZero = wi;
			return Verdict.update((state, store) -> DomainUpdate.narrowAll(state,
					(FiniteDomainConstraints) store,
					Collections.<VarWithDomain<?>> singletonList(
							VarWithDomain.of(w.getUnifiable(), wiZero))));
		}

		// quotient bounds are meaningless when the divisor interval spans zero
		// (w/v is unbounded around v = 0) — trim only sign-constant divisors
		ui = quotientBounds(wMin, wMax, vMin, vMax).getOrElse(() -> u.<T> getDomain());
		vi = quotientBounds(wMin, wMax, uMin, uMax).getOrElse(() -> v.<T> getDomain());

		Domain<T> wiF = wi, uiF = ui, viF = vi;
		return Verdict.update((state, store) -> DomainUpdate.narrowAll(state,
				(FiniteDomainConstraints) store,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(w.getUnifiable(), wiF),
						VarWithDomain.of(u.getUnifiable(), uiF),
						VarWithDomain.of(v.getUnifiable(), viF))));
	}

	/**
	 * Bounds of {@code w / d} over the endpoint box, defined only when the divisor
	 * interval is sign-constant (no zero inside). Exact integer quotients attain
	 * their extremes at the endpoints, so the endpoint min/max never excludes a
	 * valid factor.
	 */
	private static <T> Option<Domain<T>> quotientBounds(
			Arithmetic<T> wMin, Arithmetic<T> wMax,
			Arithmetic<T> dMin, Arithmetic<T> dMax) {
		Arithmetic<T> zero = dMin.subtract(dMin);
		if (dMin.compareTo(zero) <= 0 && dMax.compareTo(zero) >= 0) {
			return Option.none();
		}
		Array<Tuple2<Arithmetic<T>, Arithmetic<T>>> wdPerm = Array.of(
				Tuple.of(wMin, dMin),
				Tuple.of(wMin, dMax),
				Tuple.of(wMax, dMin),
				Tuple.of(wMax, dMax));
		return Option.of(Interval.normalized(
				minResult(Arithmetic::div, wdPerm),
				maxResult(Arithmetic::div, wdPerm)));
	}

	public static <T> Posting separate(Unifiable<T> l, Unifiable<T> r) {
		return fdPosting(separateProp(l, r), p -> {
			Term<?> lw = p.substitution().walk(l);
			Term<?> rw = p.substitution().walk(r);
			return lw.asVal().isDefined() && rw.asVal().isDefined() && lw.get().equals(rw.get());
		});
	}

	private static <T> Propagator separateProp(Unifiable<T> l, Unifiable<T> r) {
		return prop("separate",
				Array.of(l, r),
				(watched, s) -> letDomain(s, FiniteDomain.<T> typed(watched))
						.map(ds -> Tuple.of(ds.get(0), ds.get(1)))
						.map(ds -> ds.apply((ld, rd) -> {
							Option<Tuple2<Arithmetic<T>, Arithmetic<T>>> zip = MiniKanren.zip(
									getSingleElement(ld.getDomain()),
									getSingleElement(rd.getDomain()));
							if (zip.isDefined() && zip.get().apply(Objects::equals)) {
								return Verdict.fail();
							}
							if (ld.getDomain().isDisjoint(rd.getDomain())) {
								return Verdict.subsumed();
							}
							if (ld.getDomain() instanceof Singleton) {
								return Verdict.update((state, store) -> DomainUpdate.narrowAll(state,
										(FiniteDomainConstraints) store,
										Collections.<VarWithDomain<?>> singletonList(VarWithDomain.of(
												rd.getUnifiable(),
												rd.<T> getDomain().difference(ld.getDomain())))));
							}
							if (rd.getDomain() instanceof Singleton) {
								return Verdict.update((state, store) -> DomainUpdate.narrowAll(state,
										(FiniteDomainConstraints) store,
										Collections.<VarWithDomain<?>> singletonList(VarWithDomain.of(
												ld.getUnifiable(),
												ld.<T> getDomain().difference(rd.getDomain())))));
							}
							return Verdict.keep();
						}))
						.getOrElse(Verdict::keep));
	}

	private static <T> Option<Arithmetic<T>> getSingleElement(Domain<T> dom) {
		return Option.of(dom)
				.flatMap(Types.<Singleton<T>> castAs(Singleton.class))
				.map(Singleton::getValue);
	}

	private static <T extends Comparable<T>> T minResult(BinaryOperator<T> f, Array<Tuple2<T, T>> args) {
		return args.toJavaStream()
				.map(t -> t.apply(f))
				.min(T::compareTo)
				.orElseThrow(IllegalStateException::new);
	}

	private static <T extends Comparable<T>> T maxResult(BinaryOperator<T> f, Array<Tuple2<T, T>> args) {
		return args.toJavaStream()
				.map(t -> t.apply(f))
				.max(T::compareTo)
				.orElseThrow(IllegalStateException::new);
	}

	public static <T> Posting copyDomain(Unifiable<T> from, Unifiable<T> to) {
		return new CopyDomain<>(from, to);
	}

	/**
	 * One-shot statement-position copy: {@code to} takes {@code from}'s LIVE
	 * domain (or its ground value as a singleton) at imposition time — the
	 * source is read from the state, so the statement carries only its ends.
	 */
	@Value
	static class CopyDomain<T> implements Posting {
		Unifiable<T> from;
		Unifiable<T> to;

		@Override
		public Cont<Package, Nothing> apply(Package s) {
			return dom(to, from.asVar()
					.flatMap(l -> FiniteDomainConstraints.<T> getDom(s, l))
					.orElse(() -> s.walk(from).asVal()
							.map(Arithmetic::ofCasted)
							.map(Singleton::of))
					.getOrElse(() -> Singleton.of(Arithmetic.ofCasted(from.get()))))
					.apply(s);
		}

		@Override
		public Stream<Term<?>> terms() {
			return Stream.of(from, to);
		}

		@Override
		@SuppressWarnings("unchecked")
		public Fiber<Posting> rename(Renaming renaming) {
			return renaming.apply(from)
					.flatMap(f -> renaming.apply(to)
							.map(t -> new CopyDomain<>((Unifiable<T>) f, (Unifiable<T>) t)));
		}

		@Override
		public String toString() {
			return "copyDom(" + from + ", " + to + ")";
		}
	}
}
