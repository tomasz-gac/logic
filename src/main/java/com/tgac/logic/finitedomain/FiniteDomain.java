package com.tgac.logic.finitedomain;


import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.ckanren.Inference;
import com.tgac.logic.ckanren.Propagator;
import com.tgac.logic.ckanren.Verdict;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
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
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FiniteDomain {

	public static <T> Goal dom(Unifiable<T> u, Domain<T> d) {
		return fdGoal()
				.and(Goal.goal(s -> d.processDom(MiniKanren.walk(s, u)).apply(s)))
				.named(pkg -> MiniKanren.format(pkg, u) + " ⊂ " + MiniKanren.format(pkg, d));
	}

	/**
	 * The FD half of the disequality bridge (cKanren's FD/≠ integration): when
	 * {@code x} has a finite domain and {@code value} is representable in it, the
	 * disequality {@code x ≠ value} is fully expressed by excluding the value from
	 * the domain, and the caller may discharge its record. The returned goal rides
	 * {@code processDom}, so an exclusion that collapses the domain binds the
	 * variable, and one that empties it fails. None when {@code x} has no domain
	 * or the value is not arithmetic — the caller keeps the disequality.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Option<Goal> excludeFromDomain(Package p, LVar<?> x, Object value) {
		if (!(value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger)) {
			return Option.none();
		}
		if (p.getConstraints() == null
				|| !p.getConstraints().get(FiniteDomainConstraints.class).isDefined()) {
			return Option.none();
		}
		return FiniteDomainConstraints.getDom(p, (LVar) x)
				.map(d -> (Goal) s -> ((Domain) d)
						.difference(Singleton.of(Arithmetic.ofCasted(value)))
						.processDom(MiniKanren.walk(s, (LVar) x))
						.apply(s));
	}

	private static <T> Option<Array<VarWithDomain<T>>> letDomain(Package p, Array<? extends Term<T>> us) {
		return Option.of(us.toJavaStream()
						.map(u -> MiniKanren.walk(p, u))
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
	 * The step-1 adapter (capability-constraint-api.md §6): parks the constraint
	 * under today's protocol, asks the propagator for a verdict and administers it —
	 * fail kills the branch, keep stays parked, discharge returns the un-parked
	 * package, narrowed stays parked and applies the inferences in order. addTo's
	 * all-args-bound guard still decides parking, exactly as before.
	 */
	private static <T> Goal propagatorOperation(Goal constraintOp, Array<Unifiable<T>> us, Propagator prop) {
		return p -> {
			Package registered = FiniteDomainConstraints.register(p);
			Package parked = CKanren.buildWalkedConstraint(
							constraintOp, us,
							FiniteDomainConstraints.class,
							registered)
					.addTo(registered);
			return prop.propagate(parked).match(
					() -> Cont.complete(Nothing.nothing()),
					() -> Cont.just(parked),
					() -> Cont.just(registered),
					inferences -> inferences.stream()
							.map(Inference::toGoal)
							.reduce(Goal.success(), Goal::and)
							.apply(parked));
		};
	}

	private static <T> Propagator gated(Array<Unifiable<T>> us,
			java.util.function.Function<Array<VarWithDomain<T>>, Verdict> verdict) {
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
		return fdGoal()
				.and(leqFD(less, more))
				.named(pkg -> MiniKanren.format(pkg, less) + " ≤ " + MiniKanren.format(pkg, more));
	}

	public static <T> Goal lss(Unifiable<T> less, Unifiable<T> more) {
		return fdGoal()
				.and(leqFD(less, more))
				.and(separate(less, more))
				.named(pkg -> MiniKanren.format(pkg, less) + " < " + MiniKanren.format(pkg, more));
	}

	public static <T> Goal gtr(Unifiable<T> more, Unifiable<T> less) {
		return fdGoal()
				.and(leqFD(more, less))
				.and(separate(more, less))
				.named(pkg -> MiniKanren.format(pkg, more) + " > " + MiniKanren.format(pkg, less));
	}

	public static <T> Goal geq(Unifiable<T> more, Unifiable<T> less) {
		return fdGoal()
				.and(leqFD(more, less))
				.named(pkg -> MiniKanren.format(pkg, more) + " ≥ " + MiniKanren.format(pkg, less));
	}

	private static <T> Goal leqFD(Unifiable<T> less, Unifiable<T> more) {
		return propagatorOperation(
				p -> leqFD(less, more).apply(p),
				Array.of(less, more),
				gated(Array.of(less, more), vds ->
						Tuple.of(vds.get(0), vds.get(1))
								.apply((lss, mor) -> Verdict.narrowed(Arrays.asList(
										Inference.narrow(lss.getUnifiable(),
												lss.<T> getDomain().atMost(mor.<T> getDomain().max())),
										Inference.narrow(mor.getUnifiable(),
												mor.<T> getDomain().atLeast(lss.<T> getDomain().min())))))));
	}

	public static <T> Goal addo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return fdGoal()
				.and(addoFD(a, b, c))
				.named(pkg -> MiniKanren.format(pkg, a) + " + " + MiniKanren.format(pkg, b) + " = " + MiniKanren.format(pkg, c));
	}

	public static <T> Goal subtracto(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return addo(c, b, a)
				.named(pkg -> MiniKanren.format(pkg, a) + " - " + MiniKanren.format(pkg, b) + " = " + MiniKanren.format(pkg, c));
	}

	static <T> Goal addoFD(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return propagatorOperation(
				p -> addoFD(a, b, rhs).apply(p),
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

		Interval<T> wi = Interval.of(
				uMin.add(vMin),
				uMax.add(vMax.next()));

		Interval<T> vi = Interval.of(
				wMin.subtract(uMax),
				wMax.subtract(uMin).next());

		Interval<T> ui = Interval.of(
				wMin.subtract(vMax),
				wMax.subtract(vMin).next());

		return Verdict.narrowed(Arrays.asList(
				Inference.narrow(w.getUnifiable(), wi),
				Inference.narrow(v.getUnifiable(), vi),
				Inference.narrow(u.getUnifiable(), ui)));
	}

	public static <T> Goal multo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return fdGoal()
				.and(mulFD(a, b, c))
				.named(pkg -> MiniKanren.format(pkg, a) + " * " + MiniKanren.format(pkg, b) + " = " + MiniKanren.format(pkg, c));
	}

	public static <T> Goal divo(Unifiable<T> divided, Unifiable<T> divisor, Unifiable<T> result) {
		return multo(result, divisor, divided)
				.named(pkg -> MiniKanren.format(pkg, divided) + " / " + MiniKanren.format(pkg, divisor) + " = " + MiniKanren.format(pkg, result));
	}

	static <T> Goal mulFD(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return propagatorOperation(
				p -> mulFD(a, b, rhs).apply(p),
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
			return uMin.mul(vMin).compareTo(wMin) == 0 ? Verdict.discharge() : Verdict.fail();
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
			return Verdict.narrowed(Collections.singletonList(
					Inference.narrow(w.getUnifiable(), wi)));
		}

		// quotient bounds are meaningless when the divisor interval spans zero
		// (w/v is unbounded around v = 0) — trim only sign-constant divisors
		ui = quotientBounds(wMin, wMax, vMin, vMax).getOrElse(() -> u.<T> getDomain());
		vi = quotientBounds(wMin, wMax, uMin, uMax).getOrElse(() -> v.<T> getDomain());

		return Verdict.narrowed(Arrays.asList(
				Inference.narrow(w.getUnifiable(), wi),
				Inference.narrow(u.getUnifiable(), ui),
				Inference.narrow(v.getUnifiable(), vi)));
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
		return fdGoal()
				.and(separateFDC(l, r))
				.named(pkg -> MiniKanren.format(pkg, l) + " ≠_fd " + MiniKanren.format(pkg, r));
	}

	private static <T> Goal separateFDC(Unifiable<T> l, Unifiable<T> r) {
		return propagatorOperation(
				p -> separateFDC(l, r).apply(p),
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
								return Verdict.discharge();
							}
							if (ld.getDomain() instanceof Singleton) {
								return Verdict.narrowed(Collections.singletonList(
										Inference.narrow(r, rd.<T> getDomain().difference(ld.getDomain()))));
							}
							if (rd.getDomain() instanceof Singleton) {
								return Verdict.narrowed(Collections.singletonList(
										Inference.narrow(l, ld.<T> getDomain().difference(rd.getDomain()))));
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
		return fdGoal()
				.and(s -> from.asVar()
						.flatMap(l -> FiniteDomainConstraints.getDom(s, l))
						.orElse(() -> s.walk(from).asVal()
								.map(Arithmetic::ofCasted)
								.map(Singleton::of))
						.getOrElse(() -> Singleton.of(Arithmetic.ofCasted(from.get())))
						.processDom(MiniKanren.walk(s, to))
						.apply(s))
				.named(pkg -> String.format("copyDom(%s, %s)", MiniKanren.format(pkg, from), MiniKanren.format(pkg, to)));
	}
}
