package com.cs.bndtools.pde.migration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.pde.core.plugin.IPlugin;
import org.eclipse.pde.core.plugin.IPluginBase;
import org.eclipse.pde.core.plugin.IPluginModel;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.PluginModelManager;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

import bndtools.editor.model.BndEditModel;
import bndtools.model.clauses.ExportedPackage;
import bndtools.model.clauses.VersionedClause;

public class Migrator {

	private static final String PDENATURE = "org.eclipse.pde.PluginNature";
	private static final String BNDNATURE = "bndtools.core.bndnature";
	private static final List<String> PDE_BUILDERS = Arrays.asList("org.eclipse.pde.ManifestBuilder", "org.eclipse.pde.SchemaBuilder");
	private boolean success = false;

	// settings
	private String qualifierSubstitution = null;
	private boolean renamePdeResources = true;
	private boolean cloneExportedPackages;
	private boolean copyActivator;

	/** PDE model manager */
	private PluginModelManager modelManager;

	// working variables

	public Migrator() {
		modelManager = PDECore.getDefault().getModelManager();
	}

	public void setVersionQualifierSubstitution(String sub) {
		this.qualifierSubstitution = sub;
	}

	public void setRenamePdeResources(boolean rename) {
		this.renamePdeResources = rename;
	}

	public void setCloneExportedPackages(boolean clone) {
		this.cloneExportedPackages = clone;
	}

	public void setCopyActivator(boolean copy) {
		this.copyActivator = copy;
	}

	public void migrate(IProgressMonitor pm, List<IProject> projects) throws CoreException {
		SubMonitor monitor = SubMonitor.convert(pm, "Converting " + projects.size() + " projects", projects.size());
		for (IProject project : projects) {
			Job job = new ProjectMigrationJob(project);
			job.schedule();
			monitor.worked(1);
		}
		monitor.done();
	}

	/**
	 * A class encapsulating the job of migrating one project
	 */
	private class ProjectMigrationJob extends Job {
		private IProject project;

		private IFile bndFile;
		private IDocument bndDoc;
		private BndEditModel bndEditModel;

		private IPluginModelBase pdeModel;
		private IJavaProject javaProject;

		public ProjectMigrationJob(IProject project) throws CoreException {
			super("Migrate " + project.getName());
			this.project = project;

		}

		@Override
		public IStatus run(IProgressMonitor iMonitor) {
			SubMonitor monitor = SubMonitor.convert(iMonitor, "Migrating project " + project.getName(), 50);
			try {
				monitor.subTask("Looking for PDE model");
				if (project.hasNature(PDENATURE)) {
					// get JDT project
					javaProject = JavaCore.create(project);
					if (!project.hasNature(BNDNATURE)) {
						monitor.subTask("Adding BND nature");
						addBndNature();
					}

					// Load data from PDE model
					monitor.subTask("Loading PDE model");
					pdeModel = modelManager.findModel(project);

					// load BND model
					monitor.subTask("Loading BND model");
					loadBndEditModel();
					monitor.worked(5); // Worked 5

					// make sure osgi.core is on the build path
					monitor.subTask("Adding core libraries to build path");
					addOSGiCoreToBuildPath();
					monitor.worked(10); // Worked 15

					// merge models
					monitor.subTask("Merging PDE and BND models");
					mergeModels(monitor.newChild(4)); // Worked 19

					// now populate the Private-Package section (using source
					// Java packages that are not exported)
					monitor.subTask("Populate private packages in BND model");
					calculatePrivatePackages();
					monitor.worked(5); // worked 25

					// store new BND model
					monitor.subTask("Saving BND model");
					bndEditModel.saveChangesTo(bndDoc);
					writeFully(bndDoc, bndFile, false);
					monitor.worked(5); // worked 30

					// remove PDE nature and builders
					monitor.subTask("Removing PDE nature and builders");
					removePDENature();
					monitor.worked(10); // worked 40
					
					// update classpath (remove PDE classpath container and re-order)
					monitor.subTask("Update project classpath");
					modifyClasspath(monitor); // worked 20

					// rename PDE resources
					if (renamePdeResources) {
						monitor.subTask("Renaming legacy PDE resources");
						moveFile(monitor, project.getFile("/META-INF/MANIFEST.MF"), ".MF", ".PDE");
						//monitor.worked(5); // worked 45
						//moveFile(monitor, project.getFile("plugin.xml"), "plugin", "_plugin");
						monitor.worked(10); // worked 50
					}
				}
			} catch (CoreException e) {
				e.printStackTrace();
			}
			monitor.done();
			return Status.OK_STATUS;
		}

