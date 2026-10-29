package net.officefloor.sinewslim.intellij;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;

import net.officefloor.sinewslim.core.Pieces;

/**
 * Extracts one proposed piece through the IDE's own Extract Method (DESIGN.md, How it works). The
 * piece's literal declarations are moved down into it first, so the IDE does not turn them into
 * parameters; then the IDE computes the parameters and the return, the types, the throws clause,
 * static or not, and makes the edit. It is the authority on everything the syntactic facts
 * cannot see, and when it refuses the region, its reason is returned.
 *
 * <p>
 * {@code ExtractMethodProcessor} is an implementation class of the Java plugin, not stable API:
 * everything that touches it is here, so a platform that changes it changes this class alone.
 */
final class SlimExtractor {

	static final String TITLE = "SinewSlim: Extract Method";

	private SlimExtractor() {
	}

	/** Extract the piece as {@code name}; null when done, otherwise why the IDE refused. */
	static String extract(Project project, Editor editor, PsiMethod method, Pieces.Piece piece, String name) {
		PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
		List<PsiStatement> statements = statements(method, editor.getDocument(), piece.lo(), piece.hi());
		if (statements.isEmpty()) {
			return "no statements found on lines " + piece.lo() + "-" + piece.hi();
		}
		// ask the IDE first, before anything is changed: a refusal leaves the code as it was
		String refused = check(project, editor, statements.toArray(PsiElement[]::new), name);
		if (refused != null) {
			return refused;
		}
		boolean makeStatic = "static".equals(piece.binding()); // touches no field and calls no own method
		String[] failed = { null };
		PsiFile file = method.getContainingFile();
		WriteCommandAction.writeCommandAction(project, file).withName("SinewSlim: extract " + name).run(() -> {
			List<PsiElement> elements = new ArrayList<>();
			for (String moved : piece.moved()) {
				PsiElement declared = moveDeclaration(method, statements.get(0), moved);
				if (declared != null) {
					elements.add(declared);
				}
			}
			elements.addAll(statements);
			try {
				ExtractMethodProcessor p = processor(project, editor, elements.toArray(PsiElement[]::new), name);
				if (!p.prepare()) {
					failed[0] = "the IDE could not prepare the extraction";
					return;
				}
				p.testPrepare();
				p.setMethodName(name);
				if (makeStatic && p.isCanBeStatic()) {
					p.setStatic(true);
				}
				p.doRefactoring();
			} catch (PrepareFailedException e) {
				failed[0] = e.getMessage();
			}
		});
		return failed[0];
	}

	/** Whether the IDE would extract these statements; null when it would, otherwise its reason. */
	static String check(Project project, Editor editor, PsiElement[] elements, String name) {
		try {
			return processor(project, editor, elements, name).prepare() ? null : "the IDE could not prepare the extraction";
		} catch (PrepareFailedException e) {
			return e.getMessage();
		}
	}

	static ExtractMethodProcessor processor(Project project, Editor editor, PsiElement[] elements, String name) {
		ExtractMethodProcessor p = new ExtractMethodProcessor(project, editor, elements, null, TITLE, name, null);
		p.setShowErrorDialogs(false);
		p.setPreviewSupported(false);
		return p;
	}

	/**
	 * The statements of one sequence that lie wholly on lines {@code lo} to {@code hi}: the run the
	 * search proposed, as the IDE's tree has it.
	 */
	static List<PsiStatement> statements(PsiMethod method, Document document, int lo, int hi) {
		TextRange lines = new TextRange(document.getLineStartOffset(lo - 1), document.getLineEndOffset(hi - 1));
		List<PsiStatement> out = new ArrayList<>();
		for (PsiStatement s : PsiTreeUtil.findChildrenOfType(method.getBody(), PsiStatement.class)) {
			if (!lines.contains(s.getTextRange()) || s instanceof PsiSwitchLabelStatementBase) {
				continue;
			}
			PsiStatement outer = PsiTreeUtil.getParentOfType(s, PsiStatement.class, true, PsiMethod.class);
			if (outer == null || !lines.contains(outer.getTextRange())) {
				out.add(s); // topmost inside the lines
			}
		}
		out.sort((a, b) -> Integer.compare(a.getTextRange().getStartOffset(), b.getTextRange().getStartOffset()));
		// one sequence: the statements that share the first one's parent
		if (!out.isEmpty()) {
			PsiElement parent = out.get(0).getParent();
			out.removeIf(s -> s.getParent() != parent);
		}
		return out;
	}

	/**
	 * Move a variable's declaration, made before the piece with no value or a literal one, to the
	 * piece's start: it then crosses no seam. Returns the new declaration, or null when there was
	 * nothing to move.
	 */
	static PsiElement moveDeclaration(PsiMethod method, PsiStatement firstOfPiece, String name) {
		int start = firstOfPiece.getTextRange().getStartOffset();
		PsiLocalVariable found = null;
		for (PsiLocalVariable v : PsiTreeUtil.findChildrenOfType(method.getBody(), PsiLocalVariable.class)) {
			if (name.equals(v.getName()) && v.getTextRange().getEndOffset() <= start
					&& v.getParent() instanceof PsiDeclarationStatement
					&& v.getParent().getParent() instanceof PsiCodeBlock) {
				found = v; // the last such declaration before the piece
			}
		}
		if (found == null) {
			return null;
		}
		PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
		PsiExpression init = found.getInitializer();
		PsiDeclarationStatement copy = factory.createVariableDeclarationStatement(name, found.getType(),
				init == null ? null : (PsiExpression) init.copy(), firstOfPiece);
		if (found.hasModifierProperty(com.intellij.psi.PsiModifier.FINAL)) {
			PsiElement v = copy.getDeclaredElements()[0];
			if (v instanceof PsiLocalVariable lv && lv.getModifierList() != null) {
				lv.getModifierList().setModifierProperty(com.intellij.psi.PsiModifier.FINAL, true);
			}
		}
		PsiElement added = firstOfPiece.getParent().addBefore(copy, firstOfPiece);
		PsiDeclarationStatement original = (PsiDeclarationStatement) found.getParent();
		if (original.getDeclaredElements().length == 1) {
			original.delete();
		} else {
			found.delete();
		}
		return added;
	}
}
