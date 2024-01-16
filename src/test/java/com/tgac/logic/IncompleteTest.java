package com.tgac.logic;

public class IncompleteTest {
	//	static Stream<Integer> ints(int start) {
	//		return cons(start, () -> ints(start + 1));
	//	}
	//
	//	@Test
	//	public void shouldGenerateInts() {
	//		Assertions.assertThat(
	//						ints(0).toJavaStream()
	//								.limit(10))
	//				.containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
	//	}
	//
	//	static Stream<Integer> range(int start, int end) {
	//		if (start >= end) {
	//			return empty();
	//		} else {
	//			return cons(start, () -> range(start + 1, end));
	//		}
	//	}
	//
	//	@Test
	//	public void shouldConcat() {
	//		Assertions.assertThat(
	//						concat(
	//								range(0, 5),
	//								range(6, 10)))
	//				.containsExactly(0, 1, 2, 3, 4, 6, 7, 8, 9);
	//	}
	//
	//	Stream<Integer> primes() {
	//		return nextPrime(2, new ArrayList<>()).get();
	//	}
	//
	//	Recur<Stream<Integer>> nextPrime(int number, List<Integer> computedPrimes) {
	//		if (Stream.ofAll(computedPrimes)
	//				.takeWhile(i -> i <= Math.sqrt(number))
	//				.toJavaStream()
	//				.noneMatch(p -> number % p == 0)) {
	//			computedPrimes.add(number);
	//			return done(cons(
	//					number,
	//					() -> incomplete(() -> nextPrime(number + 1, computedPrimes))));
	//		} else {
	//			return recur(() -> nextPrime(number + 1, computedPrimes));
	//		}
	//	}
	//
	//	@Test
	//	public void shouldComputePrimes() {
	//		Assertions.assertThat(primes().toJavaStream().limit(10))
	//				.containsExactly(2, 3, 5, 7, 11, 13, 17, 19, 23, 29);
	//	}
	//
	//	@Test
	//	public void shouldInc() {
	//		List<Integer> incomplete = incomplete(() -> done(of(1)))
	//				.peek(System.out::println)
	//				.collect(Collectors.toList());
	//		Assertions.assertThat(incomplete)
	//				.containsExactly(1);
	//	}
	//
	//	@Test
	//	public void shouldInterleave() {
	//		Stream<Integer> ones = of(1, 1, 1);
	//		Stream<Integer> twos = of(2, 2, 2);
	//		Assertions.assertThat(
	//						interleave(Array.of(ones, twos)).get()
	//								.collect(Collectors.toList()))
	//				.containsExactly(1, 2, 1, 2, 1, 2);
	//	}
	//
	//	@Test
	//	public void shouldInterleaveMany() {
	//		Stream<Integer> ones = of(1, 1, 1);
	//		Stream<Integer> twos = of(2, 2, 2);
	//		Stream<Integer> threes = of(3, 3, 3);
	//		Stream<Integer> fours = of(4, 4, 4);
	//		Assertions.assertThat(
	//						interleave(Array.of(ones, twos, threes, fours)).get()
	//								.collect(Collectors.toList()))
	//				.containsExactly(1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4);
	//	}
	//
	//	@Test
	//	public void shouldInterleaveZero() {
	//		Assertions.assertThat(interleave(Array.empty()).get().collect(Collectors.toList()))
	//				.isEmpty();
	//	}
	//
	//	@Test
	//	public void shouldInterleaveMZeros() {
	//		Assertions.assertThat(interleave(
	//						Array.of(empty(), empty(), empty()))
	//						.get()
	//						.collect(Collectors.toList()))
	//				.isEmpty();
	//	}
	//
	//	@Test
	//	public void shouldInterleaveVariousLengths() {
	//		Stream<Integer> ones = Stream.of(1);
	//		Stream<Integer> twos = Stream.of(2, 2);
	//		Stream<Integer> threes = Stream.of(3, 3, 3);
	//		Assertions.assertThat(
	//						interleave(Array.of(ones, twos, threes))
	//								.get()
	//								.collect(Collectors.toList()))
	//				.containsExactly(1, 2, 3, 2, 3, 3);
	//
	//	}
}