package net.officefloor.sinewslim.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the cheapest set of contiguous statement runs whose extraction brings a method, and every
 * piece, to the target length ({@code SPLIT.md}; {@code split.py}, split).
 *
 * <p>
 * The search recurses once or twice per level of the statement tree, so a method with a long
 * else-if chain needs a deep stack; run it on a thread with a generous stack size.
 */
public final class Slimmer {

	private Slimmer() {
	}

	/** The order cuts are reported in: {@code split.py} sorts its (lo, hi, inputs, outputs, binding) tuples. */
	private static Comparator<Seam> cutOrder(Params p) {
		return Comparator.comparingInt(Seam::lo).thenComparingInt(Seam::hi)
				.thenComparingInt((Seam s) -> s.inputs().size()).thenComparingInt(s -> s.outputs().size())
				.thenComparing(s -> s.binding(p).name().toLowerCase());
	}

	public static SlimResult slim(MethodFacts facts, Params p) {
		MethodIndex m = new MethodIndex(facts, p.freeParams());
		int total = facts.length() > 0 ? facts.length() : m.code.length;
		if (total < p.trigger()) {
			return new SlimResult(false, true, 0, 0, total, List.of(), false, p);
		}
		Search search = new Search(m, p);
		// what the search removes is read against the statements it sees; the lines no statement
		// covers (the signature, a closing brace) stay whatever is cut
		int kidsTotal = search.covered(facts.body());
		Search.Option best = null;
		for (Search.Option o : search.solveSeq(facts.body())) {
			if (total - (kidsTotal - o.rem()) <= p.target() && (best == null || o.better(best))) {
				best = o;
			}
		}
		if (best == null) {
			return new SlimResult(true, false, SlimResult.INFEASIBLE, SlimResult.INFEASIBLE, total, List.of(), false, p);
		}
		List<Seam> cuts = new ArrayList<>(best.cuts());
		cuts.sort(cutOrder(p));
		return new SlimResult(true, true, best.width(), best.cost(), total - (kidsTotal - best.rem()), cuts,
				m.remainderLocked(cuts, p), p);
	}
}
