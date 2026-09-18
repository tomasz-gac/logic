package com.tgac.logic.finitedomain.capabilities;

// ABOUTME: The reference instances pass their own admission receipts — including
// ABOUTME: the traps this arc excavated: scale twins, Period inexactness, edges.

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;

public class CapabilityLawsTest {

	private static final List<Long> LONG_SAMPLES = Arrays.asList(
			-3L, -1L, 0L, 1L, 2L, 7L);
	private static final List<Long> LONG_EDGES = Arrays.asList(
			-3L, 0L, 7L, Long.MAX_VALUE - 1, Long.MIN_VALUE + 1);
	private static final List<Long> LONG_DIVISORS = Arrays.asList(-3L, -1L, 1L, 2L, 7L);

	@Test
	public void longsHoldEverySeat() {
		// arithmetic samples stay inside the exact range — overflow THROWS
		// (loudly, by design); edges exercise identity and stepping only
		CapabilityLaws.arithmetic(Arithmetic.LONGS, LONG_SAMPLES, LONG_SAMPLES);
		CapabilityLaws.multiplicative(Multiplicative.LONGS, LONG_SAMPLES, LONG_DIVISORS);
		CapabilityLaws.discrete(Discrete.LONGS, Comparator.naturalOrder(), LONG_EDGES);
		CapabilityLaws.identity(Comparator.naturalOrder(), LONG_EDGES);
	}

	@Test
	public void intsHoldEverySeat() {
		List<Integer> samples = Arrays.asList(-3, -1, 0, 1, 2, 7);
		CapabilityLaws.arithmetic(Arithmetic.INTS, samples, samples);
		CapabilityLaws.multiplicative(Multiplicative.INTS, samples, Arrays.asList(-3, -1, 1, 2, 7));
		CapabilityLaws.discrete(Discrete.INTS, Comparator.naturalOrder(), samples);
		CapabilityLaws.identity(Comparator.naturalOrder(), samples);
	}

	@Test
	public void bigIntegersHoldEverySeat() {
		List<BigInteger> samples = Arrays.asList(
				BigInteger.valueOf(-5), BigInteger.ZERO, BigInteger.ONE,
				BigInteger.TEN, new BigInteger("123456789012345678901234567890"));
		List<BigInteger> divisors = Arrays.asList(
				BigInteger.valueOf(-5), BigInteger.ONE, BigInteger.TEN);
		CapabilityLaws.arithmetic(Arithmetic.BIG_INTEGERS, samples, samples);
		CapabilityLaws.multiplicative(Multiplicative.BIG_INTEGERS, samples, divisors);
		CapabilityLaws.discrete(Discrete.BIG_INTEGERS, Comparator.naturalOrder(), samples);
		CapabilityLaws.identity(Comparator.naturalOrder(), samples);
	}

	@Test
	public void exactDecimalsHoldTheirSeatsOverCanonicalValues() {
		// canonical samples: the emission convention the instances keep by
		// stripping — a client stating 2.50 finds out here, deliberately
		List<BigDecimal> samples = Arrays.asList(
				new BigDecimal("-2.5"), BigDecimal.ZERO, BigDecimal.ONE,
				new BigDecimal("0.125"), new BigDecimal("40"));
		List<BigDecimal> divisors = Arrays.asList(
				new BigDecimal("-2.5"), BigDecimal.ONE, new BigDecimal("0.125"),
				new BigDecimal("40"));
		CapabilityLaws.arithmetic(Arithmetic.BIG_DECIMALS, samples, samples);
		CapabilityLaws.multiplicative(Multiplicative.BIG_DECIMALS, samples, divisors);
		CapabilityLaws.identity(BigDecimal::compareTo, samples);
	}

	@Test
	public void scaleTwinsFailTheIdentityReceipt() {
		// the kit catching exactly what it was born from: 0.5 and 0.50 are
		// comparison-equal, equals-unequal — not canonical, not admissible
		assertThatThrownBy(() -> CapabilityLaws.identity(BigDecimal::compareTo,
				Arrays.asList(new BigDecimal("0.5"), new BigDecimal("0.50"))))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("canonical");
	}

	@Test
	public void datesHoldTheirSeats() {
		List<LocalDate> dates = Arrays.asList(
				LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28),
				LocalDate.of(2024, 2, 29), LocalDate.of(2026, 9, 18));
		List<Long> days = Arrays.asList(-31L, -1L, 0L, 1L, 30L, 365L);
		CapabilityLaws.arithmetic(Arithmetic.DATES, dates, days);
		CapabilityLaws.discrete(Discrete.DATES, Comparator.naturalOrder(), dates);
		CapabilityLaws.identity(Comparator.<LocalDate> naturalOrder(), dates);
	}

	@Test
	public void instantsHoldTheirSeats() {
		List<Instant> instants = Arrays.asList(
				Instant.EPOCH, Instant.parse("2026-09-18T12:00:00Z"),
				Instant.parse("1969-07-20T20:17:00Z"));
		List<Duration> deltas = Arrays.asList(
				Duration.ZERO, Duration.ofNanos(1), Duration.ofDays(-400),
				Duration.ofSeconds(1, 999_999_999));
		CapabilityLaws.arithmetic(Arithmetic.INSTANTS, instants, deltas);
		CapabilityLaws.identity(Comparator.<Instant> naturalOrder(), instants);
	}
}
