package com.cs.bndtools.pde.migration.popup.actions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import com.cs.bndtools.pde.migration.wizard.MigrationWizard;

public class MigrateAction implements IObjectActionDelegate {

	private Shell shell;
	private ISelection selection;
	
	/**
	 * Constructor for Action1.
	 */
	public MigrateAction() {
		super();
	}

	/**
	 * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
	 */
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		shell = targetPart.getSite().getShell();
	}

	/**
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		if (selection instanceof IStructuredSelection) {
			List<IProject> projects = new ArrayList<IProject>();
	        for (Iterator it = ((IStructuredSelection) selection).iterator(); it
	                .hasNext();) {
	            Object element = it.next();
	            IProject project = null;
	            if (element instanceof IProject) {
	                project = (IProject) element;
	            } else if (element instanceof IAdaptable) {
	                project = (IProject) ((IAdaptable) element)
	                        .getAdapter(IProject.class);
	            }
	            if (project != null) {
	                projects.add(project);
	            }
	        }
	        migrate(projects);
	    }
	}

	/**
	 * Perform actual migration of a single project as part of this action
	 * @param project
	 */
	private void migrate(List<IProject> projects) {
		WizardDialog dialog = new WizardDialog(shell, new MigrationWizard(projects));
		dialog.open();
	}

	/**
	 * @see IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		this.selection = selection;
	}

}
