package com.tgac.logic.unification;

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.goals.Matche.llist;
import static com.tgac.logic.goals.Matche.matche;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.projection.ProjectionConstraints;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;
import org.junit.Test;

/** MCVE: correct anchoring split + AST-root guard; no mutable captures in lambdas. */
public class AltAnchoringMCVE {

	/* ---------------- Rel --------------- */
	@FunctionalInterface
	interface Rel<Tok> {
		Goal apply(Unifiable<LTree<Tok>> ast,
				Unifiable<LList<Tok>> in,
				Unifiable<LList<Tok>> out);
	}

	/* ---------- Token model ---------- */
	interface Token {
	}
	enum Operation {PLUS}

	@Value(staticConstructor = "of")
	static class NumberToken implements Token {
		int value;
	}

	@Value(staticConstructor = "of")
	static class OperatorToken implements Token {
		Operation operation;
	}

	@Value(staticConstructor = "of")
	static class Symbol implements Token {
		String value;
	}

	/* ---------- helpers ---------- */

	private static Unifiable<LList<Token>> toks(Object... xs) {
		Unifiable<LList<Token>> acc = LList.empty();
		for (int i = xs.length - 1; i >= 0; i--) {
			final Object x = xs[i];
			final Token t;
			if (x instanceof Integer)
				t = NumberToken.of((Integer) x);
			else if (x instanceof Operation)
				t = OperatorToken.of((Operation) x);
			else if (x instanceof String)
				t = Symbol.of((String) x);
			else
				t = (Token) x;
			acc = LList.of(lval(t), acc);
		}
		return acc;
	}

	@SafeVarargs
	private static Unifiable<LList<LTree<Token>>> kids(Unifiable<LTree<Token>>... cs) {
		Unifiable<LList<LTree<Token>>> acc = LList.empty();
		for (int i = cs.length - 1; i >= 0; i--)
			acc = LList.of(cs[i], acc);
		return acc;
	}

	private static Unifiable<LTree<Token>> num(int n) {
		return LTree.of(lval(NumberToken.of(n)), LList.empty());
	}

	private static final OperatorToken PLUS_TOK = OperatorToken.of(Operation.PLUS);

	private static Unifiable<LTree<Token>> plus(Unifiable<LTree<Token>> l, Unifiable<LTree<Token>> r) {
		return LTree.of(lval(PLUS_TOK), kids(l, r));
	}

	/* ---------- terminals (each returns Rel) ---------- */

	// Leaf NUMBER (bidirectional, guarded both ways)
	private static Rel<Token> leafNUMBER() {
		return (ast, in, out) -> {
			final Goal parse =
					matche(in, llist((tok, rest) ->
							ProjectionConstraints.project(tok, t -> Goal.successIf(t instanceof NumberToken))
									.and(ast.unifies(LTree.of(tok)))
									.and(out.unifies(rest))
					));

			final Unifiable<Token> v = lvar();
			final Goal print =
					ast.unifies(LTree.of(v))
							.and(ProjectionConstraints.project(v, t -> Goal.successIf(t instanceof NumberToken)))
							.and(in.unifies(LList.of(v, out)));

			return parse.orElse(print);
		};
	}

	// skip PLUS (bidirectional via fixed-token unification)
	private static Rel<Token> skipPLUS() {
		return (_ast, in, out) -> {
			final Goal parse =
					matche(in, llist((tok, rest) ->
							tok.unifies(lval(PLUS_TOK)).and(out.unifies(rest))));
			final Goal print = in.unifies(LList.of(lval(PLUS_TOK), out));
			return parse.orElse(print);
		};
	}

	private static Rel<Token> nodePLUS(final Rel<Token> left, final Rel<Token> right) {
		return (ast, in, out) -> {
			final Unifiable<LTree<Token>> lAst = lvar(), rAst = lvar();
			final Unifiable<LList<Token>> m1 = lvar(), m2 = lvar();

			// 1) Bind the AST shape up front so lAst/rAst are concrete subtrees in print direction
			final Goal astGuard =
					ast.unifies(LTree.of(lval(PLUS_TOK), kids(lAst, rAst)));

			// 2) Now run left, operator, right with those bound subtrees
			return astGuard
					.and(left.apply(lAst, in, m1))
					.and(skipPLUS().apply(lvar(), m1, m2))
					.and(right.apply(rAst, m2, out));
		};
	}

