package net.officefloor.sinewslim.cli.report;

import java.io.IOException;
import java.util.List;

import net.officefloor.sinewslim.core.Assessment.Status;

/** One self-contained page: no external files, light and dark, a filter box over the method lists. */
final class HtmlReport implements ReportWriter {

	static final String STYLE = """
			:root { --bg: #ffffff; --fg: #1f2328; --muted: #59636e; --line: #d1d9e0; --card: #f6f8fa;
			  --warn: #9a6700; --bad: #cf222e; --good: #1a7f37; --code: #eff1f3; }
			@media (prefers-color-scheme: dark) { :root { --bg: #0d1117; --fg: #e6edf3; --muted: #9198a1;
			  --line: #3d444d; --card: #151b23; --warn: #d29922; --bad: #f85149; --good: #3fb950; --code: #262c36; } }
			* { box-sizing: border-box; }
			body { margin: 0; padding: 24px 16px 48px; background: var(--bg); color: var(--fg);
			  font: 14px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }
			main { max-width: 1100px; margin: 0 auto; }
			h1 { font-size: 22px; margin: 0 0 4px; } h2 { font-size: 17px; margin: 32px 0 8px; }
			p.sub { color: var(--muted); margin: 0 0 20px; }
			.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
			.card { background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 12px; }
			.card b { display: block; font-size: 22px; } .card span { color: var(--muted); font-size: 12px; }
			.card.bad b { color: var(--bad); } .card.warn b { color: var(--warn); } .card.good b { color: var(--good); }
			input[type=search] { width: 100%; padding: 8px 10px; margin: 24px 0 0; border: 1px solid var(--line);
			  border-radius: 6px; background: var(--card); color: var(--fg); font: inherit; }
			details { border: 1px solid var(--line); border-radius: 6px; margin: 6px 0; background: var(--bg); }
			summary { cursor: pointer; padding: 8px 10px; overflow-wrap: anywhere; }
			details > div { padding: 0 10px 10px; overflow-x: auto; }
			code { background: var(--code); padding: 1px 4px; border-radius: 4px; font-size: 12.5px; }
			.loc { color: var(--muted); font-size: 12px; }
			table { border-collapse: collapse; width: 100%; font-size: 13px; }
			th, td { border-bottom: 1px solid var(--line); padding: 5px 8px; text-align: left; vertical-align: top; }
			th { color: var(--muted); font-weight: 600; } td.n, th.n { text-align: right; }
			.wrap { overflow-x: auto; }
			""";

	static final String SCRIPT = """
			document.getElementById('filter').addEventListener('input', e => {
			  const q = e.target.value.toLowerCase();
			  document.querySelectorAll('[data-m]').forEach(el => {
			    el.style.display = el.dataset.m.toLowerCase().includes(q) ? '' : 'none'; });
			});
			""";

	@Override
	public void write(ScanReport r, int near, Appendable out) throws IOException {
		int trigger = r.config().params().trigger(), target = r.config().params().target();
		out.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
				+ "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
				+ "<title>SinewSlim scan</title><style>").append(STYLE).append("</style></head><body><main>\n");
		out.append("<h1>SinewSlim scan</h1><p class=\"sub\">").append(escape(r.root())).append(": ")
				.append(String.valueOf(r.methods().size())).append(" methods in ").append(String.valueOf(r.files()))
				.append(" files. Over the cap at ").append(String.valueOf(trigger)).append(" code lines; slimmed to ")
				.append(String.valueOf(target)).append(".</p>\n<div class=\"cards\">");
		card(out, "", r.count(Status.UNDER_CAP), "under the cap");
		card(out, "", r.overCap(), "over the cap (" + r.excess() + " lines over)");
		card(out, "good", r.count(Status.SLIMMABLE), "slimmable");
		card(out, "warn", r.count(Status.SLIMMABLE_WITH_RECORD), "slimmable only with a record");
		card(out, "warn", r.count(Status.SLIMMABLE_BUT_LOCKED), "slimmable, bound to the class");
		card(out, "bad", r.count(Status.NOT_SLIMMABLE), "not slimmable: redesign");
		out.append("</div>\n<input type=\"search\" id=\"filter\" placeholder=\"Filter methods by name or path\">\n");

		boolean pfr = r.config().parameteriseFieldReads();
		section(out, "Not slimmable: investigate for redesign", r.longestFirst(Status.NOT_SLIMMABLE), pfr);
		section(out, "Slimmable, but every piece stays bound to the class", r.longestFirst(Status.SLIMMABLE_BUT_LOCKED), pfr);
		section(out, "Slimmable only by returning a record", r.longestFirst(Status.SLIMMABLE_WITH_RECORD), pfr);
		section(out, "Slimmable, cheapest first", r.slimmable(), pfr);

