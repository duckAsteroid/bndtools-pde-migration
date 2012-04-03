package com.cs.bndtools.pde.migration.wizard;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

public class MigrationSettingsPage extends WizardPage {
	private Text txttstamp;
	private Button btnReplacequalifierIn;
	private Button btnRenameMetainfmanifestmf;
	private Button btnCopyExportedPackages;
	private Button btnCopyBundleactivator;

	/**
	 * Create the wizard.
	 */
	public MigrationSettingsPage() {
		super("wizardPage");
		setTitle("Migration Settings");
		setDescription("Configure options for the migration");
	}

	/**
	 * Create contents of the wizard.
	 * @param parent
	 */
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);

		setControl(container);
		container.setLayout(new FormLayout());
		
		btnReplacequalifierIn = new Button(container, SWT.CHECK);
		
		FormData fd_btnReplacequalifierIn = new FormData();
		fd_btnReplacequalifierIn.top = new FormAttachment(0, 10);
		fd_btnReplacequalifierIn.left = new FormAttachment(0, 10);
		btnReplacequalifierIn.setLayoutData(fd_btnReplacequalifierIn);
		btnReplacequalifierIn.setText("Replace 'qualifier' in Bundle-Version");
		
		final Label lblReplacement = new Label(container, SWT.NONE);
		lblReplacement.setEnabled(false);
		FormData fd_lblReplacement = new FormData();
		fd_lblReplacement.top = new FormAttachment(btnReplacequalifierIn, 6);
		fd_lblReplacement.left = new FormAttachment(btnReplacequalifierIn, 10, SWT.LEFT);
		lblReplacement.setLayoutData(fd_lblReplacement);
		lblReplacement.setText("Replacement:");
		
		txttstamp = new Text(container, SWT.BORDER);
		txttstamp.setEnabled(false);
		txttstamp.setText("${tstamp}");
		FormData fd_txttstamp = new FormData();
		fd_txttstamp.top = new FormAttachment(btnReplacequalifierIn, 6);
		fd_txttstamp.left = new FormAttachment(lblReplacement, 6);
		fd_txttstamp.right = new FormAttachment(100, -183);
		txttstamp.setLayoutData(fd_txttstamp);
		
		btnRenameMetainfmanifestmf = new Button(container, SWT.CHECK);
		btnRenameMetainfmanifestmf.setSelection(true);
		FormData fd_btnRenameMetainfmanifestmf = new FormData();
		fd_btnRenameMetainfmanifestmf.top = new FormAttachment(txttstamp, 6);
		fd_btnRenameMetainfmanifestmf.left = new FormAttachment(0, 10);
		btnRenameMetainfmanifestmf.setLayoutData(fd_btnRenameMetainfmanifestmf);
		btnRenameMetainfmanifestmf.setText("Rename PDE resources in project (plugin.xml && MANIFEST.MF)");
		
		btnCopyExportedPackages = new Button(container, SWT.CHECK);
		btnCopyExportedPackages.setSelection(true);
		FormData fd_btnCopyExportedPackages = new FormData();
		fd_btnCopyExportedPackages.top = new FormAttachment(btnRenameMetainfmanifestmf, 6);
		fd_btnCopyExportedPackages.left = new FormAttachment(btnReplacequalifierIn, 0, SWT.LEFT);
		btnCopyExportedPackages.setLayoutData(fd_btnCopyExportedPackages);
		btnCopyExportedPackages.setText("Copy exported packages");
		
		btnCopyBundleactivator = new Button(container, SWT.CHECK);
		btnCopyBundleactivator.setSelection(true);
		FormData fd_btnCopyBundleactivator = new FormData();
		fd_btnCopyBundleactivator.top = new FormAttachment(btnCopyExportedPackages, 6);
		fd_btnCopyBundleactivator.left = new FormAttachment(0, 10);
		btnCopyBundleactivator.setLayoutData(fd_btnCopyBundleactivator);
		btnCopyBundleactivator.setText("Copy Bundle-Activator");
		
		btnReplacequalifierIn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				lblReplacement.setEnabled(btnReplacequalifierIn.getSelection());
				txttstamp.setEnabled(btnReplacequalifierIn.getSelection());
			}
		});
	}
	
	public String getVersionQualifierSubstitution() {
		if (btnReplacequalifierIn.getSelection()) {
			return txttstamp.getText();
		}
		return null;
	}

	public boolean isRenamePDEResources() {
		return btnRenameMetainfmanifestmf.getSelection();
	}
	
	public boolean isCloneExportedPackages() {
		return btnCopyExportedPackages.getSelection();
	}
	
	public boolean isCopyActivator() {
		return btnCopyBundleactivator.getSelection();
	}
}
