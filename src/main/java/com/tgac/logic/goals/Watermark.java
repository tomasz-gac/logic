package com.tgac.logic.goals;

// ABOUTME: The birth watermark a closed sub-solve carries: a variable born before
// ABOUTME: the mark may not surface unbound inside — the closed-aggregate age check.

import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unknown;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.Value;

/**
 * A mode marker riding the {@link Package} of a closed sub-solve. A closed
 * aggregate's sub-goal is a self-contained program: it may consume ground
 * values from the surrounding search (the walk dissolves a bound variable
 * into its value before any check sees it), but a variable born BEFORE the
 * mark surfacing inside the sub-solve means the answer set depends on
 * knowledge the surrounding search can still grow — and the fold's scalar
 * would be silently conditional. Such a variable refuses loudly, by name.
 */
@Value
public class Watermark implements Packaged {
	long mark;

	/** A watermark admitting exactly the variables born from this moment on. */
	public static Watermark now() {
		return new Watermark(LVar.births());
	}

	private boolean refuses(Unknown<?> name) {
		return name instanceof LVar && ((LVar<?>) name).getBirth() < mark;
	}

	/**
	 * Refuses {@code prefix} when it binds or mentions a variable older than
	 * the mark carried by {@code pkg}; a no-op for unmarked packages.
	 */
	public static void check(Package pkg, Prefix prefix) {
		markOn(pkg).forEach(watermark -> {
			Set<Unknown<?>> old = new LinkedHashSet<>();
			for (Tuple2<LVar<?>, Term<?>> binding : prefix.bindings()) {
				if (watermark.refuses(binding._1)) {
					old.add(binding._1);
				}
				MiniKanren.namesIn(binding._2)
						.filter(watermark::refuses)
						.forEach(old::add);
			}
			refuseIfAny(old);
		});
	}

	/**
	 * Refuses a stated {@code item} whose terms mention a variable older than
	 * the mark that is still free in {@code pkg} — the statement seam: a
	 * constraint on an outer variable binds nothing yet makes every answer
	 * conditional on it. Deep-walking first admits terms whose old variables
	 * are already bound to values.
	 */
	public static void check(Package pkg, Atom item) {
		markOn(pkg).forEach(watermark -> refuseOldFreeNames(pkg, watermark, item.watched()));
	}

	/**
	 * Refuses {@code watched} terms mentioning a variable older than the
	 * mark that is still free in {@code pkg} — the suspension seam. The
	 * watched set is the body's DECLARED read surface: reads never pass the
	 * binding or statement seams, so the declaration is the one place a
	 * read of outer state can refuse, whether the body would run inline or
	 * park.
	 */
	public static void check(Package pkg, Iterable<? extends Term<?>> watched) {
		markOn(pkg).forEach(watermark -> refuseOldFreeNames(pkg, watermark,
				StreamSupport.stream(watched.spliterator(), false).map(t -> (Term<?>) t)));
	}

	private static void refuseOldFreeNames(Package pkg, Watermark watermark, Stream<Term<?>> terms) {
		refuseIfAny(terms.flatMap(term -> pkg.substitution().namesIn(term))
				.filter(watermark::refuses)
				.collect(Collectors.toCollection(LinkedHashSet::new)));
	}

	private static void refuseIfAny(Set<Unknown<?>> old) {
		if (!old.isEmpty()) {
			throw new IllegalStateException(
					"closed sub-solve touches pre-existing variables "
							+ old.stream().map(Object::toString).collect(Collectors.joining(", "))
							+ ": its answer set would depend on knowledge the surrounding search can still grow");
		}
	}

	private static Option<Watermark> markOn(Package pkg) {
		return pkg.getStores().get(Watermark.class).map(Watermark.class::cast);
	}

}
