package com.tgac.logic.ckanren;

import static com.tgac.logic.ckanren.StoreSupport.enforceConstraints;
import static com.tgac.logic.ckanren.StoreSupport.getConstraintStore;
import static com.tgac.logic.ckanren.StoreSupport.processPrefix;
import static com.tgac.logic.ckanren.StoreSupport.withoutConstraint;

import com.tgac.functional.recursion.Recur;
import com.tgac.functional.step.Step;
import com.tgac.logic.Goal;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import java.util.Collection;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.var;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CKanren {

	public static <T> Goal unify(Unifiable<T> u, Unifiable<T> v) {
		Goal goal = s -> Step.of(MiniKanren.unify(s, u, v)
				.flatMap(s1 -> s == s1 ?
						Option.of(s) :
						processPrefix(s, s1.getSubstitutions())));
		return goal.named(u + " ≣ " + v);
	}

	public static <T> Goal unifyNc(Unifiable<T> u, Unifiable<T> v) {
		Goal goal = s -> Step.of(MiniKanren.unifyUnsafe(s, u, v)
				.flatMap(s1 -> s == s1 ?
						Option.of(s) :
						processPrefix(s, s1.getSubstitutions())));
		return goal.named(u + " ≣_nc " + v);
	}

	public static <T> Goal unify(Unifiable<T> u, T v) {
		return unify(u, LVal.lval(v));
	}

	public static <T> Goal unifyNc(Unifiable<T> u, T v) {
		return unifyNc(u, LVal.lval(v));
	}

	public static PackageAccessor runConstraints(Unifiable<?> xs, Iterable<Constraint> c) {
		return StreamSupport.stream(c.spliterator(), false)
				.map(constraint -> anyRelevantVar(xs, constraint) ?
						remRun(constraint) :
						PackageAccessor.identity())
				.reduce(PackageAccessor.identity(),
						PackageAccessor::compose);
	}

	private static PackageAccessor remRun(Constraint c) {
		return p -> getConstraintStore(p, c.getStoreClass()).contains(c) ?
				c.apply(withoutConstraint(p, c)) :
				Option.of(p);
	}

	public static <T> Step<Unifiable<T>> reify(Package s, Unifiable<T> x) {
		return enforceConstraints(s, x).apply(s)
				.flatMap(s1 -> Step.incomplete(() ->
						calculateSubstitutionAndRenamePackage(x, s1)
								.flatMap(vr -> vr.apply((v, r) ->
										r.getSubstitutions().isEmpty() ?
												Recur.done(v) :
												MiniKanren.walkAll(r, v)
														.map(result ->
																s1.getConstraints() == null ?
																		result :
																		StoreSupport.reify(s1, result, vr._2))))
								.map(Step::single)));
	}

	public static <T> Recur<Tuple2<Unifiable<T>, Package>> calculateSubstitutionAndRenamePackage(Unifiable<T> x, Package s1) {
		return MiniKanren.walkAll(s1, x)
				.flatMap(v -> MiniKanren.reifyS(Package.empty(), v)
						.map(r -> Tuple.of(v, r)));
	}

	private static boolean anyRelevantVar(Unifiable<?> xs, Constraint c) {
		return isVarRelevant(xs, c)
				|| isAnyItemRelevantCollection(xs, c)
				|| isAnyItemRelevantLList(xs, c);
	}

	private static boolean isAnyItemRelevantLList(Unifiable<?> xs, Constraint c) {
		return xs.isVal() &&
				MiniKanren.asLList(xs)
						.filter(l -> l.stream()
								.anyMatch(e -> e.fold(
										c.getArgs()::contains,
										c.getArgs()::contains)))
						.isDefined();
	}

	private static Option<Iterable<Object>> asIterable(Object w) {
		return MiniKanren.asIterable(w)
				.orElse(() -> MiniKanren.tupleAsIterable(w));
	}

	private static boolean isAnyItemRelevantCollection(Unifiable<?> xs, Constraint c) {
		if (!xs.isVal()) {
			return false;
		}
		Object w = xs.get();

		if (w instanceof Collection) {
			return processCollection((Collection<?>) w, c);
		} else {
			return processIterable(w, c);
		}
	}

	private static boolean processIterable(Object w, Constraint c) {
		return asIterable(w)
				.filter(it -> StreamSupport.stream(it.spliterator(), false)
						.map(MiniKanren::wrapUnifiable)
						.anyMatch(c.getArgs()::contains))
				.isDefined();
	}

	private static boolean processCollection(Collection<?> collection, Constraint c) {
		for (var arg : c.getArgs()) {
			if (collection.contains(arg)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isVarRelevant(Unifiable<?> xs, Constraint c) {
		return xs.asVar()
				.filter(c.getArgs()::contains)
				.isDefined();
	}
}
