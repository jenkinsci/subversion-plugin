/*
 * The MIT License
 *
 * Copyright (c) 2010, Manufacture Francaise des Pneumatiques Michelin, Romain Seguy
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

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.scm.SubversionSCM;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * Defines a new {@link ParameterDefinition} to be displayed at the top of the
 * configuration page of {@link AbstractProject}s.
 * 
 * <p>When used, this parameter will request the user to select a Subversion tag
 * at build-time by displaying a drop-down list. See
 * {@link ListSubversionTagsParameterValue}.</p>
 * 
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class ListSubversionTagsParameterDefinition extends ParameterDefinition implements Comparable<ListSubversionTagsParameterDefinition> {

  /**
   * The Subversion repository which contains the tags to be listed.
   */
  private final String tagsDir;
  /**
   * We use a UUID to uniquely identify each use of this parameter: We need this
   * to find the project using this parameter in the getTags() method (which is
   * called before the build takes place).
   */
  private final UUID uuid;

  @DataBoundConstructor
  public ListSubversionTagsParameterDefinition(String name, String tagsDir, String uuid) {
    super(name, ResourceBundleHolder.get(ListSubversionTagsParameterDefinition.class).format("TagDescription"));
    this.tagsDir = Util.removeTrailingSlash(tagsDir);

    if(uuid == null || uuid.length() == 0) {
      this.uuid = UUID.randomUUID();
    }
    else {
      this.uuid = UUID.fromString(uuid);
    }
  }

  // This method is invoked from a GET or POST HTTP request
  @Override
  public ParameterValue createValue(StaplerRequest req) {
    String[] values = req.getParameterValues(getName());
    if(values == null || values.length != 1) {
      // the parameter is mandatory, the build has to fail if it's not there (we
      // can't assume a default value)
      return null;
    }
    else {
      return new ListSubversionTagsParameterValue(getName(), getTagsDir(), values[0]);
    }
  }

  // This method is invoked when the user clicks on the "Build" button of Hudon's GUI
  @Override
  public ParameterValue createValue(StaplerRequest req, JSONObject formData) {
    ListSubversionTagsParameterValue value = req.bindJSON(ListSubversionTagsParameterValue.class, formData);
    value.setTagsDir(getTagsDir());
    // here, we could have checked for the value of the "tag" attribute of the
    // parameter value, but it's of no use because if we return null the build
    // still goes on...
    return value;
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  /**
   * Returns a list of Subversion dirs to be displayed in
   * {@code ListSubversionTagsParameterDefinition/index.jelly}.
   *
   * <p>This method plainly reuses settings that must have been preivously
   * defined when configuring the Subversion SCM.</p>
   *
   * <p>This method never returns {@code null}. In case an error happens, the
   * returned list contains an error message surrounded by &lt; and &gt;.</p>
   */
  public List<String> getTags() {
    AbstractProject context = null;
    List<AbstractProject> jobs = Hudson.getInstance().getItems(AbstractProject.class);

    // which project is this parameter bound to? (I should take time to move
    // this code to Hudson core one day)
    for(AbstractProject project : jobs) {
      ParametersDefinitionProperty property = (ParametersDefinitionProperty) project.getProperty(ParametersDefinitionProperty.class);
      if(property != null) {
        List<ParameterDefinition> parameterDefinitions = property.getParameterDefinitions();
        if(parameterDefinitions != null) {
          for(ParameterDefinition pd : parameterDefinitions) {
            if(pd instanceof ListSubversionTagsParameterDefinition && ((ListSubversionTagsParameterDefinition) pd).compareTo(this) == 0) {
              context = project;
              break;
            }
          }
        }
      }
    }

    SimpleSVNDirEntryHandler dirEntryHandler = new SimpleSVNDirEntryHandler();

    try {
      ISVNAuthenticationProvider authProvider = getDescriptor().createAuthenticationProvider(context);

      ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager();
      authManager.setAuthenticationProvider(authProvider);

      SVNURL repoURL = SVNURL.parseURIDecoded(getTagsDir());

      SVNLogClient logClient = new SVNLogClient(authManager, null);
      logClient.doList(repoURL, SVNRevision.HEAD, SVNRevision.HEAD, false, false, dirEntryHandler);
    }
    catch(SVNException e) {
      // logs are not translated (IMO, this is a bad idea to translate logs)
      LOGGER.log(Level.SEVERE, "An SVN exception occurred while listing the directory entries at " + getTagsDir(), e);
      return new ArrayList() {{
        add("&lt;" + ResourceBundleHolder.get(ListSubversionTagsParameterDefinition.class).format("SVNException") + "&gt;");
      }};
    }

    List<String> dirs = dirEntryHandler.getDirs();

    // SVNKit's doList() method returns also the parent dir, so we need to remove it
    if(dirs != null) {
      removeParentDir(dirs);
    }
    else {
      LOGGER.log(Level.INFO, "No directory entries were found for the following SVN repository: {0}", getTagsDir());
      return new ArrayList() {{
        add("&lt;" + ResourceBundleHolder.get(ListSubversionTagsParameterDefinition.class).format("NoDirectoryEntriesFound") + "&gt;");
      }};
    }

    return dirs;
  }

  public String getTagsDir() {
    return tagsDir;
  }

  /**
   * Removes the parent directory (that is, the tags directory) from a list of
   * directories.
   */
  protected void removeParentDir(List<String> dirs) {
    List<String> dirsToRemove = new ArrayList<String>();
    for(String dir : dirs) {
      if(getTagsDir().endsWith(dir)) {
        dirsToRemove.add(dir);
      }
    }
    dirs.removeAll(dirsToRemove);
  }

  public int compareTo(ListSubversionTagsParameterDefinition pd) {
    if(pd.uuid.equals(uuid)) {
      return 0;
    }
    return -1;
  }

  @Extension
  public static class DescriptorImpl extends ParameterDescriptor {

    // we reuse as much as possible settings defined at the SCM level
    private SubversionSCM.DescriptorImpl scmDescriptor;

    public ISVNAuthenticationProvider createAuthenticationProvider(AbstractProject context) {
      return getSubversionSCMDescriptor().createAuthenticationProvider(context);
    }

    public FormValidation doCheckTagsDir(StaplerRequest req, @AncestorInPath AbstractProject context, @QueryParameter String value) {
      return getSubversionSCMDescriptor().doCheckRemote(req, context, value);
    }

    @Override
    public String getDisplayName() {
      return ResourceBundleHolder.get(ListSubversionTagsParameterDefinition.class).format("DisplayName");
    }

    /**
     * Returns the descriptor of {@link SubversionSCM}.
     */
    public SubversionSCM.DescriptorImpl getSubversionSCMDescriptor() {
      if(scmDescriptor == null) {
         scmDescriptor = (SubversionSCM.DescriptorImpl) Hudson.getInstance().getDescriptor(SubversionSCM.class);
      }
      return scmDescriptor;
    }

  }

  private final static Logger LOGGER = Logger.getLogger(ListSubversionTagsParameterDefinition.class.getName());

}
