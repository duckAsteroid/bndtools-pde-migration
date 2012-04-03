package com.cs.bndtools.pde.migration.wizard;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressService;

import com.cs.bndtools.pde.migration.Activator;
import com.cs.bndtools.pde.migration.Migrator;

public class MigrationWizard extends Wizard {

	private List<IProject> projects;
	private MigrationSettingsPage settingsPage = new MigrationSettingsPage();

	public MigrationWizard(List<IProject> projects) {
		setWindowTitle("PDE to BND Project Migration Wizard");
		this.projects = projects;
	}

	@Override
	public void addPages() {
		// addPage(new SelectProjectsPage());
		addPage(settingsPage);
	}

	@Override
	public boolean performFinish() {
		IWorkbench wb = PlatformUI.getWorkbench();
		IProgressService ps = wb.getProgressService();
		final Migrator migrator = new Migrator();
		// migrator settings from settings page
		migrator.setVersionQualifierSubstitution(settingsPage.getVersionQualifierSubstitution());
		migrator.setRenamePdeResources(settingsPage.isRenamePDEResources());
		migrator.setCloneExportedPackages(settingsPage.isCloneExportedPackages());
		migrator.setCopyActivator(settingsPage.isCopyActivator());

		try {
			migrator.migrate(null, projects);
		} catch (CoreException e) {
			ErrorDialog.openError(getShell(), getWindowTitle(), "Error migrating projects", Activator.createError("Error migrating", e));
		}

		return migrator.isSuccess();
	}

}
