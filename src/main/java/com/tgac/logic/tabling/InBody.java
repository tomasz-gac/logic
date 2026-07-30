package com.tgac.logic.tabling;

// ABOUTME: One bit on a body package: this code runs inside some tabled call's body.
// ABOUTME: The delivery boundary - constrained answers stream inside it, seal-gate outside.

import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;

/**
 * Marks a package as executing inside some tabled call's body — the one fact
 * the delivery discipline needs. INSIDE a body, constrained answers must
 * stream eagerly: they are the fixpoint's fuel, and the widening derivation
 * that produces a subsuming answer may need the subsumed one to exist first.
 * OUTSIDE, they are withheld until the seal and delivered as the final
 * antichain, so arrival order cannot shape the output (the local-evaluation
 * discipline of the tabling literature, at entry granularity). Stamped once
 * per master spawn ({@link Table#bodyState}); read only on deliveries from
 * entries that hold constrained answers.
 */
final class InBody implements Packaged {

	static final InBody MARKER = new InBody();

	private InBody() {
	}

	static boolean on(Package pkg) {
		return pkg.getStores().get(InBody.class).isDefined();
	}

	@Override
	public String toString() {
		return "inBody";
	}
}
