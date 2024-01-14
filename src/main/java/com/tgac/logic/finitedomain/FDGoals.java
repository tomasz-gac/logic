package com.tgac.logic.finitedomain;

import com.tgac.functional.reflection.Types;
import com.tgac.logic.Goal;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.ckanren.RunnableConstraint;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.Domain;
import com.tgac.logic.finitedomain.domains.SimpleInterval;
import com.tgac.logic.finitedomain.domains.Singleton;
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

import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FDGoals {

	public static <T> Goal dom(Unifiable<T> u, Domain<T> d) {
		return CKanren.constructGoal(s -> {
					Package a = FiniteDomainConstraints.register(s);
					return d.processDom(MiniKanren.walk(a, u)).apply(a);
				})
				.named(u + " ⊂ " + d);
	}

	private static <T> Option<Array<VarWithDomain<T>>> letDomain(Package p, Array<Unifiable<T>> us) {
		return Option.of(us.toJavaStream()
						.map(u -> Option.of(MiniKanren.walk(p, u))
								.flatMap(v -> v.asVar()
										.flatMap(vv -> FiniteDomainConstraints.getDom(p, vv))
										.orElse(() -> v.asVal()
												.map(Arithmetic::ofCasted)
												.map(Singleton::of))
										.map(ds -> VarWithDomain.of(v, ds))))
						.flatMap(Option::toJavaStream)
						.collect(Array.collector()))
				.filter(uds -> uds.size() == us.size());
	}

	private static <T> PackageAccessor constraintOperation(PackageAccessor constraintOp, Array<Unifiable<T>> us, ConstraintBody<T> body) {
		return p -> Tuple.of(
						RunnableConstraint.of(constraintOp, us.map(Unifiable::getObjectUnifiable))
								.addTo(FiniteDomainConstraints.register(p)))
				.map(p1 -> letDomain(p1, us)
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
		Domain<?> domain;

		@SuppressWarnings("unchecked")
		public <U> Domain<U> getDomain() {
			return (Domain<U>) domain;
		}
	}

	interface ConstraintBody<T> {
		Option<Package> create(Array<VarWithDomain<T>> vds, Package p);
	}

	public static Goal leq(Unifiable<Long> less, Unifiable<Long> more) {
		return CKanren.constructGoal(leqFD(less, more))
				.named(less + " <= " + more);
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

	public static Goal sum(Unifiable<Long> a, Unifiable<Long> b, Unifiable<Long> c) {
		return CKanren.constructGoal(sumFD(a, b, c))
				.named(a + " + " + b + " = " + c);
	}

	static <T> PackageAccessor sumFD(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return constraintOperation(
				p -> sumFD(a, b, rhs).apply(p),
				Array.of(a, b, rhs), (vds, p) ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) -> Tuple.of(
												u.<T> getDomain().min(), v.<T> getDomain().min(), w.<T> getDomain().min(),
												u.<T> getDomain().max(), v.<T> getDomain().max(), w.<T> getDomain().max())
										.apply((uMin, vMin, wMin, uMax, vMax, wMax) ->
												SimpleInterval.of(
																uMin.add(vMin),
																uMax.add(vMax.next()))
														.processDom(w.getUnifiable())
														.compose(SimpleInterval.of(
																		wMin.subtract(uMax),
																		wMax.subtract(uMin).next())
																.processDom(v.getUnifiable()))
														.compose(SimpleInterval.of(
																		wMin.subtract(vMax),
																		wMax.subtract(vMin).next())
																.processDom(u.getUnifiable()))
														.apply(p))
								));
	}

	public static Goal separateFD(Unifiable<Long> l, Unifiable<Long> r) {
		return CKanren.constructGoal(separateFDC(l, r))
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
							Package a = s.withConstraint(
									RunnableConstraint.of(
											p -> separateFDC(l, r).apply(p),
											Array.of(l, r)));
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
				))
				.getOrElse(() -> Option.of(s.withConstraint(
						RunnableConstraint.of(
								p -> separateFDC(l, r).apply(p),
								Array.of(l, r)))));
	}

	private static <T> Option<Arithmetic<T>> getSingleElement(Domain<T> dom) {
		return Option.of(dom)
				.flatMap(Types.<Singleton<T>> castAs(Singleton.class))
				.map(Singleton::getValue);
	}

}
