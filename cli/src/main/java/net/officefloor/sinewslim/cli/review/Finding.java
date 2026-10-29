package net.officefloor.sinewslim.cli.review;

import net.officefloor.sinewslim.cli.report.MethodReport;
import net.officefloor.sinewslim.core.Assessment.Status;

/**
 * What the review says about one method the change touched (DESIGN.md, Review).
 *
 * @param kind   how its length moved against the cap
 * @param method the method as the change leaves it, with its verdict and proposal
 * @param before its length before the change; -1 for a new method
 * @param gates  whether it counts against the change under the enforcement
 */
public record Finding(Kind kind, MethodReport method, int before, boolean gates) {

	public enum Kind {
		/** Was under the cap, is over it now. */
		CROSSED,
		/** Was over the cap and got longer. */
		GREW,
		/** A new method, over the cap. */
		NEW_OVER,
		/** Was over the cap, and is no longer than it was: information. */
		STILL_OVER,
		/** Was over the cap, and the change brought it under: information, and good news. */
		SLIMMED
	}

	/** How the length moved, in words. */
	public String movement() {
		int after = method.length();
		return switch (kind) {
		case CROSSED -> "crossed the cap: " + before + " -> " + after + " lines";
		case GREW -> "grew from " + before + " to " + after + " lines";
		case NEW_OVER -> "new method of " + after + " lines, over the cap";
		case STILL_OVER -> "still over the cap at " + after + " lines" + (before != after ? " (was " + before + ")" : "");
		case SLIMMED -> "now under the cap: " + before + " -> " + after + " lines";
		};
	}

	/** Whether the method, over the cap, cannot be slimmed: the redesign notice. */
	public boolean redesign() {
		return method.status() == Status.NOT_SLIMMABLE;
	}
}
