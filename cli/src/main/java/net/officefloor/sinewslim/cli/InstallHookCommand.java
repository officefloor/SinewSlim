package net.officefloor.sinewslim.cli;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code sinew-slim install-hook}: a git pre-commit hook that reviews the staged change. */
@Command(name = "install-hook", mixinStandardHelpOptions = true,
		description = "Install a git pre-commit hook that reviews the staged change; with enforcement: block it stops a commit that grows a method over the cap.")
final class InstallHookCommand implements Callable<Integer> {

	@Option(names = "--repo", description = "The repository (default: the current directory).", defaultValue = ".")
	Path repo;

	@Option(names = "--force", description = "Overwrite an existing pre-commit hook.")
	boolean force;

	@Override
	public Integer call() throws IOException {
		Path root = repo.toAbsolutePath().normalize();
		String rel;
		try {
			Process p = new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "--git-path", "hooks")
					.redirectErrorStream(true).start();
			rel = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
			if (p.waitFor() != 0) {
				System.err.println("sinew-slim: " + root + " is not a git repository");
				return SinewSlim.ERROR;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return SinewSlim.ERROR;
		}
		Path hooks = root.resolve(rel);
		Files.createDirectories(hooks);
		Path hook = hooks.resolve("pre-commit");
		if (Files.exists(hook) && !force) {
			System.err.println("sinew-slim: a pre-commit hook already exists at " + hook + "; rerun with --force to overwrite it");
			return SinewSlim.ERROR;
		}
		Files.writeString(hook, script(jar()), StandardCharsets.UTF_8);
		try {
			Set<PosixFilePermission> perms = Files.getPosixFilePermissions(hook);
			perms.add(PosixFilePermission.OWNER_EXECUTE);
			perms.add(PosixFilePermission.GROUP_EXECUTE);
			perms.add(PosixFilePermission.OTHERS_EXECUTE);
			Files.setPosixFilePermissions(hook, perms);
		} catch (UnsupportedOperationException e) {
			// not a POSIX file system: git for Windows runs hooks through its own shell regardless
		}
		System.err.println("sinew-slim: installed the pre-commit hook at " + hook + ". It reviews the staged change; "
				+ "set enforcement: block in .sinew-slim.yml to stop a commit that grows a method over the cap.");
		return SinewSlim.OK;
	}

	/** The hook: {@code sinew-slim} on the path when there is one, otherwise the jar this was installed from. */
	static String script(Path jar) {
		StringBuilder b = new StringBuilder();
		b.append("#!/bin/sh\n");
		b.append("# sinew-slim pre-commit hook (installed by `sinew-slim install-hook`).\n");
		b.append("# Reviews the staged change: a block verdict (exit 2) stops the commit; ok and warn let it through.\n");
		b.append("# Configure in .sinew-slim.yml; skip once with `git commit --no-verify`.\n");
		b.append("if command -v sinew-slim >/dev/null 2>&1; then\n");
		b.append("  exec sinew-slim review --mode staged\n");
		b.append("fi\n");
		if (jar != null) {
			b.append("exec java -jar '").append(jar.toString().replace("'", "'\\''")).append("' review --mode staged\n");
		} else {
			b.append("echo 'sinew-slim: not found on the PATH; the commit is not reviewed' >&2\n");
		}
		return b.toString();
	}

	/** The jar this command runs from, or null when it runs from classes. */
	static Path jar() {
		try {
			Path p = Path.of(SinewSlim.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			return p.toString().endsWith(".jar") ? p : null;
		} catch (URISyntaxException | RuntimeException e) {
			return null;
		}
	}
}
