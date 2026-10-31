package net.officefloor.sinewslim.facts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** A consumer's facts beside the shared schema: typed, kept apart by key, absent when not said. */
class ExtraFactsTest {

	static final FactKey<List<String>> THROWS = FactKey.of("test.throws");

	@Test
	void keepsEachFactUnderItsKey() {
		FactKey<Integer> catches = FactKey.of("test.catches");
		ExtraFacts x = ExtraFacts.builder().put(THROWS, List.of("IOException")).put(catches, 2).build();
		assertEquals(Optional.of(List.of("IOException")), x.get(THROWS));
		assertEquals(Optional.of(2), x.get(catches));
		assertTrue(x.has(THROWS));
	}

	@Test
	void keysWithTheSameNameAreDistinct() {
		FactKey<String> mine = FactKey.of("shared.name"), theirs = FactKey.of("shared.name");
		ExtraFacts x = ExtraFacts.builder().put(mine, "mine").build();
		assertEquals(Optional.of("mine"), x.get(mine));
		assertFalse(x.has(theirs));
	}

	@Test
	void nothingSaidIsNothingKept() {
		ExtraFacts x = ExtraFacts.builder().put(THROWS, null).build();
		assertSame(ExtraFacts.NONE, x);
		assertTrue(x.isEmpty());
		assertEquals(Optional.empty(), x.get(THROWS));
	}
}
