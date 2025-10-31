//package com.tgac.logic.unification;
//
//import static com.tgac.logic.unification.LVal.lval;
//import static com.tgac.logic.unification.LVar.lvar;
//import static com.tgac.logic.unification.ParserGenerator.*;
//import static org.assertj.core.api.Assertions.assertThat;
//
//import com.tgac.logic.unification.ParserGenerator.Alternative;
//import com.tgac.logic.unification.ParserGenerator.CompiledGrammar;
//import com.tgac.logic.unification.ParserGenerator.ExactTokenTable;
//import com.tgac.logic.unification.ParserGenerator.Generator;
//import com.tgac.logic.unification.ParserGenerator.Node;
//import com.tgac.logic.unification.ParserGenerator.Ref;
//import com.tgac.logic.unification.ParserGenerator.Rule;
//import com.tgac.logic.unification.ParserGenerator.Terminal;
//import com.tgac.logic.unification.ParserGenerator.TerminalInfo;
//import com.tgac.logic.unification.ParserGenerator.TerminalTable;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//import lombok.Value;
//import org.junit.Test;
//
///**
// * End-to-end test: expression parser with precedence and left associativity.
// *
// * Grammar (right-recursive for left-assoc):
// * expr   := expr PLUS term  | expr MINUS term | term
// * term   := term MUL factor | term DIV   factor | factor
// * factor := NUMBER | LP expr RP
// *
// * AST nodes:
// * - leaves: LTree.of(NumberToken(n))
// * - binary nodes: LTree.of(OperatorToken(op), [left, right])
// * - parentheses are structural (not in AST)
// */
//public class ExprParserGeneratorTest {
//
//	/* ---------- Your token model (use your real ones; this mirrors your earlier code) ---------- */
//
//	enum Operation {PLUS, MINUS, MUL, DIV}
//
//	interface Token {
//	}
//
//	@Value(staticConstructor = "of")
//	static class NumberToken implements Token {
//		int value;
//	}
//
//	@Value(staticConstructor = "of")
//	static class Symbol implements Token {
//		String value;
//	}
//
//	@Value(staticConstructor = "of")
//	static class OperatorToken implements Token {
//		Operation operation;
//	}
//
//	/* ---------- Small helpers ---------- */
//
//	// Build a token list [t0, t1, ...]
//	private static Unifiable<LList<Token>> toks(Object... xs) {
//		Unifiable<LList<Token>> acc = LList.empty();
//		for (int i = xs.length - 1; i >= 0; i--) {
//			Object x = xs[i];
//			Token t;
//			if (x instanceof Integer)
//				t = NumberToken.of((Integer) x);
//			else if (x instanceof String)
//				t = Symbol.of((String) x);
//			else if (x instanceof Operation)
//				t = OperatorToken.of((Operation) x);
//			else if (x instanceof Token)
//				t = (Token) x;
//			else
//				throw new IllegalArgumentException("bad token: " + x);
//			acc = LList.of(lval(t), acc);
//		}
//		return acc;
//	}
//
//	// Children list for LTree (two children)
//	private static Unifiable<LList<LTree<Token>>> kids2(Unifiable<LTree<Token>> a,
//			Unifiable<LTree<Token>> b) {
//		Unifiable<LList<LTree<Token>>> tl = LList.empty();
//		Unifiable<LList<LTree<Token>>> k1 = LList.of(a, tl);
//		return LList.of(b, k1); // [b, a]?? No — LList.of(head, tail). We want [a, b]:
//		// Build [a | [b | []]] = cons(a, cons(b, [])):
//		// So correct is: cons(a, cons(b, [])):
//		// But our constructor is LList.of(head, tail). Let's do it properly below.
//	}
//
//	// Proper children list for LTree (variadic)
//	@SafeVarargs
//	private static Unifiable<LList<LTree<Token>>> kids(Unifiable<LTree<Token>>... cs) {
//		Unifiable<LList<LTree<Token>>> acc = LList.empty();
//		for (int i = cs.length - 1; i >= 0; i--) {
//			acc = LList.of(cs[i], acc);
//		}
//		return acc;
//	}
//
//	private static Unifiable<LTree<Token>> num(int n) {
//		return LTree.of(lval(NumberToken.of(n)), LList.empty());
//	}
//
//	private static Unifiable<LTree<Token>> bin(Operation op,
//			Unifiable<LTree<Token>> l,
//			Unifiable<LTree<Token>> r) {
//		return LTree.of(lval(OperatorToken.of(op)), kids(l, r));
//	}
//
//	/* ---------- Terminals ---------- */
//
//	/** TerminalTable: NUMBER by instanceof; symbols/operators by exact equality. */
//	private static TerminalTable<Token> terminals() {
//		// exact matches for symbols and operators
//		ExactTokenTable<Token> exact = new ExactTokenTable<Token>()
//				.bind("LP", Symbol.of("("))
//				.bind("RP", Symbol.of(")"))
//				.bind("PLUS", OperatorToken.of(Operation.PLUS))
//				.bind("MINUS", OperatorToken.of(Operation.MINUS))
//				.bind("MUL", OperatorToken.of(Operation.MUL))
//				.bind("DIV", OperatorToken.of(Operation.DIV));
//
//		// wrap it with a NUMBER predicate entry
//		return new TerminalTable<Token>() {
//			final TerminalInfo<Token> NUMBER = new TerminalInfo<>(t -> t instanceof NumberToken);
//
//			@Override
//			public TerminalInfo<Token> get(String name) {
//				if ("NUMBER".equals(name))
//					return NUMBER;
//				return exact.get(name);
//			}
//		};
//	}
//
//	/* ---------- Grammar ---------- */
//
//	private static Map<String, Rule> exprGrammar() {
//		// factor := NUMBER | LP expr RP
//		Rule factor = Alternative.of(
//				// NUMBER leaf
//				Terminal.leaf("NUMBER"),
//				// ( expr )
//				Sequence.of(
//						Terminal.skip("LP"),
//						new Ref("expr"),
//						Terminal.skip("RP")
//				)
//		);
//
//		// term := term * factor | term / factor | factor
//		Rule term = Alternative.of(
//				// term * factor  -> node with MUL(left=term, right=factor)
//				Node.of(OperatorToken.of(Operation.MUL),
//						new Ref("term"),
//						Terminal.skip("MUL"),
//						new Ref("factor")),
//				// term / factor
//				Node.of(OperatorToken.of(Operation.DIV),
//						new Ref("term"),
//						Terminal.skip("DIV"),
//						new Ref("factor")),
//				// fallback
//				new Ref("factor")
//		);
//
//		// expr := expr + term | expr - term | term
//		Rule expr = Alternative.of(
//				Node.of(OperatorToken.of(Operation.PLUS),
//						new Ref("expr"),
//						Terminal.skip("PLUS"),
//						new Ref("term")),
//				Node.of(OperatorToken.of(Operation.MINUS),
//						new Ref("expr"),
//						Terminal.skip("MINUS"),
//						new Ref("term")),
//				new Ref("term")
//		);
//
//		Map<String, Rule> defs = new HashMap<>();
//		defs.put("expr", expr);
//		defs.put("term", term);
//		defs.put("factor", factor);
//		return defs;
//	}
//
//	/* ---------- Tests ---------- */
//
//	@Test
//	public void parses_precedence_and_leftAssociativity() {
//		Generator<Token> gen = new Generator<>(terminals(), exprGrammar(), "expr");
//		CompiledGrammar<Token> g = gen.result();
//
//		// tokens: 1 + 2 * 3
//		Unifiable<LList<Token>> input = toks(1, Operation.PLUS, 2, Operation.MUL, 3);
//		Unifiable<LTree<Token>> astVar = lvar();
//
//		List<LTree<Token>> asts = g.parse(astVar, input)
//				.solve(astVar)
//				.map(Unifiable::get)
//				.collect(Collectors.toList());
//
//		// expected: (+ 1 (* 2 3))
//		LTree<Token> expected = bin(Operation.PLUS, num(1), bin(Operation.MUL, num(2), num(3))).get();
//
//		assertThat(asts).containsExactly(expected);
//	}
//
//	@Test
//	public void parses_parentheses_override_precedence() {
//		Generator<Token> gen = new Generator<>(terminals(), exprGrammar(), "expr");
//		CompiledGrammar<Token> g = gen.result();
//
//		// tokens: (1 + 2) * 3
//		Unifiable<LList<Token>> input = toks("(", 1, Operation.PLUS, 2, ")", Operation.MUL, 3);
//		Unifiable<LTree<Token>> astVar = lvar();
//
//		List<LTree<Token>> asts = g.parse(astVar, input)
//				.solve(astVar)
//				.map(Unifiable::get)
//				.collect(Collectors.toList());
//
//		// expected: (* (+ 1 2) 3)
//		LTree<Token> expected = bin(Operation.MUL, bin(Operation.PLUS, num(1), num(2)), num(3)).get();
//
//		assertThat(asts).containsExactly(expected);
//	}
//
//	@Test
//	public void prints_from_ast_deterministically_limit1() {
//		// Build the same grammar
//		Generator<Token> gen = new Generator<>(terminals(), exprGrammar(), "expr");
//		CompiledGrammar<Token> g = gen.result();
//
//		// AST: (+ 1 (* 2 3))
//		Unifiable<LTree<Token>> astVal = bin(Operation.PLUS, num(1), bin(Operation.MUL, num(2), num(3)));
//		Unifiable<LList<Token>> toksVar = lvar();
//
//		// Generate one canonical sequence
//		List<LList<Token>> seqs = g.print(astVal, toksVar)
//				.solve(toksVar)
//				.limit(1) // printing is canonical; the first solution is the only one we care about
//				.map(Unifiable::get)
//				.collect(Collectors.toList());
//
//		assertThat(seqs).containsExactly(toks(1, Operation.PLUS, 2, Operation.MUL, 3).get());
//	}
//}