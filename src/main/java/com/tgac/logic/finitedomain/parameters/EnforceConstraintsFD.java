package com.tgac.logic.finitedomain.parameters;
import com.tgac.functional.Exceptions;
import com.tgac.functional.recursion.Recur;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.Goal;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.ckanren.RunnableConstraint;
import com.tgac.logic.finitedomain.FiniteDomainConstraints;
import com.tgac.logic.finitedomain.domains.Domain;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.collection.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.stream.StreamSupport;

import static com.tgac.logic.finitedomain.FiniteDomainConstraints.getFDStore;
import static com.tgac.logic.unification.LVal.lval;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EnforceConstraintsFD {

	public static <T> Goal enforceConstraints(Unifiable<T> x) {
		return a -> forceAns(x)
				.and(a1 -> Tuple.of(getFDStore(a1).getDomains().keySet().toList())
						.apply(xs -> {
							verifyAllConstrainedHaveDomain(getFDStore(a1).getConstraints(), xs);
							return Goal.condu(Goal.defer(() -> forceAns(LList.ofAll(xs))));
						}).apply(a1))
				.apply(a);
	}

	public static <T> Goal forceAns(Unifiable<T> x) {
		return s -> Recur.done(MiniKanren.walk(s, x))
				.map(v -> v.asVar()
						.flatMap(vv -> FiniteDomainConstraints.getDom(s, vv))
						.map(d -> unifyWithAllDomainValues(x, d))
						.orElse(() -> v.asVal()
								.flatMap(w -> MiniKanren.asIterable(w)
										.orElse(() -> MiniKanren.tupleAsIterable(w)))
								.map(EnforceConstraintsFD::forceAnsIterable)
								.map(g -> g.and(rerunConstraints(v))))
						.orElse(() -> v.asVal()
								.flatMap(w -> Types.cast(w, LList.class))
								.map(EnforceConstraintsFD::forceAnsLList)
								.map(g -> g.and(rerunConstraints(v))))
						.getOrElse(Goal::success))
				.map(g -> g.apply(s))
				.get();
	}

	private static Goal rerunConstraints(Unifiable<?> x) {
		return a -> CKanren.constructGoal(CKanren.runConstraints(x,
						getFDStore(a).getConstraints()))
				.apply(a);
	}

	private static <T> Goal unifyWithAllDomainValues(Unifiable<T> x, Domain<T> d) {
		return d.stream()
				.map(domainValue -> CKanren.constructGoal(s -> MiniKanren.unify(s, x.getObjectUnifiable(), lval(domainValue))))
				.reduce(Goal::or)
				.orElseGet(Goal::failure);
	}
	private static Goal forceAnsIterable(Iterable<Object> iterable) {
		return StreamSupport.stream(iterable.spliterator(), false)
				.map(MiniKanren::wrapUnifiable)
				.map(u -> Goal.defer(() -> forceAns(u)))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	private static Goal forceAnsLList(LList<?> llist) {
		if (llist.isEmpty()) {
			return Goal.success();
		} else {
			return forceAns(llist.getHead())
					.or(Goal.defer(() -> forceAns(llist.getTail())));
		}
	}

	private static void verifyAllConstrainedHaveDomain(List<RunnableConstraint> constraints, List<LVar<?>> boundVariables) {
		constraints.toJavaStream()
				.flatMap(c -> c.getArgs().toJavaStream())
				.filter(u -> u.asVal().isDefined())
				.filter(x -> x.asVar()
						.filter(v -> !boundVariables.contains(v))
						.isDefined())
				.findAny()
				.ifPresent(Exceptions.throwingConsumer(Exceptions.format(IllegalStateException::new, "Unbound variable")));
	}
}
