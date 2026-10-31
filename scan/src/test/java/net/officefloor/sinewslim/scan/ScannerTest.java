package net.officefloor.sinewslim.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.officefloor.sinewslim.scan.Scanner.FileResult;

/** The files a scan selects, each read by the caller's reader, Java or not. */
class ScannerTest {

	@TempDir
	Path root;

	void write(String path, String text) throws IOException {
		Path p = root.resolve(path);
		Files.createDirectories(p.getParent());
		Files.writeString(p, text);
	}

	static List<Glob> globs(String... globs) {
		return Arrays.stream(globs).map(Glob::new).toList();
	}

	@Test
	void readsEachSelectedJavaFilesMethods() throws IOException {
		write("src/A.java", "class A { void f() { } void g() { } }");
		write("src/Broken.java", "class B { void f( { }");
		write("build/generated/G.java", "class G { void f() { } }");
		write("README.md", "# not code");
		List<FileResult<List<String>>> rs = Scanner.scan(root, globs("**/*.java"), globs("**/generated/**"), 2,
				Scanner.javaMethods(List.of(), (path, m) -> path + "#" + m.name()));
		Map<String, FileResult<List<String>>> by = rs.stream().collect(Collectors.toMap(FileResult::path, r -> r));
		assertEquals(2, rs.size());
		assertEquals(List.of("src/A.java#f", "src/A.java#g"), by.get("src/A.java").result());
		FileResult<List<String>> broken = by.get("src/Broken.java");
		assertFalse(broken.read());
		assertNull(broken.result());
		assertFalse(broken.problem().startsWith("Unreadable"), broken.problem()); // the parser's problem, as it is
	}

	@Test
	void readsOtherFilesTheirOwnWay() throws IOException {
		write("src/main/resources/application.officefloor", "<officefloor/>\n");
		write("src/main/resources/procedure.yml", "a: 1\nb: 2\n");
		List<FileResult<Integer>> rs = Scanner.scan(root, globs("**/*.officefloor", "**/*.yml"), List.of(), 1,
				(path, source) -> (int) source.lines().count());
		assertEquals(List.of("src/main/resources/application.officefloor", "src/main/resources/procedure.yml"),
				rs.stream().map(FileResult::path).toList());
		assertEquals(List.of(1, 2), rs.stream().map(FileResult::result).toList());
	}

	@Test
	void aReaderThatFailsIsThatFilesProblem() throws IOException {
		write("A.txt", "x");
		List<FileResult<String>> rs = Scanner.scan(root, globs("*.txt"), List.of(), 1, (path, source) -> {
			throw new IllegalStateException("cannot");
		});
		assertEquals("IllegalStateException: cannot", rs.get(0).problem());
		assertFalse(rs.get(0).read());
	}
}
