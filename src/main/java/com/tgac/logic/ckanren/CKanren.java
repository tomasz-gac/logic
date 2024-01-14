package com.tgac.logic.ckanren;

import com.tgac.functional.recursion.Recur;
import com.tgac.logic.Goal;
import com.tgac.logic.Incomplete;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.stream.StreamSupport;
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CKanren {
	public static Goal constructGoal(PackageAccessor accessor) {
		return s -> accessor.apply(s).toStream();
	}

	public static <T> Goal unify(Unifiable<T> u, Unifiable<T> v) {
		return s -> MiniKanren.unify(s, u, v)
				.flatMap(s1 -> s == s1 ?
						Option.of(s) :
						s.processPrefix(s1.getSubstitutions()))
				.toStream();
	}

	public static <T> Goal unifyNc(Unifiable<T> u, Unifiable<T> v) {
		return s -> MiniKanren.unifyUnsafe(s, u, v)
				.flatMap(s1 -> s == s1 ?
						Option.of(s) :
						s.processPrefix(s1.getSubstitutions()))
				.toStream();
	}

	public static <T> Goal unify(Unifiable<T> u, T v) {
		return unify(u, LVal.lval(v));
	}

	public static <T> Goal unifyNc(Unifiable<T> u, T v) {
		return unifyNc(u, LVal.lval(v));
	}

	public static PackageAccessor runConstraints(Unifiable<?> xs, List<RunnableConstraint> c) {
		return c.toJavaStream()
				.flatMap(constraint -> Option.of(remRun(constraint))
						.filter(__ -> anyRelevantVar(xs, constraint))
						.toJavaStream())
				.reduce(PackageAccessor.identity(),
						PackageAccessor::compose);
	}

	private static PackageAccessor remRun(RunnableConstraint c) {
		return p -> p.getConstraintStore().contains(c) ?
				c.apply(p.withoutConstraint(c)) :
				Option.of(p);
	}

	public static <T> Stream<Unifiable<T>> reify(Package s, Unifiable<T> x) {
		return s.enforceConstraints(x).apply(s)
				.flatMap(s1 -> Incomplete.incomplete(() ->
						calculateSubstitutionAndRenamePackage(x, s1)
								.flatMap(vr -> vr.apply((v, r) ->
										r.getSubstitutions().isEmpty() ?
												Recur.done(v) :
												MiniKanren.walkAll(r, v)
														.map(result ->
																s1.getConstraints() == null ?
																		result :
																		s1.reify(result, vr._2).get())))
								.map(Stream::of)));
	}

	public static <T> Recur<Tuple2<Unifiable<T>, Package>> calculateSubstitutionAndRenamePackage(Unifiable<T> x, Package s1) {
		return MiniKanren.walkAll(s1, x)
				.flatMap(v -> MiniKanren.reifyS(Package.empty(), v)
						.map(r -> Tuple.of(v, r)));
	}

	private static boolean anyRelevantVar(Unifiable<?> xs, RunnableConstraint c) {
		return xs.asVar()
				.filter(c.getArgs()::contains)
				.isDefined()
				||
				xs.asVal()
						.flatMap(w -> MiniKanren.asIterable(w)
								.orElse(() -> MiniKanren.tupleAsIterable(w)))
						.filter(it -> StreamSupport.stream(it.spliterator(), false)
								.map(MiniKanren::wrapUnifiable)
								.anyMatch(c.getArgs()::contains))
						.isDefined()
				||
				xs.asVal()
						.flatMap(MiniKanren::asLList)
						.filter(l -> l.stream()
								.anyMatch(e -> e.fold(
										c.getArgs()::contains,
										c.getArgs()::contains)))
						.isDefined();
	}
}
