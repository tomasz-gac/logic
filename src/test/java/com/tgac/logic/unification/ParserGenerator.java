package com.tgac.logic.unification;

import static com.tgac.logic.goals.Logic.ground;
import static com.tgac.logic.goals.Matche.llist;
import static com.tgac.logic.goals.Matche.matche;
import static com.tgac.logic.projection.ProjectionConstraints.project;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.goals.Goal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Relational parser generator with semantic/structural split.
 * Contract for every compiled rule: (ast, in, out) -> Goal
 *
 * - Node:    semantic (wraps its semantic children); structural children are ignored
 * - Terminal.leaf(name): semantic leaf
 * - Terminal.skip(name): structural token (no AST)
 * - Optional: semantic by default (absent => LTree.empty())
 * - Repeat:  structural by default (safe; avoids non-termination in print direction)
 * - Structural.of(rule): force any rule to be structural (ast = LTree.empty())
 * - FoldLeft: build left-associative binary trees deterministically
 */
public final class ParserGenerator<T> {

	/* -------------------- Relational contracts -------------------- */

	@FunctionalInterface
	public interface Rel<T> {
		Goal apply(Unifiable<LTree<T>> ast,
				Unifiable<LList<T>> in,
				Unifiable<LList<T>> out);
	}

	@FunctionalInterface
	interface Rel2<T> {
		Goal apply(Unifiable<LTree<T>> a,
				Unifiable<LTree<T>> b,
				Unifiable<LList<T>> in,
				Unifiable<LList<T>> out);
	}

	/* ------------------------- Grammar AST ------------------------ */

	public interface Rule {
	}

	@Value
	public static class Ref implements Rule {
		String name;
	}

	@Value
	public static class Terminal implements Rule {
		String name;
		boolean intoAst; // true = leaf, false = skip

		public static Terminal leaf(String name) {
			return new Terminal(name, true);
		}

		public static Terminal skip(String name) {
			return new Terminal(name, false);
		}
	}

	@Value
	public static class Sequence implements Rule {
		List<Rule> items;

		public static Sequence of(Rule... rs) {
			return new Sequence(Arrays.asList(rs));
		}
	}

	@Value
	public static class Alternative implements Rule {
		List<Rule> options;

		public static Alternative of(Rule... rs) {
			return new Alternative(Arrays.asList(rs));
		}
	}

	@Value
	public static class Optional implements Rule {
		Rule sub;

		public static Optional of(Rule r) {
			return new Optional(r);
		}
	}

	@Value
	public static class Repeat implements Rule {
		Rule sub;
		int min; // 0: star, 1: plus

		public static Repeat star(Rule r) {
			return new Repeat(r, 0);
		}

		public static Repeat plus(Rule r) {
			return new Repeat(r, 1);
		}
	}

	/** Force a rule to be structural (ast = LTree.empty()). */
	@Value
	public static class Structural implements Rule {
		Rule sub;

		public static Structural of(Rule r) {
			return new Structural(r);
		}
	}

	/** Regular AST node: value at head, semantic children appended (structural children ignored). */
	@Value
	public static class Node<T> implements Rule {
		T value;
		List<Rule> children;

		@SafeVarargs
		public static <T> Node<T> of(T value, Rule... kids) {
			return new Node<>(value, Arrays.asList(kids));
		}
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	public static class FoldLeft<T> implements Rule {
		Rule seed;   // semantic
		Rule tails;  // structural: emits (op rhs) pairs
	}

	/* ------------------------- Terminals -------------------------- */

	@Value
	public static class TerminalInfo<T> {
		java.util.function.Predicate<T> guard; // e.g. t -> t.equals(PLUS) or instanceof NumberToken
		T fixed; // null for category like NUMBER; non-null for exact token like PLUS

