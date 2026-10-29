package net.officefloor.sinewslim.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import net.officefloor.sinewslim.cli.report.ClassReport;
import net.officefloor.sinewslim.cli.report.MethodReport;
import net.officefloor.sinewslim.cli.report.ScanReport;
import net.officefloor.sinewslim.config.Config;
import net.officefloor.sinewslim.core.Assessment;
import net.officefloor.sinewslim.core.Assessment.Status;
import net.officefloor.sinewslim.core.Binding;
import net.officefloor.sinewslim.core.SlimResult;
import net.officefloor.sinewslim.javaparser.JavaFacts;
import net.officefloor.sinewslim.javaparser.MethodUnit;
import net.officefloor.sinewslim.javaparser.ParsedFile;

/** Reads every selected file of a repository, in parallel, and assesses every method in it. */
public final class Scanner {

	/** A file larger than this is almost always generated or vendored: listed as skipped, not read. */
	static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

	/** The search and the parser recurse with the code's nesting: a long else-if chain needs a deep stack. */
	static final long STACK_BYTES = 256L * 1024 * 1024;

	private Scanner() {
	}

	/** One file's methods, or why it was not read. */
	record FileResult(String path, String problem, List<MethodReport> methods) {
	}

	public static ScanReport scan(Path root, Config config, int threads, String version) throws IOException {
		long started = System.nanoTime();
		List<Glob> include = config.include().stream().map(Glob::new).toList();
		List<Glob> exclude = config.exclude().stream().map(Glob::new).toList();
		List<String> files = RepoFiles.list(root).stream().filter(f -> Glob.selected(f, include, exclude)).toList();

		List<FileResult> results = new ArrayList<>(files.size());
		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads), deepStacks());
		try {
			List<Future<FileResult>> futures = new ArrayList<>(files.size());
			for (String f : files) {
				futures.add(pool.submit((Callable<FileResult>) () -> file(root, f, config)));
			}
			for (int i = 0; i < futures.size(); i++) {
				try {
					results.add(futures.get(i).get());
				} catch (ExecutionException e) {
					Throwable c = e.getCause();
					results.add(new FileResult(files.get(i), c.getClass().getSimpleName() + ": " + c.getMessage(), List.of()));
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("scan interrupted", e);
		} finally {
			pool.shutdownNow();
		}

		List<ScanReport.Unparsed> unparsed = new ArrayList<>();
		List<MethodReport> methods = new ArrayList<>();
		for (FileResult r : results) {
			if (r.problem() != null) {
				unparsed.add(new ScanReport.Unparsed(r.path(), r.problem()));
			}
			methods.addAll(r.methods());
		}
		double seconds = (System.nanoTime() - started) / 1e9;
		return new ScanReport(version, root.toString(), config, seconds, files.size(), unparsed, methods, classes(methods));
	}

	/** Parse one file and assess its methods: the unit of parallel work. */
	static FileResult file(Path root, String path, Config config) {
		Path p = root.resolve(path);
		String source;
		try {
			if (Files.size(p) > MAX_FILE_BYTES) {
				return new FileResult(path, "skipped: larger than " + MAX_FILE_BYTES / (1024 * 1024) + " MB, likely generated",
						List.of());
			}
			source = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return new FileResult(path, "cannot read: " + e.getMessage(), List.of());
		}
		ParsedFile parsed;
		try {
			parsed = JavaFacts.parse(path, source);
		} catch (RuntimeException | StackOverflowError e) {
			return new FileResult(path, e.getClass().getSimpleName() + ": " + e.getMessage(), List.of());
		}
		if (!parsed.parsed()) {
			return new FileResult(path, parsed.problem(), List.of());
		}
		List<MethodReport> out = new ArrayList<>();
		for (MethodUnit m : parsed.methods()) {
			boolean framework = config.framework(m.annotations());
			Assessment a = config.assessor(m.annotations()).assess(m.facts());
			out.add(new MethodReport(path, m.type(), m.signature(), m.nameLine(), m.endLine(), m.facts().length(), framework, a));
		}
		return new FileResult(path, null, out);
	}

	/** Per class with a method over the cap: its pieces, how many are locked, its methods that cannot be slimmed. */
	static List<ClassReport> classes(List<MethodReport> methods) {
		Map<String, int[]> by = new LinkedHashMap<>();
		Map<String, String[]> names = new LinkedHashMap<>();
		for (MethodReport m : methods) {
			if (m.status() == Status.UNDER_CAP) {
				continue;
			}
			String key = m.path() + "\0" + m.type();
			names.putIfAbsent(key, new String[] { m.path(), m.type() });
			int[] c = by.computeIfAbsent(key, k -> new int[6]);
			c[0]++;
			SlimResult p = m.proposal();
			if (p.feasible()) {
				c[1] += p.pieces();
				c[2] += p.count(Binding.LOCKED);
				c[3]++;
				c[4] += p.remainderLocked() ? 1 : 0;
			}
			if (m.status() == Status.NOT_SLIMMABLE) {
				c[5]++;
			}
		}
		List<ClassReport> out = new ArrayList<>();
		by.forEach((k, c) -> out.add(new ClassReport(names.get(k)[0], names.get(k)[1], c[0], c[1], c[2], c[3], c[4], c[5])));
		out.sort(Comparator.comparingDouble(ClassReport::lockedShare).reversed()
				.thenComparing(Comparator.comparingInt(ClassReport::overCap).reversed()).thenComparing(ClassReport::path)
				.thenComparing(ClassReport::type));
		return out;
	}

	static ThreadFactory deepStacks() {
		AtomicInteger n = new AtomicInteger();
		return r -> {
			Thread t = new Thread(null, r, "sinew-slim-" + n.incrementAndGet(), STACK_BYTES);
			t.setDaemon(true);
			return t;
		};
	}
}