		/**
		 * Load the BND edit model for the project (if any)
		 * 
		 * @param project
		 * @return
		 * @throws CoreException
		 */
		protected void loadBndEditModel() throws CoreException {
			bndFile = project.getFile("bnd.bnd");
			try {
				bndDoc = readFully(bndFile);
				bndEditModel = new BndEditModel();
				bndEditModel.loadFrom(bndDoc);
			} catch (IOException e) {
				throw Activator.createException("IOException loading bnd.bnd", e);
			}
		}

		private void addOSGiCoreToBuildPath() {
			VersionedClause osgiCore = new VersionedClause("osgi.core", new HashMap<String, String>(0));
			List<VersionedClause> buildPath = bndEditModel.getBuildPath();
			if (buildPath == null) {
				buildPath = new ArrayList<VersionedClause>();
			}
			if (!buildPath.contains(osgiCore)) {
				buildPath.add(osgiCore);
			}
			bndEditModel.setBuildPath(buildPath);
		}

		/**
		 * Takes the PDE model and the BND model and tries to reconcile
		 * 
		 * @param newChild
		 * @param pdeModel
		 * @param editModel
		 */
		private void mergeModels(SubMonitor monitor) {
			IPluginBase pde = pdeModel.getPluginBase();
			BundleDescription osgiDesc = pdeModel.getBundleDescription();
			if (osgiDesc != null) {
				// transfer OSGi only settings

				// Bundle-Description
				bndEditModel.genericSet(Constants.BUNDLE_DESCRIPTION, "Not available");

				// exported packages
				if (cloneExportedPackages) {
					List<ExportPackageDescription> exportPackages = Arrays.asList(osgiDesc.getExportPackages());
					for (ExportPackageDescription pkg : exportPackages) {
						ExportedPackage newExport = new ExportedPackage(pkg.getName(), getAttribs(pkg));
						bndEditModel.addExportedPackage(newExport);
					}
				}

				// bundle activator
				if (copyActivator && pdeModel instanceof IPluginModel) {
					IPlugin plugin = ((IPluginModel) pdeModel).getPlugin();
					bndEditModel.setBundleActivator(plugin.getClassName());
				}
			} else {
				// specific parts from PDE
			}

			// general parts from PDE model

			// Bundle-SymbolicName
			if (!project.getName().equals(pde.getId())) {
				// transfer symbolic name if different from project name - bnd
				// uses
				// project name by default
				bndEditModel.setBundleSymbolicName(pde.getId());
			}

			// Bundle-Version
			String version = pde.getVersion();
			if (qualifierSubstitution != null) {
				// e.g. swap 1.0.0.qualifier for 1.0.0.${tstamp}
				version = version.replace("qualifier", qualifierSubstitution);
			}
			bndEditModel.setBundleVersion(version);

			// Bundle-Vendor
			bndEditModel.genericSet(Constants.BUNDLE_VENDOR, pde.getProviderName());

			// Bundle-Name
			bndEditModel.genericSet(Constants.BUNDLE_NAME, pde.getName());

		}

		private void addBndNature() throws CoreException {
			IProjectDescription description = project.getDescription();
			List<String> natures = new ArrayList<String>(Arrays.asList(description.getNatureIds()));
			if (!natures.contains(BNDNATURE)) {
				natures.add(BNDNATURE);
				description.setNatureIds(natures.toArray(new String[0]));
				project.setDescription(description, null);
			}
		}

		private void modifyClasspath(SubMonitor monitor) throws JavaModelException {
			// BndContainerInitializer.updateProjectClasspath(javaProject);
			ArrayList<IClasspathEntry> classPathEntries = new ArrayList<IClasspathEntry>(Arrays.asList(javaProject.getRawClasspath()));
			Iterator<IClasspathEntry> cpIter = classPathEntries.iterator();
			IClasspathEntry srcEntry = null;
			while (cpIter.hasNext()) {
				IClasspathEntry entry = cpIter.next();
				if (entry.getPath().equals(new Path("org.eclipse.pde.core.requiredPlugins"))) {
					cpIter.remove();
				}
				if (entry.getPath().toString().contains("src") && entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					cpIter.remove();
					srcEntry = entry;
				}
			}
			if (srcEntry != null) {
				classPathEntries.add(srcEntry);
			}
			javaProject.setRawClasspath(classPathEntries.toArray(new IClasspathEntry[0]), monitor.newChild(1));
		}

