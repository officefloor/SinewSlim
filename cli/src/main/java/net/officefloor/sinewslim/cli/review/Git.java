package net.officefloor.sinewslim.cli.review;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Git, run as a subprocess in the repository: the only runtime need besides the JRE. */
final class Git {

	/** Git's empty tree: the base of a repository's first commit. */
	static final String EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";

	/** A git command that failed: the message carries its standard error. */
	static final class GitException extends IOException {
		private static final long serialVersionUID = 1L;

		GitException(String message) {
			super(message);
		}
	}

	private final Path root;

	Git(Path root) {
		this.root = root;
	}

	/** The command's standard output; a non-zero exit is a {@link GitException}. */
	byte[] bytes(String... args) throws IOException {
		List<String> cmd = new ArrayList<>(List.of("git", "-C", root.toString(), "-c", "core.quotePath=false"));
		cmd.addAll(List.of(args));
		Process p = new ProcessBuilder(cmd).start();
		CompletableFuture<byte[]> err = CompletableFuture.supplyAsync(() -> drain(p.getErrorStream()));
		byte[] out = drain(p.getInputStream());
		try {
			int code = p.waitFor();
			if (code != 0) {
				throw new GitException("git " + String.join(" ", args) + " failed: "
						+ new String(err.join(), StandardCharsets.UTF_8).strip());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted waiting for git", e);
		}
		return out;
	}

	String text(String... args) throws IOException {
		return new String(bytes(args), StandardCharsets.UTF_8);
	}

	/** Whether the command succeeds, its output discarded. */
	boolean ok(String... args) {
		try {
			bytes(args);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/** The commit a revision names, or null when there is none (a repository with no commits has no HEAD). */
	String commit(String rev) {
		try {
			return text("rev-parse", "--verify", "--quiet", rev + "^{commit}").strip();
		} catch (IOException e) {
			return null;
		}
	}

	private static byte[] drain(InputStream in) {
		try (in) {
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			in.transferTo(b);
			return b.toByteArray();
		} catch (IOException e) {
			return new byte[0];
		}
	}
}
