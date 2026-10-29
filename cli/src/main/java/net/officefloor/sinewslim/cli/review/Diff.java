package net.officefloor.sinewslim.cli.review;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The files a unified diff ({@code git diff -U0 -M}) changes and, per file, the lines it touches
 * on the new side: the added and changed lines, and the points where lines were removed.
 */
final class Diff {

	/**
	 * One changed file.
	 *
	 * @param oldPath   its path on the base side; null when added
	 * @param newPath   its path on the head side; null when deleted
	 * @param ranges    the head-side line ranges added or changed, as {first, last}
	 * @param deletions the head-side lines after which lines were removed (0: before the first line)
	 */
	record FileChange(String oldPath, String newPath, List<int[]> ranges, List<Integer> deletions) {

		/** Whether the change touches a method spanning {@code start} to {@code end}. */
		boolean touches(int start, int end) {
			for (int[] r : ranges) {
				if (r[0] <= end && start <= r[1]) {
					return true;
				}
			}
			for (int d : deletions) {
				if (start <= d && d < end) {
					return true; // lines removed from inside the method
				}
			}
			return false;
		}
	}

	static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

	private Diff() {
	}

	static List<FileChange> parse(String diff) {
		List<FileChange> out = new ArrayList<>();
		String oldPath = null, newPath = null;
		List<int[]> ranges = null;
		List<Integer> deletions = null;
		boolean inFile = false;
		for (String line : diff.split("\n", -1)) {
			if (line.startsWith("diff --git ")) {
				if (inFile) {
					out.add(new FileChange(oldPath, newPath, ranges, deletions));
				}
				inFile = true;
				oldPath = null;
				newPath = null;
				ranges = new ArrayList<>();
				deletions = new ArrayList<>();
				String[] ab = splitHeader(line.substring("diff --git ".length()));
				if (ab != null) {
					oldPath = ab[0];
					newPath = ab[1];
				}
			} else if (!inFile) {
				continue;
			} else if (line.startsWith("--- ")) {
				oldPath = side(line.substring(4), "a/");
			} else if (line.startsWith("+++ ")) {
				newPath = side(line.substring(4), "b/");
			} else if (line.startsWith("rename from ")) {
				oldPath = line.substring("rename from ".length());
			} else if (line.startsWith("rename to ")) {
				newPath = line.substring("rename to ".length());
			} else if (line.startsWith("new file mode")) {
				oldPath = null;
			} else if (line.startsWith("deleted file mode")) {
				newPath = null;
			} else if (line.startsWith("@@")) {
				Matcher m = HUNK.matcher(line);
				if (m.find()) {
					int c = Integer.parseInt(m.group(3));
					int d = m.group(4) == null ? 1 : Integer.parseInt(m.group(4));
					if (d > 0) {
						ranges.add(new int[] { c, c + d - 1 });
					} else {
						deletions.add(c);
					}
				}
			}
		}
		if (inFile) {
			out.add(new FileChange(oldPath, newPath, ranges, deletions));
		}
		return out;
	}

	/** {@code a/x b/x} into its two paths, when they are the same path (the common case); null otherwise. */
	private static String[] splitHeader(String rest) {
		if (!rest.startsWith("a/")) {
			return null;
		}
		int mid = rest.length() / 2;
		String a = rest.substring(0, mid), b = rest.substring(mid + 1);
		if (rest.length() % 2 == 1 && b.startsWith("b/") && a.substring(2).equals(b.substring(2))) {
			return new String[] { a.substring(2), b.substring(2) };
		}
		return null;
	}

	private static String side(String path, String prefix) {
		path = path.endsWith("\t") ? path.substring(0, path.length() - 1) : path; // git ends a path with a space in a tab
		if (path.equals("/dev/null")) {
			return null;
		}
		return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
	}
}
