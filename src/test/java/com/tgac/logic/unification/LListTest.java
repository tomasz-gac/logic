package com.tgac.logic.unification;

import static com.tgac.logic.unification.ExpressionParser.*;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Java6Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import lombok.var;
import org.junit.Test;

public class LListTest {

	@Test
	public void shouldConvertNumberTokenToNumber() {
		Unifiable<LTree<Token>> ast = lvar();
		List<LTree<Token>> result = numbero(ast, LList.ofAll(NumberToken.of(3)), LList.empty())
				.solve(ast)
				.map(Unifiable::get)
				.collect(Collectors.toList());
		assertThat(result)
				.hasSize(1);
		assertThat(result.get(0).getValue().get())
				.isEqualTo(NumberToken.of(3));
	}

	@Test
	public void shouldConvertNumberToNumberToken() {
		Unifiable<Token> token = lvar();
		List<Token> result = numbero(LTree.of(lval(NumberToken.of(3)), LList.empty()), LList.ofAll(token), LList.empty())
				.solve(token)
				.map(Unifiable::get)
				.collect(Collectors.toList());
		assertThat(result)
				.hasSize(1);
		assertThat(result.get(0))
				.isEqualTo(NumberToken.of(3));
	}

	@Test
	public void shouldFindTokenEquivalence() {
		Unifiable<Token> token = lvar();
		List<Token> result = ExpressionParser.numbero(
				LTree.of(lval(NumberToken.of(3))),
				LList.ofAll(token),
				LList.empty())
				.and(token.unifies(NumberToken.of(3)))
				.solve(token)
				.map(Unifiable::get)
				.collect(Collectors.toList());
		assertThat(result)
				.hasSize(1);
		assertThat(result.get(0))
				.isEqualTo(NumberToken.of(3));
	}



	// --- helpers --------------------------------------------------------

	private Unifiable<LTree<Token>> num(int n) {
		return LTree.of(lval(NumberToken.of(n)), LList.empty());
	}

	private Unifiable<LTree<Token>> bin(Operation op, Unifiable<LTree<Token>> l, Unifiable<LTree<Token>> r) {
		return LTree.of(
				lval(OperatorToken.of(op)),
				LList.ofAll(l.get(), r.get()));
	}

	/**
	 * Builds a Unifiable<LList<Token>> from Java varargs.
	 * Integers become NumberToken, Strings become Symbol, Operations become OperatorToken.
	 */
	private Unifiable<LList<Token>> toks(Object... xs) {
		Unifiable<LList<Token>> list = LList.empty();
		// Build backwards because of cons-style
		for (int i = xs.length - 1; i >= 0; i--) {
			Object x = xs[i];
			Token t;
			if (x instanceof Integer) {
				t = NumberToken.of((Integer) x);
			} else if (x instanceof String) {
				t = Symbol.of((String) x);
			} else if (x instanceof Operation) {
				t = OperatorToken.of((Operation) x);
			} else if (x instanceof Token) {
				t = (Token) x;
			} else {
				throw new IllegalArgumentException("unsupported: " + x);
			}
			list = LList.of(lval(t), list);  // ← your constructor form
		}
		return list;
	}

	private Unifiable<LList<Token>> parenOpen() {
		return toks("(");
	}

	private Unifiable<LList<Token>> parenClose() {
		return toks(")");
	}

	// --- tests ----------------------------------------------------------

	@Test
	public void parsesSimpleAddition() {
		Unifiable<LTree<Token>> ast = lvar();
		Unifiable<LList<Token>> input = toks(1, Operation.PLUS, 2);

		List<LTree<Token>> results = parseo(ast, input)
				.solve(ast).map(Unifiable::get).collect(Collectors.toList());

		assertThat(results).containsExactly(bin(Operation.PLUS, num(1), num(2)).get());
	}

