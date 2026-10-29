package net.officefloor.sinewslim.cli;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A path glob, matched against a path relative to the repository root with {@code /} separators:
 * {@code **}{@code /} matches no directory or any number of them, {@code **} anything,
 * {@code *} anything within one name, {@code ?} one character.
 */
final class Glob {

	private final Pattern pattern;

	Glob(String glob) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
				if (i + 2 < glob.length() && glob.charAt(i + 2) == '/') {
					b.append("(?:.*/)?");
					i += 2;
				} else {
					b.append(".*");
					i++;
				}
			} else if (c == '*') {
				b.append("[^/]*");
			} else if (c == '?') {
				b.append("[^/]");
			} else {
				b.append(Pattern.quote(String.valueOf(c)));
			}
		}
		pattern = Pattern.compile(b.toString());
	}

	boolean matches(String path) {
		return pattern.matcher(path).matches();
	}

	/** Whether a path is included by any of {@code include} and excluded by none of {@code exclude}. */
	static boolean selected(String path, List<Glob> include, List<Glob> exclude) {
		boolean in = false;
		for (Glob g : include) {
			if (g.matches(path)) {
				in = true;
				break;
			}
		}
		if (!in) {
			return false;
		}
		for (Glob g : exclude) {
			if (g.matches(path)) {
				return false;
			}
		}
		return true;
	}
}
