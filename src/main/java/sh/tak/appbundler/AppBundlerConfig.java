package sh.tak.appbundler;

public class AppBundlerConfig {
  private final String bundlePath;
  private final String contentsPath;
  private final String javaPath;
  private final String resourcesPath;
  private final String macosPath;

  public AppBundlerConfig(
          String bundlePath,
          String contentsPath,
          String javaPath,
          String resourcesPath,
          String macosPath) {
    this.bundlePath = bundlePath;
    this.contentsPath = contentsPath;
    this.javaPath = javaPath;
    this.resourcesPath = resourcesPath;
    this.macosPath = macosPath;
  }

  public String getBundlePath() {
    return bundlePath;
  }

  public String getContentsPath() {
    return contentsPath;
  }

  public String getJavaPath() {
    return javaPath;
  }
  
  public String getResourcesPath() {
    return resourcesPath;
  }
  
  public String getMacOsPath() {
    return macosPath;
  }
}
