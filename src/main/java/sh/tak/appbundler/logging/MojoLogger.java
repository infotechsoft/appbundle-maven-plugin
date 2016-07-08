package sh.tak.appbundler.logging;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.logging.Logger;

/**
 * Simple adapter for sending Plexus logs to Mojo plugin logger.
 */
public class MojoLogger implements Logger {
  private final Log log;

  public MojoLogger(Log log) {
    this.log = log;
  }

  public void debug(String message) {
    log.debug(message);
  }

  public void debug(String message, Throwable throwable) {
    log.debug(message, throwable);
  }

  public boolean isDebugEnabled() {
    return log.isDebugEnabled();
  }

  public void info(String message) {
    log.info(message);
  }

  public void info(String message, Throwable throwable) {
    log.info(message, throwable);
  }

  public boolean isInfoEnabled() {
    return log.isInfoEnabled();
  }

  public void warn(String message) {
    log.warn(message);
  }

  public void warn(String message, Throwable throwable) {
    log.warn(message, throwable);
  }

  public boolean isWarnEnabled() {
    return log.isWarnEnabled();
  }

  public void error(String message) {
    log.error(message);
  }

  public void error(String message, Throwable throwable) {
    log.error(message, throwable);
  }

  public boolean isErrorEnabled() {
    return log.isErrorEnabled();
  }

  public void fatalError(String message) {
    error(message);
  }

  public void fatalError(String message, Throwable throwable) {
    error(message, throwable);
  }

  public boolean isFatalErrorEnabled() {
    return isErrorEnabled();
  }

  public Logger getChildLogger(String name) {
    return this;
  }

  public int getThreshold() {
    if (log.isDebugEnabled()) {
      return LEVEL_DEBUG;
    } else if (log.isInfoEnabled()) {
      return LEVEL_INFO;
    } else if (log.isWarnEnabled()) {
      return LEVEL_WARN;
    } else {
      return LEVEL_ERROR;
    }
  }

  public String getName() {
    return "PlexusMojoLogger";
  }

}