		/** Structural pass: parse fixed; or print fixed. */
		public Goal pass(Unifiable<LList<T>> in, Unifiable<LList<T>> out) {
			if (fixed == null)
				return Goal.failure();
			Goal parse = ground(in).and(
					matche(in, llist((tok, rest) -> tok.unifies(lval(fixed)).and(out.unifies(rest))))
			);
			Goal print = in.unifies(LList.of(lval(fixed), out));
			return parse.orElse(print);
		}

		/** Guard for semantic leaf head (tok must be ground where used). */
		public Goal guardHead(Unifiable<T> tok) {
			return project(tok, t -> Goal.successIf(guard.test(t)));
		}
	}

	public interface TerminalTable<T> {
		TerminalInfo<T> get(String name);
	}

	/** Map exact names to exact tokens. */
	public static class ExactTokenTable<T> implements TerminalTable<T> {
		private final Map<String, T> map = new HashMap<>();

		public ExactTokenTable<T> bind(String name, T token) {
			map.put(name, token);
			return this;
		}

		@Override
		public TerminalInfo<T> get(String name) {
			T v = map.get(name);
			if (v == null)
				return null;
			return new TerminalInfo<>(t -> Objects.equals(t, v), v);
		}
	}

	/* ----------------------- CompiledGrammar ---------------------- */

	@Value
	public static class CompiledGrammar<T> {
		Map<String, Supplier<Rel<T>>> env;
		String start;

		public Goal parse(Unifiable<LTree<T>> ast, Unifiable<LList<T>> toks) {
			Supplier<Rel<T>> r = env.get(start);
			if (r == null)
				throw new IllegalArgumentException("Unknown start: " + start);
			return r.get().apply(ast, toks, LList.empty());
		}

		/** Same relation; other direction. */
		public Goal print(Unifiable<LTree<T>> ast, Unifiable<LList<T>> toks) {
			return parse(ast, toks);
		}

		public Supplier<Rel<T>> rule(String name) {
			return env.get(name);
		}
	}

	/* -------------------------- Generator ------------------------- */

	public static class Generator<T> {
		private final TerminalTable<T> terms;
		private final Map<String, Rule> defs;
		private final String start;
		private final Map<String, Supplier<Rel<T>>> env = new HashMap<>();

		public Generator(TerminalTable<T> terms, Map<String, Rule> defs, String start) {
			this.terms = terms;
			this.defs = defs;
			this.start = start;
			compileAll();
		}

		public CompiledGrammar<T> result() {
			return new CompiledGrammar<>(env, start);
		}

		private void compileAll() {
			defs.keySet().forEach(n -> env.put(n, new Holder<>()));
			defs.forEach((n, r) -> ((Holder<T>) env.get(n)).setTarget(compile(r)));
		}

		private static final class Holder<T> implements Supplier<Rel<T>> {
			Supplier<Rel<T>> target;

			void setTarget(Supplier<Rel<T>> t) {
				this.target = t;
			}

			@Override
			public Rel<T> get() {
				return target.get();
			}
		}

		private Supplier<Rel<T>> compile(Rule r) {
			if (r instanceof Terminal)
				return compileTerminal((Terminal) r);
			if (r instanceof Ref)
				return compileRef((Ref) r);
			if (r instanceof Sequence)
				return compileSequence((Sequence) r);
			if (r instanceof Alternative)
				return compileAlternative((Alternative) r);
			if (r instanceof Optional)
				return compileOptional((Optional) r);   // semantic
			if (r instanceof Repeat)
				return compileRepeat((Repeat) r);       // structural (safe)
			if (r instanceof Structural)
				return compileStructural((Structural) r);
			if (r instanceof Node)
				return compileNode((Node<T>) r);
			if (r instanceof FoldLeft)
				return compileFoldLeft((FoldLeft<T>) r);
			throw new IllegalArgumentException("Unknown rule: " + r);
		}

		private Supplier<Rel<T>> compileRef(final Ref ref) {
			return () -> (ast, in, out) -> Goal.defer(() -> env.get(ref.getName()).get().apply(ast, in, out));
		}

