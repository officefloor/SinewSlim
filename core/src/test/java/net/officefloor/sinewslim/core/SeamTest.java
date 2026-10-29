package net.officefloor.sinewslim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The cost of one seam under the limits: the soft parameter premium above all, which split.py does not have. */
class SeamTest {

	static Seam seam(int inputs, int outputs) {
		List<String> in = List.of("a", "b", "c", "d", "e", "f").subList(0, inputs);
		List<String> out = List.of("x", "y", "z").subList(0, outputs);
		return new Seam(1, 5, in, out, List.of(), List.of(), List.of(), 0, List.of());
	}

	@Test
	void withoutASoftLimitEveryValueCostsOne() {
		Params p = Params.DEFAULTS;
		assertEquals(5, seam(4, 1).width(p));
		assertEquals(4 + 1 + 1, seam(4, 1).cost(p));
		assertEquals(3 * 2 + 2 + 1, seam(2, 2).cost(p.records()));
	}

	@Test
	void eachParameterBeyondTheSoftLimitCostsThePremium() {
		Params p = Params.DEFAULTS.toBuilder().softInputs(3).inputPremium(2).build();
		assertEquals(3, seam(3, 0).inputCost(p));
		assertEquals(4 + 2, seam(4, 0).inputCost(p));
		assertEquals(5 + 2 * 2, seam(5, 0).inputCost(p));
		assertEquals(9 + 1, seam(5, 1).width(p));
		assertEquals(9 + 1 + 1, seam(5, 1).cost(p));
	}

	@Test
	void theHardLimitsDecideFeasibility() {
		Params p = Params.DEFAULTS;
		assertTrue(seam(5, 1).feasible(p));
		assertFalse(seam(6, 0).feasible(p));
		assertFalse(seam(1, 2).feasible(p));
		assertTrue(seam(1, 2).feasible(p.records()));
		assertTrue(seam(6, 0).feasible(p.unlimitedInputs()));
	}

	@Test
	void bindingFollowsFieldsAndOwnCalls() {
		Params p = Params.DEFAULTS;
		assertEquals(Binding.FREE, seam(1, 0).binding(p));
		Seam reads = new Seam(1, 5, List.of(), List.of(), List.of(), List.of("cfg"), List.of(), 0, List.of());
		assertEquals(Binding.BOUND, reads.binding(p));
		assertEquals(Binding.LOCKED, reads.binding(p.toBuilder().lockOnAnyField(true).build()));
		Seam writes = new Seam(1, 5, List.of(), List.of(), List.of(), List.of(), List.of("cache"), 0, List.of());
		assertEquals(Binding.LOCKED, writes.binding(p));
		Seam calls = new Seam(1, 5, List.of(), List.of(), List.of(), List.of(), List.of(), 1, List.of());
		assertEquals(Binding.LOCKED, calls.binding(p));
	}
}
