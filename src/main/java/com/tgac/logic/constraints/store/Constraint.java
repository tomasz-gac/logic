package com.tgac.logic.constraints.store;

// ABOUTME: The package's constraint entry: a theory paired with its interpreter —
// ABOUTME: knowledge outside the factor, behavior and memo beside it.

import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.goals.Watermark;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * One family's entry in the package: the {@link Theory} is the knowledge —
 * the lattice citizen, the thing crossings rename and keys carry — and the
 * {@link Factor} is the family's execution behavior plus its private memo.
 * Identity is the Theory half ALONE: the factor's state is reconstructible
 * from the theory by invariant (droppability — marshal never carries it),
 * so two entries with one theory are one constraint regardless of their
 * interpreters' private state.
 */
@Value
@EqualsAndHashCode(of = "theory")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Constraint<S extends Factor<S>> implements Packaged {
	Theory<S> theory;
	S factor;

	// hand-written: lombok's staticName cannot carry the recursive bound
	public static <S extends Factor<S>> Constraint<S> of(Theory<S> theory, S factor) {
		return new Constraint<>(theory, factor);
	}

	/** The family's entry in {@code pkg} — the residence read. */
	@SuppressWarnings("unchecked")
	public static <S extends Factor<S>> Option<Constraint<S>> in(Package pkg, Class<S> family) {
		return pkg.getStores().get(family).map(entry -> (Constraint<S>) entry);
	}

	/** A factor takes residence: paired with its theory, keyed by its family. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Package put(Package pkg, Factor<?> factor) {
		return pkg.putStore(factor.getClass(),
				Constraint.of(((Factor) factor).theory(), (Factor) factor));
	}

	/** {@link #put} only when the family is absent — the registration seed. */
	public static Package register(Package pkg, Factor<?> factor) {
		return pkg.getStores().containsKey(factor.getClass()) ? pkg : put(pkg, factor);
	}

	/**
	 * The statement park: {@code atom} meets its resident family — unchanged
	 * when the family is absent. The watermark's statement seam is checked
	 * here, where the atom's watched surface is known.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Package stated(Package pkg, Atom<?> atom) {
		Watermark.check(pkg, atom.watched());
		return pkg.getStores().get(atom.getFactorClass())
				.map(entry -> (Factor) ((Constraint) entry).getFactor().meet((Atom) atom))
				.map(met -> put(pkg, (Factor<?>) met))
				.getOrElse(pkg);
	}

	@Override
	public String toString() {
		return theory.toString();
	}
}
