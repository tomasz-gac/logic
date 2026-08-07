package com.tgac.logic.goals;

// ABOUTME: The birth watermark a closed sub-solve carries: a variable born before
// ABOUTME: the mark may not surface unbound inside — the closed-aggregate age check.

import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unknown;
import io.vavr.Tuple2;
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
		pkg.getStores().get(Watermark.class)
				.map(Watermark.class::cast)
				.forEach(watermark -> {
					for (Tuple2<LVar<?>, Term<?>> binding : prefix.bindings()) {
						if (watermark.refuses(binding._1)) {
							throw refusal(binding._1);
						}
						MiniKanren.namesIn(binding._2)
								.filter(watermark::refuses)
								.findFirst()
								.ifPresent(old -> {
									throw refusal(old);
								});
					}
				});
	}

	private static IllegalStateException refusal(Unknown<?> name) {
		return new IllegalStateException(
				"closed sub-solve touches pre-existing variable " + name
						+ ": its answer set would depend on knowledge the surrounding search can still grow");
	}
}
