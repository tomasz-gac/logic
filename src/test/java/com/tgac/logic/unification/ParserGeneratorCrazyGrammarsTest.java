//package com.tgac.logic.unification;
//
//import static com.tgac.logic.unification.LVal.lval;
//import static com.tgac.logic.unification.LVar.lvar;
//import static org.assertj.core.api.Assertions.assertThat;
//
//import com.tgac.logic.unification.ParserGenerator.Alternative;
//import com.tgac.logic.unification.ParserGenerator.ChainLeft;
//import com.tgac.logic.unification.ParserGenerator.CompiledGrammar;
//import com.tgac.logic.unification.ParserGenerator.Generator;
//import com.tgac.logic.unification.ParserGenerator.Node;
//import com.tgac.logic.unification.ParserGenerator.Ref;
//import com.tgac.logic.unification.ParserGenerator.Repeat;
//import com.tgac.logic.unification.ParserGenerator.Rule;
//import com.tgac.logic.unification.ParserGenerator.Sequence;
//import com.tgac.logic.unification.ParserGenerator.Terminal;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.stream.Collectors;
//import lombok.Value;
//import lombok.var;
//import org.junit.Test;
//
//public class ParserGeneratorCrazyGrammarsTest {
//
//	/* ========================= Token model used in tests ========================= */
//
//	interface Token {
//	}
//
//	enum Operation {PLUS, MINUS, MUL, DIV}
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
//	/* ================================ Helpers =================================== */
//
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
//	private ParserGenerator.CompiledGrammar<Token> compile(String start, Map<String, ParserGenerator.Rule> defs) {
//		ParserGenerator.TerminalTable<Token> tt = name -> {
//			// GUARD terminals (no fixed token; only a predicate)
//			if ("NUMBER".equals(name)) {
//				// accept NumberToken
//				return new ParserGenerator.TerminalInfo<Token>(t -> t instanceof NumberToken, null);
//			}
//			if ("ID".equals(name)) {
//				// in tests we represent identifiers as Symbol("ID:foo")
//				return new ParserGenerator.TerminalInfo<Token>(
//						t -> (t instanceof Symbol) && ((Symbol) t).getValue().startsWith("ID:"),
//						null);
//			}
//
//			// FIXED punctuation/operators (skip or leaf that match a single exact token)
//			// parentheses / brackets / braces
//			if ("LP".equals(name))      return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("(")), Symbol.of("("));
//			if ("RP".equals(name))      return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of(")")), Symbol.of(")"));
//			if ("LBRACK".equals(name))  return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("[")), Symbol.of("["));
//			if ("RBRACK".equals(name))  return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("]")), Symbol.of("]"));
//			if ("LBRACE".equals(name))  return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("{")), Symbol.of("{"));
//			if ("RBRACE".equals(name))  return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("}")), Symbol.of("}"));
//
//			// separators / assignment
//			if ("COMMA".equals(name))   return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of(",")), Symbol.of(","));
//			if ("SEMI".equals(name))    return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of(";")), Symbol.of(";"));
//			if ("EQ".equals(name))      return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("=")), Symbol.of("="));
//
//			// question/colon (ternary)
//			if ("QUESTION".equals(name))return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("?")), Symbol.of("?"));
//			if ("COLON".equals(name))   return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of(":")), Symbol.of(":"));
//
//			// factorial / logical not (you used both FACT and NOT mapped to "!")
//			if ("FACT".equals(name) || "NOT".equals(name))
//				return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("!")), Symbol.of("!"));
//
//			// arithmetic operators (your tests used OperatorToken for + - * / in some places,
//			// and Symbol("+") etc. in others. Pick ONE; here I map to Symbol to keep it uniform.)
//			if ("PLUS".equals(name))    return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("+")), Symbol.of("+"));
//			if ("MINUS".equals(name))   return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("-")), Symbol.of("-"));
//			if ("MUL".equals(name))     return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("*")), Symbol.of("*"));
//			if ("DIV".equals(name))     return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("/")), Symbol.of("/"));
//
//			// arrow in the type grammar; your test used the literal "ARROW"
//			// (if you prefer "->", change both the table and the toks() usage)
//			if ("ARROW".equals(name))   return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("ARROW")), Symbol.of("ARROW"));
//
//			// keywords
//			if ("IF".equals(name))      return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("IF")), Symbol.of("IF"));
//			if ("ELSE".equals(name))    return new ParserGenerator.TerminalInfo<>(t -> t.equals(Symbol.of("ELSE")), Symbol.of("ELSE"));
//
//			// fallback: unknown terminal name
//			return null;
//		};
//
//		// Build the grammar with this terminal table
//		return new ParserGenerator.Generator<Token>(tt, defs, start).result();
//	}
//
//	// ---------- 1) Comma-separated lists (no trailing comma) ----------
//	@Test
//	public void list_no_trailing_comma_roundtrip() {
//		Rule elem = Alternative.of(Terminal.leaf("NUMBER"), Terminal.leaf("ID"));
//		Rule list0 = Node.of("list",
//				elem,
//				ChainLeft.of(
//						new Ref("elem"),
//						ChainLeft.op(",", Terminal.skip("COMMA"), new Ref("elem"))
//				)
//		);
//
//		Map<String, Rule> defs = new HashMap<>();
//		defs.put("elem", elem);
//		defs.put("list", list0);
//		CompiledGrammar<Token> g = compile("list", defs);
//
//		// parse then print: 1,2,3  -> canonical 1,2,3
//		Unifiable<LTree<Token>> ast = lvar();
//		var asts = g.parse(ast, toks(1, ",", 2, ",", 3))
//				.solve(ast).limit(1).collect(Collectors.toList());
//		assertThat(asts).hasSize(1);
//
//		Unifiable<LList<Token>> out = lvar();
//		var seqs = g.print(asts.get(0), out)
//				.solve(out).limit(1).map(Unifiable::get).collect(Collectors.toList());
//		assertThat(seqs).containsExactly(toks(1, ",", 2, ",", 3).get());
//	}
//
//	// ---------- 2) Dangling-else style optional clause ----------
//	@Test
//	public void if_else_optional_is_nearest_binding_canonical() {
//		Rule expr = new Ref("expr"); // reuse below
//		Rule block = Sequence.of(Terminal.skip("LBRACE"), Repeat.star(new Ref("stmt")), Terminal.skip("RBRACE"));
//		Rule assign = Node.of("assign", Terminal.leaf("ID"), Terminal.skip("EQ"), new Ref("expr"), Terminal.skip("SEMI"));
//		Rule ifElse = Sequence.of(
//				Terminal.skip("IF"), Terminal.skip("LP"), new Ref("expr"), Terminal.skip("RP"),
//				new Ref("stmt"),
//				ParserGenerator.Optional.of(Sequence.of(Terminal.skip("ELSE"), new Ref("stmt")))
//		);
//		Rule stmt = Alternative.of(ifElse, block, assign);
//		// minimal expr: NUMBER | ID
//		Rule prim = Alternative.of(Terminal.leaf("NUMBER"), Terminal.leaf("ID"));
//		Map<String, Rule> defs = new HashMap<>();
//		defs.put("expr", prim);
//		defs.put("stmt", stmt);
//		CompiledGrammar<Token> g = compile("stmt", defs);
//
//		// IF (x) IF (y) a; ELSE b;  should bind ELSE to inner IF
//		Unifiable<LTree<Token>> ast = lvar();
//		var tokens = toks("IF", "(", "ID:x", ")", "IF", "(", "ID:y", ")", "ID:a", "=", 1, ";", "ELSE", "ID:b", "=", 2, ";");
//		var asts = g.parse(ast, tokens).solve(ast).limit(1).collect(Collectors.toList());
//		assertThat(asts).hasSize(1);
//
//		// print should be canonical & same structure
//		Unifiable<LList<Token>> out = lvar();
//		var seqs = g.print(asts.get(0), out).solve(out).limit(1)
//				.map(Unifiable::get).collect(Collectors.toList());
//		assertThat(seqs).hasSize(1);
//	}
//
//	// ---------- 3) Prefix + postfix with binary precedence ----------
//	@Test
//	public void prefix_postfix_and_binaries_parse_print() {
//		Rule prim = Alternative.of(
//				Terminal.leaf("NUMBER"),
//				Terminal.leaf("ID"),
//				Sequence.of(Terminal.skip("LP"), new Ref("expr"), Terminal.skip("RP"))
//		);
//		Rule prefixOne = Alternative.of(
//				Node.of("pre+", Terminal.skip("PLUS"), new Ref("unary")),
//				Node.of("pre-", Terminal.skip("MINUS"), new Ref("unary")),
//				Node.of("pre!", Terminal.skip("NOT"), new Ref("unary"))
//		);
//		Rule unary = Alternative.of(prefixOne, new Ref("prim"));
//		Rule postfixOne = Node.of("post!", new Ref("unary"), Terminal.skip("FACT"));
//		Rule post = Alternative.of(postfixOne, new Ref("unary"));
//
//		Rule mul = ChainLeft.of(
//				new Ref("post"),
//				ChainLeft.op("*", Terminal.skip("MUL"), new Ref("post")),
//				ChainLeft.op("/", Terminal.skip("DIV"), new Ref("post"))
//		);
//		Rule add = ChainLeft.of(
//				new Ref("mul"),
//				ChainLeft.op("+", Terminal.skip("PLUS"), new Ref("mul")),
//				ChainLeft.op("-", Terminal.skip("MINUS"), new Ref("mul"))
//		);
//		Rule expr = new Ref("add");
//
//		Map<String, Rule> defs = new HashMap<>();
//		defs.put("prim", prim);
//		defs.put("unary", unary);
//		defs.put("post", post);
//		defs.put("mul", mul);
//		defs.put("add", add);
//		defs.put("expr", expr);
//		CompiledGrammar<Token> g = compile("expr", defs);
//
//		// parse: ++x! * (3 + y)  (represented as + + ID:x FACT * ( LP 3 + ID:y RP ))
//		Unifiable<LTree<Token>> ast = lvar();
//		var toks = toks("+", "+", "ID:x", "!", "*", "(", 3, "+", "ID:y", ")");
//		var asts = g.parse(ast, toks).solve(ast).limit(1).collect(Collectors.toList());
//		assertThat(asts).hasSize(1);
//
//		// print canonical
//		Unifiable<LList<Token>> out = lvar();
//		var seqs = g.print(asts.get(0), out)
//				.solve(out).limit(1).map(Unifiable::get).collect(Collectors.toList());
//		assertThat(seqs).hasSize(1);
//	}
//
//	// ---------- 4) Ternary (right-assoc) under addition ----------
//	@Test
//	public void ternary_right_assoc_roundtrip() {
//		// Reuse a small add/mul grammar
//		Rule factor = Alternative.of(Terminal.leaf("NUMBER"), Terminal.leaf("ID"));
//		Rule term = ChainLeft.of(new Ref("factor"),
//				ChainLeft.op("*", Terminal.skip("MUL"), new Ref("factor")));
//		Rule add = ChainLeft.of(new Ref("term"),
//				ChainLeft.op("+", Terminal.skip("PLUS"), new Ref("term")));
//		Rule cond = Alternative.of(
//				Node.of("?:", new Ref("add"), Terminal.skip("QUESTION"), new Ref("add"), Terminal.skip("COLON"), new Ref("cond")),
//				new Ref("add")
//		);
//
//		Map<String, Rule> defs = new HashMap<>();
//		defs.put("factor", factor);
//		defs.put("term", term);
//		defs.put("add", add);
//		defs.put("cond", cond);
//		CompiledGrammar<Token> g = compile("cond", defs);
//
//		// parse a ? b : c ? d : e   (right-assoc)
//		Unifiable<LTree<Token>> ast = lvar();
//		var tokens = toks("ID:a", "?", "ID:b", ":", "ID:c", "?", "ID:d", ":", "ID:e");
//		var asts = g.parse(ast, tokens).solve(ast).limit(1).collect(Collectors.toList());
//		assertThat(asts).hasSize(1);
//
//		// print canonical (limit 1)
//		Unifiable<LList<Token>> out = lvar();
//		var seqs = g.print(asts.get(0), out).solve(out).limit(1)
//				.map(Unifiable::get).collect(Collectors.toList());
//		assertThat(seqs).hasSize(1);
//	}
//
//	// ---------- 5) Call & index chaining ----------
//	@Test
//	public void call_and_index_chain_roundtrip() {
//		Rule args = Node.of("args",
//				new Ref("expr"),
//				Repeat.star(Sequence.of(Terminal.skip("COMMA"), new Ref("expr")))
//		);
//
//		Rule prim = Alternative.of(Terminal.leaf("ID"), Terminal.leaf("NUMBER"),
//				Sequence.of(Terminal.skip("LP"), new Ref("expr"), Terminal.skip("RP"))
//		);
//
//		Rule postCall = Node.of("call", new Ref("post"), Terminal.skip("LP"), ParserGenerator.Optional.of(new Ref("args")), Terminal.skip("RP"));
//		Rule postIdx = Node.of("index", new Ref("post"), Terminal.skip("LBRACK"), new Ref("expr"), Terminal.skip("RBRACK"));
//		Rule post = Alternative.of(postCall, postIdx, new Ref("prim"));
//
//		// Keep expression simple to focus on chaining
//		Rule expr = new Ref("post");
//
//		Map<String, Rule> defs = new HashMap<>();
//		defs.put("args", args);
//		defs.put("prim", prim);
//		defs.put("post", post);
//		defs.put("expr", expr);
//		CompiledGrammar<Token> g = compile("expr", defs);
//
//		// f(x)(y)[i]
//		Unifiable<LTree<Token>> ast = lvar();
//		var tokens = toks("ID:f", "(", "ID:x", ")", "(", "ID:y", ")", "[", "ID:i", "]");
//		var asts = g.parse(ast, tokens).solve(ast).limit(1).collect(Collectors.toList());
//		assertThat(asts).hasSize(1);
//
//		// print same chain
//		Unifiable<LList<Token>> out = lvar();
//		var seqs = g.print(asts.get(0), out).solve(out).limit(1)
//				.map(Unifiable::get).collect(Collectors.toList());
//		assertThat(seqs).containsExactly(tokens.get());
//	}
//
//	// ---------- 6) Statement list with optional trailing semicolon ----------
//	@Test
//	public void stmts_optional_trailing_semi_parse_print() {
//		Rule expr = Alternative.of(Terminal.leaf("NUMBER"), Terminal.leaf("ID"));
//		Rule stmt = Node.of("assign", Terminal.leaf("ID"), Terminal.skip("EQ"), new Ref("expr"), Terminal.skip("SEMI"));
//		Rule stmts = Sequence.of(
//				new Ref("stmt"),
//				Repeat.star(Sequence.of(Terminal.skip("SEMI"), new Ref("stmt"))),
//				ParserGenerator.Optional.of(Terminal.skip("SEMI"))
//		);
//
//		Map<String, Rule> defs = new HashMap<>();
//		defs.put("expr", expr);
//		defs.put("stmt", stmt);
//		defs.put("stmts", stmts);
//		CompiledGrammar<Token> g = compile("stmts", defs);
//
//		// Parse two statements with trailing ';'
//		Unifiable<LTree<Token>> ast = lvar();
//		var tokens = toks("ID:a", "=", 1, ";", "ID:b", "=", 2, ";"); // with trailing
//		var asts = g.parse(ast, tokens).solve(ast).limit(1).collect(Collectors.toList());
//		assertThat(asts).hasSize(1);
//
//		// Print canonical (we accept that trailing ; may or may not be emitted depending on your choice;
//		// at least ensure printing terminates and is consistent)
//		Unifiable<LList<Token>> out = lvar();
//		var seqs = g.print(asts.get(0), out).solve(out).limit(1)
//				.map(Unifiable::get).collect(Collectors.toList());
//		assertThat(seqs).hasSize(1);
//	}
//
//	// ---------- 7) Type arrows (right-assoc) ----------
//	@Test
//	public void type_arrows_right_assoc_roundtrip() {
//		Rule atomType = Terminal.leaf("ID");
//		Rule type = Alternative.of(
//				Node.of("->", new Ref("type"), Terminal.skip("ARROW"), new Ref("type")),
//				Sequence.of(Terminal.skip("LP"), new Ref("type"), Terminal.skip("RP")),
//				atomType
//		);
//
//		Map<String, Rule> defs = new HashMap<>();
//		defs.put("type", type);
//		CompiledGrammar<Token> g = compile("type", defs);
//
//		// A -> B -> C   (right-assoc)
//		Unifiable<LTree<Token>> ast = lvar();
//		var tokens = toks("ID:A", "ARROW", "ID:B", "ARROW", "ID:C");
//		var asts = g.parse(ast, tokens).solve(ast).limit(1).collect(Collectors.toList());
//		assertThat(asts).hasSize(1);
//
//		// print canonical
//		Unifiable<LList<Token>> out = lvar();
//		var seqs = g.print(asts.get(0), out).solve(out).limit(1)
//				.map(Unifiable::get).collect(Collectors.toList());
//		assertThat(seqs).hasSize(1);
//	}
//
//	// ---------- 8) Negative tests ----------
//	@Test
//	public void list_rejects_trailing_comma_when_not_allowed() {
//		Rule elem = Alternative.of(Terminal.leaf("NUMBER"), Terminal.leaf("ID"));
//		Rule list0 = Node.of("list",
//				elem,
//				ChainLeft.of(
//						new Ref("elem"),
//						ChainLeft.op(",", Terminal.skip("COMMA"), new Ref("elem"))
//				)
//		);
//		Map<String, Rule> defs = new HashMap<>();
//		defs.put("elem", elem);
//		defs.put("list", list0);
//		CompiledGrammar<Token> g = compile("list", defs);
//
//		Unifiable<LTree<Token>> ast = lvar();
//		// 1,2,   should fail
//		var bad = g.parse(ast, toks(1, ",", 2, ","))
//				.solve(ast).collect(Collectors.toList());
//		assertThat(bad).isEmpty();
//	}
//
//	// ---------- Helpers ----------
//	// Assumes you already have: compile(String, Map<String, Rule>) and toks(Object...)
//	// If "ID:foo" appears in toks(...), your compile helper should map that string to an ID token.
//}