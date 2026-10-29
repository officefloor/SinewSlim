package net.officefloor.sinewslim.core;

/**
 * What SinewSlim says about one method (DESIGN.md, The result): its status, the proposal to
 * show, and for a method that cannot be slimmed, what blocked it.
 *
 * @param status     the verdict
 * @param obstacle   what blocked every segmentation; null unless {@link Status#NOT_SLIMMABLE} or
 *                   {@link Status#SLIMMABLE_WITH_RECORD}
 * @param detail     where the obstacle is, when it is one statement ({@code the loop at lines
 *                   52-88, 37 lines}); null otherwise
 * @param proposal   the segmentation to show: the default search's, the structural one's when
 *                   it is preferred, or the records variant's; infeasible when there is none
 * @param best       the default search's result
 * @param structural the structural-only search's result, or null when not asked for
 */
public record Assessment(Status status, Obstacle obstacle, String detail, SlimResult proposal, SlimResult best,
		SlimResult structural) {

	public enum Status {
		/** Shorter than the trigger: nothing to do. */
		UNDER_CAP,
		/** A segmentation brings it under the cap with one return per piece. */
		SLIMMABLE,
		/** Every segmentation needs a piece to return two or more values: a record type. */
		SLIMMABLE_WITH_RECORD,
		/** A segmentation exists, but every piece and the remainder stay bound to the class. */
		SLIMMABLE_BUT_LOCKED,
		/** No segmentation brings it under the cap: a design problem, not an edit. */
		NOT_SLIMMABLE
	}

	/** Why no segmentation reached the cap under the limits, checked in this order. */
	public enum Obstacle {
		/** Every cut carries two or more results. */
		OUTPUTS("every cut carries two or more results"),
		/** Every cut needs more parameters than the limit. */
		INPUTS("every cut needs more parameters than the limit"),
		/** Every cut carries two or more results or needs too many parameters. */
		INPUTS_AND_OUTPUTS("every cut carries two or more results or needs more parameters than the limit"),
		/** Every run that would do has a return, break or continue leaving it. */
		JUMPS("every run that would bring it under the cap has a return, break or continue leaving it "
				+ "(early returns, or a switch of returns)"),
		/** A single statement is longer than the cap and nothing inside it can move. */
		LONG_STATEMENT("a single statement is longer than the cap and nothing inside it can move"),
		/** So long that the call lines one level of pieces leaves behind are themselves over the cap. */
		LENGTH("too long to slim in one pass: even one call line per piece leaves it over the cap");

		private final String reason;

		Obstacle(String reason) {
			this.reason = reason;
		}

		public String reason() {
			return reason;
		}
	}

	/** The reason, with where it is when that is known. */
	public String reason() {
		if (obstacle == null) {
			return null;
		}
		return detail == null ? obstacle.reason() : obstacle.reason() + ": " + detail;
	}
}
