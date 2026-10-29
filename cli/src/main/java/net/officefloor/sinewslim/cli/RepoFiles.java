package net.officefloor.sinewslim.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The files of a repository, as paths relative to its root with {@code /} separators. In a git
 * work tree, the files git knows or would add (tracked, and untracked but not ignored), so
 * {@code .gitignore} is honoured; anywhere else, every file under the root, hidden directories
 * left out.
 */
final class RepoFiles {

	private RepoFiles() {
	}

	static List<String> list(Path root) throws IOException {
		List<String> git = gitFiles(root);
		if (git != null) {
			return git;
		}
		TreeSet<String> out = new TreeSet<>();
		try (Stream<Path> s = Files.walk(root)) {
			s.filter(Files::isRegularFile).forEach(p -> {
				Path rel = root.relativize(p);
				for (Path part : rel) {
					if (part.toString().startsWith(".") && !part.equals(rel.getFileName())) {
						return;
					}
				}
				out.add(rel.toString().replace('\\', '/'));
			});
		}
		return new ArrayList<>(out);
	}

	/** The files git lists, or null when the root is not in a git work tree or git is not there. */
	static List<String> gitFiles(Path root) {
		try {
			Process p = new ProcessBuilder("git", "-C", root.toString(), "ls-files", "-z", "--cached", "--others",
					"--exclude-standard").redirectError(ProcessBuilder.Redirect.DISCARD).start();
			byte[] out;
			try (InputStream in = p.getInputStream()) {
				ByteArrayOutputStream b = new ByteArrayOutputStream();
				in.transferTo(b);
				out = b.toByteArray();
			}
			if (p.waitFor() != 0) {
				return null;
			}
			TreeSet<String> files = new TreeSet<>();
			for (String f : new String(out, StandardCharsets.UTF_8).split("\0")) {
				if (!f.isEmpty() && Files.isRegularFile(root.resolve(f))) {
					files.add(f);
				}
			}
			return new ArrayList<>(files);
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}
}
