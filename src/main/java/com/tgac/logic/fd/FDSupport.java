package com.tgac.logic.fd;

import com.tgac.functional.Exceptions;
import com.tgac.functional.recursion.MRecur;
import com.tgac.functional.recursion.Recur;
import com.tgac.logic.Goal;
import com.tgac.logic.Incomplete;
import com.tgac.logic.MiniKanren;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.CKanren;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.PackageAccessor;
import com.tgac.logic.fd.parameters.EnforceConstraintsFD;
import com.tgac.logic.fd.parameters.ProcessPrefixFd;
import com.tgac.logic.fd.parameters.ReifyConstraintsFD;
import io.vavr.Tuple;
import io.vavr.collection.Array;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FDSupport {

	public static void useFD() {
		CKanren.PROCESS_PREFIX.set(new ProcessPrefixFd());
		CKanren.ENFORCE_CONSTRAINTS.set(new EnforceConstraintsFD());
		CKanren.REIFY_CONSTRAINTS.set(new ReifyConstraintsFD());
	}

	public static <T extends Comparable<T>> Goal dom(Unifiable<T> u, FiniteDomain d) {
		return CKanren.constructGoal(a ->
				d.processDom(MiniKanren.walk(a, u)).apply(a));
	}

	static Stream<VarWithDomain> letDomain(Package p, Array<Unifiable<?>> us) {
		return us.toStream()
				.flatMap(u -> Incomplete.incomplete(() ->
						Recur.done(MiniKanren.walk(p, u.getObjectUnifiable()))
								.map(v -> VarWithDomain.of(v, v.asVar()
										.flatMap(p::getDomain)
										.map(d -> (FiniteDomain) d)
										.orElse(() -> v.asVar().isDefined() ?
												Exceptions.throwNow(new IllegalArgumentException(
														String.format("Missing domain for argument %s", us.indexOf(u)))) :
												Option.none())
										.getOrElse(() -> new SingletonFD((long) v.asVal().get()))))
								.map(Stream::of)));
	}

	static PackageAccessor constraintOperation(PackageAccessor constraintOp, Array<Unifiable<?>> us, ConstraintBody body) {
		return p -> Tuple.of(letDomain(p, us)
								.collect(Array.collector()),
						MRecur.ofRecur(p.withConstraint(Constraint.buildOc(constraintOp, us))))
				.apply((uds, p1) ->
						uds.toJavaStream()
								.noneMatch(ud -> ud.getDomain().isEmpty()) ?
								p1.flatMap(p1v -> body.create(uds, p1v)) :
								p1);
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	static class VarWithDomain {
		Unifiable<?> unifiable;
		FiniteDomain domain;
	}

	interface ConstraintBody {
		MRecur<Package> create(Array<VarWithDomain> vds, Package p);
	}

	public static <T> Goal leq(Unifiable<T> less, Unifiable<T> more) {
		return CKanren.constructGoal(leqFD(less, more));
	}

	static <T> PackageAccessor leqFD(Unifiable<T> less, Unifiable<T> more) {
		return constraintOperation(
				p -> leqFD(less, more).apply(p),
				Array.of(less, more), (vds, p) ->
						Tuple.of(vds.get(0), vds.get(1))
								.apply((lss, mor) ->
										lss.domain.copyBefore(e -> mor.domain.max() < e)
												.processDom(lss.unifiable)
												.compose(mor.domain.dropBefore(e -> lss.domain.min() <= e)
														.processDom(mor.unifiable)))
								.apply(p));
	}

}
