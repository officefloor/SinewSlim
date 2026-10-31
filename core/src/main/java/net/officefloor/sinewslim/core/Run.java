package net.officefloor.sinewslim.core;

import net.officefloor.sinewslim.facts.Seam;

/**
 * A run of statements by its first and last line: how a piece the developer skipped is left out
 * of the next search.
 */
public record Run(int lo, int hi) {

	public static Run of(Seam s) {
		return new Run(s.lo(), s.hi());
	}
}
