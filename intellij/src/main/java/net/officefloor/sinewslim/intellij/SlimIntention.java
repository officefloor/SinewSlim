package net.officefloor.sinewslim.intellij;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import net.officefloor.sinewslim.core.Assessment.Status;

/** Alt+Enter anywhere in a method over the cap: <em>Slim this method</em> shows the proposal over it. */
public final class SlimIntention extends PsiElementBaseIntentionAction {

	@Override
	public @NotNull String getFamilyName() {
		return "SinewSlim";
	}

	@Override
	public @NotNull String getText() {
		return "Slim this method (SinewSlim)";
	}

	@Override
	public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
		PsiMethod m = Psi.methodAt(element);
		if (m == null) {
			return false;
		}
		SlimService.MethodAnalysis a = Psi.analysis(m);
		return a != null && a.assessment().status() != Status.UNDER_CAP;
	}

	@Override
	public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
		PsiMethod m = Psi.methodAt(element);
		if (m != null) {
			SlimOverlay.show(project, editor, m);
		}
	}

	@Override
	public boolean startInWriteAction() {
		return false;
	}
}
