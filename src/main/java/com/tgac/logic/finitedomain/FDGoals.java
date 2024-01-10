package com.tgac.logic.finitedomain;

import com.tgac.functional.reflection.Types;
import com.tgac.logic.Goal;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.ckanren.RunnableConstraint;
import com.tgac.logic.finitedomain.domains.EnumeratedInterval;
import com.tgac.logic.finitedomain.domains.FiniteDomain;
import com.tgac.logic.finitedomain.domains.SingletonFD;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.collection.Array;
import io.vavr.collection.HashSet;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FDGoals {

	public static <T extends Comparable<T>> Goal dom(Unifiable<T> u, FiniteDomain<T> d) {
		return CKanren.constructGoal(a ->
						d.processDom(MiniKanren.walk(a, u)).apply(a))
				.named(u + " ⊂ " + d);
	}

	private static <T extends Comparable<T>> Option<Array<VarWithDomain<T>>> letDomain(Package p, Array<Unifiable<T>> us) {
		return Option.of(us.toJavaStream()
						.map(u -> Option.of(MiniKanren.walk(p, u))
								.flatMap(v -> v.asVar()
										.flatMap(vv -> FiniteDomainConstraints.getDom(p, vv))
										.flatMap(Types.<FiniteDomain<T>> castAs(EnumeratedInterval.class))
										.orElse(() -> v.asVal().map(SingletonFD::new))
										.map(ds -> VarWithDomain.of(v, ds))))
						.flatMap(Option::toJavaStream)
						.collect(Array.collector()))
				.filter(uds -> uds.size() == us.size());
	}

	private static <T extends Comparable<T>> PackageAccessor constraintOperation(PackageAccessor constraintOp, Array<Unifiable<T>> us, ConstraintBody<T> body) {
		return p -> Tuple.of(
						RunnableConstraint.of(
										FiniteDomainConstraints.class,
										constraintOp,
										us.map(Unifiable::getObjectUnifiable))
								.addTo(p))
				.map(p1 -> letDomain(p, us)
						.filter(uds -> uds.toJavaStream()
								.noneMatch(ud -> ud.getDomain().isEmpty()))
						.map(uds -> body.create(uds, p1))
						.getOrElse(Option.of(p1)))
				._1;
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	static class VarWithDomain<T> {
		Unifiable<T> unifiable;
		FiniteDomain<?> domain;

		public <U extends Comparable<U>> FiniteDomain<U> getDomain() {
			return (FiniteDomain<U>) domain;
		}
	}

	interface ConstraintBody<T> {
		Option<Package> create(Array<VarWithDomain<T>> vds, Package p);
	}

	public static Goal leq(Unifiable<Long> less, Unifiable<Long> more) {
		return CKanren.constructGoal(leqFD(less, more))
				.named(less + " <= " + more);
	}

	private static <T extends Comparable<T>> PackageAccessor leqFD(Unifiable<T> less, Unifiable<T> more) {
		return constraintOperation(
				p -> leqFD(less, more).apply(p),
				Array.of(less, more), (vds, p) ->
						Tuple.of(vds.get(0), vds.get(1))
								.apply((lss, mor) ->
										lss.<T> getDomain().copyBefore(e -> mor.<T> getDomain().max().compareTo(e) < 0)
												.processDom(lss.unifiable)
												.compose(mor.<T> getDomain().dropBefore(e -> lss.<T> getDomain().min().compareTo(e) <= 0)
														.processDom(mor.unifiable)))
								.apply(p));
	}

	public static Goal sum(Unifiable<Long> a, Unifiable<Long> b, Unifiable<Long> c) {
		return CKanren.constructGoal(sumFD(a, b, c))
				.named(a + " + " + b + " = " + c);
	}

	static PackageAccessor sumFD(Unifiable<Long> a, Unifiable<Long> b, Unifiable<Long> rhs) {
		return constraintOperation(
				p -> sumFD(a, b, rhs).apply(p),
				Array.of(a, b, rhs), (vds, p) ->
						impl(vds, p));
	}
	private static Option<Package> impl(Array<VarWithDomain<Long>> vds, Package p) {
		return Tuple.of(vds.get(0), vds.get(1), vds.get(2))
				.apply((u, v, w) -> Tuple.of(
								u.<Long> getDomain().min(), v.<Long> getDomain().min(), w.<Long> getDomain().min(),
								u.<Long> getDomain().max(), v.<Long> getDomain().max(), w.<Long> getDomain().max())
						.apply((uMin, vMin, wMin, uMax, vMax, wMax) ->
								EnumeratedInterval.of(HashSet.range(
												uMin + vMin,
												uMax + vMax + 1))
										.processDom(w.getUnifiable())
										.compose(EnumeratedInterval.of(HashSet.range(
														wMin - uMax,
														wMax - uMin + 1))
												.processDom(v.getUnifiable()))
										.compose(EnumeratedInterval.of(HashSet.range(
														wMin - vMax,
														wMax - vMin + 1))
												.processDom(u.getUnifiable()))
										.apply(p))
				);
	}
}
