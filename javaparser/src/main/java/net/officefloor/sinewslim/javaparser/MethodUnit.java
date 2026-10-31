package net.officefloor.sinewslim.javaparser;

import java.util.List;

import net.officefloor.sinewslim.facts.ExtraFacts;
import net.officefloor.sinewslim.facts.MethodFacts;

/**
 * One callable with a body, where it is, and its facts.
 *
 * @param type        the declaring type, outer types first ({@code Outer.Inner}; an anonymous
 *                    class is {@code Outer.new Base}), empty for none
 * @param name        the method's name (a constructor has its type's name)
 * @param signature   the name and the parameter types as written, {@code name(int, List<String>)}
 * @param declLine    the declaration's first line
 * @param nameLine    the line of its name
 * @param endLine     its last line
 * @param annotations the annotations on it and on each type around it, as written, innermost
 *                    first: how a framework marks its functions
 * @param facts       what the search reads
 * @param extras      what the extractors the parse was given read, beside the facts
 */
public record MethodUnit(String type, String name, String signature, int declLine, int nameLine, int endLine,
		List<String> annotations, MethodFacts facts, ExtraFacts extras) {

	public MethodUnit {
		annotations = List.copyOf(annotations);
	}
}
