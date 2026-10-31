package net.officefloor.sinewslim.scan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import net.officefloor.sinewslim.javaparser.JavaFacts;
import net.officefloor.sinewslim.javaparser.JavaFacts.Extractor;
import net.officefloor.sinewslim.javaparser.MethodUnit;
import net.officefloor.sinewslim.javaparser.ParsedFile;

/**
 * Reads every selected file of a repository, in parallel. What is read from each file is the
 * caller's: {@link #javaMethods} reads a Java file's methods, and a consumer that reads other
 * files too (configuration, wiring) selects them with its globs and reads them its own way.
 */
public final class Scanner {

	/** A file larger than this is almost always generated or vendored: listed as skipped, not read. */
	public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

	/** The search and the parser recurse with the code's nesting: a long else-if chain needs a deep stack. */
	public static final long STACK_BYTES = 256L * 1024 * 1024;

	private Scanner() {
	}

	/** What a scan reads from one file. It runs on many threads at once. */
	@FunctionalInterface
	public interface FileReader<R> {

		/**
		 * @param path   the file, relative to the root with {@code /} separators
		 * @param source its text, as UTF-8
		 * @throws Unreadable when the file cannot be read; its message is the problem as reported
		 */
		R read(String path, String source) throws Unreadable;
	}

	/** A file that could not be read: its message is reported as the problem, as it is. */
	public static final class Unreadable extends Exception {

		private static final long serialVersionUID = 1L;

		public Unreadable(String problem) {
			super(problem, null, false, false);
		}
	}

	/**
	 * One file's result, or why it was not read.
	 *
	 * @param path    the file, relative to the root
	 * @param problem null when read; otherwise why not
	 * @param result  what the reader returned; null when there is a problem
	 */
	public record FileResult<R>(String path, String problem, R result) {

		public boolean read() {
			return problem == null;
		}
	}

	/**
	 * Read every file of the repository the globs select, on a fixed pool of threads.
	 *
	 * @return one result per selected file, in the order {@link RepoFiles#list} gives them
	 */
	public static <R> List<FileResult<R>> scan(Path root, List<Glob> include, List<Glob> exclude, int threads,
			FileReader<R> reader) throws IOException {
		List<String> files = RepoFiles.list(root).stream().filter(f -> Glob.selected(f, include, exclude)).toList();
		List<FileResult<R>> results = new ArrayList<>(files.size());
		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads), deepStacks());
		try {
			List<Future<FileResult<R>>> futures = new ArrayList<>(files.size());
			for (String f : files) {
				futures.add(pool.submit((Callable<FileResult<R>>) () -> file(root, f, reader)));
			}
			for (int i = 0; i < futures.size(); i++) {
				try {
					results.add(futures.get(i).get());
				} catch (ExecutionException e) {
					results.add(new FileResult<>(files.get(i), problem(e.getCause()), null));
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("scan interrupted", e);
		} finally {
			pool.shutdownNow();
		}
		return results;
	}

	/** Read one file: the unit of parallel work. */
	public static <R> FileResult<R> file(Path root, String path, FileReader<R> reader) {
		Path p = root.resolve(path);
		String source;
		try {
			if (Files.size(p) > MAX_FILE_BYTES) {
				return new FileResult<>(path,
						"skipped: larger than " + MAX_FILE_BYTES / (1024 * 1024) + " MB, likely generated", null);
			}
			source = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return new FileResult<>(path, "cannot read: " + e.getMessage(), null);
		}
		try {
			return new FileResult<>(path, null, reader.read(path, source));
		} catch (Unreadable e) {
			return new FileResult<>(path, e.getMessage(), null);
		} catch (RuntimeException | StackOverflowError e) {
			return new FileResult<>(path, problem(e), null);
		}
	}

	/** A Java file's methods, each turned into what the caller keeps of it. */
	@FunctionalInterface
	public interface MethodReader<T> {

		T read(String path, MethodUnit method);
	}

	/**
	 * A reader of Java source: parses the file, with the extractors given, and reads each method.
	 * A file that does not parse is unreadable, with the parser's first problem.
	 */
	public static <T> FileReader<List<T>> javaMethods(List<Extractor<?>> extractors, MethodReader<T> each) {
		return (path, source) -> {
			ParsedFile parsed = JavaFacts.parse(path, source, extractors);
			if (!parsed.parsed()) {
				throw new Unreadable(parsed.problem());
			}
			List<T> out = new ArrayList<>(parsed.methods().size());
			for (MethodUnit m : parsed.methods()) {
				out.add(each.read(path, m));
			}
			return out;
		};
	}

	/** Threads with stacks deep enough for the parser and the search. */
	public static ThreadFactory deepStacks() {
		AtomicInteger n = new AtomicInteger();
		return r -> {
			Thread t = new Thread(null, r, "sinew-slim-" + n.incrementAndGet(), STACK_BYTES);
			t.setDaemon(true);
			return t;
		};
	}

	private static String problem(Throwable e) {
		return e.getClass().getSimpleName() + ": " + e.getMessage();
	}
}
