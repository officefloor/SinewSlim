package net.officefloor.sinewslim.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.officefloor.sinewslim.facts.MethodFacts;
import net.officefloor.sinewslim.facts.Stmt;
import net.officefloor.sinewslim.facts.Var;

/**
 * The facts are the study's {@code LiveWidth.java} facts, taken over: on the same source they
 * must agree on every variable's lines, the loops and the statement tree. Run against a facts file
 * the study's pass wrote:
 *
 * <pre>
 * java -cp divergence-measurer.jar net.officefloor.divergence.LiveWidth --root DIR --files LIST --out FACTS.json.gz
 * ./gradlew :sinewslim-javaparser:test -Dsinewslim.livewidth.facts=FACTS.json.gz -Dsinewslim.livewidth.root=DIR
 * </pre>
 *
 * Without the two properties the test does nothing. Files the two parse at different language
 * levels (the study's Java 21 against this Java 25) are skipped when one of them failed.
 */
class LiveWidthParityTest {

	static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
			.streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(1_000_000)
					.maxStringLength(Integer.MAX_VALUE).build())
			.build());

	@Test
	void sameFactsAsTheStudy() throws IOException {
		String factsPath = System.getProperty("sinewslim.livewidth.facts");
		String root = System.getProperty("sinewslim.livewidth.root");
		if (factsPath == null || root == null) {
			return;
		}
		JsonNode doc;
		try (InputStream in = new GZIPInputStream(Files.newInputStream(Path.of(factsPath)))) {
			doc = JSON.readTree(in);
		}
		int files = 0, methods = 0, skipped = 0;
		List<String> diffs = new ArrayList<>();
		for (JsonNode f : doc.get("files")) {
			if ("failed".equals(f.get("level").asText())) {
				skipped++;
				continue;
			}
			String path = f.get("path").asText();
			ParsedFile mine = JavaFacts.parse(path,
					Files.readString(Path.of(root).resolve(path), StandardCharsets.UTF_8));
			if (!mine.parsed()) {
				skipped++;
				continue;
			}
			files++;
			Map<String, MethodUnit> byKey = new HashMap<>();
			for (MethodUnit m : mine.methods()) {
				byKey.putIfAbsent(m.name() + "@" + m.nameLine(), m);
			}
			for (JsonNode m : f.get("methods")) {
				if (!m.has("tree")) {
					continue; // a method the study's pass failed on
				}
				methods++;
				String key = m.get("name").asText() + "@" + m.get("name_line").asInt();
				MethodUnit u = byKey.get(key);
				String d = u == null ? "missing" : diff(m, u.facts());
				if (d != null) {
					diffs.add(path + " " + key + ": " + d);
				}
			}
		}
		System.out.println(factsPath + ": " + files + " files, " + methods + " methods compared, " + skipped
				+ " files skipped, " + diffs.size() + " differ");
		assertTrue(methods > 0, "no methods compared");
		assertEquals(List.of(), diffs.subList(0, Math.min(20, diffs.size())), diffs.size() + " methods differ");
	}

	static String diff(JsonNode m, MethodFacts f) {
		if (m.get("decl_line").asInt() != f.declLine()) {
			return "decl_line";
		}
		List<String> want = new ArrayList<>(), got = new ArrayList<>();
		for (JsonNode v : m.get("vars")) {
			want.add(v.get(0).asText() + "/" + v.get(1).asInt() + "/" + v.get(2).asInt() + "/" + v.get(10).asInt() + "/"
					+ v.get(13) + "/" + v.get(14) + "/" + v.get(15));
		}
		for (Var v : f.vars()) {
			got.add(v.name() + "/" + v.kind().ordinal() + "/" + v.first() + "/" + (v.literal() ? 1 : 0) + "/"
					+ json(v.refs()) + "/" + json(v.defs()) + "/" + json(v.muts()));
		}
		if (!want.equals(got)) {
			return "vars want " + want + " got " + got;
		}
		List<String> wl = new ArrayList<>(), gl = new ArrayList<>();
		m.get("loops").forEach(l -> wl.add(l.toString()));
		f.loops().forEach(l -> gl.add(json(l)));
		if (!wl.equals(gl)) {
			return "loops want " + wl + " got " + gl;
		}
		String wt = m.get("tree").toString(), gt = tree(f.body());
		return wt.equals(gt) ? null : "tree";
	}

	static String json(int[] xs) {
		return Arrays.toString(xs).replace(" ", "");
	}

	static String tree(Stmt s) {
		StringBuilder b = new StringBuilder("[" + s.start() + "," + s.end() + "," + (s.seq() ? 1 : 0) + ","
				+ s.kind().ordinal() + ",[");
		for (int i = 0; i < s.kids().size(); i++) {
			b.append(i > 0 ? "," : "").append(tree(s.kids().get(i)));
		}
		return b.append("]]").toString();
	}
}
