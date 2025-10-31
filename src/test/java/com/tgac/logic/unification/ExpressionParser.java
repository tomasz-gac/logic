package com.tgac.logic.unification;

import static com.tgac.logic.goals.Matche.llist;
import static com.tgac.logic.goals.Matche.matche;
import static com.tgac.logic.projection.ProjectionConstraints.project;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.goals.Goal;
import lombok.RequiredArgsConstructor;
import lombok.Value;

public class ExpressionParser {
	public enum Operation {
		PLUS, MINUS, MUL, DIV
	}


	public interface Token {

	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	public static class NumberToken implements Token {
		int value;
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	public static class Symbol implements Token {
		String value;
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	public static class OperatorToken implements Token {
		Operation operation;
	}

	// numbero: token must be NumberToken; ast is a leaf with that token
	public static Goal numbero(Unifiable<LTree<Token>> ast,
			Unifiable<LList<Token>> in,
			Unifiable<LList<Token>> out) {
		return matche(in, llist((token, rest) ->
				ast.unifies(LTree.of(token))
						.and(out.unifies(rest))
						.and(project(token, t -> Goal.successIf(t instanceof NumberToken)))
		));
	}

	// operatoro: consume/produce a specific operator token
	public static Goal operatoro(Unifiable<LTree<Token>> ast,
			Operation op,
			Unifiable<LList<Token>> in,
			Unifiable<LList<Token>> out) {
		return matche(in, llist((token, rest) ->
				// force token to be that operator
				token.unifies(OperatorToken.of(op))
						// make AST a leaf wrapping that token
						.and(ast.unifies(LTree.of(token)))
						.and(out.unifies(rest))
						// (optional) sanity check after unification; token is now ground
						.and(project(token, t -> Goal.successIf(((OperatorToken) t).getOperation() == op)))
		));
	}

	// Consume exactly this symbol (no AST side effects)
	public static Goal skipSymbol(String s, Unifiable<LList<Token>> in, Unifiable<LList<Token>> out) {
		return matche(in, llist((token, rest) ->
				token.unifies(Symbol.of(s)).and(out.unifies(rest))
		));
	}

	// factor := number | '(' expr ')'   (controlled by parenBudget)
	public static Goal factoro(Unifiable<LTree<Token>> ast,
			Unifiable<LList<Token>> in,
			Unifiable<LList<Token>> out,
			int parenBudget) {
		if (parenBudget <= 0) {
			// No parentheses allowed: only numbers
			return numbero(ast, in, out);
		}

		Unifiable<LList<Token>> mid  = lvar();
		Unifiable<LList<Token>> mid2 = lvar();

		return numbero(ast, in, out)
				.or(
						skipSymbol("(", in, mid)
								.and(Goal.defer(() -> expro(ast, mid, mid2, parenBudget - 1))) // decrement budget
								.and(skipSymbol(")", mid2, out))
				);
	}

	// term := factor (('*'|'/') factor)*
	public static Goal termo(Unifiable<LTree<Token>> ast,
			Unifiable<LList<Token>> in,
			Unifiable<LList<Token>> out,
			int parenBudget) {
		Unifiable<LTree<Token>> left = lvar();
		Unifiable<LList<Token>> mid  = lvar();

		return factoro(left, in, mid, parenBudget)
				.and(Goal.defer(() -> termoTailo(left, ast, mid, out, parenBudget)));
	}

	public static Goal termoTailo(Unifiable<LTree<Token>> left,
			Unifiable<LTree<Token>> ast,
			Unifiable<LList<Token>> in,
			Unifiable<LList<Token>> out,
			int parenBudget) {
		// * branch
		Unifiable<LTree<Token>> right1 = lvar();
		Unifiable<LList<Token>> m1 = lvar(), m2 = lvar();
		Unifiable<LTree<Token>> newLeft1 = lvar();

		Goal mulBranch =
				operatoro(lvar(), Operation.MUL, in, m1)
						.and(factoro(right1, m1, m2, parenBudget))
						.and(newLeft1.unifies(
								LTree.of(lval(OperatorToken.of(Operation.MUL)), LList.ofAll(left, right1))))
						.and(Goal.defer(() -> termoTailo(newLeft1, ast, m2, out, parenBudget)));

		// / branch
		Unifiable<LTree<Token>> right2 = lvar();
		Unifiable<LList<Token>> n1 = lvar(), n2 = lvar();
		Unifiable<LTree<Token>> newLeft2 = lvar();

		Goal divBranch =
				operatoro(lvar(), Operation.DIV, in, n1)
						.and(factoro(right2, n1, n2, parenBudget))
						.and(newLeft2.unifies(
								LTree.of(lval(OperatorToken.of(Operation.DIV)), LList.ofAll(left, right2))))
						.and(Goal.defer(() -> termoTailo(newLeft2, ast, n2, out, parenBudget)));

		// stop
		Goal stop = left.unifies(ast).and(in.unifies(out));

		return mulBranch.or(divBranch).or(stop);
	}

	// expr := term (('+'|'-') term)*
	public static Goal expro(Unifiable<LTree<Token>> ast,
			Unifiable<LList<Token>> in,
			Unifiable<LList<Token>> out,
			int parenBudget) {
		Unifiable<LTree<Token>> left = lvar();
		Unifiable<LList<Token>> mid  = lvar();

		return termo(left, in, mid, parenBudget)
				.and(Goal.defer(() -> exproTailo(left, ast, mid, out, parenBudget)));
	}

	public static Goal exproTailo(Unifiable<LTree<Token>> left,
			Unifiable<LTree<Token>> ast,
			Unifiable<LList<Token>> in,
			Unifiable<LList<Token>> out,
			int parenBudget) {
		// + branch
		Unifiable<LTree<Token>> right1 = lvar();
		Unifiable<LList<Token>> m1 = lvar(), m2 = lvar();
		Unifiable<LTree<Token>> newLeft1 = lvar();

		Goal plusBranch =
				operatoro(lvar(), Operation.PLUS, in, m1)
						.and(termo(right1, m1, m2, parenBudget))
						.and(newLeft1.unifies(
								LTree.of(lval(OperatorToken.of(Operation.PLUS)), LList.ofAll(left, right1))))
						.and(Goal.defer(() -> exproTailo(newLeft1, ast, m2, out, parenBudget)));

		// - branch
		Unifiable<LTree<Token>> right2 = lvar();
		Unifiable<LList<Token>> n1 = lvar(), n2 = lvar();
		Unifiable<LTree<Token>> newLeft2 = lvar();

		Goal minusBranch =
				operatoro(lvar(), Operation.MINUS, in, n1)
						.and(termo(right2, n1, n2, parenBudget))
						.and(newLeft2.unifies(
								LTree.of(lval(OperatorToken.of(Operation.MINUS)), LList.ofAll(left, right2))))
						.and(Goal.defer(() -> exproTailo(newLeft2, ast, n2, out, parenBudget)));

		// stop
		Goal stop = left.unifies(ast).and(in.unifies(out));

		return plusBranch.or(minusBranch).or(stop);
	}

	// Preserve your current API:
	public static Goal parseo(Unifiable<LTree<Token>> ast, Unifiable<LList<Token>> toks) {
		// Full nesting allowed (same as before)
		return expro(ast, toks, LList.empty(), Integer.MAX_VALUE);
	}

	// Add a handy "no nested parens" entry point:
	public static Goal parseoNoNested(Unifiable<LTree<Token>> ast, Unifiable<LList<Token>> toks) {
		// Allow one layer of parentheses total
		return expro(ast, toks, LList.empty(), 1);
	}

	// If you want to forbid parens entirely (numbers only in factors):
	public static Goal parseoNoParens(Unifiable<LTree<Token>> ast, Unifiable<LList<Token>> toks) {
		return expro(ast, toks, LList.empty(), 0);
	}
}
