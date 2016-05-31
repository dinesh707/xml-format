package org.technicalsoftwareconfigurationmanagement.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.ardnimahc.maven.plugin.FormattingHelper;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * The XML Formatter is a plugin that is designed to be run
 * from the parent POM of a project, so that all XML files within the
 * project can be formatting using one formatting option (either spaces
 * or tabs).  This is due to the fact that when a big project is being
 * worked on by many different people, with each person using their own
 * preferred formatting style, the files become hard to read.
 *
 *
 * <p>The plugin contains two arrays in which you can specify which
 * files to include/exclude from the formatting. <strong> By default all XML
 * files are included, except those in the target folder.</strong>
 *
 * <p>To use this plugin, type <strong>one</strong> of the following at the command line:
 * <UL>
 *   <LI>mvn org.technicalsoftwareconfigurationmanagement.maven-plugin:tscm-maven-plugin:2.1-SNAPSHOT:xmlFormatter
 *   <LI>mvn org.technicalsoftwareconfigurationmanagement.maven-plugin:tscm-maven-plugin:xmlFormatter
 *   <LI>mvn tscm:xmlFormatter
 * </UL>
 *
 * <p>To format the files using tabs instead of spaces, add this onto the end of one of the above commands.
 * <UL>
 *   <LI>-DxmlFormatter.useTabs="true"
 *
 * <p>Developer's Note:  At the moment the code is setup to only work with
 * Java 1.6 or newer because of the use of transformations (JAXP which
 * was included in Java in version 1.6).
 *
 * @goal xmlFormatter
 **/
public class XmlFormatter extends AbstractMojo {

    /**
     * A flag used to tell the program to format with either <i>spaces</i>
     * or <i>tabs</i>.  By default, the formatter uses spaces.
     *
     * <UL>
     *   <LI><tt>true</tt> - tabs</LI>
     *   <LI><tt>false</tt> - spaces</LI>
     * <UL>
     *
     * <p>In configure this parameter to use tabs, use the following
     * at the command line:
     *     -DxmlFormatter.useTabs="true"
     *
     * @parameter expression="${xmlFormatter.useTabs}"
     *            default-value="false"
     **/
    @Parameter(defaultValue = "false")
    private boolean useTabs;

    @Parameter(defaultValue = "4")
    private int indentSize;

    /**
     * The base directory of the project.
     * @parameter expression="${basedir}"
     **/
    private File baseDirectory;

    /**
     * A set of file patterns that dictates which files should be
     * included in the formatting with each file pattern being relative
     * to the base directory.  <i>By default all xml files are included.</i>
     * This parameter is most easily configured in the parent pom file.
     * @parameter alias="includes"
     **/
    private String[] includes = {"**/*.xml"};

    /**
     * A set of file patterns that allow you to exclude certain
     * files/folders from the formatting.  <i>By default the target folder
     * is excluded from the formatting.</i>  This parameter is most easily
     * configured in the parent pom file.
     * @parameter alias="excludes"
     **/
    private String[] excludes = {"**/target/**"};

    /**
     * By default we have setup the exclude list to remove the target
     * folders. Setting any value including an empty array will
     * overide this functionality.  This parameter can be configured in the
     * POM file using the 'excludes' alias in the configuration option. Note
     * that all files are relative to the parent POM.
     *
     * @param excludes - String array of patterns or filenames to exclude
     *        from formatting.
     **/
    public void setExcludes(String[] excludes) {
        this.excludes = excludes;
    }

    /**
     * By default all XML files ending with .xml are included for formatting.
     * This parameter can be configured in the POM file using the 'includes'
     * alias in the configuration option.  Note that all files are
     * relative to the parent POM.
     *
     * @param includes - Default "**\/*.xml". Assigning a new value overrides
     *        the default settings.
     **/
    public void setIncludes(String[] includes) {
        this.includes = includes;
    }



    /**
     * Since parent pom execution causes multiple executions we
     * created this static map that contains the fully qualified file
     * names for all of the files we process and we check it before
     * processing a file and skip if we have already processed the file.
     * note that the execution method is called numerous times within
     * the same JVM (hence the static reference works but a local
     * reference might not work).
     **/
    private static Set<String> processedFileNames = new HashSet<String>();

    /**
     * Called automatically by each module in the project, including the parent
     * module.  All files will formatted with either <i>spaces</i> or <i>tabs</i>,
     * and will be written back to it's original location.
     *
     * @throws MojoExecutionException
     **/
    @Override
    public void execute() throws MojoExecutionException {

        if ((baseDirectory != null)
                && (getLog().isDebugEnabled())) {
            getLog().debug("[xml formatter] Base Directory:" + baseDirectory);
        }

        if (includes != null) {
            FormattingHelper helper = new FormattingHelper(getLog(), indentSize, useTabs);
            String[] filesToFormat = helper
                    .getIncludedFiles(baseDirectory, includes, excludes);

            if (getLog().isDebugEnabled()) {
                getLog().debug("[xml formatter] Format "
                        + filesToFormat.length
                        + " source files in "
                        + baseDirectory);
            }

            for (String include : filesToFormat) {
                try {

                    if (!processedFileNames.contains(baseDirectory + File.separator + include)) {
                        processedFileNames.add(baseDirectory + File.separator + include);
                        helper.format(new File(baseDirectory + File.separator + include));
                    }
                } catch(RuntimeException re) {
                    getLog().error("File <" + baseDirectory + File.separator + include
                            + "> failed to parse, skipping and moving on to the next file", re);
                }
            }
        }
    }



}