	@Test
	public void respectsPrecedence_MulBeforePlus() {
		Unifiable<LTree<Token>> ast = lvar();
		Unifiable<LList<Token>> input = toks(1, Operation.PLUS, 2, Operation.MUL, 3);

		List<LTree<Token>> results = parseo(ast, input)
				.solve(ast)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		var expected = bin(Operation.PLUS, num(1), bin(Operation.MUL, num(2), num(3)));
		assertThat(results).containsExactly(expected.get());
	}

	@Test
	public void parenthesesOverridePrecedence() {
		Unifiable<LTree<Token>> ast = lvar();
		Unifiable<LList<Token>> input = toks("(", 1, Operation.PLUS, 2, ")", Operation.MUL, 3);

		List<LTree<Token>> results = parseo(ast, input)
				.solve(ast)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		var expected = bin(Operation.MUL, bin(Operation.PLUS, num(1), num(2)), num(3));
		assertThat(results).containsExactly(expected.get());
	}

	@Test
	public void leftAssociativeForAddAndMul() {
		Unifiable<LTree<Token>> ast = lvar();
		Unifiable<LList<Token>> addInput = toks(1, Operation.PLUS, 2, Operation.PLUS, 3);
		Unifiable<LList<Token>> mulInput = toks(8, Operation.DIV, 4, Operation.DIV, 2);

		// 1 + 2 + 3 => ((1 + 2) + 3)
		var addExpected = bin(Operation.PLUS, bin(Operation.PLUS, num(1), num(2)), num(3));
		var addRes = parseo(ast, addInput)
				.solve(ast)
				.map(Unifiable::get)
				.collect(Collectors.toList());
		assertThat(addRes).containsExactly(addExpected.get());

		// 8 / 4 / 2 => ((8 / 4) / 2)
		var divExpected = bin(Operation.DIV, bin(Operation.DIV, num(8), num(4)), num(2));
		var divRes = parseo(ast, mulInput)
				.solve(ast)
				.map(Unifiable::get)
				.collect(Collectors.toList());
		assertThat(divRes).containsExactly(divExpected.get());
	}

	@Test
	public void partialParseLeavesRemainderWithExpro() {
		Unifiable<LTree<Token>> ast = lvar();
		Unifiable<LList<Token>> rest = lvar();

		var input = toks(1, Operation.PLUS, 2, Operation.MUL, 3);
		var remainders = expro(ast, input, rest, 0)
				.solve(rest)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		assertThat(remainders).contains(toks(Operation.PLUS, 2, Operation.MUL, 3).get());
	}

	@Test
	public void parseoFailsWhenLeftoverExists() {
		Unifiable<LTree<Token>> ast = lvar();
		var bad = toks(1, Operation.PLUS, 2, ")");

		var results = parseo(ast, bad)
				.solve(ast)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		assertThat(results).isEmpty();
	}

	@Test
	public void generateTokensFromAst_Bidirectional() {
		Unifiable<LTree<Token>> ast = lvar();
		var astVal = bin(Operation.PLUS, num(1), bin(Operation.MUL, num(2), num(3)));
		Unifiable<LList<Token>> toksVar = lvar();

		var seqs = parseoNoNested(astVal, toksVar)
				.solve(toksVar)
				.limit(1)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		System.out.println(seqs);

		assertThat(seqs).contains(toks(1, Operation.PLUS, 2, Operation.MUL, 3).get());
	}

	@Test
	public void factorParensRoundTrip() {
		Unifiable<LTree<Token>> ast = lvar();
		var input = toks("(", "(", 1, Operation.PLUS, 2, ")", ")", Operation.MUL, 3);

		var results = parseo(ast, input)
				.solve(ast)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		var expected = bin(Operation.MUL, bin(Operation.PLUS, num(1), num(2)), num(3));
		assertThat(results).containsExactly(expected.get());
	}

	@Test
	public void rejectsUnaryMinusForNow() {
		Unifiable<LTree<Token>> ast = lvar();
		var input = toks(Operation.MINUS, 3);

		var results = parseo(ast, input)
				.solve(ast)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		assertThat(results).isEmpty();
	}
}
