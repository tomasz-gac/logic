package com.tgac.logic.weight;

// ABOUTME: Kleene closure over a closed semiring: solves x = b ⊕ A⊗x as
// ABOUTME: x = A* ⊗ b, summing a cycle's infinitely many paths in closed form.

import com.tgac.functional.algebra.ClosedSemiring;
import io.vavr.collection.Array;

/**
 * Solves the linear recurrence {@code x_i = b_i ⊕ ⊕_j A_ij ⊗ x_j} over a
 * {@link ClosedSemiring} — the star-tabling closed form (docs/design/star-tabling.md
 * §3). The pivot loop is Floyd–Warshall/Kleene: after processing pivot {@code k},
 * {@code A[i][j]} sums every path {@code i→j} routing through {@code 0..k},
 * looping at {@code k} any number of times via {@code star}. It runs OUT OF PLACE
 * per pivot — each pass reads the previous matrix and writes a fresh one — so the
 * pivot row/column are never read after they have been overwritten.
 *
 * <p>The pass produces {@code A⁺} (paths of length ≥ 1); {@code A* = A⁺ ⊕ I} adds
 * the empty path, and {@code x = A* ⊗ b}. On an acyclic system (zero diagonals
 * throughout, {@code 0* = 1}) it degenerates to ordinary forward substitution.
 * {@code O(n³)} semiring operations.
 */
public final class StarSolve {

	private StarSolve() {
	}

	public static <S> Array<S> solve(ClosedSemiring<S> ring, Array<Array<S>> a, Array<S> b) {
		int n = b.size();
		Object[][] m = new Object[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				m[i][j] = a.get(i).get(j);
			}
		}
		for (int k = 0; k < n; k++) {
			S sk = ring.star(cast(m[k][k]));
			Object[][] next = new Object[n][n];
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					next[i][j] = ring.plus(cast(m[i][j]),
							ring.times(ring.times(cast(m[i][k]), sk), cast(m[k][j])));
				}
			}
			m = next;
		}
		Array<S> x = Array.empty();
		for (int i = 0; i < n; i++) {
			// x_i = ⊕_j A*[i][j] ⊗ b_j, with A* = A⁺ ⊕ I (identity on the diagonal)
			S xi = ring.zero();
			for (int j = 0; j < n; j++) {
				S star = i == j ? ring.plus(cast(m[i][j]), ring.one()) : cast(m[i][j]);
				xi = ring.plus(xi, ring.times(star, b.get(j)));
			}
			x = x.append(xi);
		}
		return x;
	}

	@SuppressWarnings("unchecked")
	private static <S> S cast(Object o) {
		return (S) o;
	}
}
