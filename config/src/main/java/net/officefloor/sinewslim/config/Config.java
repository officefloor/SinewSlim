package net.officefloor.sinewslim.config;

import java.util.List;

import net.officefloor.sinewslim.core.Assessor;
import net.officefloor.sinewslim.core.Params;

/**
 * The effective configuration: {@code .sinew-slim.yml} over the defaults, with any overrides from
 * the command line on top (DESIGN.md, Configuration). One configuration drives every surface, so
 * the IDE, the hook and CI never disagree about a method.
 *
 * @param profile                 the profile the values started from: {@code default} or {@code study}
 * @param params                  the limits and weights of the search
 * @param records                 when nothing is feasible with one return, try records
 * @param structural              also solve with whole if, loop, try and switch statements only
 * @param preferStructuralWithin  show the structural proposal when it costs at most this much more
 * @param parameteriseFieldReads  offer a piece that only reads fields as static, the fields passed in
 * @param frameworkAnnotations    the annotations (simple or qualified) that mark a class or method
 *                                as functions a framework calls: read under the framework reading
 * @param frameworkFreeParams     under the framework reading, a pass-through parameter is free
 * @param include                 the files scanned, as globs relative to the repository root
 * @param exclude                 the files left out, as globs
 * @param enforcement             what the review gate does with a finding
 * @param ratchet                 the review gates only on growth
 */
public record Config(String profile, Params params, boolean records, boolean structural, int preferStructuralWithin,
		boolean parameteriseFieldReads, List<String> frameworkAnnotations, boolean frameworkFreeParams,
		List<String> include, List<String> exclude, Enforcement enforcement, boolean ratchet) {

	public enum Enforcement {
		OFF, WARN, BLOCK
	}

	public Config {
		frameworkAnnotations = List.copyOf(frameworkAnnotations);
		include = List.copyOf(include);
		exclude = List.copyOf(exclude);
	}

	/** The assessor for a method, under the framework reading when its annotations mark it so. */
	public Assessor assessor(List<String> annotations) {
		Params p = framework(annotations) ? frameworkParams() : params;
		return new Assessor(p, records, structural, preferStructuralWithin);
	}

	/** Whether any of a method's annotations (its own, or its types') marks it as a framework's function. */
	public boolean framework(List<String> annotations) {
		for (String a : annotations) {
			for (String marker : frameworkAnnotations) {
				if (a.equals(marker) || marker.endsWith("." + a) || a.endsWith("." + marker)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The search under the framework reading ({@code SPLIT.md}, The framework reading). */
	public Params frameworkParams() {
		Params f = params.framework();
		return frameworkFreeParams ? f : f.toBuilder().freeParams(false).build();
	}
}
