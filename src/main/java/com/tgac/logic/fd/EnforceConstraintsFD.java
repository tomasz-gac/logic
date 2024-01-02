package com.tgac.logic.fd;
import com.tgac.functional.Exceptions;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.Goal;
import com.tgac.logic.Incomplete;
import com.tgac.logic.LList;
import com.tgac.logic.LVar;
import com.tgac.logic.MiniKanren;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.CKanren;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.Domain;
import com.tgac.logic.cKanren.parameters.EnforceConstraints;
import io.vavr.Tuple;
import io.vavr.collection.List;

import java.util.stream.StreamSupport;

import static com.tgac.functional.Exceptions.format;
import static com.tgac.logic.LVal.lval;
public class EnforceConstraintsFD implements EnforceConstraints {
	@Override
	public Goal enforce(Unifiable<?> x) {
		return a -> forceAns(x)
				.and(a1 -> Tuple.of(a1.getDomains().keySet().toList())
						.apply(xs -> {
							verifyAllConstrainedHaveDomain(a1.getConstraints(), xs);
							return Goal.condu(Goal.defer(() -> forceAns(LList.ofAll(xs))));
						}).apply(a1))
				.apply(a);
	}

	public static Goal forceAns(Unifiable<?> x) {
		return s -> Incomplete.incomplete(() -> MiniKanren.walk(s, x)
				.map(v -> v.asVar()
						.flatMap(s::getDomain)
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
				.map(g -> g.apply(s)));
	}

	private static Goal rerunConstraints(Unifiable<?> x) {
		return a -> Incomplete.incomplete(() ->
				CKanren.runConstraints(x, a.getConstraints())
						.map(CKanren::constructGoal)
						.map(g -> g.apply(a)));
	}

	private static Goal unifyWithAllDomainValues(Unifiable<?> x, Domain d) {
		return d.stream()
				.map(domainValue -> Goal.unify(x.getObjectUnifiable(), lval(domainValue)))
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

	private static void verifyAllConstrainedHaveDomain(List<Constraint> constraints, List<LVar<?>> boundVariables) {
		constraints.toJavaStream()
				.flatMap(c -> c.getArgs().toJavaStream())
				.filter(u -> u.asVal().isDefined())
				.filter(x -> x.asVar()
						.filter(v -> !boundVariables.contains(v))
						.isDefined())
				.findAny()
				.ifPresent(Exceptions.throwingConsumer(format(IllegalStateException::new, "Unbound variable")));
	}
}
