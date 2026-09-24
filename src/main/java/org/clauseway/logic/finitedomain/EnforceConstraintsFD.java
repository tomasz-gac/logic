package org.clauseway.logic.finitedomain;

import static org.clauseway.logic.unification.terms.LVal.lval;

import org.clauseway.functional.Exceptions;
import org.clauseway.functional.Nothing;
import org.clauseway.functional.fibers.Fiber;
import org.clauseway.functional.fibers.Cont;
import org.clauseway.logic.constraints.Propagation;
import org.clauseway.logic.goals.Conde;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.lattice.Propagator;
import org.clauseway.logic.unification.MiniKanren;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.functional.tuples.Tuple;
import io.vavr.control.Option;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class EnforceConstraintsFD {

	public static <T> Goal enforceConstraints(Term<T> x) {
		return a -> forceAns(x)
				.and(a1 -> Tuple.of(FiniteDomainConstraints.getDomains(a1)
								.keySet()
								.toJavaStream()
								.collect(Collectors.toSet()))
						.apply(xs -> {
							verifyAllConstrainedHaveDomain(FiniteDomainConstraints.getConstraints(a1), xs);
							// the sweep forces each domain-carrying var directly:
							// the vars are the store's, not an answer structure —
							// no term wrapping, no structural walk
							return Goal.condu(Goal.defer(() -> forceAnsEach(xs)));
						})
						.apply(a1))
				.apply(a);
	}

	private static Goal forceAnsEach(Collection<Term<?>> xs) {
		return xs.stream()
				.map(u -> Goal.defer(() -> forceAns(u)))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	public static <T> Goal forceAns(Term<T> x) {
		return s -> Cont.defer(() -> Fiber.done(s.walk(x))
				.map(v -> v.asVar()
						.flatMap(vv -> FiniteDomainConstraints.getDom(s, vv))
						.map(d -> unifyWithAllDomainValues(x, d))
						.orElse(() -> forceAnsMembers(v))
						.getOrElse(Goal::success))
				.map(g -> g.apply(s)));
	}

	/**
	 * One structural gate: whatever {@link MiniKanren#members} recognizes —
	 * collections, tuples, LList, LTree, the unifier's own decomposition —
	 * enforcement walks, member by member.
	 */
	private static <T> Option<Goal> forceAnsMembers(Term<T> v) {
		return MiniKanren.members(v)
				.map(members -> StreamSupport.stream(members.spliterator(), false)
						.map(u -> Goal.defer(() -> forceAns(u)))
						.reduce(Goal::and)
						.orElseGet(Goal::success))
				.map(g -> g.and(rerunConstraints(v)));
	}

	private static Goal rerunConstraints(Term<?> x) {
		return FiniteDomainConstraints.reexamine(x);
	}

	@SuppressWarnings("unchecked")
	private static <T> Goal unifyWithAllDomainValues(Term<T> x, Domain<T> d) {
		List<Goal> alternatives = d.stream()
				.map(domainValue -> unifyTerms((Term<Object>) x, lval(domainValue)))
				.collect(Collectors.toList());
		return alternatives.isEmpty() ?
				Goal.failure() :
				Conde.of(alternatives);
	}

	// because of ambiguity in Constraints
	private static <T> Goal unifyTerms(Term<T> u, Unifiable<T> v) {
		return s -> Cont.defer(() -> MiniKanren.unifyPrefix(s.substitution(), u, v)
				.map(prefix -> Propagation.resolve(prefix).apply(s))
				.getOrElse(() -> Cont.complete(Nothing.nothing())));
	}

	private static void verifyAllConstrainedHaveDomain(Iterable<? extends Propagator<?>> constraints, Collection<Term<?>> boundVariables) {
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
