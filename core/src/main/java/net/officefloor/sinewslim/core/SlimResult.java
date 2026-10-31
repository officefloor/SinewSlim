package net.officefloor.sinewslim.core;

import java.util.List;

import net.officefloor.sinewslim.facts.Seam;

/**
 * The best segmentation of one method under one set of parameters, or why there is none.
 *
 * @param applicable      the method is at or over the trigger
 * @param feasible        some segmentation brings it, and every piece, to the target
 * @param width           the worst seam of the best segmentation ({@link #INFEASIBLE} if none)
 * @param cost            its total cost ({@link #INFEASIBLE} if none)
 * @param remainder       code lines left in the method afterwards
 * @param cuts            the pieces, in line order (a piece inside another is listed too)
 * @param pieceLines      per cut, the code lines the new method has once the pieces inside it are
 *                        out; extracting it saves this less its one call line
 * @param remainderLocked what stays behind writes a field or calls the class's own methods
 * @param params          what it was read under
 */
public record SlimResult(boolean applicable, boolean feasible, int width, int cost, int remainder, List<Seam> cuts,
		List<Integer> pieceLines, boolean remainderLocked, Params params) {

	/** The width and cost of a method no segmentation brings under the cap: larger than any seam the limits allow. */
	public static final int INFEASIBLE = 50;

	public SlimResult {
		cuts = List.copyOf(cuts);
		pieceLines = List.copyOf(pieceLines);
	}

	public int pieces() {
		return cuts.size();
	}

	/** The sum of the seams: the sinews the slimming creates. */
	public int interfaceSize() {
		int sum = 0;
		for (Seam s : cuts) {
			sum += s.inputs().size() + s.outputs().size();
		}
		return sum;
	}

	public int count(Binding b) {
		int n = 0;
		for (Seam s : cuts) {
			if (params.binding(s) == b) {
				n++;
			}
		}
		return n;
	}
}