		private Supplier<Rel<T>> compileTerminal(final Terminal t) {
			final TerminalInfo<T> ti = terms.get(t.getName());
			if (ti == null)
				throw new IllegalArgumentException("Missing terminal: " + t.getName());

			if (!t.isIntoAst()) {
				// structural (skip)
				return () -> (ast, in, out) -> ti.pass(in, out)
						.and(ast.unifies(LTree.empty())); // force empty AST
			}

			// semantic leaf
			return () -> (ast, in, out) -> {
				Goal parse = ground(in).and(
						matche(in, llist((tok, rest) ->
								ti.guardHead(tok)
										.and(ast.unifies(LTree.of(tok)))
										.and(out.unifies(rest))))
				);
				Unifiable<T> v = lvar();
				Goal print = ast.unifies(LTree.of(v))
						.and(project(v, vv -> Goal.successIf(ti.guard.test(vv))))
						.and(in.unifies(LList.of(v, out)));
				return parse.orElse(print);
			};
		}

		private static boolean isStructural(Rule r) {
			return (r instanceof Structural) ||
					(r instanceof Terminal && !((Terminal) r).isIntoAst()) ||
					(r instanceof Repeat);     // we make Repeat structural by design here
		}

		private Supplier<Rel<T>> compileSequence(final Sequence seq) {
			final List<Rule> rs = seq.getItems();
			final List<Supplier<Rel<T>>> kids = rs.stream().map(this::compile).collect(Collectors.toList());

			// choose last semantic child index (to pass-through AST)
			final int lastSemantic = lastSemanticIndex(rs);

			return () -> (ast, in, out) -> {
				Goal acc = Goal.success();
				Unifiable<LList<T>> cur = in;

				for (int i = 0; i < kids.size(); i++) {
					final Supplier<Rel<T>> rel = kids.get(i);
					final Unifiable<LList<T>> mid = lvar();

					if (isStructural(rs.get(i))) {
						Unifiable<LTree<T>> ign = lvar();
						acc = acc.and(rel.get().apply(ign, cur, mid)); // ignore AST
					} else {
						Unifiable<LTree<T>> childAst = lvar();
						acc = acc.and(rel.get().apply(childAst, cur, mid));
						if (i == lastSemantic) {
							acc = acc.and(ast.unifies(childAst));        // pass-through
						}
					}
					cur = mid;
				}
				// If there was no semantic child at all, AST is empty.
				Goal ensureAst = (lastSemantic < 0) ? ast.unifies(LTree.empty()) : Goal.success();
				return acc.and(ensureAst).and(cur.unifies(out));
			};
		}

		private int lastSemanticIndex(List<Rule> rs) {
			int idx = -1;
			for (int i = 0; i < rs.size(); i++)
				if (!isStructural(rs.get(i)))
					idx = i;
			return idx;
		}

		private Supplier<Rel<T>> compileAlternative(final Alternative a) {
			final List<Supplier<Rel<T>>> opts = a.getOptions().stream().map(this::compile).collect(Collectors.toList());
			return () -> (ast, in, out) -> {
				Goal disj = Goal.failure();
				for (Supplier<Rel<T>> k : opts)
					disj = disj.or(Goal.defer(() -> k.get().apply(ast, in, out)));
				return disj;
			};
		}

		/** Optional is semantic: present => child AST; absent => LTree.empty(). */
		private Supplier<Rel<T>> compileOptional(final Optional opt) {
			final Supplier<Rel<T>> sub = compile(opt.getSub());
			return () -> (ast, in, out) -> {
				// present
				Goal present = Goal.defer(() -> {
					Unifiable<LTree<T>> child = lvar();
					Unifiable<LList<T>> mid = lvar();
					return sub.get().apply(child, in, mid)
							.and(ast.unifies(child))
							.and(mid.unifies(out));
				});
				// absent
				Goal absent = ast.unifies(LTree.empty()).and(in.unifies(out));
				return present.or(absent);
			};
		}

