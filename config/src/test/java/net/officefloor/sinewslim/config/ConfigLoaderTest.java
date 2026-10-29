package net.officefloor.sinewslim.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.officefloor.sinewslim.config.Config.Enforcement;
import net.officefloor.sinewslim.config.ConfigLoader.ConfigException;
import net.officefloor.sinewslim.core.Params;

class ConfigLoaderTest {

	@TempDir
	Path root;

	Config load(String yaml, Map<String, Object> overrides) throws IOException {
		if (yaml != null) {
			Files.writeString(root.resolve(ConfigLoader.FILE), yaml);
		}
		return ConfigLoader.load(root, null, overrides);
	}

	@Test
	void noFileIsTheDefaults() throws IOException {
		Config c = load(null, Map.of());
		assertEquals(Params.DEFAULTS, c.params());
		assertEquals("default", c.profile());
		assertEquals(Enforcement.WARN, c.enforcement());
		assertTrue(c.ratchet());
		assertEquals(List.of("**/*.java"), c.include());
	}

	@Test
	void anEmptyFileIsValid() throws IOException {
		assertEquals(Params.DEFAULTS, load("", Map.of()).params());
	}

	@Test
	void theFileOverridesTheDefaultsAndTheFlagsTheFile() throws IOException {
		Config c = load("""
				min_piece: 4
				max_inputs: 4
				soft_inputs: 3
				enforcement: block
				framework:
				  annotations: [Next]
				""", Map.of("max_inputs", 5));
		assertEquals(4, c.params().minPiece());
		assertEquals(5, c.params().maxInputs());
		assertEquals(3, c.params().softInputs());
		assertEquals(Enforcement.BLOCK, c.enforcement());
		assertEquals(List.of("Next"), c.frameworkAnnotations());
	}

	@Test
	void theStudyProfile() throws IOException {
		assertEquals(Params.STUDY, load("profile: study", Map.of()).params());
		assertEquals(Params.STUDY, load(null, Map.of("profile", "study")).params());
		// a flag's profile wins over the file's, and the file's values still apply on top
		Config c = load("profile: default\nmin_piece: 4", Map.of("profile", "study"));
		assertEquals(4, c.params().minPiece());
		assertEquals(3, c.params().maxInputs());
	}

	@Test
	void aNullLimitIsNoLimit() throws IOException {
		Config c = load("max_inputs: null\nsoft_inputs: null", Map.of());
		assertEquals(-1, c.params().maxInputs());
		assertEquals(-1, c.params().softInputs());
	}

	@Test
	void dottedOverridesReachNestedKeys() throws IOException {
		Config c = load(null, Map.of("framework.annotations", "Next, net.officefloor.plugin.section.clazz.Parameter"));
		assertEquals(List.of("Next", "net.officefloor.plugin.section.clazz.Parameter"), c.frameworkAnnotations());
		assertTrue(c.framework(List.of("Parameter")));
		assertTrue(c.framework(List.of("Next")));
		assertFalse(c.framework(List.of("Override")));
	}

	@Test
	void mistakesNameTheKey() {
		assertTrue(assertThrows(ConfigException.class, () -> load("min_pice: 3", Map.of())).getMessage()
				.startsWith("min_pice: unknown key"));
		assertTrue(assertThrows(ConfigException.class, () -> load("framework:\n  annotation: [X]", Map.of()))
				.getMessage().startsWith("framework.annotation: unknown key"));
		assertTrue(assertThrows(ConfigException.class, () -> load("trigger: lots", Map.of())).getMessage()
				.startsWith("trigger: expected a whole number"));
		assertTrue(assertThrows(ConfigException.class, () -> load("target: 30", Map.of())).getMessage()
				.startsWith("target: must be less than trigger"));
		assertTrue(assertThrows(ConfigException.class, () -> load("enforcement: maybe", Map.of())).getMessage()
				.startsWith("enforcement: expected off, warn or block"));
		assertTrue(assertThrows(ConfigException.class, () -> load("soft_inputs: 6", Map.of())).getMessage()
				.startsWith("soft_inputs: must be at most max_inputs"));
		assertTrue(assertThrows(ConfigException.class, () -> load("- a\n- b", Map.of())).getMessage()
				.endsWith("expected a mapping of keys to values"));
	}

	@Test
	void theDumpReadsBackAsTheSameConfiguration() throws IOException {
		Config c = load("profile: study\nsoft_inputs: 2\nframework:\n  annotations: [Next]", Map.of());
		Config again = load(ConfigLoader.dump(c), Map.of());
		assertEquals(c, again);
	}
}
