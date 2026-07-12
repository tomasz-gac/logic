package com.tgac.logic.finitedomain;

import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FiniteDomain {

	public static <T> Goal dom(Unifiable<T> u, Domain<T> d) {
		return Bounded.of(1, fdGoal()
				.and(applyDom(u, d))
				.named(pkg -> pkg.format(u) + " ⊂ " + pkg.format(d)));
	}

	/**
	 * Statement-position domain update: walk the target, apply against the live
	 * FD factor, and route the outcome through the public entries — resolve for a
	 * collapse, narrowed for a strict narrowing.
	 */
	private static Goal applyDom(Term<?> target, Domain<?> d) {
		return s -> DomainUpdate
				.apply(s, FiniteDomainConstraints.getFDStore(s), s.walk(target), d)
				.<Cont<Package, Nothing>> match(
						() -> Cont.complete(Nothing.nothing()),
						() -> Cont.just(s),
						applied -> {
							Goal binds = applied.inferred().stream()
									.map(Propagation::resolve)
									.reduce(Goal.success(), Goal::and);
							Goal wakes = applied.reexamine().stream()
									.map(FiniteDomain::reexamineOwn)
									.reduce(Goal.success(), Goal::and);
							return binds.and(wakes).apply(s.putStore(applied.factor()));
						});
	}

	/**
	 * Statement-position re-examination of this domain's own watchers of
	 * {@code x}: the store drains its cascade (a fiber — long cascades stay
	 * fairly stepped) and the collapses it yields re-enter through the
	 * chokepoint like any other inferred bindings.
	 */
	static Goal reexamineOwn(Term<?> x) {
		return s -> Cont.defer(() -> FiniteDomainConstraints.reexamine(x, s)
				.map(revision -> revision.<Cont<Package, Nothing>> match(
						() -> Cont.complete(Nothing.nothing()),
						() -> Cont.just(s),
						upd -> {
							Package updated = s.putStore(upd.factor());
							return upd.inferred().stream()
									.map(Propagation::resolve)
									.reduce(Goal.success(), Goal::and)
									.apply(updated);
						})));
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

	/**
	 * Statement-time entry: parks a propagator watching {@code us} in the FD store
	 * and queues its first examination; wakes re-examine the same parked object
	 * against the live state (constraint-kernel.md).
	 */
	private static <T> Goal fdConstraint(Array<Unifiable<T>> us,
			Function<Package, Verdict> body) {
		return p -> Propagation.activate(
						Propagator.of(FiniteDomainConstraints.class, us.toJavaList(), body))
				.apply(FiniteDomainConstraints.register(p));
	}

	private static <T> Function<Package, Verdict> gated(Array<Unifiable<T>> us,
			Function<Array<VarWithDomain<T>>, Verdict> verdict) {
		return s -> letDomain(s, us)
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

	public static <T> Goal leq(Unifiable<T> less, Unifiable<T> more) {
		return Bounded.of(1, fdGoal()
				.and(leqFD(less, more))
				.named(pkg -> pkg.format(less) + " ≤ " + pkg.format(more)));
	}

	public static <T> Goal lss(Unifiable<T> less, Unifiable<T> more) {
		return Bounded.of(1, fdGoal()
				.and(leqFD(less, more))
				.and(separate(less, more))
				.named(pkg -> pkg.format(less) + " < " + pkg.format(more)));
	}

	public static <T> Goal gtr(Unifiable<T> more, Unifiable<T> less) {
		return Bounded.of(1, fdGoal()
				.and(leqFD(more, less))
				.and(separate(more, less))
				.named(pkg -> pkg.format(more) + " > " + pkg.format(less)));
	}

	public static <T> Goal geq(Unifiable<T> more, Unifiable<T> less) {
		return Bounded.of(1, fdGoal()
				.and(leqFD(more, less))
				.named(pkg -> pkg.format(more) + " ≥ " + pkg.format(less)));
	}

	private static <T> Goal leqFD(Unifiable<T> less, Unifiable<T> more) {
		return fdConstraint(
				Array.of(less, more),
				gated(Array.of(less, more), vds ->
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

	public static <T> Goal addo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return Bounded.of(1, fdGoal()
				.and(addoFD(a, b, c))
				.named(pkg -> pkg.format(a) + " + " + pkg.format(b) + " = " + pkg.format(c)));
	}

	public static <T> Goal subtracto(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return addo(c, b, a)
				.named(pkg -> pkg.format(a) + " - " + pkg.format(b) + " = " + pkg.format(c));
	}

	static <T> Goal addoFD(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return fdConstraint(
				Array.of(a, b, rhs),
				gated(Array.of(a, b, rhs), vds ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) ->
										addVerdict(u, v, w,
												u.<T> getDomain().min(), v.<T> getDomain().min(), w.<T> getDomain().min(),
												u.<T> getDomain().max(), v.<T> getDomain().max(), w.<T> getDomain().max()))));
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

	public static <T> Goal multo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return Bounded.of(1, fdGoal()
				.and(mulFD(a, b, c))
				.named(pkg -> pkg.format(a) + " * " + pkg.format(b) + " = " + pkg.format(c)));
	}

	public static <T> Goal divo(Unifiable<T> divided, Unifiable<T> divisor, Unifiable<T> result) {
		return multo(result, divisor, divided)
				.named(pkg -> pkg.format(divided) + " / " + pkg.format(divisor) + " = " + pkg.format(result));
	}

	static <T> Goal mulFD(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return fdConstraint(
				Array.of(a, b, rhs),
				gated(Array.of(a, b, rhs), vds ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) ->
										mulVerdict(u, v, w,
												u.<T> getDomain().min(), v.<T> getDomain().min(), w.<T> getDomain().min(),
												u.<T> getDomain().max(), v.<T> getDomain().max(), w.<T> getDomain().max()))));
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

	public static <T> Goal separate(Unifiable<T> l, Unifiable<T> r) {
		return Bounded.of(1, fdGoal()
				.and(separateFDC(l, r))
				.named(pkg -> pkg.format(l) + " ≠_fd " + pkg.format(r)));
	}

	private static <T> Goal separateFDC(Unifiable<T> l, Unifiable<T> r) {
		return fdConstraint(
				Array.of(l, r),
				s -> letDomain(s, Array.of(l, r))
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

	private static Goal fdGoal() {
		return s -> Cont.just(FiniteDomainConstraints.register(s));
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

	public static <T> Goal copyDomain(Unifiable<T> from, Unifiable<T> to) {
		return Bounded.of(1, fdGoal()
				.and(s -> applyDom(to, from.asVar()
						.flatMap(l -> FiniteDomainConstraints.<T> getDom(s, l))
						.orElse(() -> s.walk(from).asVal()
								.map(Arithmetic::ofCasted)
								.map(Singleton::of))
						.getOrElse(() -> Singleton.of(Arithmetic.ofCasted(from.get()))))
						.apply(s))
				.named(pkg -> String.format("copyDom(%s, %s)", pkg.format(from), pkg.format(to))));
	}
}
