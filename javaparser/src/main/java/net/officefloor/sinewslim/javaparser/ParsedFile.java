package net.officefloor.sinewslim.javaparser;

import java.util.List;

/**
 * The methods of one source file, or why it could not be read.
 *
 * @param path    the file, as given
 * @param problem null when parsed; otherwise the parser's first problem
 * @param methods every callable with a body, in source order
 */
public record ParsedFile(String path, String problem, List<MethodUnit> methods) {

	public ParsedFile {
		methods = List.copyOf(methods);
	}

	public boolean parsed() {
		return problem == null;
	}
}
