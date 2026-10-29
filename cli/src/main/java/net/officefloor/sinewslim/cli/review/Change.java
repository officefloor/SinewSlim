package net.officefloor.sinewslim.cli.review;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * What a review compares, by mode, as ImpactGate does: the commit about to be made, the uncommitted
 * edits, or a branch against its base.
 */
final class Change {

	enum Mode {
		/** The index against {@code HEAD}: the commit about to be made, for the pre-commit hook. */
		STAGED,
		/** The working tree against {@code HEAD}: uncommitted edits, tracked files only. */
		WORKTREE,
		/** {@code HEAD} against its merge base with the base ref: a branch, for CI. */
		RANGE;

		static Mode of(String name) {
			return valueOf(name.toUpperCase(Locale.ROOT));
		}
	}

	final Mode mode;
	private final Git git;
	private final Path root;
	/** The base commit, or null when there is none (a repository's first commit). */
	final String base;
	/** What the base is, for the report: {@code HEAD}, or {@code origin/main (merge base 1a2b3c4)}. */
	final String description;
	final List<Diff.FileChange> files;

	private Change(Mode mode, Git git, Path root, String base, String description, List<Diff.FileChange> files) {
		this.mode = mode;
		this.git = git;
		this.root = root;
		this.base = base;
		this.description = description;
		this.files = files;
	}

	static Change of(Path root, Mode mode, String baseRef) throws IOException {
		Git git = new Git(root);
		String head = git.commit("HEAD");
		String[] diffArgs = { "diff", "-U0", "--no-color", "--no-ext-diff", "-M" };
		switch (mode) {
		case STAGED -> {
			String against = head != null ? head : Git.EMPTY_TREE;
			String diff = git.text(concat(diffArgs, "--cached", against));
			return new Change(mode, git, root, head, head != null ? "HEAD" : "no commits yet", Diff.parse(diff));
		}
		case WORKTREE -> {
			if (head == null) {
				throw new IOException("worktree mode needs a commit to compare with; use --mode staged");
			}
			return new Change(mode, git, root, head, "HEAD", Diff.parse(git.text(concat(diffArgs, head))));
		}
		default -> {
			if (head == null) {
				throw new IOException("range mode needs a commit at HEAD");
			}
			String ref = baseRef != null ? baseRef : "origin/main";
			String target = git.commit(ref);
			if (target == null) {
				throw new IOException("base '" + ref + "' is not a commit here; fetch it (actions/checkout with "
						+ "fetch-depth: 0, GIT_DEPTH: 0 on GitLab) or pass --base");
			}
			String mergeBase = git.text("merge-base", target, head).strip();
			String diff = git.text(concat(diffArgs, mergeBase, head));
			return new Change(mode, git, root, mergeBase,
					ref + " (merge base " + mergeBase.substring(0, Math.min(7, mergeBase.length())) + ")", Diff.parse(diff));
		}
		}
	}

	/** A file's source on the base side, or null when it has none there. */
	String base(String path) throws IOException {
		if (base == null || path == null) {
			return null;
		}
		return new String(git.bytes("show", base + ":" + path), StandardCharsets.UTF_8);
	}

	/** A file's source on the head side: the index, the working tree, or {@code HEAD}. */
	String head(String path) throws IOException {
		return switch (mode) {
		case STAGED -> new String(git.bytes("show", ":" + path), StandardCharsets.UTF_8);
		case WORKTREE -> Files.readString(root.resolve(path), StandardCharsets.UTF_8);
		case RANGE -> new String(git.bytes("show", "HEAD:" + path), StandardCharsets.UTF_8);
		};
	}

	private static String[] concat(String[] a, String... b) {
		String[] out = new String[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}
}
