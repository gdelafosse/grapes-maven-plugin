package org.axway.grapes.maven.report;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.axway.grapes.commons.datamodel.Module;
import org.axway.grapes.commons.utils.FileUtils;
import org.axway.grapes.commons.utils.JsonUtils;
import org.axway.grapes.maven.GrapesMavenPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Module Aggregator
 *
 * <p>Aggregates the modules of the sub-maven projects to create a single root module file.</p>
 *
 * @author jdcoffre
 */
public class ModuleAggregator {


    private final List<MavenProject> reactorProjects;
    
    private final File workingFolder;

    public ModuleAggregator(final List<MavenProject> reactorProjects) {
        this.workingFolder = GrapesMavenPlugin.getGrapesPluginWorkingFolder(reactorProjects.get(0));
        this.reactorProjects = reactorProjects;
    }

    /**
     * Checks all the available reports and aggregates the existing ones
     *
     * @throws IOException
     * @throws MojoExecutionException
     */
    public void aggregate() throws IOException, MojoExecutionException {
        final Map<String, File> subModules = getSubModuleReports();

        for(Map.Entry<String,File> submoduleReports : subModules.entrySet()){
            final MavenProject parentProject = getParentProject(submoduleReports.getKey());

            if(parentProject != null){
                final Module subModule = JsonUtils.unserializeModule(FileUtils.read(submoduleReports.getValue()));
                final Boolean updated = updateParent(parentProject, subModule);

                // removes the children that are taken into accounts into parent reports
                if(updated){
                    final File subModuleFile = new File(workingFolder, GrapesMavenPlugin.getSubModuleFileName(submoduleReports.getKey()));
                    subModuleFile.delete();
                }
            }
        }
    }

    /**
     * Finds the parent report and update it with sub-modules information
     *
     * @param parentProject Module
     * @param subModule MavenProject
     * @return Boolean true if the update has been performed successfully
     * @throws MojoExecutionException
     * @throws IOException
     */
    private Boolean updateParent(final MavenProject parentProject, final Module subModule) throws MojoExecutionException, IOException {
        final File parentModuleFile = getModuleReportFile(parentProject);
        final Module rootModule = GrapesMavenPlugin.getModule(workingFolder, GrapesMavenPlugin.MODULE_JSON_FILE_NAME);

        // Parent is serialized into a temp file
        if(parentModuleFile.exists()){
            final String serializedParent = FileUtils.read(parentModuleFile);
            parentModuleFile.delete();

            final Module parentModule = JsonUtils.unserializeModule(serializedParent);
            parentModule.addSubmodule(subModule);
            FileUtils.serialize(workingFolder, JsonUtils.serialize(parentModule), parentModuleFile.getName());

            return true;
        }
        // parent is serialized into module.json file
        else if(contains(rootModule, parentProject)){
            final Module parentModule = getSubModule(rootModule, parentProject);
            parentModule.addSubmodule(subModule);

            final File rootModuleFile = new File(workingFolder, GrapesMavenPlugin.MODULE_JSON_FILE_NAME);
            rootModuleFile.delete();
            FileUtils.serialize(workingFolder, JsonUtils.serialize(rootModule), GrapesMavenPlugin.MODULE_JSON_FILE_NAME);

            return true;
        }

        return false;
    }

    /**
     * Returns report files
     *
     * @return Map<String,File>
     * @throws IOException
     * @throws MojoExecutionException
     */
    private Map<String, File> getSubModuleReports() throws IOException, MojoExecutionException {
        final Map<String, File> subModules = new HashMap<String, File>();

        for(MavenProject project: reactorProjects){
            final String fileName = GrapesMavenPlugin.getSubModuleFileName(project.getBasedir().getName());
            final File reportFile = new File(workingFolder, fileName);

            if(reportFile.exists()){
                subModules.put(project.getBasedir().getName(), reportFile);
            }
        }

        return subModules;
    }

    /**
     * Return the parent project matching the sub-module key
     *
     * @param subModuleKey String
     * @return MavenProject
     */
    private MavenProject getParentProject(final String subModuleKey) {
        for(MavenProject project: reactorProjects){
            for(String submodule: project.getModules()){
                if(submodule.equals(subModuleKey)
                        // in case of a <module>folder/subModuleName</module>
                        || submodule.endsWith(File.separator + subModuleKey)){
                    return project;
                }
            }
        }

        return null;
    }

    /**
     * Returns the report file of the corresponding project
     *
     * @param project MavenProject
     * @return File
     */
    private File getModuleReportFile(final MavenProject project) {
        if(project.equals(reactorProjects.get(0))){
            return new File(workingFolder, GrapesMavenPlugin.MODULE_JSON_FILE_NAME);
        }

        return new File(workingFolder, GrapesMavenPlugin.getSubModuleFileName(project.getBasedir().getName()));
    }

    private Module getSubModule(final Module rootModule, final MavenProject parentProject) {
        final String parentSubModuleName = GrapesTranslator.generateModuleName(parentProject);

        for(Module subModule: rootModule.getSubmodules()){
            if(subModule.getName().equals(parentSubModuleName)){
                return subModule;
            }

            final Module result = getSubModule(subModule,parentProject);
            if(result != null){
                return result;
            }
        }

        return null;
    }

    /**
     * Checks if a module has a project as sub module
     *
     * @param rootModule Module
     * @param parentProject MavenProject
     * @return boolean
     */
    private boolean contains(final Module rootModule, final MavenProject parentProject) {
        return getSubModule(rootModule, parentProject) != null;
    }

}
