package sh.tak.appbundler;

import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;

public class ArtifactSet {

  /**
   * Sets the output directory to place artifacts relative to bundle 'Java' directory.
   */
  @Parameter
  private String outputDirectory;

  /**
   * Sets the mapping pattern for all dependencies included in this assembly.
   */
  @Parameter(defaultValue = "${artifact.artifactId}-${artifact.version}${dashClassifier?}."
          + "${artifact.extension}")
  private String outputFileNameMapping = "${artifact.artifactId}-${artifact.version}${dashClassifier?}."
          + "${artifact.extension}";

  /**
   * Determines whether the artifact produced during the current project's build should be included
   * in this dependency set.
   */
  @Parameter(defaultValue = "true")
  private boolean useProjectArtifact = true;

  /**
   * Used to specify list of patterns for selecting dependency artifacts to include
   */
  @Parameter
  private List<String> includes;

  /**
   * Used to specify list of patterns for excluding dependency artifacts
   */
  @Parameter
  private List<String> excludes;

  /**
   * When specified as true, any include/exclude patterns which aren't used to filter an actual
   * artifact during assembly creation will cause the build to fail with an error. This is meant to
   * highlight obsolete inclusions or exclusions, or else signal that the assembly descriptor is
   * incorrectly configured.
   */
  @Parameter(defaultValue = "false")
  private boolean useStrictFiltering = false;

  /**
   * Determines whether the include/exclude patterns in this dependency set will be applied to the
   * transitive path of a given artifact. If true, and the current artifact is a transitive
   * dependency brought in by another artifact which matches an inclusion or exclusion pattern, then
   * the current artifact has the same inclusion/exclusion logic applied to it as well.
   */
  @Parameter(defaultValue = "false")
  private boolean useTransitiveFiltering;

  public String getOutputDirectory() {
    return outputDirectory;
  }

  public void setOutputDirectory(String outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  public String getOutputFileNameMapping() {
    return outputFileNameMapping;
  }

  public void setOutputFileNameMapping(String outputFileNameMapping) {
    this.outputFileNameMapping = outputFileNameMapping;
  }

  public boolean isUseProjectArtifact() {
    return useProjectArtifact;
  }

  public void setUseProjectArtifact(boolean useProjectArtifact) {
    this.useProjectArtifact = useProjectArtifact;
  }

  public List<String> getIncludes() {
    return includes;
  }

  public void setIncludes(List<String> includes) {
    this.includes = includes;
  }

  public List<String> getExcludes() {
    return excludes;
  }

  public void setExcludes(List<String> excludes) {
    this.excludes = excludes;
  }

  public boolean isUseStrictFiltering() {
    return useStrictFiltering;
  }

  public void setUseStrictFiltering(boolean useStrictFiltering) {
    this.useStrictFiltering = useStrictFiltering;
  }

  public boolean isUseTransitiveFiltering() {
    return useTransitiveFiltering;
  }

  public void setUseTransitiveFiltering(boolean useTransitiveFiltering) {
    this.useTransitiveFiltering = useTransitiveFiltering;
  }
}
