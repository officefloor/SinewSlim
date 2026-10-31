package net.officefloor.sinewslim.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.stmt.TryStmt;

import net.officefloor.sinewslim.facts.ExtraFacts;
import net.officefloor.sinewslim.facts.FactKey;
import net.officefloor.sinewslim.javaparser.JavaFacts.Extractor;

/** A consumer's extractors read beside the shared facts, and leave them as they were. */
class ExtractorTest {

	static final FactKey<List<String>> THROWS = FactKey.of("test.throws");

	static final FactKey<Integer> TRIES = FactKey.of("test.tries");

	/** The declared exceptions, as written; nothing for a callable that declares none. */
	static final Extractor<List<String>> DECLARED = new Extractor<>() {
		public FactKey<List<String>> key() {
			return THROWS;
		}

		public List<String> extract(BodyDeclaration<?> callable) {
			if (!(callable instanceof CallableDeclaration<?> c) || c.getThrownExceptions().isEmpty()) {
				return null;
			}
			return c.getThrownExceptions().stream().map(Object::toString).toList();
		}
	};

	static final Extractor<Integer> TRY_COUNT = new Extractor<>() {
		public FactKey<Integer> key() {
			return TRIES;
		}

		public Integer extract(BodyDeclaration<?> callable) {
			return callable.findAll(TryStmt.class).size();
		}
	};

	static final String SOURCE = """
			class A {
				void read() throws java.io.IOException, InterruptedException {
					try { work(); } catch (RuntimeException e) { }
				}
				void quiet() { work(); }
				void work() { }
				record R(int x) { R { } }
			}
			""";

	@Test
	void eachMethodCarriesWhatTheExtractorsRead() {
		ParsedFile f = JavaFacts.parse("A.java", SOURCE, List.of(DECLARED, TRY_COUNT));
		assertTrue(f.parsed(), f.problem());
		Map<String, ExtraFacts> by = f.methods().stream().collect(Collectors.toMap(MethodUnit::name, MethodUnit::extras));
		assertEquals(Optional.of(List.of("java.io.IOException", "InterruptedException")), by.get("read").get(THROWS));
		assertEquals(Optional.of(1), by.get("read").get(TRIES));
		assertEquals(Optional.empty(), by.get("quiet").get(THROWS));
		assertEquals(Optional.of(0), by.get("quiet").get(TRIES));
		assertEquals(Optional.of(0), by.get("R").get(TRIES)); // a compact constructor is read too
	}

	@Test
	void theSharedFactsAreUnchanged() {
		ParsedFile plain = JavaFacts.parse("A.java", SOURCE);
		ParsedFile extended = JavaFacts.parse("A.java", SOURCE, List.of(DECLARED, TRY_COUNT));
		for (int i = 0; i < plain.methods().size(); i++) {
			MethodUnit p = plain.methods().get(i), e = extended.methods().get(i);
			assertSame(ExtraFacts.NONE, p.extras());
			assertEquals(p.signature(), e.signature());
			assertEquals(p.facts().length(), e.facts().length());
			assertEquals(p.facts().body(), e.facts().body());
			assertEquals(p.facts().vars().size(), e.facts().vars().size());
		}
	}
}
