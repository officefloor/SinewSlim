package net.officefloor.sinewslim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The core is accepted when it reproduces the study's {@code split.py} exactly (DESIGN.md,
 * Testing). The parity data is written in the study repository by
 * {@code scripts/export_split_golden.py}: per long method, the live-width facts (schema 3), the
 * own-call lines, the measurer's nloc, and split.py's Result under each named Params.
 *
 * <p>
 * The committed subset is read on every build. The full export is read when its path is given:
 * {@code ./gradlew :core:test -Dsinewslim.parity.full=/path/split-golden.jsonl.gz}.
 */
class StudyParityTest {

	static final String SUBSET = "/parity/split-golden-subset.jsonl.gz";

	/** A statement tree nests one level per else-if branch, deeper than Jackson's default limit. */
	static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
			.streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(1_000_000).build()).build());

	@Test
	void committedSubset() throws IOException {
		InputStream in = StudyParityTest.class.getResourceAsStream(SUBSET);
		assertTrue(in != null, "missing test resource " + SUBSET);
		check(in, SUBSET);
	}

	@Test
	void fullExport() throws IOException {
		String full = System.getProperty("sinewslim.parity.full");
		if (full == null) {
			return;
		}
		check(Files.newInputStream(Path.of(full)), full, true);
	}

	static void check(InputStream raw, String source) throws IOException {
		check(raw, source, false);
	}

	/**
	 * Every method of an export against the core. An export still being written ends in a
	 * truncated gzip stream; with {@code partial} it is read up to its last whole line.
	 */
	static void check(InputStream raw, String source, boolean partial) throws IOException {
		int methods = 0, compared = 0;
		List<String> mismatches = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8))) {
			Map<String, Params> params = params(JSON.readTree(r.readLine()).get("params"));
			for (String line = next(r, partial); line != null; line = next(r, partial)) {
				JsonNode row;
				try {
					row = JSON.readTree(line);
				} catch (IOException e) {
					if (partial) {
						break; // the last line, cut short
					}
					throw e;
				}
				MethodFacts facts = facts(row);
				methods++;
				Iterator<Map.Entry<String, JsonNode>> it = row.get("expect").properties().iterator();
				while (it.hasNext()) {
					Map.Entry<String, JsonNode> e = it.next();
					SlimResult got = Slimmer.slim(facts, params.get(e.getKey()));
					compared++;
					String diff = diff(got, e.getValue());
					if (diff != null && mismatches.size() < 20) {
						mismatches.add(row.get("set").asText() + " " + row.get("key").asText() + " " + row.get("path").asText()
								+ " " + row.get("name").asText() + " [" + e.getKey() + "]: " + diff);
					} else if (diff != null) {
						mismatches.add("");
					}
				}
			}
		}
		System.out.println(source + ": " + methods + " methods, " + compared + " results compared, " + mismatches.size()
				+ " mismatched");
		assertTrue(methods > 0, "no methods in " + source);
		assertEquals(List.of(), mismatches.stream().filter(s -> !s.isEmpty()).toList(),
				mismatches.size() + " of " + compared + " results differ from split.py");
	}

	static String next(BufferedReader r, boolean partial) throws IOException {
		try {
			return r.readLine();
		} catch (EOFException e) {
			if (partial) {
				return null;
			}
			throw e;
		}
	}

	/** What differs between the core's result and split.py's, or null. */
	static String diff(SlimResult got, JsonNode want) {
		Map<String, Object> g = new HashMap<>(), w = new HashMap<>();
		g.put("applicable", got.applicable());
		w.put("applicable", want.get("applicable").asBoolean());
		g.put("feasible", got.feasible());
		w.put("feasible", want.get("feasible").asBoolean());
		g.put("width", (double) got.width());
		w.put("width", want.get("width").asDouble());
		g.put("cost", (double) got.cost());
		w.put("cost", want.get("cost").asDouble());
		g.put("pieces", got.pieces());
		w.put("pieces", want.get("pieces").asInt());
		g.put("interface", got.feasible() ? got.interfaceSize() : SlimResult.INFEASIBLE);
		w.put("interface", want.get("interface").asInt());
		g.put("remainder", got.remainder());
		w.put("remainder", want.get("remainder").asInt());
		g.put("locked", got.count(Binding.LOCKED));
		w.put("locked", want.get("locked").asInt());
		g.put("bound", got.count(Binding.BOUND));
		w.put("bound", want.get("bound").asInt());
		g.put("free", got.count(Binding.FREE));
		w.put("free", want.get("free").asInt());
		g.put("remainder_locked", got.remainderLocked());
		w.put("remainder_locked", want.get("remainder_locked").asBoolean());
		List<String> gc = new ArrayList<>(), wc = new ArrayList<>();
		for (Seam s : got.cuts()) {
			gc.add(s.lo() + "-" + s.hi() + ":" + s.inputs().size() + "/" + s.outputs().size() + "/"
					+ s.binding(got.params()).name().toLowerCase());
		}
		for (JsonNode c : want.get("cuts")) {
			wc.add(c.get(0).asInt() + "-" + c.get(1).asInt() + ":" + c.get(2).asInt() + "/" + c.get(3).asInt() + "/"
					+ c.get(4).asText());
		}
		g.put("cuts", gc);
		w.put("cuts", wc);
		if (g.equals(w)) {
			return null;
		}
		StringBuilder b = new StringBuilder();
		for (String k : w.keySet()) {
			if (!w.get(k).equals(g.get(k))) {
				b.append(k).append(" want ").append(w.get(k)).append(" got ").append(g.get(k)).append("; ");
			}
		}
		return b.toString();
	}

	static Map<String, Params> params(JsonNode all) {
		Map<String, Params> out = new HashMap<>();
		all.properties().forEach(e -> {
			JsonNode p = e.getValue();
			out.put(e.getKey(), Params.STUDY.toBuilder().trigger(p.get("trigger").asInt()).target(p.get("target").asInt())
					.minPiece(p.get("min_piece").asInt()).minSaving(p.get("min_saving").asInt())
					.maxInputs(p.get("max_inputs").asInt()).maxOutputs(p.get("max_outputs").asInt()).softInputs(-1)
					.outputWeight(p.get("output_weight").asInt()).multiOutputWeight(p.get("multi_output_weight").asInt())
					.pieceCost(p.get("piece_cost").asInt()).trailingReturn(p.get("trailing_return").asBoolean())
					.structuralOnly(p.get("structural_only").asBoolean())
					.lockOnAnyField(p.get("lock_on_any_field").asBoolean()).freeParams(p.get("free_params").asBoolean())
					.build());
		});
		return out;
	}

	/** The study's facts (scripts/livewidth.py, schema 3) as the core's. */
	static MethodFacts facts(JsonNode row) {
		JsonNode f = row.get("facts");
		List<Var> vars = new ArrayList<>();
		for (JsonNode v : f.get("vars")) {
			// NAME, KIND, FIRST, LAST, LOOP_FIRST, LOOP_LAST, DEFS, STEPS, MUTS, FROM, LIT,
			// USE_FIRST, USE_LOOP_FIRST, REFS, DEF_LINES, MUT_LINES
			vars.add(new Var(v.get(0).asText(), Var.Kind.values()[v.get(1).asInt()], v.get(2).asInt(), v.get(10).asInt() != 0,
					ints(v.get(13)), ints(v.get(14)), ints(v.get(15))));
		}
		List<int[]> loops = new ArrayList<>();
		if (f.has("loops")) {
			for (JsonNode l : f.get("loops")) {
				loops.add(new int[] { l.get(0).asInt(), l.get(1).asInt() });
			}
		}
		int declLine = f.has("decl_line") ? f.get("decl_line").asInt()
				: f.has("name_line") ? f.get("name_line").asInt() : f.get("tree").get(0).asInt();
		return new MethodFacts(declLine, tree(f.get("tree")), vars, loops, ints(row.get("own")), row.get("nloc").asInt());
	}

	static Stmt tree(JsonNode n) {
		List<Stmt> kids = new ArrayList<>();
		for (JsonNode k : n.get(4)) {
			kids.add(tree(k));
		}
		return new Stmt(n.get(0).asInt(), n.get(1).asInt(), n.get(2).asInt() != 0, StmtKind.of(n.get(3).asInt()), kids);
	}

	static int[] ints(JsonNode a) {
		int[] out = new int[a.size()];
		for (int i = 0; i < a.size(); i++) {
			out[i] = a.get(i).asInt();
		}
		return out;
	}
}
