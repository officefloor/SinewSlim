package net.officefloor.sinewslim.intellij;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;

import net.officefloor.sinewslim.core.Pieces;

/**
 * Extracts a piece that returns several values, as a record (DESIGN.md, Records). Java has no
 * tuple, so the values travel in a record the developer names. The piece is first reshaped so
 * that it has one result, and then the IDE's own Extract Method does the rest, as for any piece:
 *
 * <ol>
 * <li>a result declared inside the piece has its declaration lifted out in front of it, the
 * piece keeping the assignment;</li>
 * <li>the piece ends by packing its results: {@code Totals totals = new Totals(a, b);}, and
 * after it each result is unpacked: {@code a = totals.a();}. The results are then no longer
 * read after the piece, and the record is its one output;</li>
 * <li>the record is declared in the class: {@code private record Totals(double a, double b) {}};</li>
 * <li>the IDE extracts the piece, the record its return;</li>
 * <li>each lifted declaration is folded back into its unpacking: {@code double a = totals.a();}.</li>
 * </ol>
 *
 * The record costs three per value in the search (the type, the packing, the unpacking), and the
 * unpacking is one line per value left in the method.
 */
final class RecordExtractor {

	private RecordExtractor() {
	}

	/** Extract the piece as {@code name}, returning a record {@code recordName}; null when done, otherwise why not. */
	static String extract(Project project, Editor editor, PsiMethod method, Pieces.Piece piece, String name,
			String recordName) {
		PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
		List<PsiStatement> statements = SlimExtractor.statements(method, editor.getDocument(), piece.lo(), piece.hi());
		if (statements.isEmpty()) {
			return "no statements found on lines " + piece.lo() + "-" + piece.hi();
		}
		LanguageLevel level = PsiUtil.getLanguageLevel(method);
		if (!level.isAtLeast(LanguageLevel.JDK_16)) {
			return "a record needs Java 16 or later; this module's language level is " + level.toJavaVersion();
		}
		PsiClass owner = method.getContainingClass();
		if (owner == null) {
			return "the method is in no class to declare the record in";
		}
		if (owner.findInnerClassByName(recordName, false) != null) {
			return owner.getName() + " already has a type named " + recordName;
		}
		Map<String, PsiVariable> results = new LinkedHashMap<>();
		for (String o : piece.outputs()) {
			PsiVariable v = variable(method, statements, o);
			if (v == null) {
				return "cannot find the declaration of " + o;
			}
			results.put(o, v);
		}
		// the types, read now: lifting a declaration deletes the variable it was read from
		Map<String, PsiType> types = new LinkedHashMap<>();
		results.forEach((o, v) -> types.put(o, v.getType()));

		boolean makeStatic = "static".equals(piece.binding());
		String[] failed = { null };
		WriteCommandAction.writeCommandAction(project, method.getContainingFile())
				.withName("SinewSlim: extract " + name + " returning " + recordName).run(() -> {
					PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
					List<PsiStatement> run = new ArrayList<>(statements);
					List<PsiElement> moved = new ArrayList<>();
					for (String m : piece.moved()) {
						PsiElement declared = SlimExtractor.moveDeclaration(method, run.get(0), m);
						if (declared != null) {
							moved.add(declared);
						}
					}
					PsiElement regionStart = moved.isEmpty() ? run.get(0) : moved.get(0);

					// 1. lift the declarations of results declared in the piece out in front of it
					Map<String, SmartPsiElementPointer<PsiDeclarationStatement>> lifted = new LinkedHashMap<>();
					SmartPointerManager pointers = SmartPointerManager.getInstance(project);
					for (Map.Entry<String, PsiVariable> e : results.entrySet()) {
						if (!(e.getValue() instanceof PsiLocalVariable v)
								|| !(v.getParent() instanceof PsiDeclarationStatement ds) || !run.contains(ds)) {
							continue;
						}
						PsiDeclarationStatement declaration = (PsiDeclarationStatement) regionStart.getParent().addBefore(
								factory.createVariableDeclarationStatement(e.getKey(), types.get(e.getKey()), null, regionStart),
								regionStart);
						lifted.put(e.getKey(), pointers.createSmartPsiElementPointer(declaration));
						PsiExpression init = v.getInitializer();
						PsiStatement assign = init == null ? null
								: factory.createStatementFromText(e.getKey() + " = " + init.getText() + ";", ds);
						int at = run.indexOf(ds);
						if (ds.getDeclaredElements().length == 1) {
							if (assign == null) {
								ds.delete();
								run.remove(at);
							} else {
								run.set(at, (PsiStatement) ds.replace(assign));
							}
						} else {
							if (assign != null) {
								run.add(at + 1, (PsiStatement) ds.getParent().addAfter(assign, ds));
							}
							v.delete();
						}
					}

					// 2. pack the results at the piece's end, and unpack them after it
					PsiStatement last = run.get(run.size() - 1);
					String recordVar = JavaCodeStyleManager.getInstance(project)
							.suggestUniqueVariableName(Introspector.decapitalize(recordName), last, true);
					PsiStatement pack = (PsiStatement) last.getParent().addAfter(factory.createStatementFromText(recordName
							+ " " + recordVar + " = new " + recordName + "(" + String.join(", ", results.keySet()) + ");", last),
							last);
					Map<String, SmartPsiElementPointer<PsiStatement>> unpacked = new LinkedHashMap<>();
					PsiElement prev = pack;
					for (String o : results.keySet()) {
						prev = prev.getParent().addAfter(
								factory.createStatementFromText(o + " = " + recordVar + "." + o + "();", pack), prev);
						unpacked.put(o, pointers.createSmartPsiElementPointer((PsiStatement) prev));
					}

					// 3. the record, in the class
					List<String> components = new ArrayList<>();
					types.forEach((o, t) -> components.add(t.getCanonicalText() + " " + o));
					PsiClass record = factory
							.createClassFromText("private record " + recordName + "(" + String.join(", ", components) + ") {}",
									owner)
							.getInnerClasses()[0];
					owner.add(record);

					// 4. the IDE extracts the piece, the record its one result
					List<PsiElement> elements = new ArrayList<>(moved);
					elements.addAll(run);
					elements.add(pack);
					try {
						ExtractMethodProcessor p = SlimExtractor.processor(project, editor, elements.toArray(PsiElement[]::new),
								name);
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
						return;
					}

					// 5. fold each lifted declaration into its unpacking: double a = totals.a();
					for (Map.Entry<String, SmartPsiElementPointer<PsiDeclarationStatement>> e : lifted.entrySet()) {
						PsiDeclarationStatement declaration = e.getValue().getElement();
						PsiStatement assignment = unpacked.get(e.getKey()).getElement();
						if (declaration == null || !(assignment instanceof PsiExpressionStatement es)
								|| !(es.getExpression() instanceof PsiAssignmentExpression ae) || ae.getRExpression() == null) {
							continue;
						}
						PsiLocalVariable v = (PsiLocalVariable) declaration.getDeclaredElements()[0];
						assignment.replace(factory.createVariableDeclarationStatement(e.getKey(), v.getType(),
								(PsiExpression) ae.getRExpression().copy(), assignment));
						declaration.delete();
					}
				});
		if (failed[0] != null) {
			// the reshaping is in the command: undo it, so a refusal leaves the code as it was
			FileEditor fe = TextEditorProvider.getInstance().getTextEditor(editor);
			UndoManager undo = UndoManager.getInstance(project);
			if (undo.isUndoAvailable(fe)) {
				undo.undo(fe);
			}
		}
		return failed[0];
	}