	/* ---------- Alternative (direction-sensitive, no mutable captures) ---------- */

	private static Rel<Token> alt_fixed(final Rel<Token> a, final Object headA,
			final Rel<Token> b, final Object headB) {
		return (ast, in, out) -> {
			final Goal anchored = matche(in,
					llist((head, tail) -> Logic.ground(head)));

			// parse (anchored): committed choice is OK here
			final Goal parseCommitted =
					defer(() -> a.apply(ast, in, out))
							.orElse(defer(() -> b.apply(ast, in, out)));

			// gen (unanchored): fair OR + AST-root guard for Node branches (unchanged)
			final Goal guardA = (headA != null) ? ast.unifies(LTree.of(lval((Token) headA), lvar()))
					: Goal.success();
			final Goal guardB = (headB != null) ? ast.unifies(LTree.of(lval((Token) headB), lvar()))
					: Goal.success();

			final Goal genFair =
					defer(() -> guardA.and(a.apply(ast, in, out)))
							.or(defer(() -> guardB.and(b.apply(ast, in, out))));

			return anchored.and(parseCommitted).orElse(genFair);
		};
	}

	/* ---------- Grammar: expr := expr PLUS term | term ; term := NUMBER ---------- */

	/* term := NUMBER */
	private static Rel<Token> term() { return leafNUMBER(); }

	/* expr := term (PLUS term)*   — left-associative fold */
	private static Rel<Token> expr_fixed() {
		// Tails(accIn, accOut) relates an accumulator AST to the final AST via zero-or-more (PLUS term)
		final Rel2<Token> tails = new Rel2<Token>() {
			@Override
			public Goal apply(Unifiable<LTree<Token>> accIn,
					Unifiable<LTree<Token>> accOut,
					Unifiable<LList<Token>> in,
					Unifiable<LList<Token>> out) {

				// zero case: no tail; propagate tokens and accumulator
				final Goal zero = accOut.unifies(accIn).and(in.unifies(out));

				// one case: PLUS term, then recurse with new accumulator Node(PLUS, accIn, right)
				final Goal one = Goal.defer(() -> {
					final Unifiable<LList<Token>> m1 = lvar(), m2 = lvar();
					final Unifiable<LTree<Token>> right = lvar();
					final Unifiable<LTree<Token>> newAcc = lvar();

					return skipPLUS().apply(lvar(), in, m1)
							.and(term().apply(right, m1, m2))
							// build new accumulator first (unify node shape up front)
							.and(newAcc.unifies(LTree.of(lval(PLUS_TOK), kids(accIn, right))))
							// and continue folding
							.and(this.apply(newAcc, accOut, m2, out));
				});

				// fair OR — allow zero OR one-or-more
				return zero.or(one);
			}
		};

		// expr(acc) := term then tails(acc, ast)
		return (ast, in, out) -> {
			final Unifiable<LTree<Token>> acc0 = lvar();
			final Unifiable<LList<Token>> mid = lvar();
			return term().apply(acc0, in, mid)
					.and(tails.apply(acc0, ast, mid, out));
		};
	}

	/* small helper for a 2-input-ast relation used by tails */
	@FunctionalInterface
	interface Rel2<Tok> {
		Goal apply(Unifiable<LTree<Tok>> a,
				Unifiable<LTree<Tok>> b,
				Unifiable<LList<Tok>> in,
				Unifiable<LList<Tok>> out);
	}

	/* ---------------- Tests ---------------- */

	@Test
	public void parse_ok() {
		final Unifiable<LTree<Token>> ast = lvar();
		final List<LTree<Token>> res =
				expr_fixed().apply(ast, toks(1, Operation.PLUS, 2), LList.empty())
						.solve(ast).limit(1).map(Unifiable::get).collect(Collectors.toList());
		assertThat(res).containsExactly(
				LTree.of(lval(PLUS_TOK), kids(num(1), num(2))).get());
	}

	@Test
	public void print_terminates_and_emits_operator() {
		final Unifiable<LList<Token>> out = lvar();
		final List<LList<Token>> seqs =
				expr_fixed().apply(plus(num(1), num(2)), out, LList.empty())
						.solve(out).limit(1).map(Unifiable::get).collect(Collectors.toList());
		assertThat(seqs).containsExactly(toks(1, Operation.PLUS, 2).get());
	}
}