package net.officefloor.sinewslim.cli.report;

/**
 * One class's long methods together ({@code SPLIT.md}, Across a class). A class whose long
 * methods split only into locked pieces has concentrated its logic on its data: that is the god
 * class rather than a set of long methods, and splitting will not fix it.
 *
 * @param path             the file
 * @param type             the class
 * @param overCap          its methods at or over the trigger
 * @param pieces           the pieces their proposals produce
 * @param lockedPieces     of those, the ones an instance method of this class must keep
 * @param remainders       the methods with a proposal, each leaving a remainder
 * @param lockedRemainders of those, the remainders still bound to the class
 * @param notSlimmable     its methods no segmentation brings under the cap
 */
public record ClassReport(String path, String type, int overCap, int pieces, int lockedPieces, int remainders,
		int lockedRemainders, int notSlimmable) {

	/** Locked pieces and remainders over all pieces and remainders; 0 when there are none. */
	public double lockedShare() {
		int all = pieces + remainders;
		return all == 0 ? 0 : (lockedPieces + lockedRemainders) / (double) all;
	}
}
