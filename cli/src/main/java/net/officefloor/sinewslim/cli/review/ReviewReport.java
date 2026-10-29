package net.officefloor.sinewslim.cli.review;

import java.util.List;

import net.officefloor.sinewslim.cli.report.ScanReport;
import net.officefloor.sinewslim.config.Config;
import net.officefloor.sinewslim.config.Config.Enforcement;

/**
 * Everything a review found.
 *
 * @param version  the tool's version
 * @param mode     staged, worktree or range
 * @param base     what the change was compared with
 * @param config   what it was read under
 * @param files    the changed source files reviewed
 * @param touched  the methods the change touched
 * @param findings what is said about the touched methods over the cap, or brought under it
 * @param unparsed changed files that could not be read
 */
public record ReviewReport(String version, String mode, String base, Config config, int files, int touched,
		List<Finding> findings, List<ScanReport.Unparsed> unparsed) {

	public ReviewReport {
		findings = List.copyOf(findings);
		unparsed = List.copyOf(unparsed);
	}

	public List<Finding> gating() {
		return findings.stream().filter(Finding::gates).toList();
	}

	public List<Finding> information() {
		return findings.stream().filter(f -> !f.gates()).toList();
	}

	/** Whether the change is blocked: findings that gate, under block enforcement. */
	public boolean blocked() {
		return config.enforcement() == Enforcement.BLOCK && !gating().isEmpty();
	}

	/** The one-word verdict: ok, warn or block. */
	public String verdict() {
		if (gating().isEmpty()) {
			return "ok";
		}
		return blocked() ? "block" : "warn";
	}
}
