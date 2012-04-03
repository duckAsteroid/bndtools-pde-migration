package com.cs.bndtools.pde.migration.wizard;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

public class SelectProjectsPage extends WizardPage {

	/**
	 * Create the wizard.
	 */
	public SelectProjectsPage() {
		super("wizardPage");
		setTitle("Select projects");
		setDescription("Choose the projects to perform migration on");
	}

	/**
	 * Create contents of the wizard.
	 * @param parent
	 */
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);

		setControl(container);
	}

}
