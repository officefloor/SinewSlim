package net.officefloor.sinewslim.cli.review;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.officefloor.sinewslim.core.Pieces;
import net.officefloor.sinewslim.cli.report.Describe;
import net.officefloor.sinewslim.cli.report.JsonReport;
import net.officefloor.sinewslim.cli.report.MethodReport;
import net.officefloor.sinewslim.cli.report.SarifReport;
import net.officefloor.sinewslim.cli.report.ScanReport;
import net.officefloor.sinewslim.config.ConfigLoader;

/** Writes a review in one format: the console for a hook, markdown for a PR comment, JSON, SARIF, GitLab code quality. */
public enum ReviewWriter {

	/** Plain text whose findings start {@code path:line:}, which IDE terminals turn into links. */
	CONSOLE {
		@Override
		public void write(ReviewReport r, Appendable out) throws IOException {
			String nl = System.lineSeparator();
			boolean pfr = r.config().parameteriseFieldReads();
			for (Finding f : r.findings()) {
				MethodReport m = f.method();
				out.append(m.location()).append(": ").append(m.qualified()).append(" ").append(f.movement()).append("; ")
						.append(Describe.assessment(m)).append(f.gates() ? "" : " (information)").append(nl);
				if (f.kind() != Finding.Kind.SLIMMED && m.proposal().feasible()) {
					for (String line : Describe.proposalLines(m, pfr)) {
						out.append("  ").append(line).append(nl);
					}
				}
			}
			for (ScanReport.Unparsed u : r.unparsed()) {
				out.append(u.path()).append(": could not be read: ").append(u.problem()).append(nl);
			}
			out.append(summary(r)).append(nl);
		}
	},

	/** For a PR comment or a job summary: what gates first, the rest folded. */
	MARKDOWN {
		@Override
		public void write(ReviewReport r, Appendable out) throws IOException {
			boolean pfr = r.config().parameteriseFieldReads();
			out.append("## sinew-slim\n\n");
			List<Finding> gating = r.gating();
			if (gating.isEmpty()) {
				out.append("**ok**: no method this change touches is newly over the cap of ")
						.append(String.valueOf(r.config().params().trigger())).append(" code lines, or longer than it was.\n");
			} else {
				out.append("**").append(r.verdict()).append("**: ").append(String.valueOf(gating.size()))
						.append(gating.size() == 1 ? " method this change touches is" : " methods this change touches are")
						.append(" newly over the cap of ").append(String.valueOf(r.config().params().trigger()))
						.append(" code lines, or longer than it was.");
				long redesign = gating.stream().filter(Finding::redesign).count();
				if (redesign > 0) {
					out.append(" ").append(String.valueOf(redesign)).append(" cannot be slimmed and should be investigated for redesign.");
				}
				out.append("\n\n");
				for (Finding f : gating) {
					finding(out, f, pfr, true);
				}
			}
			List<Finding> info = r.information();
			if (!info.isEmpty()) {
				out.append("\n<details><summary>").append(String.valueOf(info.size()))
						.append(" more touched method").append(info.size() == 1 ? "" : "s")
						.append(" over the cap or brought under it (not gating)</summary>\n\n");
				for (Finding f : info) {
					finding(out, f, pfr, false);
				}
				out.append("</details>\n");
			}
			if (!r.unparsed().isEmpty()) {
				out.append("\nCould not be read:\n");
				for (ScanReport.Unparsed u : r.unparsed()) {
					out.append("- `").append(u.path()).append("`: ").append(u.problem()).append('\n');
				}
			}
			out.append("\n<sub>").append(summary(r)).append("</sub>\n");
		}

		void finding(Appendable out, Finding f, boolean pfr, boolean open) throws IOException {
			MethodReport m = f.method();
			out.append("- **`").append(m.qualified()).append("`** `").append(m.location()).append("`: ").append(f.movement())
					.append("; ").append(Describe.assessment(m)).append('\n');
			if (f.kind() == Finding.Kind.SLIMMED || !m.proposal().feasible()) {
				return;
			}
			out.append("  <details").append(open ? " open" : "").append("><summary>proposal</summary>\n\n");
			out.append("  | Lines | Length | In | Out | Declares | Where | Saves |\n  |---|---:|---|---|---|---|---:|\n");
			for (Pieces.Piece p : Describe.pieces(m.proposal(), pfr)) {
				String in = Describe.list(p.inputs()) + (p.passed().isEmpty() ? "" : " (passed: " + Describe.list(p.passed()) + ")");
				out.append(String.format("  | %d-%d | %d | %s | %s | %s | %s | %d |%n", p.lo(), p.hi(), p.lines(), cell(in),
						cell(Describe.outputs(p.outputs())), cell(Describe.list(p.moved())), cell(p.binding()), p.saves()));
			}
			out.append("\n  Remainder: ").append(Describe.remainder(m)).append("\n  </details>\n\n");
		}
	},

