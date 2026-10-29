package net.officefloor.sinewslim.intellij;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;

import net.officefloor.sinewslim.core.Assessment.Status;
import net.officefloor.sinewslim.core.Pieces;

/**
 * Marks every method at or over the trigger on its name, with its verdict: a weak warning when it
 * can be slimmed, a warning when it cannot, or when every piece stays bound to the class (the
 * redesign notice). The quick fix shows the proposal over the method.
 */
public final class SlimInspection extends AbstractBaseJavaLocalInspectionTool {

	@Override
	public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
		return new JavaElementVisitor() {
			@Override
			public void visitMethod(@NotNull PsiMethod method) {
				SlimService.MethodAnalysis a = Psi.analysis(method);
				if (a == null || a.assessment().status() == Status.UNDER_CAP || method.getNameIdentifier() == null) {
					return;
				}
				holder.registerProblem(method.getNameIdentifier(), message(a), type(a.assessment().status()),
						new ShowProposalFix());
			}
		};
	}

	static String message(SlimService.MethodAnalysis a) {
		int length = a.unit().facts().length();
		return "SinewSlim: " + length + " lines; " + Pieces.verdict(a.assessment(), length);
	}

	static ProblemHighlightType type(Status s) {
		return s == Status.NOT_SLIMMABLE || s == Status.SLIMMABLE_BUT_LOCKED ? ProblemHighlightType.WARNING
				: ProblemHighlightType.WEAK_WARNING;
	}

	/** Shows the proposal over the method in the editor it is open in. */
	static final class ShowProposalFix implements LocalQuickFix {

		@Override
		public @NotNull String getFamilyName() {
			return "Show how SinewSlim would slim this method";
		}

		@Override
		public boolean startInWriteAction() {
			return false;
		}

		@Override
		public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
			PsiMethod method = Psi.methodAt(descriptor.getPsiElement());
			Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
			if (method != null && editor != null) {
				SlimOverlay.show(project, editor, method);
			}
		}
	}
}
