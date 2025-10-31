package com.tgac.logic.unification;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static com.tgac.logic.unification.ParserGenerator.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.unification.ParserGenerator.Alternative;
import com.tgac.logic.unification.ParserGenerator.CompiledGrammar;
import com.tgac.logic.unification.ParserGenerator.Generator;
import com.tgac.logic.unification.ParserGenerator.Node;
import com.tgac.logic.unification.ParserGenerator.Optional;
import com.tgac.logic.unification.ParserGenerator.Ref;
import com.tgac.logic.unification.ParserGenerator.Repeat;
import com.tgac.logic.unification.ParserGenerator.Rule;
import com.tgac.logic.unification.ParserGenerator.Sequence;
import com.tgac.logic.unification.ParserGenerator.Terminal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.var;
import org.junit.Test;

/**
 * Exhaustive unit tests for the ParserGenerator to pinpoint failures per construct.
 * Each test isolates one generator node and checks PARSE and PRINT directions.
 */
public class ParserGeneratorUnitTest {

	/* ========================= Token model used in tests ========================= */

	interface Token {
	}

	enum Operation {PLUS, MINUS, MUL, DIV}

	@Value(staticConstructor = "of")
	static class NumberToken implements Token {
		int value;
	}

	@Value(staticConstructor = "of")
	static class Symbol implements Token {
		String value;
	}

	@Value(staticConstructor = "of")
	static class OperatorToken implements Token {
		Operation operation;
	}

	/* ================================ Helpers =================================== */

	private static Unifiable<LList<Token>> toks(Object... xs) {
		Unifiable<LList<Token>> acc = LList.empty();
		for (int i = xs.length - 1; i >= 0; i--) {
			Object x = xs[i];
			Token t;
			if (x instanceof Integer)
				t = NumberToken.of((Integer) x);
			else if (x instanceof String)
				t = Symbol.of((String) x);
			else if (x instanceof Operation)
				t = OperatorToken.of((Operation) x);
			else if (x instanceof Token)
				t = (Token) x;
			else
				throw new IllegalArgumentException("bad token: " + x);
			acc = LList.of(lval(t), acc);
		}
		return acc;
	}

	@SafeVarargs
	private static Unifiable<LList<LTree<Token>>> kids(Unifiable<LTree<Token>>... cs) {
		Unifiable<LList<LTree<Token>>> acc = LList.empty();
		for (int i = cs.length - 1; i >= 0; i--) {
			acc = LList.of(cs[i], acc);
		}
		return acc;
	}

	private static Unifiable<LTree<Token>> num(int n) {
		return LTree.of(lval(NumberToken.of(n)), LList.empty());
	}

	private static Unifiable<LTree<Token>> bin(Operation op,
			Unifiable<LTree<Token>> l,
			Unifiable<LTree<Token>> r) {
		return LTree.of(lval(OperatorToken.of(op)), kids(l, r));
	}

	/** Terminal table: NUMBER by predicate; others by equality. */
	private static ParserGenerator.TerminalTable<Token> terminals() {
		ParserGenerator.ExactTokenTable<Token> exact = new ParserGenerator.ExactTokenTable<Token>()
				.bind("LP", Symbol.of("("))
				.bind("RP", Symbol.of(")"))
				.bind("PLUS", OperatorToken.of(Operation.PLUS))
				.bind("MINUS", OperatorToken.of(Operation.MINUS))
				.bind("MUL", OperatorToken.of(Operation.MUL))
				.bind("DIV", OperatorToken.of(Operation.DIV));

		return new ParserGenerator.TerminalTable<Token>() {
			final ParserGenerator.TerminalInfo<Token> NUMBER =
					new ParserGenerator.TerminalInfo<>(t -> t instanceof NumberToken, null); // <- fixed=null

			@Override
			public ParserGenerator.TerminalInfo<Token> get(String name) {
				if ("NUMBER".equals(name))
					return NUMBER;
				return exact.get(name);
			}
		};
	}

	private static CompiledGrammar<Token> compile(String start, Map<String, Rule> defs) {
		return new Generator<>(terminals(), defs, start).result();
	}

	/* =============================== Tests ===================================== */

	@Test
	public void terminal_leaf_parses_and_prints() {
		// rule S := NUMBER
		Map<String, Rule> defs = new HashMap<>();
		defs.put("S", Terminal.leaf("NUMBER"));
		CompiledGrammar<Token> g = compile("S", defs);

		// parse
		Unifiable<LTree<Token>> ast = lvar();
		var results = g.parse(ast, toks(7)).solve(ast).map(Unifiable::get).collect(Collectors.toList());
		assertThat(results).containsExactly(LTree.<Token> of(lval(NumberToken.of(7)), LList.empty()).get());

		// print (leaf -> token)
		Unifiable<LList<Token>> out = lvar();
		var toksRes = g.print(LTree.<Token> of(lval(NumberToken.of(7)), LList.empty()), out)
				.solve(out).map(Unifiable::get).collect(Collectors.toList());
		assertThat(toksRes).containsExactly(toks(7).get());
	}

