package net.officefloor.sinewslim.intellij;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;

/** Between the IDE's tree and SinewSlim's analysis, which knows methods by the line of their name. */
final class Psi {

	private Psi() {
	}

	/** SinewSlim's analysis of a method, or null when the document has none for it. */
	static SlimService.MethodAnalysis analysis(Project project, Document document, PsiMethod method) {
		PsiIdentifier name = method.getNameIdentifier();
		if (name == null || method.getBody() == null) {
			return null;
		}
		int line = document.getLineNumber(name.getTextOffset()) + 1;
		return SlimService.get(project).analyse(document, path(document)).atNameLine(line);
	}

	static SlimService.MethodAnalysis analysis(PsiMethod method) {
		PsiFile file = method.getContainingFile();
		Document document = file == null ? null
				: com.intellij.psi.PsiDocumentManager.getInstance(method.getProject()).getDocument(file);
		return document == null ? null : analysis(method.getProject(), document, method);
	}

	/** The method a caret or element is in, its body included; null outside a method. */
	static PsiMethod methodAt(PsiElement element) {
		return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
	}

	static String path(Document document) {
		VirtualFile f = FileDocumentManager.getInstance().getFile(document);
		return f == null ? "Unsaved.java" : f.getPath();
	}
}