		private void calculatePrivatePackages() throws JavaModelException {
			List<IPackageFragment> sourcePackages = getAllSourcePackages(javaProject);
			List<ExportedPackage> exportedPackages = bndEditModel.getExportedPackages();
			if (exportedPackages != null) {
				for (IPackageFragment srcPackage : sourcePackages) {
					if (!exported(srcPackage, exportedPackages)) {
						bndEditModel.addPrivatePackage(srcPackage.getElementName());
					}
				}
			}
		}

		private void removePDENature() throws CoreException {
			IProjectDescription description = project.getDescription();
			List<String> natures = new ArrayList<String>(Arrays.asList(description.getNatureIds()));
			natures.remove(PDENATURE);
			description.setNatureIds(natures.toArray(new String[0]));

			List<ICommand> buildCommands = new ArrayList<ICommand>(Arrays.asList(description.getBuildSpec()));
			Iterator<ICommand> iter = buildCommands.iterator();
			while (iter.hasNext()) {
				ICommand cmd = iter.next();
				if (PDE_BUILDERS.contains(cmd.getBuilderName())) {
					iter.remove();
				}
			}
			description.setBuildSpec(buildCommands.toArray(new ICommand[0]));
			project.setDescription(description, null);
		}
	}

	private static boolean exported(IPackageFragment srcPackage, List<ExportedPackage> exportedPackages) {
		for (ExportedPackage export : exportedPackages) {
			if (export.getName().equals(srcPackage.getElementName())) {
				return true;
			}
		}
		return false;
	}

	private static List<IPackageFragment> getAllSourcePackages(IJavaProject javaProject) throws JavaModelException {
		List<IPackageFragment> packages = new ArrayList<IPackageFragment>();
		List<IPackageFragmentRoot> roots = Arrays.asList(javaProject.getAllPackageFragmentRoots());
		for (IPackageFragmentRoot root : roots) {
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
				List<IJavaElement> children = Arrays.asList(root.getChildren());
				for (IJavaElement child : children) {
					if (child instanceof IPackageFragment) {
						IPackageFragment pkg = (IPackageFragment) child;
						if (pkg.containsJavaResources()) {
							packages.add(pkg);
						}
					}

				}

			}
		}
		return packages;
	}

	private static void moveFile(SubMonitor monitor, IFile srcFile, String search, String replace) throws CoreException {
		if (srcFile.exists()) {
			String filename = srcFile.getFullPath().toString();
			filename = filename.replace(search, replace);
			Path destination = new Path(filename);
			srcFile.move(destination, true, monitor.newChild(5));
		} else {
			monitor.worked(5);
		}
	}

	private static Map<String, String> getAttribs(ExportPackageDescription pkg) {
		Version v = pkg.getVersion();
		if (v != null && !Version.emptyVersion.equals(v)) {
			HashMap<String, String> result = new HashMap<String, String>(1);
			result.put(Constants.VERSION_ATTRIBUTE, v.toString());
			return result;
		}
		return Collections.emptyMap();
	}

	/**
	 * Was migration a complete success
	 * 
	 * @return
	 */
	public boolean isSuccess() {
		return success;
	}

	public static IDocument readFully(IFile file) throws CoreException, IOException {
		if (file.exists()) {
			InputStream stream = file.getContents();
			byte[] bytes = readFully(stream);

			String string = new String(bytes, file.getCharset());
			return new Document(string);
		}
		return null;
	}

	public static byte[] readFully(InputStream stream) throws IOException {
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();

			final byte[] buffer = new byte[1024];
			while (true) {
				int read = stream.read(buffer, 0, 1024);
				if (read == -1)
					break;
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		} finally {
			stream.close();
		}
	}

	public static void writeFully(IDocument document, IFile file, boolean createIfAbsent) throws CoreException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(document.get().getBytes());
		if (file.exists()) {
			file.setContents(inputStream, false, true, null);
		} else {
			if (createIfAbsent)
				file.create(inputStream, false, null);
			else
				throw new CoreException(new Status(IStatus.ERROR, Activator.PLUGIN_ID, 0, "File does not exist: " + file.getFullPath().toString(), null));
		}
	}
}
