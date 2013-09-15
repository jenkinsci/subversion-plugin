/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy
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

package hudson.scm.parameter.listrevs;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.util.VariableResolver;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

/**
 * This class represents the actual {@link hudson.model.ParameterValue} for the
 * {@link ListRevisionsParameterDefinition} parameter.
 */
public class ListRevisionsParameterValue extends ParameterValue {

  @Exported(visibility=3) private String repositoryURL; // this att comes from ListRevisionsParameterDefinition
  @Exported(visibility=3) private Long revision;

  @DataBoundConstructor
  public ListRevisionsParameterValue(String name, String repositoryURL, Long revision) {
    super(name);
    this.repositoryURL = repositoryURL;
    this.revision = revision;
  }

  @Override
  public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
    env.put(getName(), getRevision().toString());
  }

  @Override
  public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
    return new VariableResolver<String>() {
      public String resolve(String name) {
        return ListRevisionsParameterValue.this.name.equals(name) ? getRevision().toString() : null;
      }
    };
  }

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long revision) {
    this.revision = revision;
  }

  public String getRepositoryURL() {
    return repositoryURL;
  }

  public void setRepositoryURL(String repositoryURL) {
    this.repositoryURL = repositoryURL;
  }

  @Override
  public String toString() {
      return "(ListRevisionsParameterValue) " + getName() + ": Repository URL='" + repositoryURL + "' Revision='" + revision + "'";
  }
}
