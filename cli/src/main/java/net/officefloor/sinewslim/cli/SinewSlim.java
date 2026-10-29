package net.officefloor.sinewslim.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import net.officefloor.sinewslim.cli.report.MethodReport;
import net.officefloor.sinewslim.cli.report.ReportWriter;
import net.officefloor.sinewslim.cli.report.ScanReport;
import net.officefloor.sinewslim.config.Config;
import net.officefloor.sinewslim.config.ConfigLoader;
import net.officefloor.sinewslim.config.ConfigLoader.ConfigException;
import net.officefloor.sinewslim.core.Assessment.Status;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * The {@code sinew-slim} command. Exit codes, as ImpactGate's: 0 ok or warn, 2 blocked, 1 a usage
 * or environment error.
 */
@Command(name = "sinew-slim", mixinStandardHelpOptions = true, versionProvider = SinewSlim.Version.class,
		description = "Slims Java methods over the length cap at the seams where the least data crosses.",
		subcommands = { SinewSlim.Scan.class, ReviewCommand.class, CommentCommand.class, InstallHookCommand.class,
				SinewSlim.Baseline.class, SinewSlim.ShowConfig.class })
public final class SinewSlim implements Callable<Integer> {

	public static final int OK = 0, ERROR = 1, BLOCKED = 2;

	/** The parser and the search recurse with the code's nesting: the whole command runs on a deep stack. */
	static final long STACK_BYTES = 256L * 1024 * 1024;

	public static void main(String[] args) throws InterruptedException {
		int[] code = { ERROR };
		Thread t = new Thread(null, () -> code[0] = run(args), "sinew-slim", STACK_BYTES);
		t.start();
		t.join();
		System.exit(code[0]);
	}

	static int run(String... args) {
		CommandLine cl = new CommandLine(new SinewSlim());
		cl.setExecutionExceptionHandler((e, cmd, parsed) -> {
			if (e instanceof ConfigException || e instanceof IOException) {
				cmd.getErr().println("sinew-slim: " + e.getMessage());
				return ERROR;
			}
			throw e;
		});
		cl.setExitCodeExceptionMapper(e -> ERROR);
		return cl.execute(args);
	}

	@Override
	public Integer call() {
		new CommandLine(this).usage(System.out);
		return OK;
	}

	/** The jar's Implementation-Version, or {@code dev} when run from classes. */
	static String version() {
		String v = SinewSlim.class.getPackage().getImplementationVersion();
		return v == null ? "dev" : v;
	}

	static final class Version implements IVersionProvider {
		@Override
		public String[] getVersion() {
			return new String[] { "sinew-slim " + version() };
		}
	}

	static Writer output(Path out) throws IOException {
		if (out == null) {
			// standard output is flushed, never closed: the command may write again, and so may a test
			return new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)) {
				@Override
				public void close() {
					flush();
				}
			};
		}
		if (out.toAbsolutePath().getParent() != null) {
			Files.createDirectories(out.toAbsolutePath().getParent());
		}
		return Files.newBufferedWriter(out, StandardCharsets.UTF_8);
	}

	@Command(name = "scan", mixinStandardHelpOptions = true,
			description = "Report every method of the codebase: under the cap, slimmable with its proposal, or not slimmable.")
	static final class Scan implements Callable<Integer> {

		@Mixin
		ConfigOptions options;

		@Option(names = "--format", description = "text, markdown, html, json, csv or sarif (default: text).", defaultValue = "text")
		String format;

		@Option(names = "--out", description = "Write the report here instead of to standard output.")
		Path out;

		@Option(names = "--near", description = "List the methods under the cap within this many lines of it (default: 3).", defaultValue = "3")
		int near;

		@Option(names = "--threads", description = "Files parsed at once (default: the processors available).")
		Integer threads;

		@Override
		public Integer call() throws IOException {
			ReportWriter.Format f;
			try {
				f = ReportWriter.Format.of(format);
			} catch (IllegalArgumentException e) {
				System.err.println("sinew-slim: unknown format '" + format + "' (text, markdown, html, json, csv, sarif)");
				return ERROR;
			}
			Config config = options.load();
			int n = threads != null ? threads : Runtime.getRuntime().availableProcessors();
			ScanReport report = Scanner.scan(options.root(), config, n, version());
			try (Writer w = output(out)) {
				f.writer().write(report, near, w);
			}
			if (out != null) {
				System.err.printf("sinew-slim: %d methods, %d over the cap, %d not slimmable; report in %s%n",
						report.methods().size(), report.overCap(), report.count(Status.NOT_SLIMMABLE), out);
			}
			return OK;
		}
	}

	@Command(name = "baseline", mixinStandardHelpOptions = true,
			description = "Record every method's length and status, for the review's ratchet where there is no base commit.")
	static final class Baseline implements Callable<Integer> {

		/** Where the baseline goes by default, at the repository root. */
		static final String FILE = ".sinew-slim-baseline.json";

		@Mixin
		ConfigOptions options;

		@Option(names = "--out", description = "Where to write it (default: .sinew-slim-baseline.json at the root).")
		Path out;

		@Override
		public Integer call() throws IOException {
			Config config = options.load();
			ScanReport report = Scanner.scan(options.root(), config, Runtime.getRuntime().availableProcessors(), version());
			Map<String, Object> doc = new LinkedHashMap<>();
			doc.put("tool", "sinew-slim");
			doc.put("version", report.version());
			doc.put("schema", ScanReport.SCHEMA);
			doc.put("trigger", config.params().trigger());
			doc.put("target", config.params().target());
			List<Object> methods = new ArrayList<>();
			for (MethodReport m : report.methods()) {
				Map<String, Object> o = new LinkedHashMap<>();
				o.put("path", m.path());
				o.put("type", m.type());
				o.put("signature", m.signature());
				o.put("length", m.length());
				o.put("status", m.status().name().toLowerCase(Locale.ROOT));
				methods.add(o);
			}
			doc.put("methods", methods);
			Path file = out != null ? out : options.root().resolve(FILE);
			try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				w.write(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(doc));
				w.newLine();
			}
			System.err.printf("sinew-slim: baseline of %d methods written to %s%n", methods.size(), file);
			return OK;
		}
	}

	@Command(name = "config", mixinStandardHelpOptions = true,
			description = "Print the effective configuration: the file over the defaults, with the flags on top.")
	static final class ShowConfig implements Callable<Integer> {

		@Mixin
		ConfigOptions options;

		@Override
		public Integer call() {
			System.out.print(ConfigLoader.dump(options.load()));
			return OK;
		}
	}
}
