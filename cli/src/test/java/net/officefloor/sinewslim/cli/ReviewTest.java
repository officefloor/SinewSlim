package net.officefloor.sinewslim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The review against real git repositories: each mode, the ratchet, the exit codes. */
class ReviewTest {

	@TempDir
	Path repo;

	@TempDir
	Path out;

	static final ObjectMapper JSON = new ObjectMapper();

	/** A method of {@code steps} statements, each handing one value to the next: slimmable once long. */
	static String steps(String name, int steps) {
		StringBuilder b = new StringBuilder("\tint " + name + "(int x) {\n\t\tint v0 = x;\n");
		for (int i = 1; i <= steps; i++) {
			b.append("\t\tint v").append(i).append(" = v").append(i - 1).append(" + ").append(i).append(";\n");
		}
		return b.append("\t\treturn v").append(steps).append(";\n\t}\n").toString();
	}

	/** A method of guard clauses, every one returning: not slimmable once long. */
	static String guards(String name, int guards) {
		StringBuilder b = new StringBuilder("\tint " + name + "(int x) {\n");
		for (int i = 0; i < guards; i++) {
			b.append("\t\tif (x == ").append(i).append(") return ").append(i).append(";\n");
		}
		return b.append("\t\treturn -1;\n\t}\n").toString();
	}

	static String type(String name, String... methods) {
		return "package p;\nclass " + name + " {\n" + String.join("\n", methods) + "}\n";
	}

	void git(String... args) throws IOException, InterruptedException {
		List<String> cmd = new ArrayList<>(List.of("git", "-C", repo.toString()));
		cmd.addAll(List.of(args));
		Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(0, p.waitFor(), "git " + String.join(" ", args) + ": " + o);
	}

	void write(String path, String source) throws IOException {
		Path f = repo.resolve(path);
		Files.createDirectories(f.getParent());
		Files.writeString(f, source);
	}

	@BeforeEach
	void base() throws Exception {
		git("init", "-q", "-b", "main");
		git("config", "user.email", "test@example.com");
		git("config", "user.name", "Test");
		git("config", "commit.gpgsign", "false");
		// short (21 lines), long (31) and a long method that cannot be slimmed (31)
		write("src/p/A.java", type("A", steps("shortOne", 17), steps("longOne", 27)));
		write("src/p/G.java", type("G", guards("check", 28)));
		write("src/p/Untouched.java", type("Untouched", steps("big", 40)));
		git("add", "-A");
		git("commit", "-q", "-m", "base");
	}

	record Run(int code, JsonNode doc) {
		Map<String, JsonNode> byName() {
			Map<String, JsonNode> m = new HashMap<>();
			doc.get("findings").forEach(f -> m.put(f.get("method").get("signature").asText().replaceAll("\\(.*", ""), f));
			return m;
		}
	}

	Run review(String... args) throws IOException {
		Path file = out.resolve("review.json");
		List<String> all = new ArrayList<>(List.of("review", "--repo", repo.toString(), "--format", "json", "--out", file.toString()));
		all.addAll(List.of(args));
		int code = SinewSlim.run(all.toArray(String[]::new));
		return new Run(code, Files.exists(file) ? JSON.readTree(file.toFile()) : null);
	}

	@Test
	void stagedCrossingTheCapWarnsAndBlocksOnRequest() throws Exception {
		write("src/p/A.java", type("A", steps("shortOne", 27), steps("longOne", 27)));
		git("add", "-A");
		Run r = review();
		assertEquals(SinewSlim.OK, r.code());
		assertEquals("warn", r.doc().get("verdict").asText());
		JsonNode f = r.byName().get("shortOne");
		assertEquals("crossed", f.get("kind").asText());
		assertTrue(f.get("gates").asBoolean());
		assertEquals(21, f.get("before").asInt());
		assertEquals("slimmable", f.get("method").get("status").asText());
		assertFalse(r.byName().containsKey("longOne"), "untouched: nothing said");
		assertFalse(r.byName().containsKey("big"), "untouched file: nothing said");
		assertEquals(SinewSlim.BLOCKED, review("--enforcement", "block").code());
	}

	@Test
	void theRatchetGatesOnGrowthOnly() throws Exception {
		// longOne touched but no longer: information; with --no-ratchet it gates
		write("src/p/A.java", type("A", steps("shortOne", 17), steps("longOne", 27).replace("v1 = v0 + 1", "v1 = v0 + 100")));
		git("add", "-A");
		Run r = review("--enforcement", "block");
		assertEquals(SinewSlim.OK, r.code());
		assertEquals("still_over", r.byName().get("longOne").get("kind").asText());
		assertFalse(r.byName().get("longOne").get("gates").asBoolean());
		assertEquals(SinewSlim.BLOCKED, review("--enforcement", "block", "--no-ratchet").code());

		write("src/p/A.java", type("A", steps("shortOne", 17), steps("longOne", 30)));
		git("add", "-A");
		JsonNode grew = review().byName().get("longOne");
		assertEquals("grew", grew.get("kind").asText());
		assertTrue(grew.get("gates").asBoolean());
	}

