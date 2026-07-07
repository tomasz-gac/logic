package com.tgac.logic.ckanren;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StoreSupport {

	public static <T extends Store> T getConstraintStore(Package p, Class<T> cls) {
		return p.getConstraints().get(cls)
				.map(Types.<T>cast())
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "No store associated with package"));
	}

	public static Package withoutConstraint(Package p, Stored c) {
		return Package.of(p.getSubstitutions(),
				p.getConstraints()
						.get(c.getStoreClass())
						.map(cs -> cs.remove(c))
						.map(newStore -> p.getConstraints()
								.put(c.getStoreClass(), newStore))
						.getOrElse(p::getConstraints));
	}

	/**
	 * Checks whether any item within v is unbound within r Original name: anyVar
	 */
	public static Boolean isAssociated(Package p, Term<?> v) {
		return v.asVar()
				.map(lvar -> MiniKanren.walk(p, lvar) != lvar)
				.getOrElse(true);
	}

	public static Package withConstraint(Package p, Stored c) {
		return Package.of(p.getSubstitutions(),
				p.getConstraints().get(c.getStoreClass())
						.map(cs -> cs.prepend(c))
						.map(s -> p.getConstraints().put(c.getStoreClass(), s))
						.getOrElse(p::getConstraints));
	}

	public static Package withoutConstraints(Package p) {
		return Package.of(p.getSubstitutions(), null);
	}

	public static <T extends ConstraintStore> Package updateC(Package p, Class<T> cls, UnaryOperator<T> f) {
		return Package.of(
				p.getSubstitutions(),
				p.getConstraints().put(
						cls,
						p.getConstraints().get(cls)
								.flatMap(Types.<T> castAs(ConstraintStore.class))
								.map(f)
								.getOrElse(() -> null)));
	}

	/**
	 * The constraint chokepoint: applies newly inferred substitutions and lets every
	 * constraint domain respond. This is the ONLY way substitutions may grow in
	 * constraint-aware code — user unification ({@link CKanren#unify}), finite-domain
	 * collapse inference and labelling all route through here, which is what makes an
	 * inferred binding indistinguishable from a unification.
	 *
	 * <p>Order of events:
	 * <ol>
	 *   <li>the extension is applied exactly once ({@code extendS}, monotonic);</li>
	 *   <li>every {@link ConstraintStore}'s {@code processPrefix} reaction runs on the
	 *       extended package (verify, narrow its own factor, or fail) — stores never
	 *       touch the substitutions themselves; each receives the pre-extension
	 *       package {@code p} to diff the prefix against;</li>
	 *   <li>every store's suspended constraints watching a newly bound variable are
	 *       woken (remove-and-rerun) via the cross-store {@link #pendingConstraints}
	 *       list. A woken constraint that infers further bindings re-enters this
	 *       method — that recursion is the propagation fixpoint, trampolined by the
	 *       continuation substrate.</li>
	 * </ol>
	 *
	 * <p>Contract for callers:
	 * <ul>
	 *   <li>call it whenever bindings are added; never extend substitutions directly.
	 *       {@code MiniKanren.unify} alone bypasses all constraint processing — that
	 *       is legitimate only for deliberate trial unification (disequality's check,
	 *       on a package stripped with {@link #withoutConstraints});</li>
	 *   <li>pass the full new substitution map — the convention of every caller
	 *       (a pure delta happens to behave identically since the extension is a
	 *       merge, but do not rely on it);</li>
	 *   <li>only genuinely new pairs: a pair contradicting an existing binding is
	 *       silently ignored (the merge keeps the old value and the prefix diff drops
	 *       the key) — unify instead when the variable may already be bound;</li>
	 *   <li>the package must carry a store map ({@code getConstraints() != null});
	 *       trial unification on stripped packages stays on {@code MiniKanren.unify}.</li>
	 * </ul>
	 */
	public static Goal processPrefix(HashMap<LVar<?>, Term<?>> newSubstitutions) {
		return p -> {
			// the chokepoint applies the extension exactly once; stores only react.
			// newSubstitutions is the FULL new map — a superset of p's, per the caller
			// contract above — so replacing IS extending, in O(1); a merge walks the
			// whole map and turns every unification quadratic over a derivation
			Package extended = p.withSubstitutions(newSubstitutions);
			java.util.List<ConstraintStore> stores = p.getConstraints().values().toJavaStream()
					.filter(ConstraintStore.class::isInstance)
					.map(ConstraintStore.class::cast)
					.collect(java.util.stream.Collectors.toList());
			if (stores.isEmpty()) {
				// pure-relational fast path: no reactions, no prefix diff, no wake
				return Cont.just(extended);
			}
			HashMap<LVar<?>, Term<?>> prefix = MiniKanren.prefixS(p.getSubstitutions(), newSubstitutions);
			// reactions are DATA: fold them synchronously — each store swaps at most
			// its own factor and hands inferences to the chokepoint for routing
			Package reacted = extended;
			java.util.List<Inference> inferred = new java.util.ArrayList<>();
			for (ConstraintStore cs : stores) {
				Package before = reacted;
				Package after = cs.onPrefix(prefix, reacted).match(
						() -> null,
						() -> before,
						(store, inferences) -> {
							inferred.addAll(inferences);
							return before.putStore(store);
						});
				if (after == null) {
					return Cont.complete(Nothing.nothing());
				}
				reacted = after;
			}
			Goal applyInferred = inferred.stream()
					.distinct()
					.map(Inference::toGoal)
					.reduce(Goal.success(), Goal::and);
			// wake EVERY store's suspended constraints watching a newly bound variable;
			// read the stores from the live package, since earlier wakes re-park constraints
			Goal wake = prefix
					.keySet().toJavaStream()
					.<Goal> map(x -> s -> CKanren.runConstraints(x, pendingConstraints(s)).apply(s))
					.reduce(Goal.success(), Goal::and);
			return applyInferred.and(wake).apply(reacted);
		};
	}

	/**
	 * The union of every store's suspended constraints — the cross-store wake list.
	 */
	public static Iterable<Constraint> pendingConstraints(Package p) {
		return p.getConstraints().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.flatMap(cs -> java.util.stream.StreamSupport.stream(
						cs.pendingConstraints().spliterator(), false))
				.collect(java.util.stream.Collectors.toList());
	}

	public static <T> Goal enforceConstraints(Package p, Term<T> x) {
		return p.getConstraints().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.map(cs -> cs.enforceConstraints(x))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	public static <A> Term<A> reify(Package p, Term<A> unifiable, Package renameSubstitutions) {
		return p.getConstraints().values()
				.toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.reduce(Try.success(unifiable),
						(l, cs) -> l.flatMap(u -> Try.of(() -> cs.reify(u, renameSubstitutions, p))),
						Exceptions.throwingBiOp(UnsupportedOperationException::new))
				.get();
	}
}
