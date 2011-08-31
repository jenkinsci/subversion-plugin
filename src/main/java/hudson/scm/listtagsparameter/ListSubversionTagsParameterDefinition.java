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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.sf.json.JSONObject;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.apache.commons.lang.StringUtils;

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
  private final String tagsFilter;
  private final boolean reverseByDate;
  private final boolean reverseByName;
  private final String defaultValue;
  private final String maxTags;
  private static final String SVN_BRANCHES = "branches";
  private static final String SVN_TAGS = "tags";
  private static final String SVN_TRUNK = "trunk";
  
  /**
   * We use a UUID to uniquely identify each use of this parameter: We need this
   * to find the project using this parameter in the getTags() method (which is
   * called before the build takes place).
   */
  private final UUID uuid;

  @DataBoundConstructor
  public ListSubversionTagsParameterDefinition(String name, String tagsDir, String tagsFilter, String defaultValue, String maxTags, boolean reverseByDate, boolean reverseByName, String uuid) {
    super(name, ResourceBundleHolder.get(ListSubversionTagsParameterDefinition.class).format("TagDescription"));
    this.tagsDir = Util.removeTrailingSlash(tagsDir);
    this.tagsFilter = tagsFilter;
    this.reverseByDate = reverseByDate;
    this.reverseByName = reverseByName;
    this.defaultValue = defaultValue;
    this.maxTags = maxTags;

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
        return this.getDefaultParameterValue(); 
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
  public ParameterValue getDefaultParameterValue() {
    if (StringUtils.isEmpty(this.defaultValue)) {
      return null;
    }
    return new ListSubversionTagsParameterValue(getName(), getTagsDir(), this.defaultValue);
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

    SimpleSVNDirEntryHandler dirEntryHandler = new SimpleSVNDirEntryHandler(tagsFilter);
    List<String> dirs = new ArrayList<String>();

    try {
      ISVNAuthenticationProvider authProvider = getDescriptor().createAuthenticationProvider(context);
      ISVNAuthenticationManager authManager = SubversionSCM.createSvnAuthenticationManager(authProvider);
      SVNURL repoURL = SVNURL.parseURIDecoded(getTagsDir());

      SVNRepository repo = SVNRepositoryFactory.create(repoURL);
      repo.setAuthenticationManager(authManager);
      SVNLogClient logClient = new SVNLogClient(authManager, null);
      
      if (isSVNRepositoryProjectRoot(repo)) {
        dirs = this.getSVNRootRepoDirectories(logClient, repoURL);
      } else {
        logClient.doList(repoURL, SVNRevision.HEAD, SVNRevision.HEAD, false, false, dirEntryHandler);
        dirs = dirEntryHandler.getDirs(isReverseByDate(), isReverseByName());
      }
    }
    catch(SVNException e) {
      // logs are not translated (IMO, this is a bad idea to translate logs)
      LOGGER.log(Level.SEVERE, "An SVN exception occurred while listing the directory entries at " + getTagsDir(), e);
      return new ArrayList() {{
        add("&lt;" + ResourceBundleHolder.get(ListSubversionTagsParameterDefinition.class).format("SVNException") + "&gt;");
      }};
    }

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
    
    // Conform list to the maxTags option.
    Integer max = (isInt(this.maxTags) ? Integer.parseInt(this.maxTags) : null);
    if((max != null) && (dirs.size() > max)) {
      dirs = dirs.subList(0, max);
    }    

    return dirs;
  }

  public String getTagsDir() {
    return tagsDir;
  }

  public String getTagsFilter() {
    return tagsFilter;
  }

  public boolean isReverseByDate() {
    return reverseByDate;
  }

  public boolean isReverseByName() {
    return reverseByName;
  }
  
  public String getDefaultValue() {
    return defaultValue;
  }

  public String getMaxTags() {
    return maxTags;
  }  

  /**
   * Checks to see if given repository contains a trunk, branches, and tags
   * directories.
   * 
   * @param repo Repository to check.
   * @return True if trunk, branches, and tags exist.
   */
  private boolean isSVNRepositoryProjectRoot(SVNRepository repo) {
    try {
      SVNDirEntry trunkEntry = repo.info(SVN_TRUNK, SVNRevision.HEAD.getNumber());
      SVNDirEntry branchesEntry = repo.info(SVN_BRANCHES, SVNRevision.HEAD.getNumber());
      SVNDirEntry tagsEntry = repo.info(SVN_TAGS, SVNRevision.HEAD.getNumber());

      if ((trunkEntry != null) && (branchesEntry != null) && (tagsEntry != null)) {
        return true;
      }
    } catch (SVNException e) {
      return false;
    }
    return false;
  }

  /**
   * Appends the target directory to all entries in a list. I.E. 1.2 -->
   * branches/1.2
   * 
   * @param targetDir The target directory to append.
   * @param dirs List of directory entries
   */
  private void appendTargetDir(String targetDir, List<String> dirs) {
    if ((targetDir != null) && (dirs != null) && (dirs.size() > 0)) {
      for (int i = 0; i < dirs.size(); i++) {
        dirs.set(i, targetDir + '/' + dirs.get(i));
      }
    }
  }
  
  private boolean isInt(String value) {
    boolean isInteger = false;
    try {
      Integer.parseInt(value);
      isInteger = true;
    } catch (NumberFormatException e) {
      isInteger = false;
    }
    return isInteger;
  }  

  /**
   * Returns a list of contents from the trunk, branches, and tags
   * directories.
   * 
   * @param logClient
   * @param repoURL
   * @return List of directories.
   * @throws SVNException
   */
  private List<String> getSVNRootRepoDirectories(SVNLogClient logClient, SVNURL repoURL) throws SVNException {
    // Get the branches repository contents
    List<String> dirs = null;
    SVNURL branchesRepo = repoURL.appendPath(SVN_BRANCHES, true);
    SimpleSVNDirEntryHandler branchesEntryHandler = new SimpleSVNDirEntryHandler(null);
    logClient.doList(branchesRepo, SVNRevision.HEAD, SVNRevision.HEAD, false, false, branchesEntryHandler);
    List<String> branches = branchesEntryHandler.getDirs(isReverseByDate(), isReverseByName());
    branches.remove(SVN_BRANCHES);
    appendTargetDir(SVN_BRANCHES, branches);

    // Get the tags repository contents
    SVNURL tagsRepo = repoURL.appendPath(SVN_TAGS, true);
    SimpleSVNDirEntryHandler tagsEntryHandler = new SimpleSVNDirEntryHandler(null);
    logClient.doList(tagsRepo, SVNRevision.HEAD, SVNRevision.HEAD, false, false, tagsEntryHandler);
    List<String> tags = tagsEntryHandler.getDirs(isReverseByDate(), isReverseByName());
    tags.remove(SVN_TAGS);
    appendTargetDir(SVN_TAGS, tags);

    // Merge trunk with the contents of branches and tags.
    dirs = new ArrayList<String>();
    dirs.add(SVN_TRUNK);

    if (branches != null) {
      dirs.addAll(branches);
    }

    if (tags != null) {
      dirs.addAll(tags);
    }

    // Filter out any unwanted repository locations.
    if (StringUtils.isNotBlank(tagsFilter)) {
      Pattern filterPattern = Pattern.compile(tagsFilter);

      if ((dirs != null) && (dirs.size() > 0) && (filterPattern != null)) {
        List<String> temp = new ArrayList<String>();
        for (String dir : dirs) {
          if (filterPattern.matcher(dir).matches()) {
            temp.add(dir);
          }
        }
        dirs = temp;
      }
    }

    return dirs;
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

    public FormValidation doCheckDefaultValue(StaplerRequest req, @AncestorInPath AbstractProject context, @QueryParameter String value) {
      return getSubversionSCMDescriptor().doCheckRemote(req, context, value);
    }

    public FormValidation doCheckTagsDir(StaplerRequest req, @AncestorInPath AbstractProject context, @QueryParameter String value) {
      return getSubversionSCMDescriptor().doCheckRemote(req, context, value);
    }

    public FormValidation doCheckTagsFilter(@QueryParameter String value) {
      if(value != null && value.length() == 0) {
        try {
          Pattern.compile(value);
        }
        catch(PatternSyntaxException pse) {
          FormValidation.error(ResourceBundleHolder.get(ListSubversionTagsParameterDefinition.class).format("NotValidRegex"));
        }
      }
      return FormValidation.ok();
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