	/**
	 * The variable a result is: declared at the top level of the piece, or before it in the
	 * method (the last such declaration), or a parameter.
	 */
	static PsiVariable variable(PsiMethod method, List<PsiStatement> run, String name) {
		int end = run.get(run.size() - 1).getTextRange().getEndOffset();
		PsiVariable found = null;
		for (PsiLocalVariable v : PsiTreeUtil.findChildrenOfType(method.getBody(), PsiLocalVariable.class)) {
			if (!name.equals(v.getName()) || v.getTextRange().getStartOffset() >= end) {
				continue;
			}
			boolean inPiece = v.getTextRange().getStartOffset() >= run.get(0).getTextRange().getStartOffset();
			if (inPiece && !run.contains(v.getParent())) {
				continue; // declared deeper inside the piece: not visible after it
			}
			found = v;
		}
		if (found != null) {
			return found;
		}
		for (PsiParameter p : method.getParameterList().getParameters()) {
			if (name.equals(p.getName())) {
				return p;
			}
		}
		return null;
	}

	/** A record name from the results: {@code SumAndCount} for two, {@code SumEtc} for more. */
	static String suggestName(List<String> outputs) {
		String first = SlimOverlay.capitalise(outputs.get(0));
		return outputs.size() == 2 ? first + "And" + SlimOverlay.capitalise(outputs.get(1)) : first + "Etc";
	}
}
