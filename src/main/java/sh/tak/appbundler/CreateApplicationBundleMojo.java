/*
 * Copyright 2016, InfotechSoft Inc.
 * Copyright 2014, Takashi AOKI, John Vasquez, Wolfgang Fahl, and other contributors.
 * Copyright 2001-2008 The Codehaus.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sh.tak.appbundler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;

import org.apache.commons.lang.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.archiver.dir.DirectoryArchiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.velocity.VelocityComponent;

import sh.tak.appbundler.logging.MojoLogChute;

/**
 * Package dependencies as an Application Bundle for Mac OS X.
 */
@Mojo(name = "bundle", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class CreateApplicationBundleMojo extends AbstractMojo {

  /**
   * The root where the generated classes are.
   */
  private static final String TARGET_CLASS_ROOT = "target" + File.separator + "classes";

  /**
   * Default JVM options passed to launcher
   */
  private static String[] defaultJvmOptions = { "-Dapple.laf.useScreenMenuBar=true" };

  /**
   * signals the Info.plit creator that a JRE is present.
   */
  private boolean embeddJre = false;

  /**
   * The Maven Project Object
   */
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  /**
   * The Maven Project Helper Object.
   */
  @Component
  private MavenProjectHelper projectHelper;

  /**
   * The Velocity Component.
   */
  @Component
  private VelocityComponent velocity;

  /**
   * Paths to be put on the classpath in addition to the projects dependencies. <br/>
   * <br/>
   * Might be useful to specify locations of dependencies in the provided scope that are not
   * distributed with the bundle but have a known location on the system. <br/>
   * <br/>
   *
   * @see http://jira.codehaus.org/browse/MOJO-874
   */
  @Parameter
  private List<String> additionalClasspath;

  /**
   * Additional resources (as a list of <code>FileSet</code> objects) that will be copied into the
   * build directory and included in the .dmg alongside with the application bundle.
   */
  @Parameter
  private List<FileSet> additionalResources;

  /**
   * Additional files to bundle inside the Resources/Java directory and include on the classpath.
   * <br/>
   * <br/>
   * These could include additional JARs or JNI libraries.
   */
  @Parameter
  private List<FileSet> additionalBundledClasspathResources;

  /**
   * The directory where the application bundle will be created.
   */
  @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}")
  private File buildDirectory;

  /**
   * The name of the Bundle. <br/>
   * <br/>
   * This is the name that is given to the application bundle; and it is also what will show up in
   * the application menu, dock etc.
   */
  @Parameter(defaultValue = "${project.name}", required = true)
  private String bundleName;

  /**
   * The location of the template for <code>Info.plist</code>. <br/>
   * <br/>
   *
   * Classpath is checked before the file system.
   */
  @Parameter(defaultValue = "sh/tak/appbundler/Info.plist.template")
  private String dictionaryFile;

  /**
   * The location of the generated disk image (.dmg) file. <br/>
   * <br/>
   * This property depends on the <code>generateDiskImageFile</code> property.
   */
  @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.dmg")
  private File diskImageFile;

  /**
   * If this is set to <code>true</code>, the generated disk image (.dmg) file will be
   * internet-enabled. <br/>
   * <br/>
   * The default is ${false}. This property depends on the <code>generateDiskImageFile</code>
   * property.
   *
   * This feature can only be executed in Mac OS X environments.
   */
  @Parameter(defaultValue = "false")
  private boolean diskImageInternetEnable;

  /**
   * Tells whether to generate the disk image (.dmg) file or not. <br/>
   * <br/>
   * This feature can only be executed in Mac OS X and Linux environments.
   */
  @Parameter(defaultValue = "false")
  private boolean generateDiskImageFile;

  /**
   * Tells whether to include a symbolic link to the generated disk image (.dmg) file or not. <br/>
   * <br/>
   * Relevant only if generateDiskImageFile is set.
   */
  @Parameter(defaultValue = "false")
  private boolean includeApplicationsSymlink;

  /**
   * The icon (.icns) file for the bundle.
   */
  @Parameter
  private String iconFile;

  /**
   * The name of the Java launcher, to execute when double-clicking the Application Bundle.
   */
  @Parameter(defaultValue = "JavaAppLauncher")
  private String javaLauncherName;

  /**
   * Options to the JVM, will be used as the value of <code>JVMOptions</code> in the
   * <code>Info.plist</code>.
   */
  @Parameter
  private List<String> jvmOptions;

  /**
   * The value for the <code>JVMVersion</code> key.
   */
  @Parameter(defaultValue = "1.6+")
  private String jvmVersion;

  /**
   * The main class to execute when double-clicking the Application Bundle.
   */
  @Parameter(property = "mainClass", required = true)
  private String mainClass;

  /**
   * The version of the project. <br/>
   * <br/>
   * Will be used as the value of the <code>CFBundleVersion</code> key.
   */
  @Parameter(defaultValue = "${project.version}")
  private String version;

  /**
   * The path to the working directory. <br/>
   * This can be inside or outside the app bundle. <br/>
   * To define a working directory <b>inside</b> the app bundle, use e.g. <code>$APP_ROOT</code>.
   */
  @Parameter(defaultValue = "$APP_ROOT")
  private String workingDirectory;

  /**
   * The path to the working directory. <br/>
   * This can be inside or outside the app bundle. <br/>
   * To define a working directory <b>inside</b> the app bundle, use e.g. <code>$APP_ROOT</code>.
   */
  @Parameter(defaultValue = "")
  private String jrePath;

  /**
   * Bundle project as a Mac OS X application bundle.
   *
   * @throws MojoExecutionException If an unexpected error occurs during packaging of the bundle.
   */
  public void execute() throws MojoExecutionException {

    // 1. Create and set up directories
    getLog().info("Creating and setting up the bundle directories");
    buildDirectory.mkdirs();

    File bundleDir = new File(buildDirectory, bundleName + ".app");
    bundleDir.mkdirs();

    File contentsDir = new File(bundleDir, "Contents");
    contentsDir.mkdirs();

    File resourcesDir = new File(contentsDir, "Resources");
    resourcesDir.mkdirs();

    File javaDirectory = new File(contentsDir, "Java");
    javaDirectory.mkdirs();

    File macOSDirectory = new File(contentsDir, "MacOS");
    macOSDirectory.mkdirs();

    // 2. Copy in the native java application stub
    getLog().info("Copying the native Java Application Stub");
    File launcher = new File(macOSDirectory, javaLauncherName);
    launcher.setExecutable(true);

    FileOutputStream launcherStream = null;
    try {
      launcherStream = new FileOutputStream(launcher);
    } catch (FileNotFoundException ex) {
      throw new MojoExecutionException("Could not copy file to directory " + launcher, ex);
    }

    InputStream launcherResourceStream = this.getClass().getResourceAsStream(javaLauncherName);
    try {
      IOUtil.copy(launcherResourceStream, launcherStream);
    } catch (IOException ex) {
      throw new MojoExecutionException(
              "Could not copy file " + javaLauncherName + " to directory " + macOSDirectory,
              ex);
    }

    // 3.Copy icon file to the bundle if specified
    if (iconFile != null) {
      File f = searchFile(iconFile);

      if (f != null && f.exists() && f.isFile()) {
        getLog().info("Copying the Icon File");
        try {
          FileUtils.copyFileToDirectory(f, resourcesDir);
        } catch (IOException ex) {
          throw new MojoExecutionException(
                  "Error copying file " + iconFile + " to " + resourcesDir,
                  ex);
        }
      }
    }

    // 4. Resolve and copy in all dependencies from the pom
    getLog().info("Copying dependencies");
    List<String> files = copyDependencies(javaDirectory);
    if (additionalBundledClasspathResources != null
            && !additionalBundledClasspathResources.isEmpty()) {
      files.addAll(copyFiles(javaDirectory, additionalBundledClasspathResources));
    }

    // 5. Check if JRE should be embedded. Check JRE path. Copy JRE
    if (jrePath != null) {
      File f = new File(jrePath);
      if (f.exists() && f.isDirectory()) {
        // Check if the source folder is a jdk-home
        File pluginsDirectory = new File(contentsDir, "PlugIns/JRE/Contents/Home/jre");
        pluginsDirectory.mkdirs();

        File sourceFolder = new File(jrePath, "Contents/Home");
        if (new File(jrePath, "Contents/Home/jre").exists()) {
          sourceFolder = new File(jrePath, "Contents/Home/jre");
        }

        try {
          getLog().info(
                  "Copying the JRE Folder from : [" + sourceFolder + "] to PlugIn folder: ["
                          + pluginsDirectory + "]");
          FileUtils.copyDirectoryStructure(sourceFolder, pluginsDirectory);
          File binFolder = new File(pluginsDirectory, "bin");
          // Setting execute permissions on executables in JRE
          for (String filename : binFolder.list()) {
            new File(binFolder, filename).setExecutable(true, false);
          }

          new File(pluginsDirectory, "lib/jspawnhelper").setExecutable(true, false);
          embeddJre = true;
        } catch (IOException ex) {
          throw new MojoExecutionException(
                  "Error copying folder " + f + " to " + pluginsDirectory,
                  ex);
        }
      } else {
        getLog().warn("JRE not found check jrePath setting in pom.xml");
      }
    }

    // 6. Create and write the Info.plist file
    getLog().info("Writing the Info.plist file");
    File infoPlist = new File(bundleDir, "Contents" + File.separator + "Info.plist");
    this.writeInfoPlist(infoPlist, files);

    // 7. Copy specified additional resources into the top level directory
    getLog().info("Copying additional resources");
    if (additionalResources != null && !additionalResources.isEmpty()) {
      copyFiles(buildDirectory, additionalResources);
    }

    // 7. Make the stub executable
    if (!SystemUtils.IS_OS_WINDOWS) {
      getLog().info("Making stub executable");
      Commandline chmod = new Commandline();
      try {
        chmod.setExecutable("chmod");
        chmod.createArg().setValue("755");
        chmod.createArg().setValue(launcher.getAbsolutePath());

        chmod.execute();
      } catch (CommandLineException e) {
        throw new MojoExecutionException("Error executing " + chmod + " ", e);
      }
    } else {
      getLog().warn("The stub was created without executable file permissions for UNIX systems");
    }

    // 8. Create the DMG file
    if (generateDiskImageFile) {
      if (SystemUtils.IS_OS_MAC_OSX) {
        getLog().info("Generating the Disk Image file");
        Commandline dmg = new Commandline();
        try {
          // user wants /Applications symlink in the resulting disk image
          if (includeApplicationsSymlink) {
            createApplicationsSymlink();
          }
          dmg.setExecutable("hdiutil");
          dmg.createArg().setValue("create");
          dmg.createArg().setValue("-srcfolder");
          dmg.createArg().setValue(buildDirectory.getAbsolutePath());
          dmg.createArg().setValue(diskImageFile.getAbsolutePath());

          try {
            dmg.execute().waitFor();
          } catch (InterruptedException ex) {
            throw new MojoExecutionException(
                    "Thread was interrupted while creating DMG " + diskImageFile,
                    ex);
          } finally {
            if (includeApplicationsSymlink) {
              removeApplicationsSymlink();
            }
          }
        } catch (CommandLineException ex) {
          throw new MojoExecutionException("Error creating disk image " + diskImageFile, ex);
        }

        if (diskImageInternetEnable) {
          getLog().info("Enabling the Disk Image file for internet");
          try {
            Commandline internetEnableCommand = new Commandline();

            internetEnableCommand.setExecutable("hdiutil");
            internetEnableCommand.createArg().setValue("internet-enable");
            internetEnableCommand.createArg().setValue("-yes");
            internetEnableCommand.createArg().setValue(diskImageFile.getAbsolutePath());

            internetEnableCommand.execute();
          } catch (CommandLineException ex) {
            throw new MojoExecutionException(
                    "Error internet enabling disk image: " + diskImageFile,
                    ex);
          }
        }
        projectHelper.attachArtifact(project, "dmg", null, diskImageFile);
      }
      if (SystemUtils.IS_OS_LINUX) {
        getLog().info("Generating the Disk Image file");
        Commandline linux_dmg = new Commandline();
        try {
          linux_dmg.setExecutable("genisoimage");
          linux_dmg.createArg().setValue("-V");
          linux_dmg.createArg().setValue(bundleName);
          linux_dmg.createArg().setValue("-D");
          linux_dmg.createArg().setValue("-R");
          linux_dmg.createArg().setValue("-apple");
          linux_dmg.createArg().setValue("-no-pad");
          linux_dmg.createArg().setValue("-o");
          linux_dmg.createArg().setValue(diskImageFile.getAbsolutePath());
          linux_dmg.createArg().setValue(buildDirectory.getAbsolutePath());

          try {
            linux_dmg.execute().waitFor();
          } catch (InterruptedException ex) {
            throw new MojoExecutionException(
                    "Thread was interrupted while creating DMG " + diskImageFile,
                    ex);
          }
        } catch (CommandLineException ex) {
          throw new MojoExecutionException(
                  "Error creating disk image " + diskImageFile + " genisoimage probably missing",
                  ex);
        }
        projectHelper.attachArtifact(project, "dmg", null, diskImageFile);

      } else {
        getLog().warn("Disk Image file cannot be generated in non Mac OS X and Linux environments");
      }
    }

    getLog().info("App Bundle generation finished");
  }

  /**
   * The bundle name is used in paths, so we need to clean it for unwanted characters, like ":" on
   * MS Windows.
   *
   * @param bundleName the "unclean" bundle name.
   * @return a clean bundle name
   */
  private String cleanBundleName(String bundleName) {
    return bundleName.replace(':', '-');
  }

  /**
   * Copy all dependencies into the $JAVAROOT directory
   *
   * @param javaDirectory where to put jar files
   * @return A list of file names added
   * @throws MojoExecutionException
   */
  private List<String> copyDependencies(File javaDirectory) throws MojoExecutionException {
    ArtifactRepositoryLayout layout = new DefaultRepositoryLayout();

    List<String> list = new ArrayList<String>();

    // First, copy the project's own artifact
    File artifactFile = project.getArtifact().getFile();
    list.add(layout.pathOf(project.getArtifact()));

    try {
      FileUtils.copyFile(
              artifactFile,
              new File(javaDirectory, layout.pathOf(project.getArtifact())));
    } catch (IOException ex) {
      throw new MojoExecutionException(
              "Could not copy artifact file " + artifactFile + " to " + javaDirectory,
              ex);
    }

    for (Artifact artifact : project.getArtifacts()) {
      File file = artifact.getFile();
      File dest = new File(javaDirectory, layout.pathOf(artifact));

      getLog().debug("Adding " + file);

      try {
        FileUtils.copyFile(file, dest);
      } catch (IOException ex) {
        throw new MojoExecutionException(
                "Error copying file " + file + " into " + javaDirectory,
                ex);
      }

      list.add(layout.pathOf(artifact));
    }

    return list;
  }

  /**
   * Writes an Info.plist file describing this bundle.
   *
   * @param infoPlist The file to write Info.plist contents to
   * @param files A list of file names of the jar files to add in $JAVAROOT
   * @throws MojoExecutionException
   */
  private void writeInfoPlist(File infoPlist, List<String> files) throws MojoExecutionException {
    Velocity.setProperty(Velocity.RUNTIME_LOG_LOGSYSTEM, new MojoLogChute(this));
    Velocity.setProperty("file.resource.loader.path", TARGET_CLASS_ROOT);

    try {
      Velocity.init();
    } catch (Exception ex) {
      throw new MojoExecutionException("Exception occured in initializing velocity", ex);
    }

    VelocityContext velocityContext = new VelocityContext();

    velocityContext.put("mainClass", mainClass);
    velocityContext.put("cfBundleExecutable", javaLauncherName);
    velocityContext.put("bundleName", cleanBundleName(bundleName));
    velocityContext.put("workingDirectory", workingDirectory);

    if (embeddJre) {
      velocityContext.put("jrePath", "JRE");
    } else {
      velocityContext.put("jrePath", "");
    }

    if (iconFile == null) {
      velocityContext.put("iconFile", "GenericJavaApp.icns");
    } else {
      File f = searchFile(iconFile);
      velocityContext.put(
              "iconFile",
              (f != null && f.exists() && f.isFile()) ? f.getName() : "GenericJavaApp.icns");
    }

    velocityContext.put("version", version);
    velocityContext.put("jvmVersion", jvmVersion);

    StringBuilder options = new StringBuilder();
    options.append("<array>").append("\n      ");

    for (String jvmOption : defaultJvmOptions) {
      options.append("      ").append("<string>").append(jvmOption).append("</string>").append(
              "\n");
    }

    options.append("      ").append("<string>").append("-Xdock:name=" + bundleName).append(
            "</string>").append("\n");

    if (jvmOptions != null) {
      for (String jvmOption : jvmOptions) {
        options.append("      ").append("<string>").append(jvmOption).append("</string>").append(
                "\n");
      }
    }

    options.append("    ").append("</array>");
    velocityContext.put("jvmOptions", options);

    StringBuilder jarFiles = new StringBuilder();
    jarFiles.append("<array>").append("\n");
    for (String file : files) {
      jarFiles.append("      ").append("<string>").append(file).append("</string>").append("\n");
    }

    if (additionalClasspath != null) {
      for (String pathElement : additionalClasspath) {
        jarFiles.append("      ").append("<string>").append(pathElement).append("</string>");
      }
    }
    jarFiles.append("    ").append("</array>");

    velocityContext.put("classpath", jarFiles.toString());
    try {
      File sourceInfoPlist = new File(TARGET_CLASS_ROOT, dictionaryFile);

      if (sourceInfoPlist.exists() && sourceInfoPlist.isFile()) {
        String encoding = detectEncoding(sourceInfoPlist);
        getLog().debug("Detected encoding " + encoding + " for dictionary file " + dictionaryFile);

        Writer writer = new OutputStreamWriter(new FileOutputStream(infoPlist), encoding);

        Template template = Velocity.getTemplate(dictionaryFile, encoding);
        template.merge(velocityContext, writer);

        writer.close();
      } else {
        Writer writer = new OutputStreamWriter(new FileOutputStream(infoPlist), "UTF-8");

        velocity.getEngine().mergeTemplate(dictionaryFile, "UTF-8", velocityContext, writer);

        writer.close();
      }
    } catch (IOException ex) {
      throw new MojoExecutionException("Could not write Info.plist to file " + infoPlist, ex);
    } catch (ParseErrorException ex) {
      throw new MojoExecutionException("Error parsing " + dictionaryFile, ex);
    } catch (ResourceNotFoundException ex) {
      throw new MojoExecutionException(
              "Could not find resource for template " + dictionaryFile,
              ex);
    } catch (MethodInvocationException ex) {
      throw new MojoExecutionException(
              "MethodInvocationException occured merging Info.plist template " + dictionaryFile,
              ex);
    } catch (Exception ex) {
      throw new MojoExecutionException(
              "Exception occured merging Info.plist template " + dictionaryFile,
              ex);
    }
  }

  private static String detectEncoding(File file) throws Exception {
    return XMLInputFactory.newInstance().createXMLStreamReader(
            new FileReader(file)).getCharacterEncodingScheme();
  }

  private List<String> copyFiles(File dest, List<FileSet> fileSets) throws MojoExecutionException {
    DirectoryArchiver copier = new DirectoryArchiver();
    copier.setDestFile(dest);
    for (FileSet fs : fileSets) {
      int dirMode = modeToInt(fs.getDirectoryMode());
      if (dirMode != -1) {
        copier.setDirectoryMode(dirMode);
      }
      int fileMode = modeToInt(fs.getFileMode());
      if (fileMode != -1) {
        copier.setFileMode(fileMode);
      }

      DefaultFileSet fileSet = new DefaultFileSet();
      File baseDirectory = new File(fs.getDirectory());
      if (!baseDirectory.isAbsolute()) {
        baseDirectory = new File(project.getBasedir(), baseDirectory.getPath());
      }
      fileSet.setDirectory(baseDirectory);

      String outDir = fs.getOutputDirectory();
      if (StringUtils.isNotBlank(outDir)) {
        if (!outDir.endsWith(File.separator)) {
          outDir = outDir + File.separator;
        }
        fileSet.setPrefix(outDir);
      }

      fileSet.setIncludingEmptyDirectories(false);
      fileSet.setIncludes(fs.getIncludesArray());
      fileSet.setExcludes(fs.getExcludesArray());
      fileSet.setUsingDefaultExcludes(fs.isUseDefaultExcludes());
      copier.addFileSet(fileSet);
    }
    Map<String, ArchiveEntry> copiedFilesMap = copier.getFiles();
    try {
      copier.createArchive();
    } catch (Exception e) {
      throw new MojoExecutionException("Error copying resources: " + e.getMessage(), e);
    }

    List<String> copiedFiles = new ArrayList<String>(copiedFilesMap.size());
    for (String fileName : copiedFilesMap.keySet()) {
      if (fileName != null && fileName.length() > 0) {
        copiedFiles.add(fileName);
      }
    }
    return copiedFiles;
  }

  private static int modeToInt(String mode) throws MojoExecutionException {
    if (mode == null || mode.trim().length() < 1) {
      return -1;
    }
    try {
      return Integer.parseInt(mode, 8);
    } catch (NumberFormatException e) {
      throw new MojoExecutionException("Unable to parse mode: \'" + mode + "\'.", e);
    }
  }

  private static File searchFile(String path) {
    File f = new File(path);

    if (f.exists()) {
      return f;
    }

    f = new File(TARGET_CLASS_ROOT, path);

    if (f.exists()) {
      return f;
    }

    return null;
  }

  private void createApplicationsSymlink() throws MojoExecutionException, CommandLineException {
    Commandline symlink = new Commandline();
    symlink.setExecutable("ln");
    symlink.createArg().setValue("-s");
    symlink.createArg().setValue("/Applications");
    symlink.createArg().setValue(buildDirectory.getAbsolutePath());
    try {
      symlink.execute().waitFor();
    } catch (InterruptedException ex) {
      throw new MojoExecutionException(
              "Error preparing bundle disk image while creating symlink" + diskImageFile,
              ex);
    }
  }

  private void removeApplicationsSymlink() throws MojoExecutionException, CommandLineException {
    Commandline remSymlink = new Commandline();
    String symlink = buildDirectory.getAbsolutePath() + "/Applications";
    if (!new File(symlink).exists()) {
      return;
    }
    remSymlink.setExecutable("rm");
    remSymlink.createArg().setValue(symlink);
    try {
      remSymlink.execute().waitFor();
    } catch (InterruptedException ex) {
      throw new MojoExecutionException(
              "Error cleaning up (while removing " + symlink
                      + " symlink.) Please check permissions for that symlink" + diskImageFile,
              ex);
    }
  }
}