		/** Repeat is structural (safe): threads tokens; ast = empty. */
		private Supplier<Rel<T>> compileRepeat(final Repeat rep) {
			final Supplier<Rel<T>> sub = compile(rep.getSub());

			if (rep.getMin() == 0) {
				// star
				return () -> (ast, in, out) -> {
					Goal zero = ast.unifies(LTree.empty()).and(in.unifies(out));
					Goal more = Goal.defer(() -> {
						Unifiable<LTree<T>> ign = lvar();
						Unifiable<LList<T>> mid = lvar();
						return sub.get().apply(ign, in, mid)
								.and(compileRepeat(Repeat.star(rep.getSub())).get().apply(ign, mid, out));
					});
					// parse prefers consuming (when anchored); print prefers zero first
					return ground(in).and(more.orElse(zero))
							.orElse(zero.orElse(more));
				};
			} else {
				// plus
				return () -> (ast, in, out) -> Goal.defer(() -> {
					Unifiable<LTree<T>> ign = lvar();
					Unifiable<LList<T>> mid = lvar();
					return sub.get().apply(ign, in, mid)
							.and(compileRepeat(Repeat.star(rep.getSub())).get().apply(ign, mid, out))
							.and(ast.unifies(LTree.empty()));
				});
			}
		}

		/** Force any rule to be structural (ast = empty). */
		private Supplier<Rel<T>> compileStructural(final Structural s) {
			final Supplier<Rel<T>> sub = compile(s.getSub());
			return () -> (ast, in, out) -> Goal.defer(() -> {
				Unifiable<LTree<T>> ign = lvar();
				return sub.get().apply(ign, in, out).and(ast.unifies(LTree.empty()));
			});
		}

		/** Node: unify head BEFORE children; collect only semantic children; ignore structural ones. */
		private Supplier<Rel<T>> compileNode(final Node<T> n) {
			final List<Rule> childRules = n.getChildren();
			final List<Supplier<Rel<T>>> kids = childRules.stream().map(this::compile).collect(Collectors.toList());

			return () -> (ast, in, out) -> {
				Goal acc = Goal.success();
				Unifiable<LList<T>> cur = in;

				// children difference-list
				Unifiable<LList<LTree<T>>> kidsHead = lvar();
				Unifiable<LList<LTree<T>>> kidsTail = kidsHead;

				// lock parent node first (print-safe)
				acc = acc.and(ast.unifies(LTree.of(lval(n.getValue()), kidsHead)));

				for (int i = 0; i < kids.size(); i++) {
					final Rule r = childRules.get(i);
					final Supplier<Rel<T>> rel = kids.get(i);
					final Unifiable<LList<T>> mid = lvar();

					Unifiable<LTree<T>> childAst = lvar();
					acc = acc.and(rel.get().apply(childAst, cur, mid));

					// append only non-empty (semantic) children, but DO NOT fail if empty
					if (!isStructural(r)) {
						Unifiable<LList<LTree<T>>> nextTail = lvar();

						// Branch 1: skip empty child -> no cons; advance tail to nextTail
						Goal skipEmpty =
								childAst.unifies(LTree.empty())
										.and(kidsTail.unifies(nextTail));

						// Branch 2: take non-empty -> cons(childAst, nextTail)
						Goal takeChild =
								project(childAst, ca -> Goal.successIf(!ca.isEmpty()))
										.and(kidsTail.unifies(LList.of(childAst, nextTail)));

						acc = acc.and(skipEmpty.orElse(takeChild));

						// In both branches, nextTail is the new open end of the DL
						kidsTail = nextTail;
					}
					cur = mid;
				}

				return acc
						.and(kidsTail.unifies(LList.empty()))
						.and(cur.unifies(out));
			};
		}

