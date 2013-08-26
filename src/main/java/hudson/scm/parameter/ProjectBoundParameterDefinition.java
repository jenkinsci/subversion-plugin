/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy, Jeff Blaisdell
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.scm.parameter;

import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;

import java.util.List;
import java.util.UUID;

public abstract class ProjectBoundParameterDefinition extends ParameterDefinition implements Comparable<ProjectBoundParameterDefinition> {
  /**
   * We use a UUID to uniquely identify each use of this parameter: We need this
   * to find the project using this parameter in the getTags() method (which is
   * called before the build takes place).
   */
  private final UUID uuid;

  public ProjectBoundParameterDefinition(String name, String description, String uuid) {
    super(name, description);

    if (uuid == null || uuid.length() == 0) {
      this.uuid = UUID.randomUUID();
    }
    else {
      this.uuid = UUID.fromString(uuid);
    }
  }

  public AbstractProject getProject() {
    List<AbstractProject> jobs = Hudson.getInstance().getItems(AbstractProject.class);

    // which project is this parameter bound to? (I should take time to move
    // this code to Hudson core one day)
    for (AbstractProject project : jobs) {
      @SuppressWarnings("unchecked")
      ParametersDefinitionProperty property = (ParametersDefinitionProperty) project.getProperty(ParametersDefinitionProperty.class);
      if (property == null)
        continue;

      List<ParameterDefinition> parameterDefinitions = property.getParameterDefinitions();
      if (parameterDefinitions == null)
        continue;

      for (ParameterDefinition pd : parameterDefinitions) {
        if (pd instanceof ProjectBoundParameterDefinition && ((ProjectBoundParameterDefinition) pd).compareTo(this) == 0) {
          return project;
        }
      }
    }

    return null;
  }

  public int compareTo(ProjectBoundParameterDefinition pd) {
    return uuid.compareTo(pd.uuid);
  }
}