	/** Every finding with its method, as the scan's JSON writes a method. */
	JSON {
		@Override
		public void write(ReviewReport r, Appendable out) throws IOException {
			Map<String, Object> doc = new LinkedHashMap<>();
			doc.put("tool", "sinew-slim");
			doc.put("version", r.version());
			doc.put("schema", ScanReport.SCHEMA);
			doc.put("mode", r.mode());
			doc.put("base", r.base());
			doc.put("verdict", r.verdict());
			doc.put("files", r.files());
			doc.put("touched", r.touched());
			doc.put("config", ConfigLoader.values(r.config()));
			List<Object> findings = new ArrayList<>();
			for (Finding f : r.findings()) {
				Map<String, Object> o = new LinkedHashMap<>();
				o.put("kind", f.kind().name().toLowerCase(Locale.ROOT));
				o.put("gates", f.gates());
				o.put("before", f.before() < 0 ? null : f.before());
				o.put("method", JsonReport.method(f.method(), r.config().parameteriseFieldReads()));
				findings.add(o);
			}
			doc.put("findings", findings);
			List<Object> unparsed = new ArrayList<>();
			for (ScanReport.Unparsed u : r.unparsed()) {
				unparsed.add(Map.of("path", u.path(), "problem", u.problem()));
			}
			doc.put("unparsed", unparsed);
			out.append(JsonReport.JSON.writeValueAsString(doc)).append(System.lineSeparator());
		}
	},

	/** SARIF 2.1.0 of the touched methods over the cap, for code scanning annotations on the PR's lines. */
	SARIF {
		@Override
		public void write(ReviewReport r, Appendable out) throws IOException {
			Map<MethodReport, Finding> by = new LinkedHashMap<>();
			for (Finding f : r.findings()) {
				if (f.kind() != Finding.Kind.SLIMMED) {
					by.put(f.method(), f);
				}
			}
			SarifReport.write(new ArrayList<>(by.keySet()), r.version(), r.config().parameteriseFieldReads(),
					m -> by.get(m).movement() + ". ", out);
		}
	},

	/**
	 * GitHub Actions workflow commands: an annotation on each touched method's lines in the PR, a
	 * warning when it gates and a notice when it does not. Needs no code scanning.
	 */
	GITHUB {
		@Override
		public void write(ReviewReport r, Appendable out) throws IOException {
			boolean pfr = r.config().parameteriseFieldReads();
			for (Finding f : r.findings()) {
				MethodReport m = f.method();
				StringBuilder msg = new StringBuilder(m.qualified() + " " + f.movement() + "; " + Describe.assessment(m) + ".");
				if (f.kind() != Finding.Kind.SLIMMED && m.proposal().feasible()) {
					for (String line : Describe.proposalLines(m, pfr)) {
						msg.append('\n').append(line);
					}
				}
				String title = f.redesign() ? "sinew-slim: redesign" : "sinew-slim";
				out.append("::").append(f.gates() ? "warning" : "notice").append(" file=").append(property(m.path()))
						.append(",line=").append(String.valueOf(m.nameLine())).append(",endLine=")
						.append(String.valueOf(m.endLine())).append(",title=").append(property(title)).append("::")
						.append(data(msg.toString())).append(System.lineSeparator());
			}
			out.append(summary(r)).append(System.lineSeparator());
		}

		String data(String s) {
			return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
		}

		String property(String s) {
			return data(s).replace(":", "%3A").replace(",", "%2C");
		}
	},

	/** GitLab's code quality report, so the findings show in the merge request widget. */
	CODEQUALITY {
		@Override
		public void write(ReviewReport r, Appendable out) throws IOException {
			List<Object> issues = new ArrayList<>();
			for (Finding f : r.findings()) {
				if (f.kind() == Finding.Kind.SLIMMED) {
					continue;
				}
				MethodReport m = f.method();
				Map<String, Object> o = new LinkedHashMap<>();
				o.put("description", m.qualified() + " " + f.movement() + "; " + Describe.assessment(m));
				o.put("check_name", "sinew-slim/" + m.status().name().toLowerCase(Locale.ROOT));
				o.put("fingerprint", md5(m.path() + "#" + m.qualified()));
				o.put("severity", f.gates() ? (f.redesign() ? "major" : "minor") : "info");
				o.put("location", Map.of("path", m.path(), "lines", Map.of("begin", m.nameLine())));
				issues.add(o);
			}
			out.append(JsonReport.JSON.writeValueAsString(issues)).append(System.lineSeparator());
		}
	};

	public abstract void write(ReviewReport r, Appendable out) throws IOException;

	public static ReviewWriter of(String name) {
		return valueOf(name.toUpperCase(Locale.ROOT));
	}

	/** The last line of every format: what was reviewed and the verdict. */
	static String summary(ReviewReport r) {
		long redesign = r.findings().stream().filter(f -> f.gates() && f.redesign()).count();
		return String.format("sinew-slim: %d method%s touched in %d file%s against %s; %d over the cap and newly so or longer%s (%s)",
				r.touched(), r.touched() == 1 ? "" : "s", r.files(), r.files() == 1 ? "" : "s", r.base(), r.gating().size(),
				redesign > 0 ? ", " + redesign + " of them not slimmable" : "", r.verdict());
	}

	static String cell(String s) {
		return s.replace("|", "\\|").replace("\n", " ");
	}

	static String md5(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