		/** FoldLeft: build left-associative binary tree from seed and structural tails. */
		private Supplier<Rel<T>> compileFoldLeft(final FoldLeft<T> f) {
			final Supplier<Rel<T>> seedRel  = compile(f.getSeed());
			final Supplier<Rel<T>> tailsRel = compile(f.getTails());

			// step(accIn, accOut, in, out): recursively accumulate
			final Rel2<T> step = new Rel2<T>() {
				@Override
				public Goal apply(Unifiable<LTree<T>> accIn,
						Unifiable<LTree<T>> accOut,
						Unifiable<LList<T>> in,
						Unifiable<LList<T>> out) {

					// zero: stop (no tail)
					Goal zero = accOut.unifies(accIn).and(in.unifies(out));

					// --- PARSE branch (anchored input): consume one tail, extend accIn, recurse ---
					Goal oneParse = Goal.defer(() -> {
						Unifiable<LTree<T>> rhs = lvar();          // semantic tail AST: Node(op, [rhsChild])
						Unifiable<LList<T>> mid = lvar();
						Unifiable<LTree<T>> newAcc = lvar();

						return tailsRel.get().apply(rhs, in, mid)
								.and(project(rhs, (LTree<T> r) -> {
									if (r == null || r.isEmpty()) return Goal.failure();

									// rhs is Node(op, [child]); build newAcc = Node(op, [accIn, child])
									Unifiable<T> opVal = r.getValue();
									Unifiable<LList<LTree<T>>> children = r.getChildren();
									return matche(children,
											llist((child, rest) ->
													newAcc.unifies(LTree.of(opVal,
															LList.of(accIn, LList.of(child, LList.empty()))))),
											llist(() -> Goal.failure()));
								}))
								.and(this.apply(newAcc, accOut, mid, out));
					});

					// --- PRINT branch (unanchored input): peel accOut and force exactly that tail ---
					Goal onePrint = Goal.defer(() -> {
						// accOut must be Node(op, [accMid, rhsChild])
						Unifiable<T> opVal = lvar();
						Unifiable<LTree<T>> accMid = lvar();
						Unifiable<LTree<T>> rhsChild = lvar();
						Unifiable<LList<LTree<T>>> kids = lvar();

						Unifiable<LTree<T>> rhsTailAst = lvar();   // Node(op, [rhsChild])
						Unifiable<LList<T>> mid = lvar();

						return accOut.unifies(LTree.of(opVal, kids))
								.and(matche(kids,
										llist((leftKid, rest) ->
														// leftKid must be accMid; rhsChild is the second
														leftKid.unifies(accMid).and(matche(rest,
																llist((second, tailEnd) ->
																		second.unifies(rhsChild).and(tailEnd.unifies(LList.empty()))),
																llist(() -> Goal.failure()))),
												llist(() -> Goal.failure())))
										// Build the exact tail AST that tailsRel expects to print
										.and(rhsTailAst.unifies(LTree.of(opVal, LList.of(rhsChild, LList.empty()))))
										// Print exactly that tail (emits operator token + rhs tokens)
										.and(tailsRel.get().apply(rhsTailAst, in, mid))
										// Recurse toward the base: (accIn -> accMid)
										.and(this.apply(accIn, accMid, mid, out));
					});

					// Direction split: parse (anchored) prefers one; print prefers zero unless peeling fits.
					return ground(in).and(oneParse.orElse(zero))
							.orElse(onePrint.orElse(zero));
				}
			};

			// Outer: start from seed, then fold tails
			return () -> (ast, in, out) -> Goal.defer(() -> {
				Unifiable<LTree<T>> seedAst = lvar();
				Unifiable<LList<T>> mid     = lvar();
				return seedRel.get().apply(seedAst, in, mid)
						.and(step.apply(seedAst, ast, mid, out));
			});
		}
	}
}