	@Test
	void notSlimmableGrowingIsTheRedesignNotice() throws Exception {
		write("src/p/G.java", type("G", guards("check", 30)));
		git("add", "-A");
		Run r = review();
		JsonNode f = r.byName().get("check");
		assertEquals("grew", f.get("kind").asText());
		assertEquals("not_slimmable", f.get("method").get("status").asText());
		assertEquals("jumps", f.get("method").get("obstacle").asText());
		Path md = out.resolve("review.md");
		assertEquals(SinewSlim.OK, SinewSlim.run("review", "--repo", repo.toString(), "--format", "markdown", "--out", md.toString()));
		String text = Files.readString(md);
		assertTrue(text.contains("cannot be slimmed and should be investigated for redesign"), text);
		assertTrue(text.contains("`G.check(int)`"), text);
	}

	@Test
	void newMethodsAndSlimmedMethods() throws Exception {
		write("src/p/A.java", type("A", steps("shortOne", 17), steps("longOne", 20), steps("fresh", 30)));
		git("add", "-A");
		Map<String, JsonNode> f = review().byName();
		assertEquals("new_over", f.get("fresh").get("kind").asText());
		assertNull(f.get("fresh").get("before").textValue());
		assertEquals("slimmed", f.get("longOne").get("kind").asText());
		assertFalse(f.get("longOne").get("gates").asBoolean());
	}

	@Test
	void worktreeAndRangeModes() throws Exception {
		write("src/p/A.java", type("A", steps("shortOne", 27), steps("longOne", 27)));
		assertEquals("crossed", review("--mode", "worktree").byName().get("shortOne").get("kind").asText());
		assertTrue(review("--mode", "staged").byName().isEmpty(), "nothing staged yet");

		git("checkout", "-q", "-b", "feature");
		git("commit", "-q", "-am", "grow");
		Run r = review("--mode", "range", "--base", "main");
		assertEquals("crossed", r.byName().get("shortOne").get("kind").asText());
		assertTrue(r.doc().get("base").asText().startsWith("main (merge base "));
		assertEquals(SinewSlim.ERROR, review("--mode", "range", "--base", "no-such-branch").code());
	}

	@Test
	void aRenamedFileKeepsItsHistory() throws Exception {
		git("mv", "src/p/A.java", "src/p/B.java");
		String moved = Files.readString(repo.resolve("src/p/B.java")).replace("class A", "class B");
		write("src/p/B.java", moved.replace("v1 = v0 + 1", "v1 = v0 + 100"));
		git("add", "-A");
		// renamed, so longOne has a base: it did not grow; the class name changed, so it matches by name
		JsonNode f = review().byName().get("longOne");
		assertEquals("still_over", f.get("kind").asText());
	}

	@Test
	void everyFormatAndTheConsoleLinks() throws Exception {
		write("src/p/A.java", type("A", steps("shortOne", 27), steps("longOne", 27)));
		git("add", "-A");
		for (String f : new String[] { "console", "markdown", "sarif", "codequality" }) {
			Path file = out.resolve("r." + f);
			assertEquals(SinewSlim.OK, SinewSlim.run("review", "--repo", repo.toString(), "--format", f, "--out", file.toString()));
			assertTrue(Files.readString(file).contains("shortOne"), f);
		}
		String console = Files.readString(out.resolve("r.console"));
		assertTrue(console.startsWith("src/p/A.java:3: A.shortOne(int) crossed the cap: 21 -> 31 lines"), console);
		assertTrue(console.strip().endsWith("(warn)"), console);
		assertEquals(1, JSON.readTree(out.resolve("r.sarif").toFile()).get("runs").get(0).get("results").size());
		assertEquals("minor", JSON.readTree(out.resolve("r.codequality").toFile()).get(0).get("severity").asText());
	}

	@Test
	void theFirstCommitReadsTheBaseline() throws Exception {
		Path fresh = Files.createDirectories(out.resolve("fresh"));
		Path saved = repo;
		repo = fresh;
		git("init", "-q", "-b", "main");
		write("src/p/A.java", type("A", steps("longOne", 27)));
		git("add", "-A");
		assertEquals("new_over", review().byName().get("longOne").get("kind").asText());
		assertEquals(SinewSlim.OK, SinewSlim.run("baseline", "--repo", repo.toString()));
		assertEquals("still_over", review().byName().get("longOne").get("kind").asText());
		repo = saved;
	}

	@Test
	void installHookWritesAnExecutableHook() throws Exception {
		assertEquals(SinewSlim.OK, SinewSlim.run("install-hook", "--repo", repo.toString()));
		Path hook = repo.resolve(".git/hooks/pre-commit");
		assertTrue(Files.isExecutable(hook));
		assertTrue(Files.readString(hook).contains("sinew-slim review --mode staged"));
		assertEquals(SinewSlim.ERROR, SinewSlim.run("install-hook", "--repo", repo.toString()), "an existing hook is kept");
		assertEquals(SinewSlim.OK, SinewSlim.run("install-hook", "--repo", repo.toString(), "--force"));
	}

	@Test
	void enforcementOffReviewsNothing() throws Exception {
		Files.writeString(repo.resolve(".sinew-slim.yml"), "enforcement: off\n");
		assertNull(review().doc());
	}
}
