package net.officefloor.sinewslim.javaparser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.officefloor.sinewslim.core.Assessment;
import net.officefloor.sinewslim.core.Assessment.Obstacle;
import net.officefloor.sinewslim.core.Assessment.Status;
import net.officefloor.sinewslim.core.Assessor;
import net.officefloor.sinewslim.core.Binding;
import net.officefloor.sinewslim.core.Params;
import net.officefloor.sinewslim.core.Seam;
import net.officefloor.sinewslim.core.SlimResult;

/** Source in, facts and an assessment out, on small methods whose answer can be read by eye. */
class JavaFactsTest {

	static MethodUnit only(String source, String name) {
		ParsedFile f = JavaFacts.parse("Fixture.java", source);
		assertTrue(f.parsed(), f.problem());
		return f.methods().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void lengthCountsCodeLinesOnly() {
		MethodUnit m = only("""
				class A {
					@Override
					public int f(int x) {   // 1: the name's line

						// a comment line is not code
						int y = x + 1;      // 2
						/* nor is a block
						   comment */
						return y;           // 3
					}                       // 4
				}
				""", "f");
		assertEquals(4, m.facts().length());
		assertEquals("A", m.type());
		assertEquals("f(int)", m.signature());
		assertEquals(2, m.declLine());
		assertEquals(3, m.nameLine());
	}

	@Test
	void ownCallsAreInstanceMethodsOfTheEnclosingTypes() {
		MethodUnit m = only("""
				class A {
					void inst() {}
					static void util() {}
					void f(B b) {
						inst();
						this.inst();
						util();
						b.inst();
						new Object() {
							void g() { inst(); }
						};
					}
				}
				""", "f");
		// inst() and this.inst(); not the static util(), not b.inst(), not the anonymous class's body
		assertArrayEquals(new int[] { 5, 6 }, m.facts().ownCallLines());
	}

	@Test
	void anonymousAndNestedTypesAreNamed() {
		ParsedFile f = JavaFacts.parse("Fixture.java", """
				class A {
					class B {
						void f() {}
					}
					void g() {
						new Runnable() {
							public void run() {}
						};
					}
				}
				""");
		assertEquals(List.of("A.B", "A", "A.new Runnable"), f.methods().stream().map(MethodUnit::type).toList());
	}

	@Test
	void unparseableSourceSaysWhy() {
		ParsedFile f = JavaFacts.parse("Broken.java", "class A { void f( { }");
		assertTrue(!f.parsed());
		assertTrue(f.methods().isEmpty());
	}

	/** Thirty lines of steps, each handing one value to the next: cheap seams. */
	static final String SEQUENTIAL = """
			class Orders {
				int total;
				String place(String request, int quantity) {
					String trimmed = request.trim();
					String upper = trimmed.toUpperCase();
					String key = upper + ":" + quantity;
					int a = key.length();
					int b = a * 2;
					int c = b + 3;
					int d = c - 4;
					int e = d * 5;
					int f = e + 6;
					int g = f - 7;
					int h = g * 8;
					int i = h + 9;
					int j = i - 10;
					int k = j * 11;
					int l = k + 12;
					int m = l - 13;
					int n = m * 14;
					int o = n + 15;
					int p = o - 16;
					int q = p * 17;
					int r = q + 18;
					int s = r - 19;
					int t = s * 20;
					int u = t + 21;
					int v = u - 22;
					String out = key + v;
					return out;
				}
			}
			""";

	@Test
	void sequentialStepsAreSlimmable() {
		MethodUnit m = only(SEQUENTIAL, "place");
		assertEquals(29, m.facts().length()); // the name's line, 27 statements, the closing brace
		Assessment a = Assessor.DEFAULTS.assess(m.facts());
		assertEquals(Status.SLIMMABLE, a.status());
		SlimResult r = a.proposal();
		assertTrue(r.feasible());
		assertTrue(r.remainder() <= Params.DEFAULTS.target());
		for (Seam s : r.cuts()) {
			assertTrue(s.inputs().size() <= 2, () -> "a step needs at most two values: " + s);
			assertTrue(s.outputs().size() <= 1);
			assertEquals(Binding.FREE, s.binding(r.params()));
		}
	}

	/**
	 * Six accumulators updated in turn: any run of three lines or more writes three of them, each
	 * read later, so every cut but the tail carries several results; and the tail reads all six
	 * and the array, one parameter over the limit. The accumulators start from the array, not a
	 * literal, so their declarations cannot move into a piece. Only a head piece returning a record
	 * fits.
	 */
	static final String INTERWOVEN = """
			class Stats {
				double summary(double[] x) {
					double a = x[24], b = x[25], c = x[26], d = x[27], e = x[28], f = x[29];
					a += x[0];
					b += x[1];
					c += x[2];
					d += x[3];
					e += x[4];
					f += x[5];
					a += x[6];
					b += x[7];
					c += x[8];
					d += x[9];
					e += x[10];
					f += x[11];
					a += x[12];
					b += x[13];
					c += x[14];
					d += x[15];
					e += x[16];
					f += x[17];
					a += x[18];
					b += x[19];
					c += x[20];
					d += x[21];
					e += x[22];
					f += x[23];
					return a + b + c + d + e + f;
				}
			}
			""";

	@Test
	void interwovenNeedsARecord() {
		MethodUnit m = only(INTERWOVEN, "summary");
		Assessment a = Assessor.DEFAULTS.assess(m.facts());
		assertEquals(Status.SLIMMABLE_WITH_RECORD, a.status());
		assertEquals(Obstacle.OUTPUTS, a.obstacle());
		assertTrue(a.proposal().feasible());
		assertTrue(a.proposal().cuts().stream().anyMatch(s -> s.outputs().size() >= 2));

		Assessment without = new Assessor(Params.DEFAULTS, false, true, 1).assess(m.facts());
		assertEquals(Status.NOT_SLIMMABLE, without.status());
		assertEquals(Obstacle.OUTPUTS, without.obstacle());
	}

	@Test
	void shortMethodIsUnderTheCap() {
		MethodUnit m = only("class A { int f(int x) { return x; } }", "f");
		Assessment a = Assessor.DEFAULTS.assess(m.facts());
		assertEquals(Status.UNDER_CAP, a.status());
		assertNull(a.obstacle());
	}
}
