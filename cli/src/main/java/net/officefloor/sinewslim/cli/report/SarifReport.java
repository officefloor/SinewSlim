package net.officefloor.sinewslim.cli.report;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.officefloor.sinewslim.core.Pieces;
import net.officefloor.sinewslim.core.Assessment.Status;

/**
 * SARIF 2.1.0, for GitHub code scanning and other viewers: one result per method over the cap, on
 * the method's lines. A method that cannot be slimmed, or that is bound to its class, is a
 * warning; one that can be slimmed is a note carrying its proposal.
 */
public final class SarifReport implements ReportWriter {

	record Rule(String id, Status status, String name, String level, String text) {
	}

	static final List<Rule> RULES = List.of(
			new Rule("SS001", Status.NOT_SLIMMABLE, "NotSlimmable", "warning",
					"A method over the length cap that no extraction brings under it: investigate for redesign."),
			new Rule("SS002", Status.SLIMMABLE_BUT_LOCKED, "SlimmableButLocked", "warning",
					"A method over the length cap whose every piece stays bound to its class: the class is the problem."),
			new Rule("SS003", Status.SLIMMABLE_WITH_RECORD, "SlimmableWithRecord", "note",
					"A method over the length cap that only an extraction returning a record brings under it."),
			new Rule("SS004", Status.SLIMMABLE, "Slimmable", "note",
					"A method over the length cap, with the extractions that bring it under."));

	@Override
	public void write(ScanReport r, int near, Appendable out) throws IOException {
		write(r.methods(), r.version(), r.config().parameteriseFieldReads(), m -> "", out);
	}

	/**
	 * SARIF for any list of methods: a result for each one over the cap, its message opened by
	 * {@code lead} (the review says how the change moved it).
	 */
	public static void write(List<MethodReport> methods, String version, boolean parameteriseFieldReads,
			java.util.function.Function<MethodReport, String> lead, Appendable out) throws IOException {
		List<Object> rules = new ArrayList<>();
		for (Rule rule : RULES) {
			Map<String, Object> o = new LinkedHashMap<>();
			o.put("id", rule.id());
			o.put("name", rule.name());
			o.put("shortDescription", Map.of("text", rule.text()));
			o.put("defaultConfiguration", Map.of("level", rule.level()));
			o.put("helpUri", "https://github.com/officefloor/SinewSlim");
			rules.add(o);
		}
		List<Object> results = new ArrayList<>();
		for (MethodReport m : methods) {
			Rule rule = RULES.stream().filter(x -> x.status() == m.status()).findFirst().orElse(null);
			if (rule == null) {
				continue;
			}
			StringBuilder text = new StringBuilder(lead.apply(m) + m.qualified() + ": " + Describe.verdict(m) + ".");
			if (m.proposal().feasible()) {
				for (Pieces.Piece p : Describe.pieces(m.proposal(), parameteriseFieldReads)) {
					text.append(String.format("%nlines %d-%d (%d lines): in %s, out %s, %s", p.lo(), p.hi(), p.lines(),
							Describe.list(concat(p.inputs(), p.passed())), Describe.outputs(p.outputs()), p.binding()));
				}
			}
			Map<String, Object> region = new LinkedHashMap<>();
			region.put("startLine", m.nameLine());
			region.put("endLine", m.endLine());
			Map<String, Object> physical = new LinkedHashMap<>();
			physical.put("artifactLocation", Map.of("uri", m.path(), "uriBaseId", "%SRCROOT%"));
			physical.put("region", region);
			Map<String, Object> o = new LinkedHashMap<>();
			o.put("ruleId", rule.id());
			o.put("ruleIndex", RULES.indexOf(rule));
			o.put("level", rule.level());
			o.put("message", Map.of("text", text.toString()));
			o.put("locations", List.of(Map.of("physicalLocation", physical)));
			o.put("partialFingerprints", Map.of("sinewSlimMethod/v1", m.path() + "#" + m.qualified()));
			results.add(o);
		}
		Map<String, Object> driver = new LinkedHashMap<>();
		driver.put("name", "sinew-slim");
		driver.put("version", version);
		driver.put("informationUri", "https://github.com/officefloor/SinewSlim");
		driver.put("rules", rules);
		Map<String, Object> run = new LinkedHashMap<>();
		run.put("tool", Map.of("driver", driver));
		run.put("results", results);
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
		doc.put("version", "2.1.0");
		doc.put("runs", List.of(run));
		out.append(JsonReport.JSON.writeValueAsString(doc)).append(System.lineSeparator());
	}

	static List<String> concat(List<String> a, List<String> b) {
		List<String> out = new ArrayList<>(a);
		out.addAll(b);
		return out;
	}
}
