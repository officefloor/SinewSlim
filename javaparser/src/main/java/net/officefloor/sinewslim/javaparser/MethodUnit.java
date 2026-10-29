package net.officefloor.sinewslim.javaparser;

import net.officefloor.sinewslim.core.MethodFacts;

/**
 * One callable with a body, where it is, and its facts.
 *
 * @param type      the declaring type, outer types first ({@code Outer.Inner}; an anonymous
 *                  class is {@code Outer.new Base}), empty for none
 * @param name      the method's name ({@code <init>} is not used: a constructor has its type's name)
 * @param signature the name and the parameter types as written, {@code name(int, List<String>)}
 * @param declLine  the declaration's first line
 * @param nameLine  the line of its name
 * @param endLine   its last line
 * @param facts     what the search reads
 */
public record MethodUnit(String type, String name, String signature, int declLine, int nameLine, int endLine,
		MethodFacts facts) {
}
