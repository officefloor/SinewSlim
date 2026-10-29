package net.officefloor.sinewslim.cli.report;

import java.io.IOException;
import java.util.List;

import net.officefloor.sinewslim.core.Pieces;
import net.officefloor.sinewslim.core.Assessment.Status;

/** Markdown for a job summary, a PR comment or a wiki page: GitHub renders the {@code <details>} blocks folded. */
final class MarkdownReport implements ReportWriter {

	@Override
	public void write(ScanReport r, int near, Appendable out) throws IOException {
		int trigger = r.config().params().trigger(), target = r.config().params().target();
		out.append("## sinew-slim\n\n");
		out.append(String.format("%d methods in %d files. A method of %d code lines or more is over the cap; "
				+ "slimming brings it, and every piece, to %d.%n%n", r.methods().size(), r.files(), trigger, target));
		out.append("| | Methods |\n|---|---:|\n");
		out.append(String.format("| Under the cap | %d |%n", r.count(Status.UNDER_CAP)));
		out.append(String.format("| Over the cap (%d lines over in all) | %d |%n", r.excess(), r.overCap()));
		out.append(String.format("| &nbsp;&nbsp;Slimmable | %d |%n", r.count(Status.SLIMMABLE)));
		out.append(String.format("| &nbsp;&nbsp;Slimmable only by returning a record | %d |%n",
				r.count(Status.SLIMMABLE_WITH_RECORD)));
		out.append(String.format("| &nbsp;&nbsp;Slimmable, but bound to the class | %d |%n",
				r.count(Status.SLIMMABLE_BUT_LOCKED)));
		out.append(String.format("| &nbsp;&nbsp;**Not slimmable: investigate for redesign** | **%d** |%n",
				r.count(Status.NOT_SLIMMABLE)));
		if (!r.unparsed().isEmpty()) {
			out.append(String.format("| Files that could not be read | %d |%n", r.unparsed().size()));
		}

		boolean pfr = r.config().parameteriseFieldReads();
		List<MethodReport> not = r.longestFirst(Status.NOT_SLIMMABLE);
		if (!not.isEmpty()) {
			out.append("\n### Not slimmable: investigate for redesign\n\n| Method | Lines | Why |\n|---|---:|---|\n");
			for (MethodReport m : not) {
				out.append(String.format("| `%s` %s | %d | %s |%n", cell(m.qualified()), cell(m.location()), m.length(),
						cell(m.assessment().reason())));
			}
		}
		section(out, "Slimmable, but every piece stays bound to the class", r.longestFirst(Status.SLIMMABLE_BUT_LOCKED), pfr);
		section(out, "Slimmable only by returning a record", r.longestFirst(Status.SLIMMABLE_WITH_RECORD), pfr);
		section(out, "Slimmable, cheapest first", r.slimmable(), pfr);

		List<MethodReport> close = r.nearCap(near);
		if (!close.isEmpty()) {
			out.append(String.format("%n### Under the cap, within %d lines of it%n%n| Method | Lines |%n|---|---:|%n", near));
			for (MethodReport m : close) {
				out.append(String.format("| `%s` %s | %d |%n", cell(m.qualified()), cell(m.location()), m.length()));
			}
		}

		List<ClassReport> classes = r.classes().stream().filter(c -> c.pieces() + c.remainders() > 0 || c.notSlimmable() > 0)
				.toList();
		if (!classes.isEmpty()) {
			out.append("\n### Classes, by locked share\n\n| Class | Locked | Over the cap | Pieces | Not slimmable |\n"
					+ "|---|---:|---:|---:|---:|\n");
			for (ClassReport c : classes) {
				out.append(String.format("| `%s` %s | %s | %d | %d | %d |%n", cell(c.type()), cell(c.path()),
						Describe.percent(c.lockedShare()), c.overCap(), c.pieces(), c.notSlimmable()));
			}
		}
		if (!r.unparsed().isEmpty()) {
			out.append("\n### Files that could not be read\n\n");
			for (ScanReport.Unparsed u : r.unparsed()) {
				out.append(String.format("- `%s`: %s%n", u.path(), u.problem()));
			}
		}
	}

	static void section(Appendable out, String title, List<MethodReport> methods, boolean pfr) throws IOException {
		if (methods.isEmpty()) {
			return;
		}
		out.append(String.format("%n### %s (%d)%n%n", title, methods.size()));
		for (MethodReport m : methods) {
			out.append(String.format("<details><summary><code>%s</code> %s: %s</summary>%n%n", html(m.qualified()),
					html(m.location()), html(Describe.verdict(m))));
			out.append("| Lines | Length | In | Out | Declares | Where | Saves |\n|---|---:|---|---|---|---|---:|\n");
			for (Pieces.Piece p : Describe.pieces(m.proposal(), pfr)) {
				String in = Describe.list(p.inputs()) + (p.passed().isEmpty() ? "" : " (passed: " + Describe.list(p.passed()) + ")");
				out.append(String.format("| %d-%d | %d | %s | %s | %s | %s | %d |%n", p.lo(), p.hi(), p.lines(), cell(in),
						cell(Describe.outputs(p.outputs())), cell(Describe.list(p.moved())), cell(p.binding()), p.saves()));
			}
			out.append(String.format("%nRemainder: %s%n%n</details>%n", Describe.remainder(m)));
		}
	}

	static String cell(String s) {
		return s.replace("|", "\\|").replace("\n", " ");
	}

	static String html(String s) {
		return HtmlReport.escape(s);
	}
}
