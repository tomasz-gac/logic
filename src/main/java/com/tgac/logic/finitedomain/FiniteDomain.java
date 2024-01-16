package com.tgac.logic.finitedomain;

import com.tgac.functional.Exceptions;
import com.tgac.functional.reflection.Types;
import com.tgac.functional.step.Step;
import com.tgac.logic.Goal;
import com.tgac.logic.ckanren.Constraint;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.tgac.logic.ckanren.StoreSupport.withConstraint;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FiniteDomain {

	public static <T> Goal dom(Unifiable<T> u, Domain<T> d) {
		return fdGoal()
				.and(PackageAccessor.of(s -> d.processDom(MiniKanren.walk(s, u)).apply(s)).asGoal())
				.named(u + " ⊂ " + d);
	}

	private static <T> Option<Array<VarWithDomain<T>>> letDomain(Package p, Array<Unifiable<T>> us) {
		return Option.of(us.toJavaStream()
						.flatMap(u -> {
							Unifiable<T> v = MiniKanren.walk(p, u);
							if (v.isVal()) {
								return Stream.of(VarWithDomain.of(v, Singleton.of(Arithmetic.ofCasted(v.get()))));
							} else {
								return FiniteDomainConstraints.getDom(p, (LVar<T>) v)
										.map(d -> VarWithDomain.of(v, d))
										.toJavaStream();
							}
						})
						.collect(Array.collector()))
				.filter(uds -> uds.size() == us.size());
	}

	private static <T> PackageAccessor constraintOperation(PackageAccessor constraintOp, Array<Unifiable<T>> us, ConstraintBody<T> body) {
		return p -> {
			Package p1 = Constraint.of(constraintOp,
							us.map(u -> MiniKanren.walk(p, u))
									.map(Unifiable::getObjectUnifiable)
									.toJavaList())
					.addTo(FiniteDomainConstraints.register(p));
			return letDomain(p1, us)
					.filter(uds -> uds.toJavaStream()
							.noneMatch(ud -> ud.getDomain().isEmpty()))
					.map(uds -> body.create(uds, p1))
					.getOrElse(Option.of(p1));
		};
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	static class VarWithDomain<T> {
		Unifiable<T> unifiable;
		Domain<?> domain;

		@SuppressWarnings("unchecked")
		public <U> Domain<U> getDomain() {
			return (Domain<U>) domain;
		}
	}

	interface ConstraintBody<T> {
		Option<Package> create(Array<VarWithDomain<T>> vds, Package p);
	}

	public static <T> Goal leq(Unifiable<T> less, Unifiable<T> more) {
		return fdGoal()
				.and(leqFD(less, more).asGoal())
				.named(less + " ≤ " + more);
	}

	public static <T> Goal lss(Unifiable<T> less, Unifiable<T> more) {
		return fdGoal()
				.and(leqFD(less, more).asGoal())
				.and(separate(less, more))
				.named(less + " < " + more);
	}

	public static <T> Goal gtr(Unifiable<T> more, Unifiable<T> less) {
		return fdGoal()
				.and(leqFD(more, less).asGoal())
				.and(separate(more, less))
				.named(more + " > " + less);
	}

	public static <T> Goal geq(Unifiable<T> more, Unifiable<T> less) {
		return fdGoal()
				.and(leqFD(more, less).asGoal())
				.named(more + " ≥ " + less);
	}

	private static <T> PackageAccessor leqFD(Unifiable<T> less, Unifiable<T> more) {
		return constraintOperation(
				p -> leqFD(less, more).apply(p),
				Array.of(less, more), (vds, p) ->
						Tuple.of(vds.get(0), vds.get(1))
								.apply((lss, mor) ->
										lss.<T> getDomain().copyBefore(mor.<T> getDomain().max())
												.processDom(lss.unifiable)
												.compose(mor.<T> getDomain().dropBefore(lss.<T> getDomain().min())
														.processDom(mor.unifiable)))
								.apply(p));
	}

	public static <T> Goal addo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return fdGoal()
				.and(addoFD(a, b, c).asGoal())
				.named(a + " + " + b + " = " + c);
	}

	public static <T> Goal subtracto(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return addo(c, b, a)
				.named(a + " - " + b + " = " + c);
	}

	static <T> PackageAccessor addoFD(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return constraintOperation(
				p -> addoFD(a, b, rhs).apply(p),
				Array.of(a, b, rhs), (vds, p) ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) ->
										addIntervals(u, v, w,
												u.<T> getDomain().min(), v.<T> getDomain().min(), w.<T> getDomain().min(),
												u.<T> getDomain().max(), v.<T> getDomain().max(), w.<T> getDomain().max()))
								.apply(p));
	}

	private static <T> PackageAccessor addIntervals(
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

		return s -> wi.processDom(w.getUnifiable())
				.compose(vi.processDom(v.getUnifiable()))
				.compose(ui.processDom(u.getUnifiable()))
				.apply(s);
	}

	public static <T> Goal multo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c) {
		return fdGoal()
				.and(mulFD(a, b, c).asGoal())
				.named(a + " + " + b + " = " + c);
	}

	public static <T> Goal divo(Unifiable<T> divided, Unifiable<T> divisor, Unifiable<T> result) {
		return multo(result, divisor, divided)
				.named(divided + " / " + divisor + " = " + result);
	}

	private static <T> Option<Arithmetic<T>> div(Arithmetic<T> lhs, Arithmetic<T> rhs) {
		return Option.of(Tuple.of(lhs, rhs))
				.filter(t -> !t._2.isZero())
				.map(t -> t.apply(Arithmetic::div));
	}

	static <T> PackageAccessor mulFD(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return constraintOperation(
				p -> mulFD(a, b, rhs).apply(p),
				Array.of(a, b, rhs), (vds, p) ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) ->
										mulIntervals(u, v, w,
												u.<T> getDomain().min(), v.<T> getDomain().min(), w.<T> getDomain().min(),
												u.<T> getDomain().max(), v.<T> getDomain().max(), w.<T> getDomain().max())
												.apply(p)));
	}

	private static <T> PackageAccessor mulIntervals(
			VarWithDomain<T> u, VarWithDomain<T> v, VarWithDomain<T> w,
			Arithmetic<T> uMin, Arithmetic<T> vMin, Arithmetic<T> wMin,
			Arithmetic<T> uMax, Arithmetic<T> vMax, Arithmetic<T> wMax) {
		// all are numbers -> check multiplication
		if (uMin.equals(uMax) && vMin.equals(vMax) && wMin.equals(wMax)) {
			return PackageAccessor.filter(uMin.mul(vMin).compareTo(wMin) == 0);
		}

		// some are numbers -> do nothing until all generated
		if (uMin.equals(uMax) || vMin.equals(vMax) || wMin.equals(wMax)) {
			return PackageAccessor.identity();
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
			return wi.processDom(w.getUnifiable());
		}

		ui = Interval.normalized(
				div(wMin, vMax).getOrElse(uMin),
				div(wMax, vMin).getOrElse(uMax));

		vi = Interval.normalized(
				div(wMin, uMax).getOrElse(vMin),
				div(wMax, uMin).getOrElse(vMax));

		return s -> wi.processDom(w.getUnifiable())
				.compose(ui.processDom(u.getUnifiable()))
				.compose(vi.processDom(v.getUnifiable()))
				.apply(s);
	}

	public static <T> Goal separate(Unifiable<T> l, Unifiable<T> r) {
		return fdGoal()
				.and(separateFDC(l, r).asGoal())
				.named(l + " ≠_fd " + r);
	}

	private static <T> PackageAccessor separateFDC(Unifiable<T> l, Unifiable<T> r) {
		return s -> letDomain(s, Array.of(l, r))
				.map(ds -> Tuple.of(ds.get(0), ds.get(1)))
				.map(ds -> ds.apply((ld, rd) -> {
							Option<Tuple2<Arithmetic<T>, Arithmetic<T>>> zip = MiniKanren.zip(
									getSingleElement(ld.getDomain()),
									getSingleElement(rd.getDomain()));
							if (zip.isDefined() && zip.get().apply(Objects::equals)) {
								return Option.<Package> none();
							}
							if (ld.getDomain().isDisjoint(rd.getDomain())) {
								return Option.of(s);
							}
							Package a = withConstraint(s,
									Constraint.of(
											p -> separateFDC(l, r).apply(p),
											Arrays.asList(l, r)));
							if (ld.getDomain() instanceof Singleton) {
								return rd.<T> getDomain().difference(ld.getDomain())
										.processDom(r)
										.apply(a);
							} else if (rd.getDomain() instanceof Singleton) {
								return ld.<T> getDomain().difference(rd.getDomain())
										.processDom(l)
										.apply(a);
							} else {
								return Option.of(a);
							}
						}
				)).getOrElse(() -> Tuple.of(s.walk(l), s.walk(r))
						.apply((lv, rv) ->
								Option.of(withConstraint(s,
										Constraint.of(p ->
														separateFDC(lv, rv).apply(p),
												Arrays.asList(lv, rv))))));
	}

	private static <T> Option<Arithmetic<T>> getSingleElement(Domain<T> dom) {
		return Option.of(dom)
				.flatMap(Types.<Singleton<T>> castAs(Singleton.class))
				.map(Singleton::getValue);
	}

	private static Goal fdGoal() {
		return s -> Step.single(FiniteDomainConstraints.register(s));
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
		Supplier<IllegalArgumentException> noDomain =
				Exceptions.format(IllegalArgumentException::new, "%s has no domain", from);
		return Goal.goal(s -> from.asVar()
						.flatMap(l -> FiniteDomainConstraints.getDom(s, l))
						.orElse(() -> s.walk(from).asVal()
								.map(Arithmetic::ofCasted)
								.map(Singleton::of))
						.getOrElse(() -> Singleton.of(Arithmetic.ofCasted(from.get())))
						.processDom(to)
						.andThen(Step::of)
						.apply(s))
				.named(String.format("copyDom(%s, %s)", from, to));
	}
}
