package net.officefloor.sinewslim.intellij;

import java.util.List;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import net.officefloor.sinewslim.core.Assessment.Status;
import net.officefloor.sinewslim.core.Pieces;

/** The plugin in a light IDE: the inspection, the gutter, the intention, the overlay, and extraction through the IDE. */
public class SlimPluginTest extends LightJavaCodeInsightFixtureTestCase {

	/** A method of {@code steps} statements each handing one value to the next, then a tail that uses them. */
	static String steps(String name, int steps) {
		StringBuilder b = new StringBuilder("    int " + name + "(int x) {\n        int v0 = x;\n");
		for (int i = 1; i <= steps; i++) {
			b.append("        int v").append(i).append(" = v").append(i - 1).append(" + ").append(i).append(";\n");
		}
		return b.append("        return v").append(steps).append(";\n    }\n").toString();
	}

	static String guards(String name, int guards) {
		StringBuilder b = new StringBuilder("    int " + name + "(int x) {\n");
		for (int i = 0; i < guards; i++) {
			b.append("        if (x == ").append(i).append(") return ").append(i).append(";\n");
		}
		return b.append("        return -1;\n    }\n").toString();
	}

	PsiMethod method(String name) {
		PsiClass c = ((PsiJavaFile) myFixture.getFile()).getClasses()[0];
		return c.findMethodsByName(name, false)[0];
	}

	void configure(String... methods) {
		myFixture.configureByText("A.java", "class A {\n" + String.join("\n", methods) + "}\n");
	}

	public void testInspectionGivesTheVerdict() {
		configure(steps("longOne", 30), steps("shortOne", 5), guards("check", 30));
		myFixture.enableInspections(new SlimInspection());
		List<String> found = myFixture.doHighlighting().stream().map(HighlightInfo::getDescription)
				.filter(d -> d != null && d.startsWith("SinewSlim")).toList();
		assertEquals(found.toString(), 2, found.size());
		assertTrue(found.toString(), found.stream().anyMatch(d -> d.contains("slimmable at cost")));
		assertTrue(found.toString(), found.stream().anyMatch(d -> d.contains("cannot be slimmed")));
	}

	public void testGutterMarksTheLongMethodsOnly() {
		configure(steps("longOne", 30), steps("shortOne", 5));
		List<GutterMark> gutters = myFixture.findAllGutters().stream()
				.filter(g -> g.getTooltipText() != null && g.getTooltipText().startsWith("SinewSlim")).toList();
		assertEquals(1, gutters.size());
	}

	public void testIntentionOnlyInsideALongMethod() {
		configure(steps("longOne", 30), steps("shortOne", 5));
		int inLong = myFixture.getFile().getText().indexOf("v10 =");
		myFixture.getEditor().getCaretModel().moveToOffset(inLong);
		assertNotNull(myFixture.getAvailableIntention("Slim this method (SinewSlim)"));
		int inShort = myFixture.getFile().getText().indexOf("shortOne") + 20;
		myFixture.getEditor().getCaretModel().moveToOffset(inShort);
		List<IntentionAction> all = myFixture.getAvailableIntentions();
		assertFalse(all.stream().anyMatch(i -> i.getText().equals("Slim this method (SinewSlim)")));
	}

	public void testOverlayDrawsTheHeaderAndEachPiece() {
		configure(steps("longOne", 30));
		SlimOverlay o = SlimOverlay.show(getProject(), myFixture.getEditor(), method("longOne"));
		assertNotNull(o);
		assertEquals(Status.SLIMMABLE, o.shown().status());
		int pieces = o.shown().proposal().pieces();
		assertTrue(pieces >= 1);
		assertEquals(pieces + 1, o.inlays().size());
		assertTrue(o.inlays().get(0).getRenderer().text().startsWith("SinewSlim · 34 lines · slimmable"));
		assertEquals("Extract…", o.inlays().get(1).getRenderer().buttons().get(0).label());
		// the same method again hides it
		assertNull(SlimOverlay.toggle(getProject(), myFixture.getEditor(), method("longOne")));
		assertEquals(0, myFixture.getEditor().getInlayModel().getBlockElementsInRange(0, myFixture.getFile().getTextLength()).size());
	}

