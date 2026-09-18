package com.tgac.logic.finitedomain.relations;

// ABOUTME: The schemas' shared toolkit: the domain gate, ground minting, bound
// ABOUTME: arithmetic and hull selection every relation's verdict reads through.

import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.finitedomain.Bound;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.FiniteDomainConstraints;
import com.tgac.logic.finitedomain.capabilities.Arithmetic;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.capabilities.Multiplicative;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Operators {

	@Value
	@RequiredArgsConstructor(staticName = "of")
	static class VarWithDomain<T> {
		Term<T> unifiable;
		Domain<?> domain;

		@SuppressWarnings("unchecked")
		public <U> Domain<U> getDomain() {
			return (Domain<U>) domain;
		}
	}

	static long cmpOrder(Substitutions s, Term<?> l, Term<?> r, IntPredicate satisfied, Comparator<Object> order) {
		Term<?> lw = s.walk(l);
		Term<?> rw = s.walk(r);
		if (lw.asVal().isDefined() && rw.asVal().isDefined()) {
			return satisfied.test(order.compare(lw.get(), rw.get())) ? 1 : 0;
		}
		return 1;
	}

	static <T> BiFunction<Array<? extends Term<?>>, Package, Verdict> gated(
			Comparator<T> order,
			Function<Array<VarWithDomain<T>>, Verdict> verdict) {
		return gated(order, verdict, soleFree -> Verdict.keep());
	}

	/**
	 * The gate with the computed-third door: when the strict gate refuses
	 * because exactly one position is an unbound, domainless variable while
	 * every other is known (ground or domained), {@code computed} gets to
	 * determine it — a binding from points, a minted hull from wide
	 * operands. Any other refusal (two free positions, a dying branch)
	 * still keeps.
	 */
	static <T> BiFunction<Array<? extends Term<?>>, Package, Verdict> gated(
			Comparator<T> order,
			Function<Array<VarWithDomain<T>>, Verdict> verdict,
			Function<SoleFree<T>, Verdict> computed) {
		return gated(i -> order, verdict, computed);
	}

	/** The gate over per-position orders — an affine schema's positions type differently. */
	static <T> BiFunction<Array<? extends Term<?>>, Package, Verdict> gated(
			IntFunction<Comparator<T>> orderAt,
			Function<Array<VarWithDomain<T>>, Verdict> verdict,
			Function<SoleFree<T>, Verdict> computed) {
		return (watched, s) -> letDomain(s, Operators.<T> typed(watched), orderAt)
				.filter(uds -> uds.toJavaStream()
						.noneMatch(ud -> ud.getDomain().isEmpty()))
				.map(verdict)
				.getOrElse(() -> soleFree(s, Operators.<T> typed(watched), orderAt)
						.map(computed)
						.getOrElse(Verdict::keep));
	}

	/** One free position among known ones: where it stands, and their domains. */
	@Value
	@RequiredArgsConstructor(staticName = "of")
	static class SoleFree<T> {
		int position;
		Term<T> variable;
		Array<Option<VarWithDomain<T>>> resolved;

		public Domain<T> domainAt(int i) {
			return resolved.get(i).get().getDomain();
		}

		public boolean pointAt(int i, Comparator<T> order) {
			Domain<T> d = domainAt(i);
			return order.compare(d.lower().getValue(), d.upper().getValue()) == 0;
		}

		public T valueAt(int i) {
			return domainAt(i).lower().getValue();
		}
	}

	static <T> Option<SoleFree<T>> soleFree(Package p, Array<? extends Term<T>> us,
			IntFunction<Comparator<T>> orderAt) {
		List<Option<VarWithDomain<T>>> resolved = new ArrayList<>(us.size());
		int freeAt = -1;
		Term<T> freeVar = null;
		for (int i = 0; i < us.size(); i++) {
			Term<T> walked = p.walk(us.get(i));
			if (walked.asVal().isDefined()) {
				resolved.add(Option.of(VarWithDomain.of(walked,
						Singleton.of(walked.get(), orderAt.apply(i), Option.<Discrete<T>> none()))));
				continue;
			}
			Option<Domain<T>> domain = FiniteDomainConstraints.<T> getDom(p, walked.getVar());
			if (domain.isDefined()) {
				if (domain.get().isEmpty()) {
					// a dying branch: the strict gate's business, not a computation
					return Option.none();
				}
				resolved.add(Option.of(VarWithDomain.of(walked, domain.get())));
			} else {
				if (freeAt >= 0) {
					return Option.none();
				}
				freeAt = i;
				freeVar = walked;
				resolved.add(Option.none());
			}
		}
		return freeAt < 0 ? Option.none() :
				Option.of(SoleFree.of(freeAt, freeVar, Array.ofAll(resolved)));
	}

	/** The one narrowing that binds: a point value through the store's collapse. */
	static <T> Verdict narrowToPoint(Term<T> variable, T value,
			Comparator<T> order, Option<Discrete<T>> step) {
		return narrow(variable, Singleton.of(value, order, step));
	}

	/**
	 * Mints the free position's hull: a point hull is a fresh singleton and
	 * collapses to a binding; a wide one becomes the variable's domain, and
	 * its re-examination note wakes whatever else watches the variable —
	 * minting rides the cascade, not the statement order.
	 */
	static <T> Verdict mintHull(Term<T> variable, Bound<T> lo, Bound<T> hi,
			Comparator<T> order, Option<Discrete<T>> step) {
		return narrow(variable, Bound.point(lo, hi, order) ?
				Singleton.of(lo.getValue(), order, step) :
				Interval.of(lo, hi, order, step));
	}

	@SuppressWarnings("unchecked")
	private static <T> Verdict narrow(Term<T> variable, Domain<T> domain) {
		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Collections.singletonList(VarWithDomain.of(variable, domain))));
	}

	static <T> Option<Array<VarWithDomain<T>>> letDomain(Package p, Array<? extends Term<T>> us, Comparator<T> order) {
		return letDomain(p, us, i -> order);
	}

	static <T> Option<Array<VarWithDomain<T>>> letDomain(Package p, Array<? extends Term<T>> us,
			IntFunction<Comparator<T>> orderAt) {
		// the first domainless position refuses — no walking the rest
		List<VarWithDomain<T>> resolved = new ArrayList<>(us.size());
		for (int i = 0; i < us.size(); i++) {
			Term<T> walked = p.walk(us.get(i));
			if (walked.asVal().isDefined()) {
				resolved.add(VarWithDomain.of(walked,
						Singleton.of(walked.get(), orderAt.apply(i), Option.<Discrete<T>> none())));
				continue;
			}
			Option<Domain<T>> domain = FiniteDomainConstraints.<T> getDom(p, walked.getVar());
			if (!domain.isDefined()) {
				return Option.none();
			}
			resolved.add(VarWithDomain.of(walked, domain.get()));
		}
		return Option.of(Array.ofAll(resolved));
	}

	@SuppressWarnings("unchecked")
	static <T> Option<T> getSingleElement(Domain<T> dom) {
		return dom.asPoint().map(v -> (T) v);
	}

	@SuppressWarnings("unchecked")
	static <T> Array<? extends Term<T>> typed(Array<? extends Term<?>> watched) {
		return (Array<? extends Term<T>>) watched;
	}

	static <P, V> Bound<P> plus(Bound<P> point, Bound<V> delta, Arithmetic<P, V> arithmetic) {
		return new Bound<>(arithmetic.plus(point.getValue(), delta.getValue()),
				point.isIncluded() && delta.isIncluded());
	}

	static <P, V> Bound<P> minus(Bound<P> point, Bound<V> delta, Arithmetic<P, V> arithmetic) {
		return new Bound<>(arithmetic.minus(point.getValue(), delta.getValue()),
				point.isIncluded() && delta.isIncluded());
	}

	static <P, V> Bound<V> between(Bound<P> from, Bound<P> to, Arithmetic<P, V> arithmetic) {
		return new Bound<>(arithmetic.between(from.getValue(), to.getValue()),
				from.isIncluded() && to.isIncluded());
	}

	static <T> Bound<T> times(Bound<T> a, Bound<T> b, Multiplicative<T> multiplicative) {
		return new Bound<>(multiplicative.times(a.getValue(), b.getValue()),
				a.isIncluded() && b.isIncluded());
	}

	/** The hull's lower bound: least value; at a tie an attained (closed) endpoint wins. */
	static <T> Bound<T> lowest(Array<Bound<T>> candidates, Comparator<T> order) {
		return candidates.reduce((a, b) -> {
			int c = order.compare(a.getValue(), b.getValue());
			if (c != 0) {
				return c < 0 ? a : b;
			}
			return a.isIncluded() ? a : b;
		});
	}

	/** The hull's upper bound: greatest value; at a tie an attained (closed) endpoint wins. */
	static <T> Bound<T> highest(Array<Bound<T>> candidates, Comparator<T> order) {
		return candidates.reduce((a, b) -> {
			int c = order.compare(a.getValue(), b.getValue());
			if (c != 0) {
				return c > 0 ? a : b;
			}
			return a.isIncluded() ? a : b;
		});
	}
}
