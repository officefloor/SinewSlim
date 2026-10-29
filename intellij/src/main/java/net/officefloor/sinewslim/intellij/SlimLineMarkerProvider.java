package net.officefloor.sinewslim.intellij;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;

import net.officefloor.sinewslim.core.Assessment.Status;

/** The gutter mark on a method over the cap; clicking it shows or hides the proposal. */
public final class SlimLineMarkerProvider implements LineMarkerProvider {

	static final Icon SLIMMABLE = IconLoader.getIcon("/icons/sinewSlim.svg", SlimLineMarkerProvider.class);
	static final Icon REDESIGN = IconLoader.getIcon("/icons/sinewSlimRedesign.svg", SlimLineMarkerProvider.class);

	@Override
	public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
		if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiMethod method)
				|| method.getNameIdentifier() != element) {
			return null;
		}
		SlimService.MethodAnalysis a = Psi.analysis(method);
		if (a == null || a.assessment().status() == Status.UNDER_CAP) {
			return null;
		}
		Status s = a.assessment().status();
		Icon icon = s == Status.NOT_SLIMMABLE || s == Status.SLIMMABLE_BUT_LOCKED ? REDESIGN : SLIMMABLE;
		String tip = SlimInspection.message(a) + " (click to show or hide the proposal)";
		GutterIconNavigationHandler<PsiElement> click = (e, elt) -> {
			Editor editor = FileEditorManager.getInstance(elt.getProject()).getSelectedTextEditor();
			PsiMethod m = Psi.methodAt(elt);
			if (editor != null && m != null) {
				SlimOverlay.toggle(elt.getProject(), editor, m);
			}
		};
		return new LineMarkerInfo<>(element, element.getTextRange(), icon, e -> tip, click, GutterIconRenderer.Alignment.RIGHT,
				() -> "SinewSlim");
	}
}
