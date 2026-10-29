package net.officefloor.sinewslim.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class GlobTest {

	@Test
	void doubleStarSlashMatchesNoDirectoryOrAny() {
		Glob g = new Glob("**/*.java");
		assertTrue(g.matches("A.java"));
		assertTrue(g.matches("src/main/java/a/A.java"));
		assertFalse(g.matches("A.javax"));
		assertFalse(g.matches("A.kt"));
	}

	@Test
	void singleStarStaysInOneName() {
		Glob g = new Glob("src/*.java");
		assertTrue(g.matches("src/A.java"));
		assertFalse(g.matches("src/a/A.java"));
	}

	@Test
	void excludeWinsOverInclude() {
		List<Glob> in = List.of(new Glob("**/*.java"));
		List<Glob> out = List.of(new Glob("**/generated/**"), new Glob("**/target/**"));
		assertTrue(Glob.selected("src/A.java", in, out));
		assertFalse(Glob.selected("build/generated/A.java", in, out));
		assertFalse(Glob.selected("target/A.java", in, out));
		assertFalse(Glob.selected("README.md", in, out));
	}
}