	public void testSkipSolvesAgainWithoutThePiece() {
		configure(steps("longOne", 30));
		SlimOverlay o = SlimOverlay.show(getProject(), myFixture.getEditor(), method("longOne"));
		Pieces.Piece first = Pieces.of(o.shown().proposal(), true).get(0);
		o.inlays().get(1).getRenderer().buttons().get(1).action().run(); // Skip
		assertFalse(o.shown().proposal().cuts().stream().anyMatch(s -> s.lo() == first.lo() && s.hi() == first.hi()));
		assertTrue(o.inlays().get(0).getRenderer().text().contains("1 skipped"));
	}

	public void testExtractTakesOnlyTheName() {
		configure(steps("longOne", 30));
		SlimService.MethodAnalysis a = Psi.analysis(method("longOne"));
		Pieces.Piece piece = Pieces.of(a.assessment().proposal(), true).get(0);
		String refused = SlimExtractor.extract(getProject(), myFixture.getEditor(), method("longOne"), piece, "tail");
		assertNull(refused, refused);
		String text = myFixture.getEditor().getDocument().getText();
		assertTrue(text, text.contains("tail("));
		assertTrue(text, text.contains("static int tail("));
		// what is left is under the cap
		SlimService.MethodAnalysis after = Psi.analysis(method("longOne"));
		assertEquals(Status.UNDER_CAP, after.assessment().status());
	}

	public void testALiteralDeclarationMovesIntoThePiece() {
		// the head reads three parameters and the tail only one: the cheapest piece is the tail,
		// the only place count is used, so its literal declaration moves into it
		StringBuilder b = new StringBuilder("    void log(StringBuilder out, int a, int b) {\n        int count = 0;\n");
		for (int i = 0; i < 10; i++) {
			b.append("        out.append(a + b + ").append(i).append(");\n");
		}
		for (int i = 0; i < 15; i++) {
			b.append(i % 2 == 0 ? "        count += out.length();\n" : "        out.append(count);\n");
		}
		b.append("    }\n");
		configure(b.toString());
		SlimService.MethodAnalysis a = Psi.analysis(method("log"));
		Pieces.Piece piece = Pieces.of(a.assessment().proposal(), true).stream().filter(p -> p.moved().contains("count"))
				.findFirst().orElse(null);
		assertNotNull("a piece takes the literal declaration of count: " + a.assessment().proposal().cuts(), piece);
		assertNull(SlimExtractor.extract(getProject(), myFixture.getEditor(), method("log"), piece, "finish"));
		String text = myFixture.getEditor().getDocument().getText();
		String head = text.substring(text.indexOf("void log"), text.indexOf("void finish") > 0 ? text.indexOf("void finish") : text.length());
		assertFalse("the declaration left log: " + text, head.contains("int count = 0;"));
		assertTrue("finish declares count itself, not as a parameter: " + text, text.contains("finish(StringBuilder out)"));
	}

	public void testTheIdeRefusalIsReported() {
		configure(steps("longOne", 30));
		// lines that hold no whole statement: the IDE has nothing to extract
		SlimService.MethodAnalysis a = Psi.analysis(method("longOne"));
		Pieces.Piece real = Pieces.of(a.assessment().proposal(), true).get(0);
		net.officefloor.sinewslim.core.Seam s = real.seam();
		net.officefloor.sinewslim.core.Seam bogus = new net.officefloor.sinewslim.core.Seam(1, 1, s.inputs(), s.outputs(),
				s.moved(), s.fieldsRead(), s.fieldsWritten(), s.ownCalls(), s.passed());
		Pieces.Piece p = new Pieces.Piece(bogus, real.lines(), real.saves(), real.inputs(), real.passed(), real.binding());
		assertNotNull(SlimExtractor.extract(getProject(), myFixture.getEditor(), method("longOne"), p, "nothing"));
	}
}
