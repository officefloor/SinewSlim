package net.officefloor.sinewslim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.officefloor.sinewslim.facts.Seam;

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
		assertEquals(5, p.width(seam(4, 1)));
		assertEquals(4 + 1 + 1, p.cost(seam(4, 1)));
		assertEquals(3 * 2 + 2 + 1, p.records().cost(seam(2, 2)));
	}

	@Test
	void eachParameterBeyondTheSoftLimitCostsThePremium() {
		Params p = Params.DEFAULTS.toBuilder().softInputs(3).inputPremium(2).build();
		assertEquals(3, p.inputCost(seam(3, 0)));
		assertEquals(4 + 2, p.inputCost(seam(4, 0)));
		assertEquals(5 + 2 * 2, p.inputCost(seam(5, 0)));
		assertEquals(9 + 1, p.width(seam(5, 1)));
		assertEquals(9 + 1 + 1, p.cost(seam(5, 1)));
	}

	@Test
	void theHardLimitsDecideFeasibility() {
		Params p = Params.DEFAULTS;
		assertTrue(p.feasible(seam(5, 1)));
		assertFalse(p.feasible(seam(6, 0)));
		assertFalse(p.feasible(seam(1, 2)));
		assertTrue(p.records().feasible(seam(1, 2)));
		assertTrue(p.unlimitedInputs().feasible(seam(6, 0)));
	}

	@Test
	void bindingFollowsFieldsAndOwnCalls() {
		Params p = Params.DEFAULTS;
		assertEquals(Binding.FREE, p.binding(seam(1, 0)));
		Seam reads = new Seam(1, 5, List.of(), List.of(), List.of(), List.of("cfg"), List.of(), 0, List.of());
		assertEquals(Binding.BOUND, p.binding(reads));
		assertEquals(Binding.LOCKED, p.toBuilder().lockOnAnyField(true).build().binding(reads));
		Seam writes = new Seam(1, 5, List.of(), List.of(), List.of(), List.of(), List.of("cache"), 0, List.of());
		assertEquals(Binding.LOCKED, p.binding(writes));
		Seam calls = new Seam(1, 5, List.of(), List.of(), List.of(), List.of(), List.of(), 1, List.of());
		assertEquals(Binding.LOCKED, p.binding(calls));
	}
}