		List<MethodReport> close = r.nearCap(near);
		if (!close.isEmpty()) {
			out.append("<h2>Under the cap, within ").append(String.valueOf(near))
					.append(" lines of it</h2><div class=\"wrap\"><table><tr><th>Method</th><th class=\"n\">Lines</th></tr>");
			for (MethodReport m : close) {
				out.append("<tr data-m=\"").append(escape(m.qualified() + " " + m.path())).append("\"><td><code>")
						.append(escape(m.qualified())).append("</code> <span class=\"loc\">").append(escape(m.location()))
						.append("</span></td><td class=\"n\">").append(String.valueOf(m.length())).append("</td></tr>");
			}
			out.append("</table></div>\n");
		}

		List<ClassReport> classes = r.classes().stream().filter(c -> c.pieces() + c.remainders() > 0 || c.notSlimmable() > 0)
				.toList();
		if (!classes.isEmpty()) {
			out.append("<h2>Classes, by locked share</h2><div class=\"wrap\"><table><tr><th>Class</th>"
					+ "<th class=\"n\">Locked</th><th class=\"n\">Over the cap</th><th class=\"n\">Pieces</th>"
					+ "<th class=\"n\">Not slimmable</th></tr>");
			for (ClassReport c : classes) {
				out.append("<tr data-m=\"").append(escape(c.type() + " " + c.path())).append("\"><td><code>")
						.append(escape(c.type())).append("</code> <span class=\"loc\">").append(escape(c.path()))
						.append("</span></td><td class=\"n\">").append(Describe.percent(c.lockedShare()))
						.append("</td><td class=\"n\">").append(String.valueOf(c.overCap())).append("</td><td class=\"n\">")
						.append(String.valueOf(c.pieces())).append("</td><td class=\"n\">")
						.append(String.valueOf(c.notSlimmable())).append("</td></tr>");
			}
			out.append("</table></div>\n");
		}
		if (!r.unparsed().isEmpty()) {
			out.append("<h2>Files that could not be read</h2><ul>");
			for (ScanReport.Unparsed u : r.unparsed()) {
				out.append("<li><code>").append(escape(u.path())).append("</code>: ").append(escape(u.problem())).append("</li>");
			}
			out.append("</ul>\n");
		}
		out.append("</main><script>").append(SCRIPT).append("</script></body></html>\n");
	}

	static void card(Appendable out, String kind, long n, String label) throws IOException {
		out.append("<div class=\"card ").append(kind).append("\"><b>").append(String.valueOf(n)).append("</b><span>")
				.append(escape(label)).append("</span></div>");
	}

	static void section(Appendable out, String title, List<MethodReport> methods, boolean pfr) throws IOException {
		if (methods.isEmpty()) {
			return;
		}
		out.append("<h2>").append(escape(title)).append(" (").append(String.valueOf(methods.size())).append(")</h2>\n");
		for (MethodReport m : methods) {
			out.append("<details data-m=\"").append(escape(m.qualified() + " " + m.path())).append("\"><summary><code>")
					.append(escape(m.qualified())).append("</code> <span class=\"loc\">").append(escape(m.location()))
					.append("</span><br>").append(escape(Describe.verdict(m))).append("</summary><div>");
			if (m.proposal().feasible()) {
				out.append("<table><tr><th>Lines</th><th class=\"n\">Length</th><th>In</th><th>Out</th><th>Declares</th>"
						+ "<th>Where</th><th class=\"n\">Saves</th></tr>");
				for (Describe.Piece p : Describe.pieces(m.proposal(), pfr)) {
					String in = Describe.list(p.inputs())
							+ (p.passed().isEmpty() ? "" : " (passed: " + Describe.list(p.passed()) + ")");
					out.append("<tr><td>").append(p.lo() + "&ndash;" + p.hi()).append("</td><td class=\"n\">")
							.append(String.valueOf(p.lines())).append("</td><td>").append(escape(in)).append("</td><td>")
							.append(escape(Describe.outputs(p.outputs()))).append("</td><td>")
							.append(escape(Describe.list(p.moved()))).append("</td><td>").append(escape(p.binding()))
							.append("</td><td class=\"n\">").append(String.valueOf(p.saves())).append("</td></tr>");
				}
				out.append("</table><p>Remainder: ").append(escape(Describe.remainder(m))).append("</p>");
			} else {
				out.append("<p>").append(escape(m.assessment().reason())).append(".</p>");
			}
			out.append("</div></details>\n");
		}
	}

	static String escape(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (char c : s.toCharArray()) {
			switch (c) {
			case '<' -> b.append("&lt;");
			case '>' -> b.append("&gt;");
			case '&' -> b.append("&amp;");
			case '"' -> b.append("&quot;");
			default -> b.append(c);
			}
		}
		return b.toString();
	}
}