	@Test
	public void terminal_skip_consumes_only() {
		// rule S := LP RP  (no AST produced)
		Map<String, Rule> defs = new HashMap<>();
		defs.put("S", Sequence.of(Terminal.skip("LP"), Terminal.skip("RP")));
		CompiledGrammar<Token> g = compile("S", defs);

		Unifiable<LTree<Token>> ast = lvar(); // should end up unconstrained leaf? We demand empty tree.
		Unifiable<LList<Token>> in = toks("(", ")");
		Unifiable<LList<Token>> rest = LList.empty();

		// parse: we don't care about AST shape here; just expect success and empty remainder
		var oks = g.rule("S").get().apply(ast, in, rest).solve(ast).limit(1).collect(Collectors.toList());
		assertThat(oks).isNotEmpty();
	}

	@Test
	public void sequence_two_leaves_builds_last_child_ast() {
		// By design, Sequence passes AST from its last child unless wrapped by Node.
		// S := NUMBER NUMBER
		Map<String, Rule> defs = new HashMap<>();
		defs.put("S", Sequence.of(Terminal.leaf("NUMBER"), Terminal.leaf("NUMBER")));
		CompiledGrammar<Token> g = compile("S", defs);

		Unifiable<LTree<Token>> ast = lvar();
		var res = g.parse(ast, toks(1, 2)).solve(ast).map(Unifiable::get).collect(Collectors.toList());

		// expecting AST of the last NUMBER: 2
		assertThat(res).containsExactly(LTree.<Token> of(lval(NumberToken.of(2)), LList.empty()).get());
	}

	@Test
	public void node_wraps_children() {
		// S := Node(PLUS, NUMBER, PLUS, NUMBER)
		//            ^^^^^ AST value       ^ structural token in the surface form
		Map<String, Rule> defs = new HashMap<>();
		defs.put("S", Node.of(
				OperatorToken.of(Operation.PLUS),
				Terminal.leaf("NUMBER"),
				Terminal.skip("PLUS"),          // <-- consume/emit the PLUS token
				Terminal.leaf("NUMBER")
		));
		CompiledGrammar<Token> g = compile("S", defs);

		// parse: 10 PLUS 20
		Unifiable<LTree<Token>> ast = lvar();
		var res = g.parse(ast, toks(10, Operation.PLUS, 20))
				.solve(ast).map(Unifiable::get).collect(Collectors.toList());
		assertThat(res).containsExactly(bin(Operation.PLUS, num(10), num(20)).get());

		// print: (+ 10 20) -> 10 PLUS 20
		Unifiable<LList<Token>> out = lvar();
		var toksRes = g.print(bin(Operation.PLUS, num(10), num(20)), out)
				.solve(out).limit(1).map(Unifiable::get).collect(Collectors.toList());
		assertThat(toksRes).containsExactly(toks(10, Operation.PLUS, 20).get());
	}

	@Test
	public void alternative_first_guard_picks_correct_branch() {
		// S := NUMBER | LP RP
		Map<String, Rule> defs = new HashMap<>();
		defs.put("S", Alternative.of(Terminal.leaf("NUMBER"),
				Sequence.of(Terminal.skip("LP"), Terminal.skip("RP"))));
		CompiledGrammar<Token> g = compile("S", defs);

		// parse a NUMBER
		Unifiable<LTree<Token>> ast = lvar();
		var res1 = g.parse(ast, toks(5)).solve(ast).map(Unifiable::get).collect(Collectors.toList());
		assertThat(res1).containsExactly(LTree.<Token> of(lval(NumberToken.of(5)), LList.empty()).get());

		// parse LP RP
		var res2 = g.parse(ast, toks("(", ")")).solve(ast).limit(1).collect(Collectors.toList());
		assertThat(res2).isNotEmpty(); // AST not shaped; success means the branch was chosen
	}

	@Test
	public void optional_present_and_absent() {
		// S := NUMBER? NUMBER
		Map<String, Rule> defs = new HashMap<>();
		defs.put("S", Sequence.of(Optional.of(Terminal.leaf("NUMBER")), Terminal.leaf("NUMBER")));
		CompiledGrammar<Token> g = compile("S", defs);

		// present: 1 2  -> last child ast is 2
		Unifiable<LTree<Token>> ast = lvar();
		var a = g.parse(ast, toks(1, 2)).solve(ast).map(Unifiable::get).collect(Collectors.toList());
		assertThat(a).contains(LTree.<Token> of(lval(NumberToken.of(2)), LList.empty()).get());

		// absent: 2
		var b = g.parse(ast, toks(2)).solve(ast).map(Unifiable::get).collect(Collectors.toList());
		assertThat(b).contains(LTree.<Token> of(lval(NumberToken.of(2)), LList.empty()).get());
	}

