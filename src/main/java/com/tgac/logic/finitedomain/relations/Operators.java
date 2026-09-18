package com.tgac.logic.finitedomain.relations;

// ABOUTME: The schemas' shared toolkit: the domain gate, ground minting, bound
// ABOUTME: arithmetic and hull selection every relation's verdict reads through.

import com.tgac.functional.reflection.Types;
import com.tgac.logic.finitedomain.Bound;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.FiniteDomainConstraints;
import com.tgac.logic.finitedomain.capabilities.Arithmetic;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.capabilities.Multiplicative;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.constraints.store.Theory;
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
import java.util.function.IntPredicate;
import java.util.stream.Stream;
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
	 * every other is a point, {@code computed} gets to determine it — the
	 * is/2 tier. Any other refusal (a wide-domained position alongside the
	 * free one, two free positions) still keeps.
	 */
	static <T> BiFunction<Array<? extends Term<?>>, Package, Verdict> gated(
			Comparator<T> order,
			Function<Array<VarWithDomain<T>>, Verdict> verdict,
			Function<SoleFree<T>, Verdict> computed) {
		return (watched, s) -> letDomain(s, Operators.<T> typed(watched), order)
				.filter(uds -> uds.toJavaStream()
						.noneMatch(ud -> ud.getDomain().isEmpty()))
				.map(verdict)
				.getOrElse(() -> soleFree(s, Operators.<T> typed(watched))
						.map(computed)
						.getOrElse(Verdict::keep));
	}

	/** One free position among points: where it stands, and the known values. */
	@Value
	@RequiredArgsConstructor(staticName = "of")
	static class SoleFree<T> {
		int position;
		Term<T> variable;
		Array<Option<T>> points;

		public T pointAt(int i) {
			return points.get(i).get();
		}
	}

	static <T> Option<SoleFree<T>> soleFree(Package p, Array<? extends Term<T>> us) {
		List<Option<T>> points = new ArrayList<>(us.size());
		int freeAt = -1;
		Term<T> freeVar = null;
		for (int i = 0; i < us.size(); i++) {
			Term<T> walked = p.walk(us.get(i));
			Option<T> point = pointOf(p, walked);
			if (point.isDefined()) {
				points.add(point);
			} else if (walked.asVar().isDefined()
					&& !FiniteDomainConstraints.getDom(p, walked.getVar()).isDefined()) {
				if (freeAt >= 0) {
					return Option.none();
				}
				freeAt = i;
				freeVar = walked;
				points.add(Option.none());
			} else {
				// wide-domained: the strict gate's business, not a computation
				return Option.none();
			}
		}
		return freeAt < 0 ? Option.none() :
				Option.of(SoleFree.of(freeAt, freeVar, Array.ofAll(points)));
	}

	/** A position's known value: ground, or a singleton domain's point. */
	static <T> Option<T> pointOf(Package p, Term<T> walked) {
		if (walked.asVal().isDefined()) {
			return Option.of(walked.get());
		}
		return FiniteDomainConstraints.getDom(p, walked.getVar())
				.flatMap(Operators::getSingleElement);
	}

	/** The one narrowing that binds: a point value through the store's collapse. */
	@SuppressWarnings("unchecked")
	static <T> Verdict narrowToPoint(Term<T> variable, T value,
			Comparator<T> order, Option<Discrete<T>> step) {
		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Collections.singletonList(
						VarWithDomain.of(variable, Singleton.of(value, order, step)))));
	}

	static <T> Option<Array<VarWithDomain<T>>> letDomain(Package p, Array<? extends Term<T>> us,
			Comparator<T> order) {
		return Option.of(us.toJavaStream()
						.map(p::walk)
						.flatMap(v -> v.asVal()
								.map(val -> VarWithDomain.of(v,
										Singleton.of(v.get(), order, Option.none())))
								.map(Stream::of)
								.getOrElse(() -> FiniteDomainConstraints.getDom(p, v.getVar())
										.map(d -> VarWithDomain.of(v, d))
										.toJavaStream()))
						.collect(Array.collector()))
				.filter(uds -> uds.size() == us.size());
	}

	static <T> Option<T> getSingleElement(Domain<T> dom) {
		return Option.of(dom)
				.flatMap(Types.<Singleton<T>> castAs(Singleton.class))
				.map(Singleton::getValue);
	}

	@SuppressWarnings("unchecked")
	static <T> Array<? extends Term<T>> typed(Array<? extends Term<?>> watched) {
		return (Array<? extends Term<T>>) watched;
	}

	static <T> Bound<T> plus(Bound<T> a, Bound<T> b, Arithmetic<T, T> arithmetic) {
		return new Bound<>(arithmetic.plus(a.getValue(), b.getValue()),
				a.isIncluded() && b.isIncluded());
	}

	static <T> Bound<T> minus(Bound<T> a, Bound<T> b, Arithmetic<T, T> arithmetic) {
		return new Bound<>(arithmetic.minus(a.getValue(), b.getValue()),
				a.isIncluded() && b.isIncluded());
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
