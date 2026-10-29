package net.officefloor.sinewslim.core;

/**
 * What SinewSlim says about one method (DESIGN.md, The result): its status, the proposal to
 * show, and for a method that cannot be slimmed, what blocked it.
 *
 * @param status     the verdict
 * @param obstacle   what blocked every segmentation; null unless {@link Status#NOT_SLIMMABLE} or
 *                   {@link Status#SLIMMABLE_WITH_RECORD}
 * @param proposal   the segmentation to show: the default search's, the structural one's when
 *                   it is preferred, or the records variant's; infeasible when there is none
 * @param best       the default search's result
 * @param structural the structural-only search's result, or null when not asked for
 */
public record Assessment(Status status, Obstacle obstacle, SlimResult proposal, SlimResult best, SlimResult structural) {

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

	/** Why no segmentation reached the cap under the limits. */
	public enum Obstacle {
		/** Every cut carries two or more results. */
		OUTPUTS("every cut carries two or more results"),
		/** Every cut needs more parameters than the limit. */
		INPUTS("every cut needs more parameters than the limit"),
		/** Every cut carries two or more results or needs too many parameters. */
		INPUTS_AND_OUTPUTS("every cut carries two or more results or needs more parameters than the limit"),
		/** No run can move: a jump leaves it, or a statement longer than the cap cannot be shortened from inside. */
		STRUCTURE("no run can be moved: a break, continue or return leaves it, or a statement longer than the cap cannot be shortened from inside");

		private final String reason;

		Obstacle(String reason) {
			this.reason = reason;
		}

		public String reason() {
			return reason;
		}
	}
}
