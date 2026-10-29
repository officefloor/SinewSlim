package net.officefloor.sinewslim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import net.officefloor.sinewslim.cli.review.Comment;

/** The sticky comment against a stand-in for each provider's API: created once, then updated. */
class CommentTest {

	HttpServer server;
	final List<String> calls = new ArrayList<>();
	String existing = "[]";

	@TempDir
	Path dir;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", x -> {
			String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String auth = x.getRequestHeaders().getFirst("Authorization");
			String priv = x.getRequestHeaders().getFirst("PRIVATE-TOKEN");
			calls.add(x.getRequestMethod() + " " + x.getRequestURI() + " " + (auth != null ? auth : priv) + " " + body);
			byte[] reply = (x.getRequestMethod().equals("GET") ? existing : "{}").getBytes(StandardCharsets.UTF_8);
			x.sendResponseHeaders(200, reply.length);
			x.getResponseBody().write(reply);
			x.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	String api() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@Test
	void githubCreatesThenUpdates() throws IOException {
		Path event = dir.resolve("event.json");
		Files.writeString(event, "{\"pull_request\": {\"number\": 7}}");
		Comment c = new Comment(Map.of("GITHUB_TOKEN", "t", "GITHUB_REPOSITORY", "o/r", "GITHUB_EVENT_PATH",
				event.toString(), "GITHUB_API_URL", api()));
		assertEquals("github", c.provider());
		assertEquals("created", c.post("github", "hello", null, null, null));
		assertTrue(calls.get(0).startsWith("GET /repos/o/r/issues/7/comments?per_page=100&page=1 Bearer t"), calls.get(0));
		assertTrue(calls.get(1).startsWith("POST /repos/o/r/issues/7/comments Bearer t"), calls.get(1));
		assertTrue(calls.get(1).contains(Comment.MARKER + "\\nhello"), "the body carries the marker: " + calls.get(1));

		existing = "[{\"id\": 42, \"body\": \"someone else\"}, {\"id\": 43, \"body\": \"" + Comment.MARKER + "\\nold\"}]";
		calls.clear();
		assertEquals("updated", c.post("github", "again", null, null, null));
		assertTrue(calls.get(1).startsWith("PATCH /repos/o/r/issues/comments/43 "), calls.get(1));
	}

	@Test
	void gitlabCreatesThenUpdates() throws IOException {
		Comment c = new Comment(Map.of("GITLAB_CI", "true", "GITLAB_TOKEN", "g", "CI_PROJECT_ID", "group/proj",
				"CI_MERGE_REQUEST_IID", "3", "CI_API_V4_URL", api() + "/api/v4"));
		assertEquals("gitlab", c.provider());
		assertEquals("created", c.post("gitlab", "hello", null, null, null));
		assertTrue(calls.get(1).startsWith("POST /api/v4/projects/group%2Fproj/merge_requests/3/notes g"), calls.get(1));
		existing = "[{\"id\": 9, \"body\": \"" + Comment.MARKER + "\"}]";
		calls.clear();
		assertEquals("updated", c.post("gitlab", "again", null, null, null));
		assertTrue(calls.get(1).startsWith("PUT /api/v4/projects/group%2Fproj/merge_requests/3/notes/9 g"), calls.get(1));
	}

	@Test
	void missingContextSaysWhat() {
		Comment c = new Comment(Map.of());
		assertTrue(assertThrows(Comment.CommentException.class, () -> c.post("github", "x", null, null, null)).getMessage()
				.contains("GITHUB_TOKEN"));
	}
}
