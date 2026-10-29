package net.officefloor.sinewslim.cli;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import net.officefloor.sinewslim.cli.review.ReviewReport;
import net.officefloor.sinewslim.cli.review.ReviewWriter;
import net.officefloor.sinewslim.cli.review.Reviewer;
import net.officefloor.sinewslim.config.Config;
import net.officefloor.sinewslim.config.Config.Enforcement;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * {@code sinew-slim review}: the methods a change touches. Exit 0 for ok or warn, 2 when blocked,
 * 1 on an error, so a hook or a CI step gates on the code alone.
 */
@Command(name = "review", mixinStandardHelpOptions = true,
		description = "Review the methods a change touches: a proposal for each one over the cap, a redesign notice for each that cannot be slimmed.")
final class ReviewCommand implements Callable<Integer> {

	@Mixin
	ConfigOptions options;

	@Option(names = "--mode", description = "staged (the commit about to be made, the default), worktree (uncommitted edits) or range (a branch against --base, for CI).", defaultValue = "staged")
	String mode;

	@Option(names = "--base", description = "The base ref in range mode (default: origin/main).")
	String base;

	@Option(names = "--format", description = "console (default), markdown, json, sarif, github (Actions annotations) or codequality (GitLab).", defaultValue = "console")
	String format;

	@Option(names = "--out", description = "Write the report here instead of to standard output.")
	Path out;

	@Option(names = "--enforcement", description = "off, warn or block: overrides the configuration.")
	String enforcement;

	@Option(names = "--no-ratchet", description = "Gate on every touched method over the cap, not only on growth.")
	boolean noRatchet;

	@Override
	public Integer call() throws IOException {
		ReviewWriter w;
		try {
			w = ReviewWriter.of(format);
		} catch (IllegalArgumentException e) {
			System.err.println("sinew-slim: unknown format '" + format + "' (console, markdown, json, sarif, github, codequality)");
			return SinewSlim.ERROR;
		}
		if (enforcement != null) {
			options.set.put("enforcement", enforcement);
		}
		if (noRatchet) {
			options.set.put("ratchet", "false");
		}
		Config config = options.load();
		if (config.enforcement() == Enforcement.OFF) {
			System.err.println("sinew-slim: enforcement is off; nothing reviewed");
			return SinewSlim.OK;
		}
		ReviewReport r;
		try {
			r = Reviewer.review(options.root(), config, mode, base, SinewSlim.version());
		} catch (IllegalArgumentException e) {
			System.err.println("sinew-slim: unknown mode '" + mode + "' (staged, worktree, range)");
			return SinewSlim.ERROR;
		}
		try (Writer o = SinewSlim.output(out)) {
			w.write(r, o);
		}
		return r.blocked() ? SinewSlim.BLOCKED : SinewSlim.OK;
	}
}
