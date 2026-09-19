// ABOUTME: The value-capability seats: operation-only instances a type either
// ABOUTME: has or lacks — order, arithmetic, discreteness — never fused.

/**
 * The three seats a value type may hold in the constraint tier, each an
 * OPERATION-ONLY instance — a stateless bundle of functions proving the
 * type can do something, never wrapping a value, never carrying state:
 *
 * <ul>
 * <li><b>Order</b> — {@link java.util.Comparator}, deliberately the JDK
 * type: comparison constraints decide on ground values and prune bounds
 * where domains exist; every {@code Comparable} holds this seat for free
 * ({@code Comparator.naturalOrder()}).</li>
 * <li><b>Arithmetic</b> — {@link org.clauseway.logic.finitedomain.capabilities.Arithmetic},
 * the affine additive half: points and deltas, possibly different types
 * ({@code Instant + Duration}); and
 * {@link org.clauseway.logic.finitedomain.capabilities.Multiplicative}, the
 * homogeneous half with the exact-or-refuse inverse. A type may hold
 * one without the other — dates add and never multiply.</li>
 * <li><b>Discreteness</b> — {@link org.clauseway.logic.finitedomain.capabilities.Discrete}:
 * a successor/predecessor the labelling floor steps by. Its ABSENCE is
 * a fact, not a failure: a dense type (BigDecimal) keeps interval
 * domains and propagation, and the ground floor refuses to label it
 * loudly.</li>
 * </ul>
 *
 * The seats are independently optional; the capability ladder for a
 * type is literally which instances exist. Constraint relations own all
 * semantics — parking, collapsing, minting, refusing — and take these
 * instances as plain arguments; per-type fronts pin the concrete types
 * so no caller ever meets a cast.
 *
 * <p>THE LAWS ARE MEMBERSHIP: an instance that breaks its interface's
 * laws is a bug, never a flavor — there is no "laws up to rounding".
 * Rounded arithmetic (float, double, BigDecimal under a MathContext)
 * has no exact inverses, so it holds NO seat here: forward computation
 * over rounded types is ordinary Java inside {@code project}. The one
 * lawful future door for dense numerics is interval propagation under
 * DIRECTED rounding (bounds down/up — conservative, sound), shelved
 * until a real domain wants it. {@code CapabilityLaws} is the
 * executable form of every law above — client instances run it as
 * their admission receipt.
 *
 * <p>REPLAY CONTRACT: propagators are rebuilt positionally, so every
 * instance they carry must be a stateless, immutable function bundle —
 * these interfaces guarantee it by construction; keep it that way.
 */
package org.clauseway.logic.finitedomain.capabilities;
