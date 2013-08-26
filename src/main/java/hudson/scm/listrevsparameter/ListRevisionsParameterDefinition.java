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

package hudson.scm.listrevsparameter;

import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.scm.SubversionSCM;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Defines a new {@link hudson.model.ParameterDefinition} to be displayed at the top of the
 * configuration page of {@link hudson.model.AbstractProject}s.
 *
 * <p>When used, this parameter will request the user to select a Subversion revision
 * at build-time for specific directory in repository. See
 * {@link ListRevisionsParameterValue}.</p>
 */
public class ListRevisionsParameterDefinition extends ParameterDefinition implements Comparable<ListRevisionsParameterDefinition> {
  private static final long serialVersionUID = 1L;

  /**
   * The Subversion repository which contains the revision to be selected.
   */
  private final String revisionsDir;

  /**
   * We use a UUID to uniquely identify each use of this parameter: We need this
   * to find the project using this parameter in the getTags() method (which is
   * called before the build takes place).
   */
  private final UUID uuid;

  @DataBoundConstructor
  public ListRevisionsParameterDefinition(String name, String revisionsDir, String uuid) {
    super(name);
    this.revisionsDir = Util.removeTrailingSlash(revisionsDir);

    if (uuid == null || uuid.length() == 0) {
      this.uuid = UUID.randomUUID();
    }
    else {
      this.uuid = UUID.fromString(uuid);
    }
  }

  @Override
  public String getDescription() {
    List<Long> firstLastRevisions = getFirstLastRevisions();
    if (firstLastRevisions != null) {
      return ResourceBundleHolder.get(ListRevisionsParameterDefinition.class).format(
          "TagDescription", firstLastRevisions.get(0).toString(), firstLastRevisions.get(1).toString());
    } else {
      return ResourceBundleHolder.get(ListRevisionsParameterDefinition.class).format(
          "SVNException", getRevisionsDir());
    }
  }

  // This method is invoked from a GET or POST HTTP request
  @Override
  public ParameterValue createValue(StaplerRequest req) {
    String[] values = req.getParameterValues(getName());
    if (values == null || values.length != 1) {
        return this.getDefaultParameterValue();
    }
    else {
      return new ListRevisionsParameterValue(getName(), getRevisionsDir(), Long.valueOf(values[0]));
    }
  }

  // This method is invoked when the user clicks on the "Build" button of Hudon's GUI
  @Override
  public ParameterValue createValue(StaplerRequest req, JSONObject formData) {
    ListRevisionsParameterValue value = req.bindJSON(ListRevisionsParameterValue.class, formData);
    value.setRevisionsDir(getRevisionsDir());
    // here, we could have checked for the value of the "tag" attribute of the
    // parameter value, but it's of no use because if we return null the build
    // still goes on...
    return value;
  }

  @Override
  public ParameterValue getDefaultParameterValue() {
    return new ListRevisionsParameterValue(getName(), getRevisionsDir(), getLastRevision());
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  public Long getLastRevision() {
    return getFirstLastRevisions().get(1);
  }

  /**
   * Returns a list of Subversion dirs to be displayed in
   * {@code ListRevisionsParameterDefinition/index.jelly}.
   *
   * <p>This method plainly reuses settings that must have been previously
   * defined when configuring the Subversion SCM.</p>
   */
  public List<Long> getFirstLastRevisions() {
    AbstractProject context = null;
    List<AbstractProject> jobs = Hudson.getInstance().getItems(AbstractProject.class);

    // which project is this parameter bound to? (I should take time to move
    // this code to Hudson core one day)
    for(AbstractProject project : jobs) {
      @SuppressWarnings("unchecked")
      ParametersDefinitionProperty property = (ParametersDefinitionProperty) project.getProperty(ParametersDefinitionProperty.class);
      if(property != null) {
        List<ParameterDefinition> parameterDefinitions = property.getParameterDefinitions();
        if(parameterDefinitions != null) {
          for(ParameterDefinition pd : parameterDefinitions) {
            if(pd instanceof ListRevisionsParameterDefinition && ((ListRevisionsParameterDefinition) pd).compareTo(this) == 0) {
              context = project;
              break;
            }
          }
        }
      }
    }

    final List<Long> revisions = new ArrayList<Long>();

    try {
      ISVNAuthenticationProvider authProvider = getDescriptor().createAuthenticationProvider(context);
      ISVNAuthenticationManager authManager = SubversionSCM.createSvnAuthenticationManager(authProvider);
      SVNURL repoURL = SVNURL.parseURIEncoded(getRevisionsDir());

      SVNRepository repo = SVNRepositoryFactory.create(repoURL);
      repo.setAuthenticationManager(authManager);
      SVNLogClient logClient = new SVNLogClient(authManager, null);

      logClient.doLog(repoURL, new String[]{""}, SVNRevision.HEAD, SVNRevision.create(0), SVNRevision.HEAD, false, false, 1, new ISVNLogEntryHandler() {
        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
          revisions.add(logEntry.getRevision());
        }
      });
      logClient.doLog(repoURL, new String[]{""}, SVNRevision.HEAD, SVNRevision.HEAD, SVNRevision.create(0), false, false, 1, new ISVNLogEntryHandler() {
        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
          revisions.add(logEntry.getRevision());
        }
      });

      return revisions;
    }
    catch(SVNException e) {
      LOGGER.log(Level.SEVERE, "An SVN exception occurred while getting log at " + getRevisionsDir(), e);
      return null;
    }
  }

  public String getRevisionsDir() {
    return revisionsDir;
  }

  public int compareTo(ListRevisionsParameterDefinition pd) {
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

    public FormValidation doCheckRevisionsDir(StaplerRequest req, @AncestorInPath AbstractProject context, @QueryParameter String value) {
      return getSubversionSCMDescriptor().doCheckRemote(req, context, value);
    }

    @Override
    public String getDisplayName() {
      return ResourceBundleHolder.get(ListRevisionsParameterDefinition.class).format("DisplayName");
    }

    /**
     * Returns the descriptor of {@link hudson.scm.SubversionSCM}.
     */
    public SubversionSCM.DescriptorImpl getSubversionSCMDescriptor() {
      if(scmDescriptor == null) {
        scmDescriptor = (SubversionSCM.DescriptorImpl) Hudson.getInstance().getDescriptor(SubversionSCM.class);
      }
      return scmDescriptor;
    }
  }

  private final static Logger LOGGER = Logger.getLogger(ListRevisionsParameterDefinition.class.getName());
}
