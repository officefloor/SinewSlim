package net.officefloor.sinewslim.cli.review;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Posts the review as one sticky comment on a GitHub pull request or note on a GitLab merge
 * request: found by a hidden marker and updated on each run, never added again. Follows
 * ImpactGate's comment (its environment variables and its marker convention).
 */
public final class Comment {

	/** The hidden line that marks our comment, so each run updates it rather than adding another. */
	public static final String MARKER = "<!-- sinew-slim -->";

	static final ObjectMapper JSON = new ObjectMapper();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
	private final Map<String, String> env;

	public Comment(Map<String, String> env) {
		this.env = env;
	}

	/** A comment that could not be posted: the message says what was missing or what the server said. */
	public static final class CommentException extends IOException {
		private static final long serialVersionUID = 1L;

		CommentException(String message) {
			super(message);
		}
	}

	/** The provider from the environment when none is given: GitLab inside a GitLab pipeline, GitHub otherwise. */
	public String provider() {
		return env.containsKey("GITLAB_CI") || env.containsKey("CI_MERGE_REQUEST_IID") ? "gitlab" : "github";
	}

	/** @return {@code created} or {@code updated} */
	public String post(String provider, String body, String token, String repo, String number) throws IOException {
		String tagged = MARKER + "\n" + body;
		return "gitlab".equals(provider) ? gitlab(tagged, token, repo, number) : github(tagged, token, repo, number);
	}

	String github(String body, String token, String repo, String pr) throws IOException {
		token = token != null ? token : env.get("GITHUB_TOKEN");
		repo = repo != null ? repo : env.get("GITHUB_REPOSITORY");
		pr = pr != null ? pr : prFromEvent(env.get("GITHUB_EVENT_PATH"));
		if (token == null || repo == null || pr == null) {
			throw new CommentException("need a token, the repository (owner/name) and the PR number to comment "
					+ "(GITHUB_TOKEN, GITHUB_REPOSITORY and GITHUB_EVENT_PATH are set in Actions)");
		}
		String api = env.getOrDefault("GITHUB_API_URL", "https://api.github.com").replaceAll("/$", "");
		Map<String, String> headers = Map.of("Authorization", "Bearer " + token, "Accept", "application/vnd.github+json",
				"X-GitHub-Api-Version", "2022-11-28");
		for (int page = 1;; page++) {
			JsonNode batch = call("GET", api + "/repos/" + repo + "/issues/" + pr + "/comments?per_page=100&page=" + page,
					headers, null);
			for (JsonNode c : batch) {
				if (c.path("body").asText().contains(MARKER)) {
					call("PATCH", api + "/repos/" + repo + "/issues/comments/" + c.get("id").asText(), headers, body);
					return "updated";
				}
			}
			if (batch.size() < 100) {
				break;
			}
		}
		call("POST", api + "/repos/" + repo + "/issues/" + pr + "/comments", headers, body);
		return "created";
	}

	String gitlab(String body, String token, String project, String mr) throws IOException {
		token = token != null ? token : env.get("GITLAB_TOKEN");
		project = project != null ? project : env.get("CI_PROJECT_ID");
		mr = mr != null ? mr : env.get("CI_MERGE_REQUEST_IID");
		if (token == null || project == null || mr == null) {
			throw new CommentException("need a token, the project and the MR iid to comment on GitLab (GITLAB_TOKEN "
					+ "with api scope; CI_PROJECT_ID and CI_MERGE_REQUEST_IID are set in merge-request pipelines)");
		}
		String api = env.getOrDefault("CI_API_V4_URL", "https://gitlab.com/api/v4").replaceAll("/$", "");
		String base = api + "/projects/" + URLEncoder.encode(project, StandardCharsets.UTF_8) + "/merge_requests/" + mr
				+ "/notes";
		Map<String, String> headers = Map.of("PRIVATE-TOKEN", token);
		for (int page = 1;; page++) {
			JsonNode batch = call("GET", base + "?per_page=100&page=" + page, headers, null);
			for (JsonNode n : batch) {
				if (n.path("body").asText().contains(MARKER)) {
					call("PUT", base + "/" + n.get("id").asText(), headers, body);
					return "updated";
				}
			}
			if (batch.size() < 100) {
				break;
			}
		}
		call("POST", base, headers, body);
		return "created";
	}

	/** The PR number from the Actions event payload. */
	static String prFromEvent(String eventPath) throws IOException {
		if (eventPath == null || !Files.isRegularFile(Path.of(eventPath))) {
			return null;
		}
		JsonNode e = JSON.readTree(Path.of(eventPath).toFile());
		if (e.path("pull_request").has("number")) {
			return e.get("pull_request").get("number").asText();
		}
		return e.has("number") ? e.get("number").asText() : null;
	}

	JsonNode call(String method, String url, Map<String, String> headers, String body) throws IOException {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
				.header("User-Agent", "sinew-slim");
		headers.forEach(b::header);
		if (body != null) {
			b.header("Content-Type", "application/json");
			b.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("body", body))));
		} else {
			b.method(method, HttpRequest.BodyPublishers.noBody());
		}
		HttpResponse<String> r;
		try {
			r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted posting the comment", e);
		}
		if (r.statusCode() / 100 != 2) {
			throw new CommentException(method + " " + url + " answered " + r.statusCode() + ": "
					+ r.body().substring(0, Math.min(300, r.body().length())));
		}
		return r.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(r.body());
	}
}
