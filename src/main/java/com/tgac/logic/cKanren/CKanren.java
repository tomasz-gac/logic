package com.tgac.logic.cKanren;

import com.tgac.functional.recursion.Recur;
import com.tgac.logic.Goal;
import com.tgac.logic.MiniKanren;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.stream.StreamSupport;

import static com.tgac.functional.recursion.Recur.done;
import static com.tgac.logic.Incomplete.incomplete;
import static com.tgac.logic.MiniKanren.prefixS;
import static com.tgac.logic.MiniKanren.reifyS;
import static com.tgac.logic.MiniKanren.walkAll;
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CKanren {
	public static Goal constructGoal(PackageAccessor accessor) {
		return s -> accessor.apply(s).toStream();
	}

	private static <T> PackageAccessor unifyC(Unifiable<T> u, Unifiable<T> v) {
		return s -> MiniKanren.unify(s, u, v)
				.flatMap(s1 -> s == s1 ?
						Option.of(s) :
						s.processPrefix(prefixS(
										s.getSubstitutions(),
										s1.getSubstitutions()))
								.apply(s.withSubstitutionsFrom(s1)));
	}

	public static <T> Goal unify(Unifiable<T> u, Unifiable<T> v) {
		return constructGoal(unifyC(u, v));
	}

	public static PackageAccessor runConstraints(Unifiable<?> xs, List<Constraint> c) {
		return c.toJavaStream()
				.flatMap(constraint -> Option.of(remRun(constraint))
						.filter(__ -> anyRelevantVar(xs, constraint))
						.toJavaStream())
				.reduce(PackageAccessor.identity(),
						PackageAccessor::compose);
	}

	private static PackageAccessor remRun(Constraint c) {
		return p -> p.get(c.getTag()).contains(c) ?
				c.apply(p.withoutConstraint(c)) :
				Option.of(p);
	}

	public static <T> Stream<Unifiable<T>> reify(Package s, Unifiable<T> x) {
		return s.enforceConstraints(x).apply(s)
				.flatMap(s1 -> incomplete(() -> calculateSubstitutionAndRenamePackage(x, s1)
						.flatMap(vr ->
								vr._2.getSubstitutions().isEmpty() ?
										done(vr._1) :
										walkAll(vr._2, vr._1)
												.map(result ->
														s1.getConstraints().isEmpty() ?
																result :
																reifyConstraints(s1, vr, result)))
						.map(Stream::of)));
	}

	private static <T> Unifiable<T> reifyConstraints(Package s1, Tuple2<Unifiable<T>, Package> vr, Unifiable<T> result) {
		return s1.reify(result, vr._2).get();
	}
	private static <T> Recur<Tuple2<Unifiable<T>, Package>> calculateSubstitutionAndRenamePackage(Unifiable<T> x, Package s1) {
		return walkAll(s1, x)
				.flatMap(v -> reifyS(Package.empty(), v)
						.map(r -> Tuple.of(v, r)));
	}

	private static boolean anyRelevantVar(Unifiable<?> xs, Constraint c) {
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
