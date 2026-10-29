package net.officefloor.sinewslim.intellij;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiNameHelper;
import com.intellij.ui.components.JBTextField;

/**
 * The two names a piece that returns a record needs: the new method's and the record's. The one
 * place SinewSlim asks for more than a name, since the type is a name too.
 */
final class RecordNamesDialog extends DialogWrapper {

	private final Project project;
	private final JBTextField method = new JBTextField(24);
	private final JBTextField record = new JBTextField(24);
	private final String outputs;

	RecordNamesDialog(Project project, int lo, int hi, String outputs, String suggestedMethod, String suggestedRecord) {
		super(project);
		this.project = project;
		this.outputs = outputs;
		method.setText(suggestedMethod);
		record.setText(suggestedRecord);
		setTitle("SinewSlim: Extract Method Returning a Record");
		setOKButtonText("Extract");
		init();
		setTitle("SinewSlim: Extract Lines " + lo + "-" + hi);
	}

	String methodName() {
		return method.getText().trim();
	}

	String recordName() {
		return record.getText().trim();
	}

	@Override
	protected @Nullable JComponent createCenterPanel() {
		JPanel p = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 4, 4, 4);
		c.anchor = GridBagConstraints.WEST;
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		p.add(new JLabel("The piece returns " + outputs + " together, as a record."), c);
		c.gridwidth = 1;
		c.gridy = 1;
		p.add(new JLabel("Method name:"), c);
		c.gridx = 1;
		p.add(method, c);
		c.gridx = 0;
		c.gridy = 2;
		p.add(new JLabel("Record name:"), c);
		c.gridx = 1;
		p.add(record, c);
		return p;
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return method;
	}

	@Override
	protected @Nullable ValidationInfo doValidate() {
		PsiNameHelper names = PsiNameHelper.getInstance(project);
		if (!names.isIdentifier(methodName())) {
			return new ValidationInfo("Not a Java identifier", method);
		}
		if (!names.isIdentifier(recordName())) {
			return new ValidationInfo("Not a Java identifier", record);
		}
		return null;
	}
}
