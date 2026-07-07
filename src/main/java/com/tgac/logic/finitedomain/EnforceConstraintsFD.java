package com.tgac.logic.finitedomain;
import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.ckanren.Propagator;
import com.tgac.logic.ckanren.Propagation;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.tgac.logic.finitedomain.FiniteDomainConstraints.getFDStore;
import static com.tgac.logic.unification.LVal.lval;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class EnforceConstraintsFD {

	public static <T> Goal enforceConstraints(Term<T> x) {
		return a -> forceAns(x)
				.and(a1 -> Tuple.of(getFDStore(a1).getDomains()
								.keySet()
								.toJavaStream()
								.collect(Collectors.toSet()))
						.apply(xs -> {
							verifyAllConstrainedHaveDomain(getFDStore(a1).getConstraints(), xs);
							return Goal.condu(Goal.defer(() -> forceAns(lval(xs))));
						})
						.apply(a1))
				.apply(a);
	}

	public static <T> Goal forceAns(Term<T> x) {
		return s -> Fiber.done(MiniKanren.walk(s, x))
				.map(v -> v.asVar()
						.flatMap(vv -> FiniteDomainConstraints.getDom(s, vv))
						.map(d -> unifyWithAllDomainValues(x, d))
						.orElse(() -> forceAnsAndRerunConstraintsIterable(v))
						.orElse(() -> forceAnsAndRerunConstraintsLList(v))
						.getOrElse(Goal::success))
				.map(g -> g.apply(s))
				.get();
	}

	private static <T> Option<Goal> forceAnsAndRerunConstraintsLList(Term<T> v) {
		return v.asVal()
				.flatMap(w -> Types.cast(w, LList.class))
				.map(EnforceConstraintsFD::forceAnsLList)
				.map(g -> g.and(rerunConstraints(v)));
	}

	private static <T> Option<Goal> forceAnsAndRerunConstraintsIterable(Term<T> v) {
		return v.asVal()
				.flatMap(w -> MiniKanren.asIterable(w)
						.orElse(() -> MiniKanren.tupleAsIterable(w)))
				.map(EnforceConstraintsFD::forceAnsIterable)
				.map(goal -> goal.and(rerunConstraints(v)));
	}

	private static Goal rerunConstraints(Term<?> x) {
		return Propagation.wake(x);
	}

	private static <T> Goal unifyWithAllDomainValues(Term<T> x, Domain<T> d) {
		return d.stream()
				.map(domainValue -> unifyTerms((Term<Object>) x, lval(domainValue)))
				.reduce(Goal::or)
				.orElseGet(Goal::failure);
	}

	// because of ambiguity in CKanren
	private static <T> Goal unifyTerms(Term<T> u, Unifiable<T> v) {
		return s -> Cont.defer(() -> MiniKanren.unifyPrefix(s, u, v)
				.map(prefix -> Propagation.resolve(prefix).apply(s))
				.getOrElse(() -> Cont.complete(Nothing.nothing())));
	}

	private static Goal forceAnsIterable(Iterable<Object> iterable) {
		return StreamSupport.stream(iterable.spliterator(), false)
				.map(MiniKanren::wrapTerm)
				.map(u -> Goal.defer(() -> forceAns(u)))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	private static Goal forceAnsLList(LList<?> llist) {
		if (llist.isEmpty()) {
			return Goal.success();
		} else {
			// since we're building goals - this will not recurse
			return forceAns(llist.getHead())
					.and(Goal.defer(() -> forceAns(llist.getTail())));
		}
	}

	private static void verifyAllConstrainedHaveDomain(Iterable<Propagator> constraints, Collection<LVar<?>> boundVariables) {
		StreamSupport.stream(constraints.spliterator(), false)
				.flatMap(c -> StreamSupport.stream(c.watchedTerms().spliterator(), false))
				.filter(u -> u.asVal().isDefined())
				.filter(x -> x.asVar()
						.filter(v -> !boundVariables.contains(v))
						.isDefined())
				.findAny()
				.ifPresent(Exceptions.throwingConsumer(Exceptions.format(IllegalStateException::new, "Unbound variable")));
	}
}
