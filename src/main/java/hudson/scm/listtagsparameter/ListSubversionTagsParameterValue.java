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

package hudson.scm.listtagsparameter;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.util.VariableResolver;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

/**
 * This class represents the actual {@link ParameterValue} for the
 * {@link ListSubversionTagsParameterDefinition} parameter.
 *
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class ListSubversionTagsParameterValue extends ParameterValue {

  private String tagsDir; // this att comes from ListSubversionTagsParameterDefinition
  private String tag;

  @DataBoundConstructor
  public ListSubversionTagsParameterValue(String name, String tagsDir, String tag) {
    super(name);
    this.tagsDir = tagsDir;
    this.tag = tag;
  }

  @Override
  public void buildEnvironment(Run<?,?> build, EnvVars env) {
    env.put(getName(), getTag());
  }

  @Override
  public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
    return new VariableResolver<String>() {
      public String resolve(String name) {
        return ListSubversionTagsParameterValue.this.name.equals(name) ? getTag() : null;
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ListSubversionTagsParameterValue)) return false;
    if (!super.equals(o)) return false;

    ListSubversionTagsParameterValue that = (ListSubversionTagsParameterValue) o;

    if (tag != null ? !tag.equals(that.tag) : that.tag != null) return false;
    if (tagsDir != null ? !tagsDir.equals(that.tagsDir) : that.tagsDir != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (tagsDir != null ? tagsDir.hashCode() : 0);
    result = 31 * result + (tag != null ? tag.hashCode() : 0);
    return result;
  }

  @Exported(visibility=3)
  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  @Exported(visibility=3)
  public String getTagsDir() {
    return tagsDir;
  }

  public void setTagsDir(String tagsDir) {
    this.tagsDir = tagsDir;
  }

  @Override
  public String toString() {
      return "(ListSubversionTagsParameterValue) " + getName() + ": Repository URL='" + tagsDir + "' Tag='" + tag + "'";
  }
}