	@Test
	public void repeat_star_zero_and_many() {
		// S := (LP RP)* NUMBER  (repeat is structural)
		Map<String, Rule> defs = new HashMap<>();
		defs.put("S", Sequence.of(Repeat.star(Sequence.of(Terminal.skip("LP"), Terminal.skip("RP"))),
				Terminal.leaf("NUMBER")));
		CompiledGrammar<Token> g = compile("S", defs);

		Unifiable<LTree<Token>> ast = lvar();
		// zero
		var a = g.parse(ast, toks(9)).solve(ast).map(Unifiable::get).collect(Collectors.toList());
		assertThat(a).containsExactly(LTree.<Token> of(lval(NumberToken.of(9)), LList.empty()).get());
		// many
		var b = g.parse(ast, toks("(", ")", "(", ")", 9)).solve(ast).map(Unifiable::get).collect(Collectors.toList());
		assertThat(b).containsExactly(LTree.<Token> of(lval(NumberToken.of(9)), LList.empty()).get());
	}

	@Test
	public void expression_end_to_end_precedence_and_print() {
		// factor := NUMBER | LP expr RP
		Rule factor = Alternative.of(
				Terminal.leaf("NUMBER"),
				Sequence.of(Terminal.skip("LP"), new Ref("expr"), Terminal.skip("RP"))
		);

		// one-step mul/div tail: (MUL factor) | (DIV factor)
		Rule mulTail = Alternative.of(
				Node.of(OperatorToken.of(Operation.MUL),  Terminal.skip("MUL"),  new Ref("factor")),
				Node.of(OperatorToken.of(Operation.DIV),  Terminal.skip("DIV"),  new Ref("factor"))
		);

		// term := FoldLeft(factor, mulTail)
		Rule term = FoldLeft.of(new Ref("factor"), mulTail);

		// one-step plus/minus tail: (PLUS term) | (MINUS term)
		Rule addTail = Alternative.of(
				Node.of(OperatorToken.of(Operation.PLUS),  Terminal.skip("PLUS"),  new Ref("term")),
				Node.of(OperatorToken.of(Operation.MINUS), Terminal.skip("MINUS"), new Ref("term"))
		);

		// expr := FoldLeft(term, addTail)
		Rule expr = FoldLeft.of(new Ref("term"), addTail);

		Map<String, Rule> defs = new HashMap<>();
		defs.put("expr", expr);
		defs.put("term", term);
		defs.put("factor", factor);
		defs.put("addTail", addTail);
		defs.put("mulTail", mulTail);

		CompiledGrammar<Token> g = compile("expr", defs);

		// parse: 1 + 2 * 3  => (+ 1 (* 2 3))
		Unifiable<LTree<Token>> ast = lvar();
		var asts = g.parse(ast, toks(1, Operation.PLUS, 2, Operation.MUL, 3))
				.solve(ast).limit(1).map(Unifiable::get).collect(Collectors.toList());
		assertThat(asts).containsExactly(
				bin(Operation.PLUS, num(1), bin(Operation.MUL, num(2), num(3))).get());

		// print same AST -> canonical tokens
		Unifiable<LList<Token>> out = lvar();
		var seqs = g.print(bin(Operation.PLUS, num(1), bin(Operation.MUL, num(2), num(3))), out)
				.solve(out).limit(1).map(Unifiable::get).collect(Collectors.toList());
		assertThat(seqs).containsExactly(toks(1, Operation.PLUS, 2, Operation.MUL, 3).get());
	}

	@Test
	public void alternative_ast_root_guard_prunes_generation() {
		// S := (+ NUMBER PLUS NUMBER) | (* NUMBER MUL NUMBER)
		//              ^^^^^ structural token included
		Map<String, Rule> defs = new HashMap<>();
		defs.put("S", Alternative.of(
				Node.of(OperatorToken.of(Operation.PLUS),
						Terminal.leaf("NUMBER"),
						Terminal.skip("PLUS"),          // <-- include structural operator
						Terminal.leaf("NUMBER")),
				Node.of(OperatorToken.of(Operation.MUL),
						Terminal.leaf("NUMBER"),
						Terminal.skip("MUL"),
						Terminal.leaf("NUMBER"))
		));
		CompiledGrammar<Token> g = compile("S", defs);

		// PRINT: (+ 1 2) should only emit PLUS branch tokens
		Unifiable<LList<Token>> out = lvar();
		var seqs = g.print(bin(Operation.PLUS, num(1), num(2)), out)
				.solve(out).limit(1).map(Unifiable::get).collect(Collectors.toList());

		assertThat(seqs).containsExactly(toks(1, Operation.PLUS, 2).get());
	}

	@Test
	public void parse_fails_on_wrong_token() {
		// S := NUMBER
		Map<String, Rule> defs = new HashMap<>();
		defs.put("S", Terminal.leaf("NUMBER"));
		CompiledGrammar<Token> g = compile("S", defs);

		Unifiable<LTree<Token>> ast = lvar();
		var res = g.parse(ast, toks(Operation.PLUS)).solve(ast).collect(Collectors.toList());
		assertThat(res).isEmpty();
	}
}