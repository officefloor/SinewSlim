package net.officefloor.sinewslim.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import net.officefloor.sinewslim.cli.review.Comment;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code sinew-slim comment}: post a review as the sticky comment on its pull or merge request. */
@Command(name = "comment", mixinStandardHelpOptions = true,
		description = "Post a report as one sticky comment on the GitHub pull request or GitLab merge request, updated on each run.")
final class CommentCommand implements Callable<Integer> {

	@Option(names = "--body-file", description = "The comment, as markdown (default: standard input).")
	Path bodyFile;

	@Option(names = "--provider", description = "github or gitlab (default: gitlab inside a GitLab pipeline, github otherwise).")
	String provider;

	@Option(names = "--token", description = "Default: GITHUB_TOKEN, or GITLAB_TOKEN (api scope).")
	String token;

	@Option(names = "--repo-slug", description = "owner/name on GitHub, the project id or path on GitLab (default: from the CI environment).")
	String repo;

	@Option(names = "--pr", description = "The PR number or MR iid (default: from the CI environment).")
	String pr;

	@Override
	public Integer call() throws IOException {
		String body = bodyFile != null ? Files.readString(bodyFile, StandardCharsets.UTF_8)
				: new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
		Comment c = new Comment(System.getenv());
		String p = provider != null ? provider : c.provider();
		String result = c.post(p, body, token, repo, pr);
		System.err.println("sinew-slim: " + ("gitlab".equals(p) ? "MR note " : "PR comment ") + result);
		return SinewSlim.OK;
	}
}
