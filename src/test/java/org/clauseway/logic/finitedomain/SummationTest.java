package org.clauseway.logic.finitedomain;

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.unification.LVal.lval;
import static org.clauseway.logic.unification.LVar.lvar;

import org.clauseway.logic.Utils;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.unification.LList;
import org.clauseway.logic.unification.Term;
import org.clauseway.logic.unification.Unifiable;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple3;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class SummationTest {

	@Test
	public void shouldSum() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();
		Unifiable<Long> k = lvar();

		Instant start = Instant.now();

		Goal goal =
				Longs.addo(i, j, k)
						.and(Longs.separate(i, j))
						.and(dom(i, Longs.interval(0, 100)))
						.and(dom(j, Longs.interval(0, 100)))
						.and(dom(k, Longs.interval(0, 100)));

		List<Tuple3<Long, Long, Long>> result =
				Utils.collect(goal.solve(lval(Tuple.of(i, j, k)), TestSchedulers.factory())
						.map(Term::get)
						.map(t -> t
								.map1(Term::get)
								.map2(Term::get)
								.map3(Term::get)));

		Assertions.assertThat(result.stream().allMatch(t -> t._1 + t._2 == t._3))
				.isTrue();
		Assertions.assertThat(result.stream().noneMatch(t -> t._1.equals(t._2)))
				.isTrue();
	}

	public static Goal addDigitso(
			Unifiable<Integer> augend,
			Unifiable<Integer> addend,
			Unifiable<Integer> carryIn,
			Unifiable<Integer> carryOut,
			Unifiable<Integer> digit) {
		Unifiable<Integer> partialSum = lvar();
		Unifiable<Integer> sum = lvar();
		// the intermediates' domains are MINTED: each addo hulls its free
		// position from the operands it can see
		return Ints.addo(augend, addend, partialSum)
				.and(Ints.addo(partialSum, carryIn, sum))
				.and(Goal.failure()
						.or(Ints.lss(lval(9), sum)
								.and(carryOut.unifies(1))
								.and(Ints.addo(digit, lval(10), sum)))
						.or(Ints.leq(sum, lval(9))
								.and(carryOut.unifies(0))
								.and(digit.unifies(sum))));
	}

	public static Goal sendMoreMoneyo(Unifiable<LList<Integer>> letters) {
		Unifiable<Integer> s = lvar();
		Unifiable<Integer> e = lvar();
		Unifiable<Integer> n = lvar();
		Unifiable<Integer> d = lvar();
		Unifiable<Integer> m = lvar();
		Unifiable<Integer> o = lvar();
		Unifiable<Integer> r = lvar();
		Unifiable<Integer> y = lvar();

		Unifiable<Integer> carry0 = lvar();
		Unifiable<Integer> carry1 = lvar();
		Unifiable<Integer> carry2 = lvar();
		Unifiable<LList<Integer>> lst = LList.ofAll(s, e, n, d, m, o, r, y);
		return letters.unifies(lst)
				.and(FiniteDomainTest.distinctoFd(Arrays.asList(s, e, n, d, m, o, r, y)))
				.and(dom(s, Ints.interval(1, 9)))
				.and(dom(m, Ints.interval(1, 9)))
				.and(dom(e, Ints.interval(0, 9)))
				.and(dom(n, Ints.interval(0, 9)))
				.and(dom(d, Ints.interval(0, 9)))
				.and(dom(o, Ints.interval(0, 9)))
				.and(dom(r, Ints.interval(0, 9)))
				.and(dom(y, Ints.interval(0, 9)))
				.and(dom(carry0, Ints.interval(0, 1)))
				.and(dom(carry1, Ints.interval(0, 1)))
				.and(dom(carry2, Ints.interval(0, 1)))
				.and(addDigitso(s, m, carry2, m, o))
				.and(addDigitso(e, o, carry1, carry2, n))
				.and(addDigitso(n, r, carry0, carry1, e))
				.and(addDigitso(d, e, lval(0), carry0, y));
	}

	@Test
	public void shouldSend() {
		Unifiable<LList<Integer>> letters = lvar();

		List<List<Integer>> result = Utils.collect(sendMoreMoneyo(letters)
				.solve(letters, TestSchedulers.factory())
				.map(Term::get)
				.map(l -> l.toValueStream().collect(Collectors.toList())));

		int S = result.get(0).get(0);
		int E = result.get(0).get(1);
		int N = result.get(0).get(2);
		int D = result.get(0).get(3);
		int M = result.get(0).get(4);
		int O = result.get(0).get(5);
		int R = result.get(0).get(6);
		int Y = result.get(0).get(7);

		Assertions.assertThat(
						asNumber(Arrays.asList(S, E, N, D))
								+ asNumber(Arrays.asList(M, O, R, E)))
				.isEqualTo(asNumber(Arrays.asList(M, O, N, E, Y)));
	}

	@Test
	public void testAsNumber() {
		Assertions.assertThat(
						asNumber(Arrays.asList(1, 2, 3, 4)))
				.isEqualTo(1234);
	}

	private static int asNumber(List<Integer> digits) {
		return IntStream.range(0, digits.size())
				.mapToObj(i -> Tuple.of(i,
						BigInteger.TEN
								.pow(digits.size() - i - 1)
								.intValueExact()))
				.map(t -> t.apply((i, pow) ->
						pow * digits.get(i)))
				.reduce(Integer::sum)
				.orElse(0);
	}

	@Test
	public void sumComputesTheUndomainedAddend() {
		// b needs no domain: its hull is minted from a and c, and each
		// labelled pair collapses b = c − a, negatives included
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();
		List<Tuple3<Integer, Integer, Integer>> results =
				Utils.collect(Ints.addo(a, b, c)
						.and(dom(a, Ints.interval(0, 10)))
						.and(dom(c, Ints.interval(0, 10)))
						.solve(lval(Tuple.of(a, b, c)), TestSchedulers.factory())
						.map(Term::get)
						.map(t -> t.map1(Term::get).map2(Term::get).map3(Term::get)));

		Assertions.assertThat(results)
				.hasSize(121)
				.allMatch(t -> t._1 + t._2 == t._3);
	}

	@Test(expected = RuntimeException.class)
	public void twoFreePositionsStillRefuseLoudly() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();
		// two positions without domains: nothing determines them, and the
		// parked constraint refuses at reify instead of losing answers
		Assertions.assertThat(Utils.collect(Ints.addo(a, b, c)
						.and(dom(a, Ints.interval(0, 10)))
						.solve(lval(Tuple.of(a, b, c)), TestSchedulers.factory())
						.map(Term::get)))
				.isEmpty();
	}
}
