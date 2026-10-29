package net.officefloor.sinewslim.core;

import java.util.Arrays;
import java.util.List;

/**
 * Everything the search reads about one method. A fact producer (JavaParser outside the IDE, the
 * IDE's own tree inside it) fills it; the core never sees source.
 *
 * @param declLine      the declaration's first line (its annotations and modifiers included)
 * @param body          the statement tree of the body: a sequence
 * @param vars          the variables the body reads or writes
 * @param loops         the line ranges of the body's loops, as {start, end}
 * @param ownCallLines  the lines of the calls to the class's own instance methods, one entry per
 *                      call, in order
 * @param length        the method's length in code lines, the trigger and the target are read
 *                      on it; 0 to take the lines the statement tree covers instead
 */
public record MethodFacts(int declLine, Stmt body, List<Var> vars, List<int[]> loops, int[] ownCallLines, int length) {

	public MethodFacts {
		vars = List.copyOf(vars);
		loops = List.copyOf(loops);
		ownCallLines = ownCallLines.clone();
		Arrays.sort(ownCallLines);
	}
}
