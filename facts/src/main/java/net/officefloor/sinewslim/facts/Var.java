package net.officefloor.sinewslim.facts;

import java.util.Arrays;

/**
 * A variable a method's own body reads or writes, with the lines it is touched on.
 *
 * @param name     its name
 * @param kind     parameter, local or field
 * @param first    the line of its first reference, a literal declaration included
 * @param literal  declared with no value or a literal one: the declaration could move down to
 *                 the first use without changing the method
 * @param refs     the distinct lines of its uses, in order, a literal declaration excluded
 * @param defs     the distinct lines of its definitions that have a line (an initializer, a
 *                 binding, an assignment, an increment)
 * @param muts     the distinct lines of the writes into its object ({@code a[i] = v},
 *                 {@code a.f = v})
 */
public record Var(String name, Kind kind, int first, boolean literal, int[] refs, int[] defs, int[] muts) {

	/** The schema's order: a kind's ordinal is its number in the study's facts. */
	public enum Kind {
		PARAM, LOCAL, FIELD
	}

	public Var {
		refs = sorted(refs);
		defs = sorted(defs);
		muts = sorted(muts);
	}

	private static int[] sorted(int[] lines) {
		int[] out = lines.clone();
		Arrays.sort(out);
		return out;
	}
}
