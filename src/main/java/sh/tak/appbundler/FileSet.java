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

import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.SetBase;

/**
 * Object of this class represents a set of file resources
 * that can be specified by directory and exclude/include patterns.
 * <p/>
 * Created date: Jan 19, 2008
 *
 * @author Zhenya Nyden, Schwarmi Bamamoto
 */
public class FileSet extends SetBase {

  /**
   * 
   * Absolute or relative from the module's directory. For example, "src/main/bin" would select this
   * subdirectory of the project in which this dependency is defined.
   */
  @Parameter
  private String directory;
  
  public String getDirectory() {
    return directory;
  }
  
  public void setDirectory(String directory) {
    this.directory = directory;
  }
}
