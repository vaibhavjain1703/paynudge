package com.paynudge.csvimport;

import java.util.Random;

import com.paynudge.common.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money is integer paise from the spreadsheet to the reminder, so it is exact to the paisa. Checked over many random
 * amounts (fixed seed): what a reminder shows reads back as the same paise, and sums stay exact where floating
 * point would drift.
 */
class MoneyExactnessTest {

	private static final int CASES = 100_000;

	@Test
	void everyAmountReadsBackAsTheSamePaiseAfterBeingShown() {
		Random random = new Random(150050L);
		for (int i = 0; i < CASES; i++) {
			// Up to the ₹1 crore limit, with paise half the time.
			long paise = 1 + random.nextLong(Amounts.MAX_RUPEES.longValueExact() * 100);
			if (random.nextBoolean()) {
				paise -= paise % 100;
				paise = Math.max(paise, 100);
			}
			String shown = Money.rupees(paise);

			assertThat(Amounts.parsePaise(shown)).as(shown).isEqualTo(paise);
		}
	}

	@Test
	void amountsAddUpExactlyWhereDoublesWouldNot() {
		long total = 0;
		double floating = 0;
		for (int i = 0; i < 1_000; i++) {
			total += Amounts.parsePaise("0.10");
			floating += 0.10;
		}
		assertThat(total).isEqualTo(Amounts.parsePaise("100"));
		assertThat(Money.rupees(total)).isEqualTo("₹100");
		assertThat(floating).as("why paise, not doubles").isNotEqualTo(100.0);
	}

	@Test
	void indianGroupingAndPaiseAreShownAsPeopleWriteThem() {
		assertThat(Money.rupees(1)).isEqualTo("₹0.01");
		assertThat(Money.rupees(150050)).isEqualTo("₹1,500.50");
		assertThat(Money.rupees(15_000_000)).isEqualTo("₹1,50,000");
		assertThat(Money.rupees(1_000_000_000)).isEqualTo("₹1,00,00,000");
		assertThat(Amounts.parsePaise("₹1,00,00,000")).isEqualTo(1_000_000_000);
		assertThat(Amounts.parsePaise("Rs. 1,500.5")).isEqualTo(150050);
	}

}
