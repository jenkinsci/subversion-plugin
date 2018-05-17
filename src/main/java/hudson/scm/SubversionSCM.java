/*
 * The MIT License
 * 
 * Copyright (c) 2004-2012, Sun Microsystems, Inc., Kohsuke Kawaguchi, Fulvio Cavarretta,
 * Jean-Baptiste Quenot, Luca Domenico Milanesio, Renaud Bruyeron, Stephen Connolly,
 * Tom Huybrechts, Yahoo! Inc., Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy, OHTAKE Tomohiro
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
package hudson.scm;

import static hudson.Util.fixEmptyAndTrim;
import static hudson.scm.PollingResult.BUILD_NOW;
import static hudson.scm.PollingResult.NO_CHANGES;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.cloudbees.plugins.credentials.domains.SchemeSpecification;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.model.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.UnsupportedCharsetException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.WeakHashMap;

import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.scm.UserProvidedCredential.AuthenticationManagerImpl;
import hudson.scm.subversion.CheckoutUpdater;
import hudson.scm.subversion.Messages;
import hudson.scm.subversion.SvnHelper;
import hudson.scm.subversion.UpdateUpdater;
import hudson.scm.subversion.UpdateWithRevertUpdater;
import hudson.scm.subversion.WorkspaceUpdater;
import hudson.scm.subversion.WorkspaceUpdater.UpdateTask;
import hudson.scm.subversion.WorkspaceUpdaterDescriptor;
import hudson.util.FormValidation;
import hudson.util.LogTaskListener;
import hudson.util.MultipartFormDataParser;
import hudson.util.Scrambler;
import hudson.util.Secret;
import hudson.util.TimeUnit2;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.ServletException;
import javax.xml.transform.stream.StreamResult;

import jenkins.scm.impl.subversion.RemotableSVNErrorMessage;
import net.sf.json.JSONObject;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Chmod;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.dav.http.DefaultHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.trilead.ssh2.DebugLogger;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.crypto.Base64;
import javax.annotation.Nonnull;
import jenkins.MasterToSlaveFileCallable;
import jenkins.security.Roles;
import jenkins.security.SlaveToMasterCallable;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Subversion SCM.
 *
 * <h2>Plugin Developer Notes</h2>
 * <p>
 * Plugins that interact with Subversion can use {@link DescriptorImpl#createAuthenticationProvider(AbstractProject)}
 * so that it can use the credentials (username, password, etc.) that the user entered for Hudson.
 * See the javadoc of this method for the precautions you need to take if you run Subversion operations
 * remotely on slaves.
 *
 * <h2>Implementation Notes</h2>
 * <p>
 * Because this instance refers to some other classes that are not necessarily
 * Java serializable (like {@link #browser}), remotable {@link FileCallable}s all
 * need to be declared as static inner classes.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("rawtypes")
public class SubversionSCM extends SCM implements Serializable {
    /**
     * the locations field is used to store all configured SVN locations (with
     * their local and remote part). Direct access to this field should be
     * avoided and the getLocations() method should be used instead. This is
     * needed to make importing of old hudson-configurations possible as
     * getLocations() will check if the modules field has been set and import
     * the data.
     *
     * @since 1.91
     */
    private ModuleLocation[] locations = new ModuleLocation[0];

    /**
     * Additional credentials to use when checking out svn:externals
     * @since 2.0
     */
    @CheckForNull
    private List<AdditionalCredentials> additionalCredentials;

    private final SubversionRepositoryBrowser browser;
    private String excludedRegions;
    private String includedRegions;
    private String excludedUsers;
    /**
     * Revision property names that are ignored for the sake of polling. Whitespace separated, possibly null.
     */
    private String excludedRevprop;
    private String excludedCommitMessages;

    private WorkspaceUpdater workspaceUpdater;

    // No longer in use but left for serialization compatibility.
    @Deprecated
    private String modules;

    // No longer used but left for serialization compatibility
    @Deprecated
    private Boolean useUpdate;
    @Deprecated
    private Boolean doRevert;

    private boolean ignoreDirPropChanges;
    private boolean filterChangelog;
    private boolean quietOperation;

    /**
     * A cache of the svn:externals (keyed by project).
     */
    private transient Map<Job, List<External>> projectExternalsCache;

    private transient boolean pollFromMaster = POLL_FROM_MASTER;

    /**
     * @deprecated as of 1.286
     */
    public SubversionSCM(String[] remoteLocations, String[] localLocations,
                         boolean useUpdate, SubversionRepositoryBrowser browser) {
        this(remoteLocations,localLocations, useUpdate, browser, null, null, null);
    }

    /**
     * @deprecated as of 1.311
     */
    public SubversionSCM(String[] remoteLocations, String[] localLocations,
                         boolean useUpdate, SubversionRepositoryBrowser browser, String excludedRegions) {
        this(ModuleLocation.parse(remoteLocations,localLocations,null,null), useUpdate, false, browser, excludedRegions, null, null, null);
    }

    /**
     * @deprecated as of 1.315
     */
     public SubversionSCM(String[] remoteLocations, String[] localLocations,
                         boolean useUpdate, SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers, String excludedRevprop) {
        this(ModuleLocation.parse(remoteLocations,localLocations,null,null), useUpdate, false, browser, excludedRegions, excludedUsers, excludedRevprop, null);
    }

   /**
     * @deprecated as of 1.315
     */
    public SubversionSCM(List<ModuleLocation> locations,
                         boolean useUpdate, SubversionRepositoryBrowser browser, String excludedRegions) {
        this(locations, useUpdate, false, browser, excludedRegions, null, null, null);
    }

    /**
     * @deprecated as of 1.324
     */
    public SubversionSCM(List<ModuleLocation> locations,
            boolean useUpdate, SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers, String excludedRevprop) {
        this(locations, useUpdate, false, browser, excludedRegions, excludedUsers, excludedRevprop, null);
    }

    /**
     * @deprecated as of 1.328
     */
    public SubversionSCM(List<ModuleLocation> locations,
            boolean useUpdate, SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers, String excludedRevprop, String excludedCommitMessages) {
    	this(locations, useUpdate, false, browser, excludedRegions, excludedUsers, excludedRevprop, excludedCommitMessages);
    }

    /**
     * @deprecated as of 1.xxx
     */
    public SubversionSCM(List<ModuleLocation> locations,
                         boolean useUpdate, boolean doRevert, SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers, String excludedRevprop, String excludedCommitMessages) {
        this(locations, useUpdate, doRevert, browser, excludedRegions, excludedUsers, excludedRevprop, excludedCommitMessages, null);
    }

    /**
     * @deprecated  as of 1.23
     */
    public SubversionSCM(List<ModuleLocation> locations,
                         boolean useUpdate, boolean doRevert, SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers, String excludedRevprop, String excludedCommitMessages,
                         String includedRegions) {
        this(locations, useUpdate?(doRevert?new UpdateWithRevertUpdater():new UpdateUpdater()):new CheckoutUpdater(),
                browser, excludedRegions, excludedUsers, excludedRevprop, excludedCommitMessages, includedRegions);
    }

    /**
     *
     * @deprecated as of ...
     */
    public SubversionSCM(List<ModuleLocation> locations, WorkspaceUpdater workspaceUpdater,
                         SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers, String excludedRevprop, String excludedCommitMessages,
                         String includedRegions) {
      this(locations, workspaceUpdater, browser, excludedRegions, excludedUsers, excludedRevprop, excludedCommitMessages, includedRegions, false);
    }

    /**
     *  @deprecated
     */
    public SubversionSCM(List<ModuleLocation> locations, WorkspaceUpdater workspaceUpdater,
            SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers, String excludedRevprop, String excludedCommitMessages,
            String includedRegions, boolean ignoreDirPropChanges) {
        this(locations, workspaceUpdater, browser, excludedRegions, excludedUsers, excludedRevprop, excludedCommitMessages, includedRegions, ignoreDirPropChanges, false, null);
    }

    /**
     *  @deprecated by quietOperation
     */
    public SubversionSCM(List<ModuleLocation> locations, WorkspaceUpdater workspaceUpdater,
            SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers,
            String excludedRevprop, String excludedCommitMessages,
            String includedRegions, boolean ignoreDirPropChanges, boolean filterChangelog,
            List<AdditionalCredentials> additionalCredentials) {
        this(locations, workspaceUpdater, browser, excludedRegions, excludedUsers, excludedRevprop, excludedCommitMessages, 
            includedRegions, ignoreDirPropChanges, filterChangelog, additionalCredentials, false);
    }

    @DataBoundConstructor
    public SubversionSCM(List<ModuleLocation> locations, WorkspaceUpdater workspaceUpdater,
                         SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers,
                         String excludedRevprop, String excludedCommitMessages,
                         String includedRegions, boolean ignoreDirPropChanges, boolean filterChangelog,
                         List<AdditionalCredentials> additionalCredentials, boolean quietOperation) {
        for (Iterator<ModuleLocation> itr = locations.iterator(); itr.hasNext(); ) {
            ModuleLocation ml = itr.next();
            String remote = Util.fixEmptyAndTrim(ml.remote);
            if (remote == null) {
                itr.remove();
            }
        }
        this.locations = locations.toArray(new ModuleLocation[locations.size()]);
        if (additionalCredentials == null) {
            this.additionalCredentials = null;
        } else {
            this.additionalCredentials = new ArrayList<AdditionalCredentials>(additionalCredentials);
        }

        this.workspaceUpdater = workspaceUpdater;
        this.browser = browser;
        this.excludedRegions = excludedRegions;
        this.excludedUsers = excludedUsers;
        this.excludedRevprop = excludedRevprop;
        this.excludedCommitMessages = excludedCommitMessages;
        this.includedRegions = includedRegions;
        this.ignoreDirPropChanges = ignoreDirPropChanges;
        this.filterChangelog = filterChangelog;
        this.quietOperation = quietOperation;
    }

    /**
     * Convenience constructor, especially during testing.
     */
    public SubversionSCM(String svnUrl) {
        this(svnUrl, null, ".");
    }

    /**
     * Convenience constructor, especially during testing.
     */
    public SubversionSCM(String svnUrl, String local) {
        this(svnUrl, null, local);
    }

    /**
     * Convenience constructor, especially during testing.
     */
    public SubversionSCM(String svnUrl, String credentialId, String local) {
        this(new String[]{svnUrl}, new String[]{credentialId}, new String[]{local});
    }

    /**
     * Convenience constructor, especially during testing.
     */
    public SubversionSCM(String[] svnUrls, String[] locals) {
        this(svnUrls, null, locals);
    }

    /**
     * Convenience constructor, especially during testing.
     */
    public SubversionSCM(String[] svnUrls, String[] credentialIds, String[] locals) {
        this(ModuleLocation.parse(svnUrls, credentialIds, locals, null,null,null), true, false, null, null, null, null, null);
    }

    /**
     * @deprecated
     *      as of 1.91. Use {@link #getLocations()} instead.
     */
    public String getModules() {
        return null;
    }

    /**
     * list of all configured svn locations
     *
     * @since 1.91
     */
    @Exported
    public ModuleLocation[] getLocations() {
    	return getLocations(null, null);
    }

    @Override public String getKey() {
        StringBuilder b = new StringBuilder("svn");
        for (ModuleLocation loc : getLocations()) {
            b.append(' ').append(loc.getURL());
        }
        return b.toString();
    }

    public List<AdditionalCredentials> getAdditionalCredentials() {
        List<AdditionalCredentials> result = new ArrayList<AdditionalCredentials>();
        if (additionalCredentials != null) {
            result.addAll(additionalCredentials);
        }
        return result;
    }

    @Exported
    public WorkspaceUpdater getWorkspaceUpdater() {
        if (workspaceUpdater!=null)
            return workspaceUpdater;

        // data must have been read from old configuration.
        if (useUpdate!=null && !useUpdate)
            return new CheckoutUpdater();
        if (doRevert!=null && doRevert)
            return new UpdateWithRevertUpdater();
        return new UpdateUpdater();
    }

    public void setWorkspaceUpdater(WorkspaceUpdater workspaceUpdater) {
        this.workspaceUpdater = workspaceUpdater;
    }

    /**
     * @since 1.252
     * @deprecated Use {@link #getLocations(EnvVars, Run)} for vars
     *             expansion to be performed on all env vars rather than just
     *             build parameters.
     */
    public ModuleLocation[] getLocations(AbstractBuild<?,?> build) {
        return getLocations(null, build);
    }

    /**
     * List of all configured svn locations, expanded according to all env vars
     * or, if none defined, according to only build parameters values.
     * Both may be defined, in which case the variables are combined.
     * @param env If non-null, variable expansions are performed against these vars
     * @param build If non-null, variable expansions are
     *              performed against the build parameters
     */
    public ModuleLocation[] getLocations(EnvVars env, Run<?,?> build) {
        // check if we've got a old location
        if (modules != null) {
            // import the old configuration
            List<ModuleLocation> oldLocations = new ArrayList<ModuleLocation>();
            StringTokenizer tokens = new StringTokenizer(modules);
            while (tokens.hasMoreTokens()) {
                // the remote (repository location)
                // the normalized name is always without the trailing '/'
                String remoteLoc = Util.removeTrailingSlash(tokens.nextToken());

                oldLocations.add(new ModuleLocation(remoteLoc, null));
            }

            locations = oldLocations.toArray(new ModuleLocation[oldLocations.size()]);
            modules = null;
        }

        if(env == null && build == null)
            return locations;

        ModuleLocation[] outLocations = new ModuleLocation[locations.length];
        EnvVars env2 = env != null ? new EnvVars(env) : new EnvVars();
        if (build instanceof AbstractBuild) {
            env2.putAll(((AbstractBuild<?,?>) build).getBuildVariables());
        }
        EnvVars.resolve(env2);
        for (int i = 0; i < outLocations.length; i++) {
            outLocations[i] = locations[i].getExpandedLocation(env2);
        }

        return outLocations;
    }

    /**
     * Get the list of every checked-out location. This differs from {@link #getLocations()}
     * which returns only the configured locations whereas this method returns the configured
     * locations + any svn:externals locations.
     */
    public ModuleLocation[] getProjectLocations(Job project) throws IOException {
        List<External> projectExternals = getExternals(project);

        ModuleLocation[] configuredLocations = getLocations();
        if (projectExternals.isEmpty()) {
            return configuredLocations;
        }

        List<ModuleLocation> allLocations = new ArrayList<ModuleLocation>(configuredLocations.length + projectExternals.size());
        allLocations.addAll(Arrays.asList(configuredLocations));

        for (External external : projectExternals) {
            allLocations.add(new ModuleLocation(external.url, external.path));
        }

        return allLocations.toArray(new ModuleLocation[allLocations.size()]);
    }

    private List<External> getExternals(Job context) throws IOException {
        Map<Job, List<External>> projectExternalsCache = getProjectExternalsCache();
        List<External> projectExternals;
        synchronized (projectExternalsCache) {
            projectExternals = projectExternalsCache.get(context);
        }

        if (projectExternals == null) {
            projectExternals = SvnExternalsFileManager.parseExternalsFile(context);

            synchronized (projectExternalsCache) {
                if (!projectExternalsCache.containsKey(context)) {
                    projectExternalsCache.put(context, projectExternals);
                }
            }
        }
        return projectExternals;
    }

    @Override
    @Exported
    public SubversionRepositoryBrowser getBrowser() {
        return browser;
    }

    @Exported
    public String getExcludedRegions() {
        return excludedRegions;
    }

    public String[] getExcludedRegionsNormalized() {
        return (excludedRegions == null || excludedRegions.trim().equals(""))
                ? null : excludedRegions.split("[\\r\\n]+");
    }

    private Pattern[] getExcludedRegionsPatterns() {
        String[] excluded = getExcludedRegionsNormalized();
        if (excluded != null) {
            Pattern[] patterns = new Pattern[excluded.length];

            int i = 0;
            for (String excludedRegion : excluded) {
                patterns[i++] = Pattern.compile(excludedRegion);
            }

            return patterns;
        }

        return new Pattern[0];
    }

    @Exported
    public String getIncludedRegions() {
        return includedRegions;
    }

    public String[] getIncludedRegionsNormalized() {
        return (includedRegions == null || includedRegions.trim().equals(""))
                ? null : includedRegions.split("[\\r\\n]+");
    }

    private Pattern[] getIncludedRegionsPatterns() {
        String[] included = getIncludedRegionsNormalized();
        if (included != null) {
            Pattern[] patterns = new Pattern[included.length];

            int i = 0;
            for (String includedRegion : included) {
                patterns[i++] = Pattern.compile(includedRegion);
            }

            return patterns;
        }

        return new Pattern[0];
    }

    @Exported
    public String getExcludedUsers() {
        return excludedUsers;
    }

    public Set<String> getExcludedUsersNormalized() {
        String s = fixEmptyAndTrim(excludedUsers);
        if (s==null)
            return Collections.emptySet();

        Set<String> users = new HashSet<String>();
        for (String user : s.split("[\\r\\n]+"))
            users.add(user.trim());
        return users;
    }

    @Exported
    public String getExcludedRevprop() {
        return excludedRevprop;
    }

    private String getExcludedRevpropNormalized() {
        String s = fixEmptyAndTrim(getExcludedRevprop());
        if (s!=null)        return s;
        return getDescriptor().getGlobalExcludedRevprop();
    }

    @Exported
    public String getExcludedCommitMessages() {
        return excludedCommitMessages;
    }

    public String[] getExcludedCommitMessagesNormalized() {
        String s = fixEmptyAndTrim(excludedCommitMessages);
        return s == null ? new String[0] : s.split("[\\r\\n]+");
    }

    private Pattern[] getExcludedCommitMessagesPatterns() {
        String[] excluded = getExcludedCommitMessagesNormalized();
        Pattern[] patterns = new Pattern[excluded.length];

        int i = 0;
        for (String excludedCommitMessage : excluded) {
            patterns[i++] = Pattern.compile(excludedCommitMessage);
        }

        return patterns;
    }

    @Exported
    public boolean isIgnoreDirPropChanges() {
      return ignoreDirPropChanges;
    }

    @Exported
    public boolean isFilterChangelog() {
      return filterChangelog;
    }

    @Exported
    public boolean isQuietOperation() {
      return quietOperation;
    }

    /**
     * Convenience method solely for testing.
     */
    public void setQuietOperation(boolean quietOperation) {
        this.quietOperation = quietOperation;
    }

    // TODO: 2.60+ Delete this override.
    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String,String> env) {
        buildEnvironment(build, env);
    }

    /**
     * TODO: 2.60+ - add @Override.
     * Sets the <tt>SVN_REVISION_n</tt> and <tt>SVN_URL_n</tt> environment variables during the build.
     */
    public void buildEnvironment(Run<?, ?> build, Map<String, String> env) {
        ModuleLocation[] svnLocations = getLocations(new EnvVars(env), build);

        try {
            Map<String,Long> revisions = parseSvnRevisionFile(build);
            Set<String> knownURLs = revisions.keySet();
            if(svnLocations.length==1) {
                // for backwards compatibility if there's only a single modulelocation, we also set
                // SVN_REVISION and SVN_URL without '_n'
                String url = svnLocations[0].getURL();
                Long rev = revisions.get(url);
                if(rev!=null) {
                    env.put("SVN_REVISION",rev.toString());
                    env.put("SVN_URL",url);
                } else if (!knownURLs.isEmpty()) {
                    LOGGER.log(WARNING, "no revision found corresponding to {0}; known: {1}", new Object[] {url, knownURLs});
                }
            }

            for(int i=0;i<svnLocations.length;i++) {
                String url = svnLocations[i].getURL();
                Long rev = revisions.get(url);
                if(rev!=null) {
                    env.put("SVN_REVISION_"+(i+1),rev.toString());
                    env.put("SVN_URL_"+(i+1),url);
                } else if (!knownURLs.isEmpty()) {
                    LOGGER.log(WARNING, "no revision found corresponding to {0}; known: {1}", new Object[] {url, knownURLs});
                }
            }

        } catch (IOException e) {
            LOGGER.log(WARNING, "error building environment variables", e);
        }
    }

    /**
     * Called after checkout/update has finished to compute the changelog.
     */
    private void calcChangeLog(Run<?,?> build, FilePath workspace, File changelogFile, SCMRevisionState baseline, TaskListener listener, Map<String, List<SubversionSCM.External>> externalsMap, EnvVars env) throws IOException, InterruptedException {
        if (baseline == null) {
            // nothing to compare against
            createEmptyChangeLog(changelogFile, listener, "log");
            return;
        }

        // some users reported that the file gets created with size 0. I suspect
        // maybe some XSLT engine doesn't close the stream properly.
        // so let's do it by ourselves to be really sure that the stream gets closed.
        OutputStream os = new BufferedOutputStream(new FileOutputStream(changelogFile));
        boolean created;
        try {
            created = new SubversionChangeLogBuilder(build, workspace, (SVNRevisionState) baseline, env, listener, this).run(externalsMap, new StreamResult(os));
        } finally {
            os.close();
        }
        if(!created)
            createEmptyChangeLog(changelogFile, listener, "log");
    }

    /**
     * Please consider using the non-static version {@link #parseSvnRevisionFile(Run)}!
     */
    /*package*/ static Map<String,Long> parseRevisionFile(Run<?,?> build) throws IOException {
        return parseRevisionFile(build,true,false);
    }

    /*package*/ Map<String,Long> parseSvnRevisionFile(Run<?,?> build) throws IOException {
        return parseRevisionFile(build);
    }

    /**
     * Reads the revision file of the specified build (or the closest, if the flag is so specified.)
     *
     * @param findClosest
     *      If true, this method will go back the build history until it finds a revision file.
     *      A build may not have a revision file for any number of reasons (such as failure, interruption, etc.)
     * @return
     *      map from {@link SvnInfo#url Subversion URL} to its revision.  If there is more than one, choose
     *      the one with the smallest revision number
     */
    /*package*/ static Map<String,Long> parseRevisionFile(Run<?,?> build, boolean findClosest, boolean prunePinnedExternals) throws IOException {
        Map<String,Long> revisions = new HashMap<String,Long>(); // module -> revision

        if (findClosest) {
            for (Run<?,?> b=build; b!=null; b=b.getPreviousBuild()) {
                if(getRevisionFile(b).exists()) {
                    build = b;
                    break;
                }
            }
        }

        {// read the revision file of the build
            File file = getRevisionFile(build);
            if(!file.exists())
                // nothing to compare against
                return revisions;

            BufferedReader br = new BufferedReader(new FileReader(file));
            try {
                String line;
                while((line=br.readLine())!=null) {
                	boolean isPinned = false;
                	int indexLast = line.length();
                	if (line.lastIndexOf("::p") == indexLast-3) {
                		isPinned = true;
                		indexLast -= 3;
                	}
                	int index = line.lastIndexOf('/');
                    if(index<0) {
                        continue;   // invalid line?
                    }
                    try {
                    	String url = line.substring(0, index);
                    	long revision = Long.parseLong(line.substring(index+1,indexLast));
                    	Long oldRevision = revisions.get(url);
                    	if (isPinned) {
                    		if (!prunePinnedExternals) {
                    			if (oldRevision == null)
                    				// If we're writing pinned, only write if there are no unpinned
                    				revisions.put(url, revision);
                    		}
                    	} else {
                    		// unpinned
                        	if (oldRevision == null || oldRevision > revision)
                        		// For unpinned, take minimum
                        		revisions.put(url, revision);
                    	}
                	} catch (NumberFormatException e) {
                	    // perhaps a corrupted line.
                	    LOGGER.log(WARNING, "Error parsing line " + line, e);
                	}
                }
            } finally {
                br.close();
            }
        }

        return revisions;
    }

    

    /**
     * Polling can happen on the master and does not require a workspace.
     */
    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override public void checkout(Run build, Launcher launcher, FilePath workspace, final TaskListener listener, File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
        EnvVars env = build.getEnvironment(listener);
        if (build instanceof AbstractBuild) {
            EnvVarsUtils.overrideAll(env, ((AbstractBuild) build).getBuildVariables());
        }

        Map<String, List<External>> externalsMap = checkout(build,workspace,listener,env);

        List<External> externalsForAll = new ArrayList<>();
        if (externalsMap != null) {
          for (String moduleLocationRemote : externalsMap.keySet()) {
            externalsForAll.addAll(externalsMap.get(moduleLocationRemote));
          }
        }

        // write out the revision file
        PrintWriter w = new PrintWriter(new FileOutputStream(getRevisionFile(build)));
        try {
            List<SvnInfoP> pList = workspace.act(new BuildRevisionMapTask(build, this, listener, externalsForAll, env));
            List<SvnInfo> revList= new ArrayList<SvnInfo>(pList.size());
            for (SvnInfoP p: pList) {
                if (p.pinned)
                    w.println( p.info.url +'/'+ p.info.revision + "::p");
                else
                    w.println( p.info.url +'/'+ p.info.revision);
                revList.add(p.info);
            }
            build.addAction(new SubversionTagAction(build,revList));
        } finally {
            w.close();
        }

        // write out the externals info
        SvnExternalsFileManager.writeExternalsFile(build.getParent(), externalsForAll);
        Map<Job, List<External>> projectExternalsCache = getProjectExternalsCache();
        synchronized (projectExternalsCache) {
            projectExternalsCache.put(build.getParent(), externalsForAll);
        }

        if (changelogFile != null) {
            calcChangeLog(build, workspace, changelogFile, baseline, listener, externalsMap, env);
        }
    }

    /**
     * Performs the checkout or update, depending on the configuration and workspace state.
     *
     * <p>
     * Use canonical path to avoid SVNKit/symlink problem as described in
     * https://wiki.svnkit.com/SVNKit_FAQ
     *
     * @return null
     *      if the operation failed. Otherwise the set of local workspace paths
     *      (relative to the workspace root) that has loaded due to svn:external.
     */
    private Map<String, List<External>> checkout(Run build, FilePath workspace, TaskListener listener, EnvVars env) throws IOException, InterruptedException {
        if (repositoryLocationsNoLongerExist(build, listener, env)) {
            Run lsb = build.getParent().getLastSuccessfulBuild();
            if (build instanceof AbstractBuild && lsb != null && build.getNumber()-lsb.getNumber()>10
            && build.getTimestamp().getTimeInMillis()-lsb.getTimestamp().getTimeInMillis() > TimeUnit2.DAYS.toMillis(1)) {
                // Disable this project if the location doesn't exist any more, see issue #763
                // but only do so if there was at least some successful build,
                // to make sure that initial configuration error won't disable the build. see issue #1567
                // finally, only disable a build if the failure persists for some time.
                // see http://www.nabble.com/Should-Hudson-have-an-option-for-a-content-fingerprint--td24022683.html

                listener.getLogger().println("One or more repository locations do not exist anymore for " + build.getParent().getName() + ", project will be disabled.");
                disableProject(((AbstractBuild) build).getProject(), listener);
            }
            return null;
        }

        Map<String, List<External>> externalsMap = new HashMap<>();

        Set<String> unauthenticatedRealms = new LinkedHashSet<String>();
        for (ModuleLocation location : getLocations(env, build)) {
            CheckOutTask checkOutTask =
                    new CheckOutTask(build, this, location, build.getTimestamp().getTime(), listener, env, quietOperation);
            List<External> externals = new ArrayList<External>();
            externals.addAll(workspace.act(checkOutTask));
            // save location <---> externals maps
            externalsMap.put(location.remote, externals);
            unauthenticatedRealms.addAll(checkOutTask.getUnauthenticatedRealms());
            // olamy: remove null check at it cause test failure
            // see https://github.com/jenkinsci/subversion-plugin/commit/de23a2b781b7b86f41319977ce4c11faee75179b#commitcomment-1551273
            /*if ( externalsFound != null ){
                externals.addAll(externalsFound);
            } else {
                externals.addAll( new ArrayList<External>( 0 ) );
            }*/
        }
        if (additionalCredentials != null) {
            for (AdditionalCredentials c : additionalCredentials) {
                unauthenticatedRealms.remove(c.getRealm());
            }
        }
        if (!unauthenticatedRealms.isEmpty()) {
            listener.getLogger().println("WARNING: The following realms could not be authenticated:");
            for (String realm : unauthenticatedRealms) {
                listener.getLogger().println(" * " + realm);
            }
            if (build == build.getParent().getLastBuild()) {
                if (additionalCredentials == null) {
                    additionalCredentials = new ArrayList<AdditionalCredentials>();
                }
                for (String realm : unauthenticatedRealms) {
                    additionalCredentials.add(new AdditionalCredentials(realm, null));
                }
                try {
                    listener.getLogger().println("Adding missing realms to configuration...");
                    build.getParent().save();
                    listener.getLogger().println("Updated project configuration saved.");
                } catch (IOException e) {
                    listener.getLogger().println("Could not update project configuration: " + e.getMessage());
                }
            }
        }

        return externalsMap;
    }

    private synchronized Map<Job, List<External>> getProjectExternalsCache() {
        if (projectExternalsCache == null) {
            projectExternalsCache = new WeakHashMap<Job, List<External>>();
        }

        return projectExternalsCache;
    }

    /**
     * Either run "svn co" or "svn up" equivalent.
     */
    private static class CheckOutTask extends UpdateTask implements FileCallable<List<External>> {
        private final UpdateTask task;

        CheckOutTask(Run<?, ?> build, SubversionSCM parent, ModuleLocation location, Date timestamp,
                            TaskListener listener, EnvVars env, boolean quietOperation) {
            this.authProvider = parent.createAuthenticationProvider(build.getParent(), location, listener);
            this.timestamp = timestamp;
            this.listener = listener;
            this.location = location;
            this.revisions = build.getAction(RevisionParameterAction.class);
            this.task = parent.getWorkspaceUpdater().createTask();
            this.quietOperation = quietOperation;
        }

        Set<String> getUnauthenticatedRealms() {
            if (authProvider instanceof CredentialsSVNAuthenticationProviderImpl) {
                return ((CredentialsSVNAuthenticationProviderImpl) authProvider).getUnauthenticatedRealms();
            }
            return Collections.emptySet();
        }

        @Override
        public List<External> invoke(File ws, VirtualChannel channel) throws IOException {
            clientManager = createClientManager(authProvider);
            manager = clientManager.getCore();
            this.ws = ws;
            try {
                List<External> externals = perform();

                checkClockOutOfSync();

                return externals;

            } catch (InterruptedException e) {
                throw (InterruptedIOException)new InterruptedIOException().initCause(e);
            } finally {
                clientManager.dispose();
            }
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            checker.check(this, Roles.SLAVE);
        }

        /**
         * This round-about way of executing the task ensures that the error-prone {@link #delegateTo(UpdateTask)} method
         * correctly copies everything.
         */
        @Override
        public List<External> perform() throws IOException, InterruptedException {
            return delegateTo(task);
        }

        private void checkClockOutOfSync() {
            try {
                SVNDirEntry dir = clientManager.createRepository(location.getSVNURL(), true).info("/", -1);
                if (dir != null) {// I don't think this can ever be null, but be defensive
                    if (dir.getDate() != null && dir.getDate().after(new Date())) // see http://www.nabble.com/NullPointerException-in-SVN-Checkout-Update-td21609781.html that reported this being null.
                    {
                        listener.getLogger().println(Messages.SubversionSCM_ClockOutOfSync());
                    }
                }
            } catch (SVNAuthenticationException e) {
                // if we don't have access to '/', ignore. error
                LOGGER.log(Level.FINE,"Failed to estimate the remote time stamp",e);
            } catch (SVNException e) {
                LOGGER.log(Level.INFO,"Failed to estimate the remote time stamp",e);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     *
     * @deprecated as of 1.40
     *      Use {@link #createClientManager(ISVNAuthenticationProvider)}
     */
    public static SVNClientManager createSvnClientManager(ISVNAuthenticationProvider authProvider) {
        return createClientManager(authProvider).getCore();
    }

    /**
     * Creates {@link SVNClientManager}.
     *
     * <p>
     * This method must be executed on the slave where svn operations are performed.
     *
     * @param authProvider
     *      The value obtained from {@link #createAuthenticationProvider(Job,ModuleLocation, TaskListener)}.
     *      If the operation runs on slaves,
     *      (and properly remoted, if the svn operations run on slaves.)
     */
    public static SvnClientManager createClientManager(ISVNAuthenticationProvider authProvider) {
        ISVNAuthenticationManager sam = createSvnAuthenticationManager(authProvider);
        return new SvnClientManager(SVNClientManager.newInstance(createDefaultSVNOptions(), sam));
    }

    /**
     * Creates the {@link DefaultSVNOptions}.
     *
     * @return the {@link DefaultSVNOptions}.
     */
    public static DefaultSVNOptions createDefaultSVNOptions() {
        DefaultSVNOptions defaultOptions = SVNWCUtil.createDefaultOptions(true);
        DescriptorImpl descriptor = descriptor();
        if (defaultOptions != null && descriptor != null) { // TODO JENKINS-48543 bad design
            defaultOptions.setAuthStorageEnabled(descriptor.isStoreAuthToDisk());
        }
        return defaultOptions;
    }

    public static ISVNAuthenticationManager createSvnAuthenticationManager(ISVNAuthenticationProvider authProvider) {
        File configDir;
        if (CONFIG_DIR!=null)
            configDir = new File(CONFIG_DIR);
        else
            configDir = SVNWCUtil.getDefaultConfigurationDirectory();

        ISVNAuthenticationManager sam = new SVNAuthenticationManager(configDir, null, null);
        sam.setAuthenticationProvider(authProvider);
        SVNAuthStoreHandlerImpl.install(sam);
        return sam;
    }

    /**
     * @deprecated as of 2.0
     *      Use {@link #createClientManager(AbstractProject)}
     *
     */
    public static SVNClientManager createSvnClientManager(AbstractProject context) {
        return createClientManager(context).getCore();
    }

    /**
     * Creates {@link SVNClientManager} for code running on the master.
     * <p>
     * CAUTION: this code only works when invoked on master. On slaves, use
     * {@link #createSvnClientManager(ISVNAuthenticationProvider)} and get {@link ISVNAuthenticationProvider}
     * from the master via remoting.
     */
    public static SvnClientManager createClientManager(AbstractProject context) {
        return new SvnClientManager(createSvnClientManager(descriptor().createAuthenticationProvider(context)));
    }

    /**
     * Creates {@link ISVNAuthenticationProvider}.
     * This method must be invoked on the master, but the returned object is remotable.
     *
     * <p>
     * Therefore, to access {@link ISVNAuthenticationProvider}, you need to call this method
     * on the master, then pass the object to the slave side, then call
     * {@link SubversionSCM#createSvnClientManager(ISVNAuthenticationProvider)} on the slave.
     *
     * @see SubversionSCM#createSvnClientManager(ISVNAuthenticationProvider)
     */
    public ISVNAuthenticationProvider createAuthenticationProvider(Job<?, ?> inContextOf,
                                                                   ModuleLocation location,
                                                                   TaskListener listener) {
        return CredentialsSVNAuthenticationProviderImpl.createAuthenticationProvider(inContextOf, this, location, listener);
    }

    @Deprecated
    public ISVNAuthenticationProvider createAuthenticationProvider(Job<?, ?> inContextOf,
                                                                   ModuleLocation location) {
        return createAuthenticationProvider(inContextOf, location, TaskListener.NULL);
    }


    public static final class SvnInfo implements Serializable, Comparable<SvnInfo> {
        /**
         * Decoded repository URL.
         */
        public final String url;
        public final long revision;

        public SvnInfo(String url, long revision) {
            this.url = url;
            this.revision = revision;
        }

        public SvnInfo(SVNInfo info) {
            this( info.getURL().toDecodedString(), info.getCommittedRevision().getNumber() );
        }

        public SVNURL getSVNURL() throws SVNException {
            return SVNURL.parseURIDecoded(url);
        }

        public int compareTo(SvnInfo that) {
            int r = this.url.compareTo(that.url);
            if(r!=0)    return r;

            if(this.revision<that.revision) return -1;
            if(this.revision>that.revision) return +1;
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SvnInfo svnInfo = (SvnInfo) o;
            return revision==svnInfo.revision && url.equals(svnInfo.url);

        }

        @Override
        public int hashCode() {
            int result;
            result = url.hashCode();
            result = 31 * result + (int) (revision ^ (revision >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return String.format("%s (rev.%s)",url,revision);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * {@link SvnInfo} plus a flag if the revision is fixed.
     */
    private static final class SvnInfoP implements Serializable {
        /**
         * SvnInfo with an indicator boolean indicating whether this is a pinned external
         */
        public final SvnInfo info;
        public final boolean pinned;

        public SvnInfoP(SvnInfo info, boolean pinned) {
            this.info = info;
            this.pinned = pinned;
        }
        private static final long serialVersionUID = 1L;
    }

    /**
     * Information about svn:external
     */
    public static final class External implements Serializable {
        /**
         * Relative path within the workspace where this <tt>svn:exteranls</tt> exist.
         */
        public final String path;

        /**
         * External SVN URL to be fetched.
         */
        public final String url;

        /**
         * If the svn:external link is with the -r option, its number.
         * Otherwise -1 to indicate that the head revision of the external repository should be fetched.
         */
        public final long revision;

        public External(String path, SVNURL url, long revision) {
            this.path = path;
            this.url = url.toDecodedString();
            this.revision = revision;
        }

        /**
         * Returns true if this reference is to a fixed revision.
         */
        public boolean isRevisionFixed() {
            return revision!=-1;
        }

        private static final long serialVersionUID = 1L;
    }


    /**
     * Gets the SVN metadata for the remote repository.
     *
     * @param remoteUrl
     *      The target to run "svn info".
     */
    static SVNInfo parseSvnInfo(SVNURL remoteUrl, ISVNAuthenticationProvider authProvider) throws SVNException {
        final SvnClientManager manager = createClientManager(authProvider);
        try {
            final SVNWCClient svnWc = manager.getWCClient();
            return svnWc.doInfo(remoteUrl, SVNRevision.HEAD, SVNRevision.HEAD);
        } finally {
            manager.dispose();
        }
    }

    /**
     * Checks .svn files in the workspace and finds out revisions of the modules
     * that the workspace has.
     */
    private static class BuildRevisionMapTask extends MasterToSlaveFileCallable<List<SvnInfoP>> {
        private final ISVNAuthenticationProvider defaultAuthProvider;
        private final Map<String,ISVNAuthenticationProvider> authProviders;
        private final TaskListener listener;
        private final List<External> externals;
        private final ModuleLocation[] locations;

        public BuildRevisionMapTask(Run<?, ?> build, SubversionSCM parent, TaskListener listener, List<External> externals, EnvVars env) {
            this.listener = listener;
            this.externals = externals;
            this.locations = parent.getLocations(env, build);
            this.defaultAuthProvider = parent.createAuthenticationProvider(build.getParent(), null, listener);
            this.authProviders = new LinkedHashMap<String, ISVNAuthenticationProvider>();
            for (ModuleLocation loc: locations) {
                authProviders.put(loc.remote, parent.createAuthenticationProvider(build.getParent(), loc, listener));
            }
        }

        /**
         * @return
         *      null if the parsing somehow fails. Otherwise a map from the repository URL to revisions.
         */
        public List<SvnInfoP> invoke(File ws, VirtualChannel channel) throws IOException {
            List<SvnInfoP> revisions = new ArrayList<SvnInfoP>();

            for (ModuleLocation module : locations) {
                ISVNAuthenticationProvider authProvider = authProviders.get(module.remote);
                if (authProvider == null) {
                    authProvider = defaultAuthProvider;
                }
                final SvnClientManager manager = createClientManager(authProvider);
                try {
                    final SVNWCClient svnWc = manager.getWCClient();
                    // invoke the "svn info"
                    try {
                        SvnInfo info =
                                new SvnInfo(svnWc.doInfo(new File(ws, module.getLocalDir()), SVNRevision.WORKING));
                        revisions.add(new SvnInfoP(info, false));
                    } catch (SVNException e) {
                        e.printStackTrace(listener.error("Failed to parse svn info for " + module.remote));
                    }
                } finally {
                    manager.dispose();
                }
            }
            final SvnClientManager manager = createClientManager(defaultAuthProvider);
            try {
                final SVNWCClient svnWc = manager.getWCClient();
                for (External ext : externals) {
                    try {
                        SvnInfo info = new SvnInfo(svnWc.doInfo(new File(ws, ext.path), SVNRevision.WORKING));
                        revisions.add(new SvnInfoP(info, ext.isRevisionFixed()));
                    } catch (SVNException e) {
                        e.printStackTrace(
                                listener.error("Failed to parse svn info for external " + ext.url + " at " + ext.path));
                    }
                }

                return revisions;
            } finally {
                manager.dispose();
            }
        }
        private static final long serialVersionUID = 1L;
    }

    /**
     * Gets the file that stores the revision.
     */
    public static File getRevisionFile(Run build) {
        return new File(build.getRootDir(),"revision.txt");
    }

    /**
     * @deprecated use {@link hudson.scm.SubversionSCM#getRevisionFile(hudson.model.Run)} instead.
     *
     * Gets the file that stores the revision.
     */
    @Deprecated
    public static File getRevisionFile(AbstractBuild build) {
        return getRevisionFile((Run) build);
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        // exclude locations that are svn:external-ed with a fixed revision.
        Map<String,Long> wsRev = parseRevisionFile(build,true,true);
        return new SVNRevisionState(wsRev);
    }

    private boolean isPollFromMaster() {
        return pollFromMaster;
    }

    void setPollFromMaster(boolean pollFromMaster) {
        this.pollFromMaster = pollFromMaster;
    }

    @Override
    public PollingResult compareRemoteRevisionWith(Job<?, ?> project, Launcher launcher, FilePath workspace, final
            TaskListener listener, SCMRevisionState _baseline) throws IOException, InterruptedException {

        final SVNRevisionState baseline;
        if (_baseline instanceof SVNRevisionState) {
            baseline = (SVNRevisionState) _baseline;
        } else if (project.getLastBuild() != null) {
            baseline = (SVNRevisionState) calcRevisionsFromBuild(project.getLastBuild(), launcher != null ? workspace
                    : null, launcher, listener);
        } else {
            baseline = new SVNRevisionState(null);
        }

        // The job was never built before
        if (project.getLastBuild() == null) {
            listener.getLogger().println(Messages.SubversionSCM_pollChanges_noBuilds());
            return BUILD_NOW;
        }

        String nodeName = "master";
        VirtualChannel channel = null;
        if (workspace != null && !isPollFromMaster()) {
            channel = workspace.getChannel();
            if (channel != null && channel instanceof Channel) {
                nodeName = ((Channel) channel).getName();
            }
        }

        if (channel == null) {
            channel = FilePath.localChannel;
        }

        Node node;
        if (nodeName.equals("master")) {
            node = Jenkins.getInstance();
        } else {
            node = Jenkins.getInstance().getNode(nodeName);
        }

        // Reference: https://github.com/jenkinsci/subversion-plugin/pull/131
        // Right way to get the environment variables when we do polling. http://tinyurl.com/o2o2kg9
        EnvVars env = project.getEnvironment(node, listener);

        Run<?, ?> lastCompletedBuild = project.getLastCompletedBuild();

        if (lastCompletedBuild != null) {
            if (project instanceof AbstractProject && repositoryLocationsNoLongerExist(lastCompletedBuild, listener, env)) {
                // Disable this project, see HUDSON-763
                listener.getLogger().println(Messages.SubversionSCM_pollChanges_locationsNoLongerExist(project));
                disableProject((AbstractProject) project, listener);
                return NO_CHANGES;
            }

            // Are the locations checked out in the workspace consistent with the current configuration?
            for (ModuleLocation loc : getLocations(env, lastCompletedBuild)) {
                // baseline.revisions has URIdecoded URL
                String url;
                try {
                    url = loc.getSVNURL().toDecodedString();
                } catch (SVNException ex) {
                    listener.error(Messages.SubversionSCM_pollChanges_exception(loc.getURL()));
                    return BUILD_NOW;
                }
                if (!baseline.revisions.containsKey(url)) {
                    listener.getLogger().println(Messages.SubversionSCM_pollChanges_locationNotInWorkspace(url));
                    return BUILD_NOW;
                }
            }
        }

        final SVNLogHandler logHandler = new SVNLogHandler(createSVNLogFilter(), listener);

        final Map<String, ISVNAuthenticationProvider> authProviders = new LinkedHashMap<String,
                ISVNAuthenticationProvider>();

        for (ModuleLocation loc : getLocations(env, null)) {
            String url;
            try {
                url = loc.getExpandedLocation(project).getSVNURL().toDecodedString();
            } catch (SVNException ex) {
                ex.printStackTrace(listener.error(Messages.SubversionSCM_pollChanges_exception(loc.getURL())));
                return BUILD_NOW;
            }
            authProviders.put(url, createAuthenticationProvider(project, loc, listener));
        }
        final ISVNAuthenticationProvider defaultAuthProvider = createAuthenticationProvider(project, null, listener);

        // figure out the remote revisions
        return channel.call(new CompareAgainstBaselineCallable(baseline, logHandler, project.getName(), listener,
                defaultAuthProvider, authProviders, nodeName));
    }

    public SVNLogFilter createSVNLogFilter() {
        return new DefaultSVNLogFilter(getExcludedRegionsPatterns(), getIncludedRegionsPatterns(),
                getExcludedUsersNormalized(), getExcludedRevpropNormalized(), getExcludedCommitMessagesPatterns(), isIgnoreDirPropChanges());
    }

    /**
     * Goes through the changes between two revisions and see if all the changes
     * are excluded.
     */
    static final class SVNLogHandler implements ISVNLogEntryHandler, Serializable {

        private boolean changesFound = false;
        private SVNLogFilter filter;

        SVNLogHandler(SVNLogFilter svnLogFilter, TaskListener listener) {
            this.filter = svnLogFilter;;
            this.filter.setTaskListener(listener);
        }

        public boolean isChangesFound() {
            return changesFound;
        }

        /**
         * Checks it the revision range [from,to] has any changes that are not excluded via exclusions.
         */
        public boolean findNonExcludedChanges(SVNURL url, long from, long to, ISVNAuthenticationProvider authProvider) throws SVNException {
            if (from>to)        return false; // empty revision range, meaning no change

            // if no exclusion rules are defined, don't waste time going through "svn log".
            if (!filter.hasExclusionRule())    return true;

            final SvnClientManager manager = createClientManager(authProvider);
            try {
                manager.getLogClient().doLog(url, null, SVNRevision.UNDEFINED,
                        SVNRevision.create(from), // get log entries from the local revision + 1
                        SVNRevision.create(to), // to the remote revision
                        false, // Don't stop on copy.
                        true, // Report paths.
                        false, // Don't included merged revisions
                        0, // Retrieve log entries for unlimited number of revisions.
                        null, // Retrieve all revprops
                        this);
            } finally {
                manager.dispose();
            }

            return isChangesFound();
        }

        /**
         * Handles a log entry passed.
         * Check for log entries that should be excluded from triggering a build.
         * If an entry is not an entry that should be excluded, set changesFound to true
         *
         * @param logEntry an {@link org.tmatesoft.svn.core.SVNLogEntry} object
         *                 that represents per revision information
         *                 (committed paths, log message, etc.)
         * @throws org.tmatesoft.svn.core.SVNException
         */
        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            if (filter.isIncluded(logEntry)) {
                changesFound = true;
            }
        }

        private static final long serialVersionUID = 1L;
    }

    public ChangeLogParser createChangeLogParser() {
        return new SubversionChangeLogParser(ignoreDirPropChanges);
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * @deprecated
     */
    @Override
    @Deprecated
    public FilePath getModuleRoot(FilePath workspace) {
        if (getLocations().length > 0)
            return workspace.child(getLocations()[0].getLocalDir());
        return workspace;
    }

    @Override
    public FilePath getModuleRoot(FilePath workspace, AbstractBuild build) {
        if (build == null) {
            return getModuleRoot(workspace);
        }

        // TODO: can't I get the build listener here?
        TaskListener listener = new LogTaskListener(LOGGER, WARNING);
        final EnvVars env;
        try {
            env = build.getEnvironment(listener);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (getLocations().length > 0)
            return _getModuleRoot(workspace, getLocations()[0].getLocalDir(), env);
        return workspace;
    }

    @Deprecated
    @Override
    public FilePath[] getModuleRoots(FilePath workspace) {
        final ModuleLocation[] moduleLocations = getLocations();
        if (moduleLocations.length > 0) {
            FilePath[] moduleRoots = new FilePath[moduleLocations.length];
            for (int i = 0; i < moduleLocations.length; i++) {
                moduleRoots[i] = workspace.child(moduleLocations[i].getLocalDir());
            }
            return moduleRoots;
        }
        return new FilePath[] { getModuleRoot(workspace) };
    }

    @Override
    public FilePath[] getModuleRoots(FilePath workspace, AbstractBuild build) {
        if (build == null) {
            return getModuleRoots(workspace);
        }

        // TODO: can't I get the build listener here?
        TaskListener listener = new LogTaskListener(LOGGER, WARNING);
        final EnvVars env;
        try {
            env = build.getEnvironment(listener);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        final ModuleLocation[] moduleLocations = getLocations();
        if (moduleLocations.length > 0) {
            FilePath[] moduleRoots = new FilePath[moduleLocations.length];
            for (int i = 0; i < moduleLocations.length; i++) {
                moduleRoots[i] = _getModuleRoot(workspace, moduleLocations[i].getLocalDir(), env);
            }
            return moduleRoots;
        }
        return new FilePath[] { getModuleRoot(workspace, build) };

    }

    FilePath _getModuleRoot(FilePath workspace, String localDir, EnvVars env) {
        return workspace.child(
                env.expand(localDir));
    }

    private static String getLastPathComponent(String s) {
        String[] tokens = s.split("/");
        return tokens[tokens.length-1]; // return the last token
    }

    @hudson.init.Initializer(after = InitMilestone.JOB_LOADED, before = InitMilestone.COMPLETED)
    public static void perJobCredentialsMigration() {
        DescriptorImpl descriptor = descriptor();
        if (descriptor != null) {
            descriptor.migratePerJobCredentials();
        }
    }

    @Extension
    public static class DescriptorImpl extends SCMDescriptor<SubversionSCM> implements hudson.model.ModelObject {
        /**
         * SVN authentication realm to its associated credentials.
         * This is the global credential repository.
         */
        private transient Map<String,Credential> credentials;

        private boolean mayHaveLegacyPerJobCredentials;

        /**
         * Stores name of Subversion revision property to globally exclude
         */
        private String globalExcludedRevprop = null;

        private int workspaceFormat = SVNAdminAreaFactory.WC_FORMAT_14;

        /**
         * When set to true, repository URLs will be validated up to the first
         * dollar sign which is encountered.
         */
        private boolean validateRemoteUpToVar = false;

        /**
         * When set to {@code false}, then auth details will never be stored on disk.
         * @since 1.27
         */
        private boolean storeAuthToDisk = true;

        @Override
        public void load() {
            super.load();
            if (credentials != null && !credentials.isEmpty()) {
                SecurityContext oldContext = ACL.impersonate(ACL.SYSTEM);
                try {
                    BulkChange bc = new BulkChange(this);
                    try {
                        mayHaveLegacyPerJobCredentials = true;
                        CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
                        for (Map.Entry<String, Credential> e : credentials.entrySet()) {
                            migrateCredentials(store, e.getKey(), e.getValue());
                        }
                        save();
                        bc.commit();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Could not migrate stored credentials", e);
                    } finally {
                        bc.abort();
                    }
                } finally {
                    SecurityContextHolder.setContext(oldContext);
                }
            }
        }

        /*package*/ void migratePerJobCredentials() {
            if (credentials == null && !mayHaveLegacyPerJobCredentials ) {
                // nothing to do here
                return;
            }
            boolean allOk = true;

            for (AbstractProject<?, ?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                File jobCredentials = new File(job.getRootDir(), "subversion.credentials");
                if (jobCredentials.isFile()) {
                    try {
                        if (job.getScm() instanceof SubversionSCM) {
                            new PerJobCredentialStore(job).migrateCredentials(this);
                            job.save();
                        } // else: job is not using Subversion anymore
                        if (!jobCredentials.delete()) {
                            LOGGER.log(Level.WARNING, "Could not remove legacy per-job credentials store file: {0}", jobCredentials);
                            allOk = false;
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Could not migrate per-job credentials for " + job.getFullName(), e);
                        allOk = false;
                    }
                }
            }
            mayHaveLegacyPerJobCredentials = !allOk;
            save();
        }

        /*package*/ StandardCredentials migrateCredentials(CredentialsStore store, String legacyRealm, Credential legacyCredential)
                throws IOException {
            StandardCredentials credential = legacyCredential.toCredentials(null, legacyRealm);
            if (credential != null) {
                return credential;
            }
            credential = legacyCredential.toCredentials(legacyRealm);
            if (store.isDomainsModifiable()) {
                Matcher matcher = Pattern.compile("\\s*<([^>]+)>.*").matcher(legacyRealm);
                if (matcher.matches()) {
                    String url = matcher.group(1);
                    if (url.startsWith("http:") || url.startsWith("svn:") || url.startsWith("https:") || url
                            .startsWith("svn+ssh:")) {
                        // this is a reasonably valid URL
                        List<DomainRequirement> requirements = URIRequirementBuilder.fromUri(url).build();
                        HostnameRequirement hostnameRequirement = null;
                        SchemeRequirement schemeRequirement = null;
                        for (DomainRequirement r : requirements) {
                            if (hostnameRequirement == null && r instanceof HostnameRequirement) {
                                hostnameRequirement = (HostnameRequirement) r;
                            }
                            if (schemeRequirement == null && r instanceof SchemeRequirement) {
                                schemeRequirement = (SchemeRequirement) r;
                            }
                            if (schemeRequirement != null && hostnameRequirement != null) {
                                break;
                            }
                        }
                        Domain domain = null;
                        if (hostnameRequirement != null) {
                            for (Domain d : store.getDomains()) {
                                HostnameSpecification spec = null;
                                for (DomainSpecification s : d.getSpecifications()) {
                                    if (s instanceof HostnameSpecification) {
                                        spec = (HostnameSpecification) s;
                                        break;
                                    }
                                }
                                if (spec != null && spec.test(hostnameRequirement).isMatch() && d.test(requirements)) {
                                    domain = d;
                                    break;
                                }
                            }
                        }
                        if (domain == null) {
                            if (hostnameRequirement != null) {
                                List<DomainSpecification> specs = new ArrayList<DomainSpecification>();
                                specs.add(
                                        new HostnameSpecification(hostnameRequirement.getHostname(), null));
                                if (schemeRequirement != null) {
                                    specs.add(new SchemeSpecification(schemeRequirement.getScheme()));
                                }
                                domain = new Domain(hostnameRequirement.getHostname(), null, specs);
                                if (store.addDomain(domain, credential)) {
                                    return credential;
                                }
                            }
                        } else {
                            if (store.addCredentials(domain, credential)) {
                                return credential;
                            }
                        }
                    }
                }
            }
            store.addCredentials(Domain.global(), credential);
            return credential;
        }

        /**
         * Stores {@link SVNAuthentication} for a single realm.
         *
         * <p>
         * {@link Credential} holds data in a persistence-friendly way,
         * and it's capable of creating {@link SVNAuthentication} object,
         * to be passed to SVNKit.
         */
        public static abstract class Credential implements Serializable {
            /**
             *
             */
            private static final long serialVersionUID = -3707951427730113110L;

            /**
             * @param kind
             *      One of the constants defined in {@link ISVNAuthenticationManager},
             *      indicating what subtype of {@link SVNAuthentication} is expected.
             */
            public abstract SVNAuthentication createSVNAuthentication(String kind) throws SVNException;

            public abstract StandardCredentials toCredentials(String description) throws IOException;

            public abstract StandardCredentials toCredentials(ModelObject context, String description) throws IOException;

            protected ItemGroup findItemGroup(ModelObject context) {
                if (context instanceof ItemGroup) return (ItemGroup) context;
                if (context instanceof Item) return ((Item) context).getParent();
                return Jenkins.getInstance();
            }
        }

        /**
         * Username/password based authentication.
         */
        public static final class PasswordCredential extends Credential {
            /**
             *
             */
            private static final long serialVersionUID = -1676145651108866745L;
            private final String userName;
            private final Secret password; // for historical reasons, scrambled by base64 in addition to using 'Secret'

            public PasswordCredential(String userName, String password) {
                this.userName = userName;
                this.password = Secret.fromString(Scrambler.scramble(password));
            }

            @Override
            public SVNAuthentication createSVNAuthentication(String kind) {
                if(kind.equals(ISVNAuthenticationManager.SSH))
                    return new SVNSSHAuthentication(userName, getPassword(),-1,false);
                else
                    return new SVNPasswordAuthentication(userName, getPassword(),false);
            }


            @Override
            public StandardCredentials toCredentials(String description) {
                return new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, description, userName, getPassword());
            }

            @Override
            public StandardCredentials toCredentials(ModelObject context, String description) throws IOException {
                for (StandardUsernamePasswordCredentials c : CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        findItemGroup(context),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList())) {
                    if (userName.equals(c.getUsername()) && getPassword().equals(c.getPassword().getPlainText())) {
                        return c;
                    }
                }
                return null;
            }

            private String getPassword() {
                return Scrambler.descramble(Secret.toString(password));
            }
        }

        /**
         * Public key authentication for Subversion over SSH.
         */
        public static final class SshPublicKeyCredential extends Credential {
            /**
             *
             */
            private static final long serialVersionUID = -4649332611621900514L;
            private final String userName;
            private final Secret passphrase; // for historical reasons, scrambled by base64 in addition to using 'Secret'
            private final String id;

            /**
             * @param keyFile
             *      stores SSH private key. The file will be copied.
             */
            public SshPublicKeyCredential(String userName, String passphrase, File keyFile) throws SVNException {
                this.userName = userName;
                this.passphrase = Secret.fromString(Scrambler.scramble(passphrase));

                Random r = new Random();
                StringBuilder buf = new StringBuilder();
                for(int i=0;i<16;i++)
                    buf.append(Integer.toHexString(r.nextInt(16)));
                this.id = buf.toString();

                try {
                    File savedKeyFile = getKeyFile();
                    FileUtils.copyFile(keyFile,savedKeyFile);
                    setFilePermissions(savedKeyFile, "600");
                } catch (IOException e) {
                    throw new SVNException(
                            new RemotableSVNErrorMessage(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE,
                                    "Unable to save private key"), e);
                }
            }

            /**
             * Gets the location where the private key will be permanently stored.
             */
            private File getKeyFile() {
                File dir = new File(Hudson.getInstance().getRootDir(),"subversion-credentials");
                if(dir.mkdirs()) {
                    // make sure the directory exists. if we created it, try to set the permission to 600
                    // since this is sensitive information
                    setFilePermissions(dir, "600");
                }
                return new File(dir,id);
            }

            /**
             * Set the file permissions
             */
            private boolean setFilePermissions(File file, String perms) {
                try {
                    Chmod chmod = new Chmod();
                    chmod.setProject(new Project());
                    chmod.setFile(file);
                    chmod.setPerm(perms);
                    chmod.execute();
                } catch (BuildException e) {
                    // if we failed to set the permission, that's fine.
                    LOGGER.log(Level.WARNING, "Failed to set permission of "+file,e);
                    return false;
                }

                return true;
            }

            @Override
            public SVNSSHAuthentication createSVNAuthentication(String kind) throws SVNException {
                if(kind.equals(ISVNAuthenticationManager.SSH)) {
                    try {
                        Channel channel = Channel.current();
                        String privateKey;
                        if(channel!=null) {
                            // remote
                            // Unsafe in general, but we should have already converted $JENKINS_HOME/subversion-credentials anyway.
                            privateKey = channel.call(new SlaveToMasterCallable<String,IOException>() {
                                /**
                                 *
                                 */
                                private static final long serialVersionUID = -3088632649290496373L;

                                public String call() throws IOException {
                                    return FileUtils.readFileToString(getKeyFile(),"iso-8859-1");
                                }
                            });
                        } else {
                            privateKey = FileUtils.readFileToString(getKeyFile(),"iso-8859-1");
                        }
                        return new SVNSSHAuthentication(userName, privateKey.toCharArray(), Scrambler.descramble(Secret.toString(passphrase)),-1,false);
                    } catch (IOException | InterruptedException e) {
                        throw new SVNException(
                                new RemotableSVNErrorMessage(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE,
                                        "Unable to load private key"), e);
                    }
                } else
                    return null; // unknown
            }

            @Override
            public StandardCredentials toCredentials(String description) throws IOException {
                try {
                    return new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, null, userName,
                            new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(
                                    FileUtils.readFileToString(getKeyFile(), "iso-8859-1")
                            ),
                            Scrambler.descramble(Secret.toString(passphrase)), description);
                } catch (UnsupportedCharsetException e) {
                    throw new IllegalStateException(
                            "Java Language Specification lists ISO-8859-1 as a required standard charset",
                            e
                    );
                }
            }

            @Override
            public StandardCredentials toCredentials(ModelObject context, String description) throws IOException {
                String key = FileUtils.readFileToString(getKeyFile(), "iso-8859-1");
                for (SSHUserPrivateKey c : CredentialsProvider.lookupCredentials(
                        SSHUserPrivateKey.class,
                        findItemGroup(context),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList())) {
                    if (userName.equals(c.getUsername()) && c.getPrivateKeys().contains(key)) {
                        return c;
                    }
                }
                return null;
            }
        }

        /**
         * SSL client certificate based authentication.
         */
        public static final class SslClientCertificateCredential extends Credential {
            /**
             *
             */
            private static final long serialVersionUID = 5455755079546887446L;
            private final Secret certificate;
            private final Secret password; // for historical reasons, scrambled by base64 in addition to using 'Secret'

            public SslClientCertificateCredential(File certificate, String password) throws IOException {
                this.password = Secret.fromString(Scrambler.scramble(password));
                this.certificate = Secret.fromString(new String(Base64.encode(FileUtils.readFileToByteArray(certificate))));
            }

            @Override
            public SVNAuthentication createSVNAuthentication(String kind) {
                if(kind.equals(ISVNAuthenticationManager.SSL))
                    try {
                        SVNSSLAuthentication authentication = SVNSSLAuthentication.newInstance(
                                Base64.decode(certificate.getPlainText().toCharArray()),
                                Scrambler.descramble(Secret.toString(password)).toCharArray(),
                                false, null, false);
                        return authentication;
                    } catch (IOException e) {
                        throw new Error(e); // can't happen
                    }
                else
                    return null; // unexpected authentication type
            }

            @Override
            public StandardCertificateCredentials toCredentials(String description) {
                return new CertificateCredentialsImpl(CredentialsScope.GLOBAL, null, description,
                        Scrambler.descramble(Secret.toString(password)),
                        new CertificateCredentialsImpl.UploadedKeyStoreSource(certificate.getEncryptedValue()));
            }

            @Override
            public StandardCredentials toCredentials(ModelObject context, String description) throws IOException {
                StandardCertificateCredentials result = toCredentials(description);
                for (StandardCertificateCredentials c : CredentialsProvider.lookupCredentials(
                        StandardCertificateCredentials.class,
                        findItemGroup(context),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList())) {
                    if (c.getPassword().equals(result.getPassword())) {
                        // now for the more complex Keystore comparison
                        KeyStore s1 = c.getKeyStore();
                        KeyStore s2 = result.getKeyStore();
                        try {
                            // if the aliases differ we know it's not a match, this is a faster test than serial form
                            Set<String> a1 = new HashSet<String>(Collections.list(s1.aliases()));
                            Set<String> a2 = new HashSet<String>(Collections.list(s2.aliases()));
                            if (!a1.equals(a2)) {
                                continue;
                            }
                            // this may give false misses but it will not give false hits
                            ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
                            ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
                            s1.store(bos1, c.getPassword().getPlainText().toCharArray());
                            s2.store(bos2, c.getPassword().getPlainText().toCharArray());
                            if (Arrays.equals(bos1.toByteArray(), bos2.toByteArray())) {
                                return c;
                            }
                        } catch (KeyStoreException e) {
                            continue;
                        } catch (NoSuchAlgorithmException e) {
                            continue;
                        } catch (CertificateException e) {
                            continue;
                        }
                    }
                }
                return null;
            }
        }

        /**
         * Remoting interface that allows remote {@link ISVNAuthenticationProvider}
         * to read from local {@link DescriptorImpl#credentials}.
         */
        interface RemotableSVNAuthenticationProvider extends Serializable {
            Credential getCredential(SVNURL url, String realm);

            /**
             * Indicates that the specified credential worked.
             */
            void acknowledgeAuthentication(String realm, Credential credential);
        }

        /**
         * There's no point in exporting multiple {@link RemotableSVNAuthenticationProviderImpl} instances,
         * so let's just use one instance.
         */
        private transient final RemotableSVNAuthenticationProviderImpl remotableProvider = new RemotableSVNAuthenticationProviderImpl();

        private final class RemotableSVNAuthenticationProviderImpl implements RemotableSVNAuthenticationProvider {
            /**
             *
             */
            private static final long serialVersionUID = 1243451839093253666L;

            public Credential getCredential(SVNURL url, String realm) {
                for (SubversionCredentialProvider p : SubversionCredentialProvider.all()) {
                    Credential c = p.getCredential(url,realm);
                    if(c!=null) {
                        LOGGER.fine(String.format("getCredential(%s)=>%s by %s",realm,c,p));
                        return c;
                    }
                }
                LOGGER.fine(String.format("getCredential(%s)=>%s",realm,credentials.get(realm)));
                return credentials.get(realm);
            }

            public void acknowledgeAuthentication(String realm, Credential credential) {
                // this notification is only used on the project-local store.
            }

            /**
             * When sent to the remote node, send a proxy.
             */
            private Object writeReplace() {
                return Channel.current().export(RemotableSVNAuthenticationProvider.class, this);
            }
        }

        @Override
        public SCM newInstance(StaplerRequest staplerRequest, JSONObject jsonObject) throws FormException {
            return super.newInstance(staplerRequest, jsonObject);
        }

        @Override public boolean isApplicable(Job project) {
            return true;
        }

        public DescriptorImpl() {
            super(SubversionRepositoryBrowser.class);
            load();
        }

        @SuppressWarnings("unchecked")
        protected DescriptorImpl(Class clazz, Class<? extends RepositoryBrowser> repositoryBrowser) {
            super(clazz, repositoryBrowser);
        }

        public String getDisplayName() {
            return "Subversion";
        }

        @Restricted(NoExternalUse.class)
        void setGlobalExcludedRevprop(String revprop) {
            globalExcludedRevprop = revprop;
        }

        public String getGlobalExcludedRevprop() {
            return globalExcludedRevprop;
        }

        public int getWorkspaceFormat() {
            if (workspaceFormat==0)
                return SVNAdminAreaFactory.WC_FORMAT_14; // default
            return workspaceFormat;
        }

        public boolean isValidateRemoteUpToVar() {
            return validateRemoteUpToVar;
        }

        public boolean isStoreAuthToDisk() {
            return storeAuthToDisk;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            globalExcludedRevprop = fixEmptyAndTrim(
                    req.getParameter("svn.global_excluded_revprop"));
            workspaceFormat = Integer.parseInt(req.getParameter("svn.workspaceFormat"));
            validateRemoteUpToVar = formData.containsKey("validateRemoteUpToVar");
            storeAuthToDisk = formData.containsKey("storeAuthToDisk");

            // Save configuration
            save();

            return super.configure(req, formData);
        }

        @Override
        public boolean isBrowserReusable(SubversionSCM x, SubversionSCM y) {
            ModuleLocation[] xl = x.getLocations(), yl = y.getLocations();
            if (xl.length != yl.length) return false;
            for (int i = 0; i < xl.length; i++)
                if (!xl[i].getURL().equals(yl[i].getURL())) return false;
            return true;
        }

        /**
         * Creates {@link ISVNAuthenticationProvider} backed by {@link #credentials}.
         * This method must be invoked on the master, but the returned object is remotable.
         *
         * <p>
         * Therefore, to access {@link ISVNAuthenticationProvider}, you need to call this method
         * on the master, then pass the object to the slave side, then call
         * {@link SubversionSCM#createSvnClientManager(ISVNAuthenticationProvider)} on the slave.
         *
         * @see SubversionSCM#createSvnClientManager(ISVNAuthenticationProvider)
         * @deprecated as of 2.0
         */
        @Deprecated
        public ISVNAuthenticationProvider createAuthenticationProvider(AbstractProject<?,?> inContextOf) {
            SubversionSCM scm = null;
            if (inContextOf != null && inContextOf.getScm() instanceof SubversionSCM) {
                scm = (SubversionSCM)inContextOf.getScm();
            }
            return CredentialsSVNAuthenticationProviderImpl.createAuthenticationProvider(inContextOf, scm, null);
        }

        /**
         * @deprecated as of 1.18
         *      Now that Hudson allows different credentials to be given in different jobs,
         *      The caller should use {@link #createAuthenticationProvider(AbstractProject)} to indicate
         *      the project in which the subversion operation is performed.
         */
        @Deprecated
        public ISVNAuthenticationProvider createAuthenticationProvider() {
            return CredentialsSVNAuthenticationProviderImpl.createAuthenticationProvider(null, null, null);
        }

        /**
         * Submits the authentication info.
         */
        // TODO: stapler should do multipart/form-data handling
        public void doPostCredential(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            Hudson.getInstance().checkPermission(Item.CONFIGURE);

            MultipartFormDataParser parser = new MultipartFormDataParser(req);

            // we'll record what credential we are trying here.
            StringWriter log = new StringWriter();
            PrintWriter logWriter = new PrintWriter(log);

            UserProvidedCredential upc = UserProvidedCredential.fromForm(req,parser);

            try {
                postCredential(parser.get("url"), upc, logWriter);
                rsp.sendRedirect("credentialOK");
            } catch (SVNException e) {
                logWriter.println("FAILED: "+e.getErrorMessage());
                req.setAttribute("message",log.toString());
                req.setAttribute("pre",true);
                req.setAttribute("exception",e);
                rsp.forward(Hudson.getInstance(),"error",req);
            } finally {
                upc.close();
            }
        }

        /**
         * @deprecated as of 1.18
         *      Use {@link #postCredential(AbstractProject, String, String, String, File, PrintWriter)}
         */
        public void postCredential(String url, String username, String password, File keyFile, PrintWriter logWriter) throws SVNException, IOException {
            postCredential(null,url,username,password,keyFile,logWriter);
        }

        public void postCredential(AbstractProject inContextOf, String url, String username, String password, File keyFile, PrintWriter logWriter) throws SVNException, IOException {
            postCredential(url,new UserProvidedCredential(username,password,keyFile,inContextOf),logWriter);
        }

        /**
         * Submits the authentication info.
         *
         * This code is fairly ugly because of the way SVNKit handles credentials.
         */
        public void postCredential(String url, final UserProvidedCredential upc, PrintWriter logWriter) throws SVNException, IOException {
            SVNRepository repository = null;

            try {
                // the way it works with SVNKit is that
                // 1) svnkit calls AuthenticationManager asking for a credential.
                //    this is when we can see the 'realm', which identifies the user domain.
                // 2) DefaultSVNAuthenticationManager returns the username and password we set below
                // 3) if the authentication is successful, svnkit calls back acknowledgeAuthentication
                //    (so we store the password info here)
                repository = SVNRepositoryFactory.create(SVNURL.parseURIDecoded(url));
                repository.setTunnelProvider( createDefaultSVNOptions() );
                AuthenticationManagerImpl authManager = upc.new AuthenticationManagerImpl(logWriter);
                authManager.setAuthenticationForced(true);
                repository.setAuthenticationManager(authManager);
                repository.testConnection();
                authManager.checkIfProtocolCompleted();
            } finally {
                if (repository != null)
                    repository.closeSession();
            }
        }

        /**
         * @deprecated retained for API compatibility only
         */
        @CheckForNull
        @Deprecated
        public FormValidation doCheckRemote(StaplerRequest req, @AncestorInPath AbstractProject context, @QueryParameter String value, @QueryParameter String credentialsId) {
            Jenkins instance = Jenkins.getInstance();
            if (instance != null) {
                ModuleLocation.DescriptorImpl d = instance.getDescriptorByType(ModuleLocation.DescriptorImpl.class);
                if (d != null) {
                    return d.doCheckCredentialsId(req, context, value, credentialsId);
                }
            }

            return FormValidation.warning("Unable to check remote.");
        }

        /**
         * @deprecated use {@link #checkRepositoryPath(hudson.model.Job, org.tmatesoft.svn.core.SVNURL, com.cloudbees.plugins.credentials.common.StandardCredentials)}
         */
        @Deprecated
        public SVNNodeKind checkRepositoryPath(AbstractProject context, SVNURL repoURL) throws SVNException {
            return checkRepositoryPath(context, repoURL, null);
        }

        @Deprecated
        public SVNNodeKind checkRepositoryPath(Job context, SVNURL repoURL, StandardCredentials credentials) throws SVNException {
            return checkRepositoryPath((Item) context, repoURL, credentials);
        }

        public SVNNodeKind checkRepositoryPath(Item context, SVNURL repoURL, StandardCredentials credentials) throws SVNException {
            SVNRepository repository = null;

            try {
                repository = getRepository(context, repoURL, credentials, Collections.<String, Credentials>emptyMap(),
                        null);
                repository.testConnection();

                long rev = repository.getLatestRevision();
                String repoPath = getRelativePath(repoURL, repository);
                return repository.checkPath(repoPath, rev);
            } catch (SVNException e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LogRecord lr = new LogRecord(Level.FINE,
                            "Could not check repository path {0} using credentials {1} ({2})");
                    lr.setThrown(e);
                    lr.setParameters(new Object[]{
                            repoURL,
                            credentials == null ? null : CredentialsNameProvider.name(credentials),
                            credentials
                    });
                    LOGGER.log(lr);
                }
                throw e;
            } finally {
                if (repository != null) {
                    repository.closeSession();
                }
            }
        }

        /**
         * @deprecated Use {@link #getRepository(hudson.model.Job, org.tmatesoft.svn.core.SVNURL, com.cloudbees.plugins.credentials.common.StandardCredentials, java.util.Map, org.tmatesoft.svn.core.io.ISVNSession)}
         */
        @Deprecated
        protected SVNRepository getRepository(AbstractProject context, SVNURL repoURL) throws SVNException {
            return getRepository(context, repoURL, null, Collections.<String, Credentials>emptyMap(), null);
        }

        /**
         * @deprecated Use {@link #getRepository(hudson.model.Job, org.tmatesoft.svn.core.SVNURL, com.cloudbees.plugins.credentials.common.StandardCredentials, java.util.Map, org.tmatesoft.svn.core.io.ISVNSession)}
         */
        @Deprecated
        protected SVNRepository getRepository(AbstractProject context, SVNURL repoURL, ISVNSession session) throws SVNException {
            return getRepository(context, repoURL, null, Collections.<String, Credentials>emptyMap(), null);
        }

        /**
         * @deprecated Use {@link #getRepository(hudson.model.Job, org.tmatesoft.svn.core.SVNURL, com.cloudbees.plugins.credentials.common.StandardCredentials, java.util.Map, org.tmatesoft.svn.core.io.ISVNSession)}
         */
        @Deprecated
        protected SVNRepository getRepository(AbstractProject context, SVNURL repoURL, StandardCredentials credentials,
                                              Map<String, Credentials> additionalCredentials) throws SVNException {
            return getRepository(context, repoURL, credentials, additionalCredentials, null);
        }

        @Deprecated
        protected SVNRepository getRepository(Job context, SVNURL repoURL, StandardCredentials credentials,
                                              Map<String, Credentials> additionalCredentials, ISVNSession session) throws SVNException {
            return getRepository((Item) context, repoURL, credentials, additionalCredentials, session);
        }

        protected SVNRepository getRepository(Item context, SVNURL repoURL, StandardCredentials credentials,
                                              Map<String, Credentials> additionalCredentials, ISVNSession session) throws SVNException {
            SVNRepository repository = SVNRepositoryFactory.create(repoURL, session);
        
            ISVNAuthenticationManager sam = createSvnAuthenticationManager(
                    new CredentialsSVNAuthenticationProviderImpl(credentials, additionalCredentials, /* TODO */ TaskListener.NULL)
            );
            sam = new FilterSVNAuthenticationManager(sam) {
                // If there's no time out, the blocking read operation may hang forever, because TCP itself
                // has no timeout. So always use some time out. If the underlying implementation gives us some
                // value (which may come from ~/.subversion), honor that, as long as it sets some timeout value.
                @Override
                public int getReadTimeout(SVNRepository repository) {
                    int r = super.getReadTimeout(repository);
                    if(r<=0)    r = DEFAULT_TIMEOUT;
                    return r;
                }
            };
            repository.setTunnelProvider(createDefaultSVNOptions());
            repository.setAuthenticationManager(sam);

            return repository;
        }

        public static String getRelativePath(SVNURL repoURL, SVNRepository repository) throws SVNException {
            String repoPath = repoURL.getPath().substring(repository.getRepositoryRoot(true).getPath().length());
            if(!repoPath.startsWith("/"))    repoPath="/"+repoPath;
            return repoPath;
        }

        /**
         * Validates the excludeRegions Regex
         */
        public FormValidation doCheckExcludedRegions(@QueryParameter String value) throws IOException, ServletException {
            for (String region : Util.fixNull(value).trim().split("[\\r\\n]+"))
                try {
                    Pattern.compile(region);
                } catch (PatternSyntaxException e) {
                    return FormValidation.error("Invalid regular expression. " + e.getMessage());
                }
            return FormValidation.ok();
        }

        /**
         * Validates the includedRegions Regex
         */
        public FormValidation doCheckIncludedRegions(@QueryParameter String value) throws IOException, ServletException {
            return  doCheckExcludedRegions(value);
        }

        /**
         * Regular expression for matching one username. Matches 'windows' names ('DOMAIN&#92;user') and
         * 'normal' names ('user'). Where user (and DOMAIN) has one or more characters in 'a-zA-Z_0-9')
         */
        private static final Pattern USERNAME_PATTERN = Pattern.compile("(\\w+\\\\)?+(\\w+)");

        /**
         * Validates the excludeUsers field
         */
        public FormValidation doCheckExcludedUsers(@QueryParameter String value) throws IOException, ServletException {
            for (String user : Util.fixNull(value).trim().split("[\\r\\n]+")) {
                user = user.trim();

                if ("".equals(user)) {
                    continue;
                }

                if (!USERNAME_PATTERN.matcher(user).matches()) {
                    return FormValidation.error("Invalid username: " + user);
                }
            }

            return FormValidation.ok();
        }

        public List<WorkspaceUpdaterDescriptor> getWorkspaceUpdaterDescriptors() {
            return WorkspaceUpdaterDescriptor.all();
        }

        /**
         * Validates the excludeCommitMessages field
         */
        public FormValidation doCheckExcludedCommitMessages(@QueryParameter String value) throws IOException, ServletException {
            for (String message : Util.fixNull(value).trim().split("[\\r\\n]+")) {
                try {
                    Pattern.compile(message);
                } catch (PatternSyntaxException e) {
                    return FormValidation.error("Invalid regular expression. " + e.getMessage());
                }
            }
            return FormValidation.ok();
        }

        /**
         * Validates the remote server supports custom revision properties
         */
        public FormValidation doCheckRevisionPropertiesSupported(@AncestorInPath Item context,
                                                                 @QueryParameter String value,
                                                                 @QueryParameter String credentialsId,
                                                                 @QueryParameter String excludedRevprop) throws IOException, ServletException {
              String v = Util.fixNull(value).trim();
            if (v.length() == 0)
                return FormValidation.ok();

            String revprop = Util.fixNull(excludedRevprop).trim();
            if (revprop.length() == 0)
                return FormValidation.ok();

            // Test the connection only if we have admin permission
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                return FormValidation.ok();

            try {
                SVNURL repoURL = SVNURL.parseURIDecoded(new EnvVars(EnvVars.masterEnvVars).expand(v));
                StandardCredentials credentials = lookupCredentials(context, credentialsId, repoURL);
                SVNNodeKind node = null;
                try {
                    node = checkRepositoryPath(context,repoURL, credentials);
                } catch (SVNCancelException ce) {
                    if (isAuthenticationFailedError(ce)) {
                        // don't care about this here, another field's validation will show this
                        return FormValidation.ok();
                    }
                    throw ce;
                }
                if (node!=SVNNodeKind.NONE)
                    // something exists
                    return FormValidation.ok();

                SVNRepository repository = null;
                try {
                    repository = getRepository(context,repoURL, credentials, Collections.<String, Credentials>emptyMap(), null);
                    if (repository.hasCapability(SVNCapability.LOG_REVPROPS))
                        return FormValidation.ok();
                } finally {
                    if (repository != null)
                        repository.closeSession();
                }
            } catch (SVNException e) {
                String message="";
                message += "Unable to access "+Util.escape(v)+" : "+Util.escape(e.getErrorMessage().getFullMessage());
                LOGGER.log(Level.INFO, "Failed to access subversion repository "+v,e);
                return FormValidation.errorWithMarkup(message);
            }

            return FormValidation.warning(Messages.SubversionSCM_excludedRevprop_notSupported(v));
        }

        static {
            new Initializer();
        }

    }

    // copied from WorkspaceUpdater:
    private static boolean isAuthenticationFailedError(SVNCancelException e) {
      // this is very ugly. SVNKit (1.7.4 at least) reports missing authentication data as a cancel exception
      // "No credential to try. Authentication failed"
      // See DefaultSVNAuthenticationManager#getFirstAuthentication
      if (String.valueOf(e.getMessage()).contains("No credential to try")) {
        return true;
      }
      Throwable cause = e.getCause();
      if (cause instanceof SVNCancelException) {
        return isAuthenticationFailedError((SVNCancelException) cause);
      } else {
        return false;
      }
    }

    @CheckForNull
    private static DescriptorImpl descriptor() {
        Jenkins instance = Jenkins.getInstanceOrNull();
        return instance == null ? null : instance.getDescriptorByType(DescriptorImpl.class);
    }

    /**
     * @deprecated 1.34
     */
    public boolean repositoryLocationsNoLongerExist(AbstractBuild<?,?> build, TaskListener listener) {
        return repositoryLocationsNoLongerExist(build, listener, null);
    }

    /**
     * @since 1.34
     */
    public boolean repositoryLocationsNoLongerExist(Run<?,?> build, TaskListener listener, EnvVars env) {
        PrintStream out = listener.getLogger();

        for (ModuleLocation l : getLocations(env, build))
            try {
                if (getDescriptor().checkRepositoryPath(build.getParent(),
                        l.getSVNURL(),
                        lookupCredentials(build.getParent(), l.credentialsId, l.getSVNURL())) == SVNNodeKind.NONE) {
                    out.println("Location '" + l.remote + "' does not exist");

                    ParametersAction params = build.getAction(ParametersAction.class);
                    if (params != null) {
                        // since this is used to disable projects, be conservative
                        LOGGER.fine("Location could be expanded on build '" + build
                                + "' parameters values:");
                        return false;
                    }
                    return true;
                }
            } catch (SVNException e) {
                // be conservative, since we are just trying to be helpful in detecting
                // non existent locations. If we can't detect that, we'll do nothing
                LOGGER.log(FINE, "Location check failed",e);
            }
        return false;
    }
    
    /**
     * Disables the project if it is possible and prints messages to the log.
     * @param project Project to be disabled
     * @param listener Logger
     * @throws IOException Cannot disable the project
     */
    private void disableProject(@NonNull AbstractProject project, @NonNull TaskListener listener)
            throws IOException {
        if (project.supportsMakeDisabled()) {
            project.makeDisabled(true);
            listener.getLogger().println(Messages.SubversionSCM_disableProject_disabled());
        } else {
            listener.getLogger().println(Messages.SubversionSCM_disableProject_unsupported());
        }
    }

    static final Pattern URL_PATTERN = Pattern.compile("(https?|svn(\\+[a-z0-9]+)?|file)://.+");

    private static final long serialVersionUID = 1L;

    // noop, but this forces the initializer to run.
    public static void init() {}

    static {
        new Initializer();
    }

    private static final class Initializer {
        static {
            if(Boolean.getBoolean("hudson.spool-svn"))
                DAVRepositoryFactory.setup(new DefaultHTTPConnectionFactory(null,true,null));
            else
                DAVRepositoryFactory.setup();   // http, https
            SVNRepositoryFactoryImpl.setup();   // svn, svn+xxx
            FSRepositoryFactory.setup();    // file

            // disable the connection pooling, which causes problems like
            // http://www.nabble.com/SSH-connection-problems-p12028339.html
            if(System.getProperty("svnkit.ssh2.persistent")==null)
                System.setProperty("svnkit.ssh2.persistent","false");

            // push Negotiate to the end because it requires a valid Kerberos configuration.
            // see HUDSON-8153
            if(System.getProperty("svnkit.http.methods")==null)
                System.setProperty("svnkit.http.methods","Digest,Basic,NTLM,Negotiate");

            // use SVN1.4 compatible workspace by default.
            SVNAdminAreaFactory.setSelector(new SubversionWorkspaceSelector());
        }
    }

    /**
     * small structure to store local and remote (repository) location
     * information of the repository. As a addition it holds the invalid field
     * to make failure messages when doing a checkout possible
     */
    @ExportedBean
    public static final class ModuleLocation extends AbstractDescribableImpl<ModuleLocation> implements Serializable {
        /**
         * Subversion URL to check out.
         *
         * This may include "@NNN" at the end to indicate a fixed revision.
         */
        @Exported
        public final String remote;

        /**
         * The credentials to checkout with.
         */
        public String credentialsId;

        /**
         * Remembers the user-given value.
         * Can be null.
         *
         * @deprecated
         *      Code should use {@link #getLocalDir()}. This field is only intended for form binding.
         */
        @Exported
        public final String local;

        /**
         * Subversion remote depth. Used as "--depth" option for checkout and update commands.
         * Default value is "infinity".
         */
        @Exported
        public final String depthOption;

        /**
         * Flag to ignore subversion externals definitions.
         */
        @Exported
        public boolean ignoreExternalsOption;

        /**
         * Flag to cancel the process when checkout/update svn:externals failed.
         */
        @Exported
        public boolean cancelProcessOnExternalsFail;

        /**
         * Cache of the repository UUID.
         */
        private transient volatile UUID repositoryUUID;
        private transient volatile SVNURL repositoryRoot;

        /**
         * Constructor to support backwards compatibility.
         */
        @Deprecated
        public ModuleLocation(String remote, String local) {
            this(remote, null, local, null, false, false);
        }

        /**
         * Sets the credentials identifier.
         */
        void setCredentialsId (final String id) {
            credentialsId = id;
        }

        /**
         * Constructor to support backwards compatibility.
         */
        @Deprecated
        public ModuleLocation(String remote, String local, String depthOption, boolean ignoreExternalsOption) {
            this(remote,null,local,depthOption,ignoreExternalsOption, false);
        }

        /**
         * Constructor to support backwards compatibility.
         */
        @Deprecated
        public ModuleLocation(String remote, String credentialsId, String local, String depthOption, boolean ignoreExternalsOption) {
          this(remote,credentialsId,local,depthOption,ignoreExternalsOption, false);
        }

        @DataBoundConstructor
        public ModuleLocation(String remote, String credentialsId, String local, String depthOption, boolean ignoreExternalsOption,
                              boolean cancelProcessOnExternalsFail) {
            this.remote = Util.removeTrailingSlash(Util.fixNull(remote).trim());
            this.credentialsId = credentialsId;
            this.local = fixEmptyAndTrim(local);
            this.depthOption = StringUtils.isEmpty(depthOption) ? SVNDepth.INFINITY.getName() : depthOption;
            this.ignoreExternalsOption = ignoreExternalsOption;
            this.cancelProcessOnExternalsFail = cancelProcessOnExternalsFail;
        }

        public ModuleLocation withRemote(String remote) {
            return new ModuleLocation(remote, credentialsId, local, depthOption, ignoreExternalsOption, cancelProcessOnExternalsFail);
        }

        public ModuleLocation withCredentialsId(String credentialsId) {
            return new ModuleLocation(remote, credentialsId, local, depthOption, ignoreExternalsOption, cancelProcessOnExternalsFail);
        }

        public ModuleLocation withLocal(String local) {
            return new ModuleLocation(remote, credentialsId, local, depthOption, ignoreExternalsOption, cancelProcessOnExternalsFail);
        }

        public ModuleLocation withDepthOption(String depthOption) {
            return new ModuleLocation(remote, credentialsId, local, depthOption, ignoreExternalsOption, cancelProcessOnExternalsFail);
        }

        public ModuleLocation withIgnoreExternalsOption(boolean ignoreExternalsOption) {
            return new ModuleLocation(remote, credentialsId, local, depthOption, ignoreExternalsOption, cancelProcessOnExternalsFail);
        }

        public ModuleLocation withCancelProcessOnExternalsFailed(boolean cancelProcessOnExternalsFailed) {
          return new ModuleLocation(remote, credentialsId, local, depthOption, ignoreExternalsOption, cancelProcessOnExternalsFailed);
        }

        /**
         * Local directory to place the file to.
         * Relative to the workspace root.
         */
        public String getLocalDir() {
            if(local==null)
                return getLastPathComponent(getURL());
            return local;
        }

        /**
         * Returns the pure URL portion of {@link #remote} by removing
         * possible "@NNN" suffix.
         */
        public String getURL() {
        	return SvnHelper.getUrlWithoutRevision(remote);
        }

        /**
         * Gets {@link #remote} as {@link SVNURL}.
         */
        public SVNURL getSVNURL() throws SVNException {
            return SVNURL.parseURIEncoded(getURL());
        }

        /**
         * Repository UUID. Lazy computed and cached.
         */
        public UUID getUUID(Job context, SCM scm) throws SVNException {
            if(repositoryUUID==null || repositoryRoot==null) {
                LOGGER.fine("UUID of " + remote + " not cached for " + context);
                synchronized (this) {
                    // don't keep connections open for further use to prevent having too many open at the same time.
                    SVNRepository r = openRepository(context, scm, false);
                    if (r.getRepositoryUUID(false) == null)
                        r.testConnection(); // make sure values are fetched
                    repositoryUUID = UUID.fromString(r.getRepositoryUUID(false));
                    repositoryRoot = r.getRepositoryRoot(true);
                }
            }
            return repositoryUUID;
        }

        @Deprecated
        public UUID getUUID(AbstractProject context) throws SVNException {
            return getUUID(context, context.getScm());
        }

        @Deprecated
        public SVNRepository openRepository(AbstractProject context) throws SVNException {
            return openRepository(context, true);
        }

        @Deprecated
        public SVNRepository openRepository(AbstractProject context, boolean keepConnection) throws SVNException {
            return openRepository(context, context.getScm(), true);
        }

        public SVNRepository openRepository(Job context, SCM scm, boolean keepConnection) throws SVNException {
            SVNURL repoURL = getSVNURL();

            StandardCredentials creds = lookupCredentials(context, credentialsId, repoURL);
            Map<String, Credentials> additional = new HashMap<String, Credentials>();
            if (creds == null) {
                // we should add additional credentials, this looks like it's going to be an external
                // TODO only necessary with externals, or can we always do this?
                List<AdditionalCredentials> additionalCredentialsList = ((SubversionSCM) scm).getAdditionalCredentials();
                for (AdditionalCredentials c : additionalCredentialsList) {
                    String credentialsId = c.getCredentialsId();
                    if (credentialsId != null) {
                        StandardCredentials cred = CredentialsMatchers
                                .firstOrNull(CredentialsProvider.lookupCredentials(StandardCredentials.class, context,
                                        ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                                        CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId),
                                                CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(
                                                        StandardCredentials.class), CredentialsMatchers.instanceOf(
                                                        SSHUserPrivateKey.class))));
                        if (cred != null) {
                            additional.put(c.getRealm(), cred);
                        }
                    }
                }
            }

            if (keepConnection) {
                return descriptor().getRepository(context, repoURL, creds, additional, null);
            }
            return descriptor().getRepository(context, repoURL, creds, additional, new ISVNSession() {
                public boolean keepConnection(SVNRepository repository) {
                    return false;
                }
                public void saveCommitMessage(SVNRepository repository, long revision, String message) {
                }
                public String getCommitMessage(SVNRepository repository, long revision) {
                    return null;
                }
                public boolean hasCommitMessage(SVNRepository repository, long revision) {
                    return false;
                }
            });
        }

        @Deprecated
        public SVNURL getRepositoryRoot(AbstractProject context) throws SVNException {
            return getRepositoryRoot(context, context.getScm());
        }

        public @Nonnull SVNURL getRepositoryRoot(Job context, SCM scm) throws SVNException {
            getUUID(context, scm);
            return repositoryRoot;
        }

        /**
         * Figures out which revision to check out.
         *
         * If {@link #remote} is {@code url@rev}, then this method
         * returns that specific revision.
         *
         * @param defaultValue
         *      If "@NNN" portion is not in the URL, this value will be returned.
         *      Normally, this is the SVN revision timestamped at the build date.
         */
        public SVNRevision getRevision(SVNRevision defaultValue) {
            SVNRevision revision = getRevisionFromRemoteUrl(remote);
            return revision != null ? revision : defaultValue;
        }

        private String getExpandedRemote(AbstractBuild<?,?> build) {
            String outRemote = remote;

            ParametersAction parameters = build.getAction(ParametersAction.class);
            if (parameters != null)
                outRemote = parameters.substitute(build, remote);

            return outRemote;
        }

        /**
         * @deprecated This method is used by {@link #getExpandedLocation(AbstractBuild)}
         *             which is deprecated since it expands variables only based
         *             on build parameters.
         */
        private String getExpandedLocalDir(AbstractBuild<?,?> build) {
            String outLocalDir = getLocalDir();

            ParametersAction parameters = build.getAction(ParametersAction.class);
            if (parameters != null)
                outLocalDir = parameters.substitute(build, getLocalDir());

            return outLocalDir;
        }

        /**
         * Returns the value of remote depth option.
         *
         * @return the value of remote depth option.
         */
        public String getDepthOption() {
            return depthOption;
        }

        /**
         * Returns {@link org.tmatesoft.svn.core.SVNDepth} by string value.
         *
         * @return {@link org.tmatesoft.svn.core.SVNDepth} value.
         */
        private static SVNDepth getSvnDepth(String name) {
            return SVNDepth.fromString(name);
        }

        /**
         * Returns the SVNDepth to use for updating the module.
         *
         * This is just mapping the depthOption to an SVN Depth
         *
         * @return {@link org.tmatesoft.svn.core.SVNDepth} value.
         */
        public SVNDepth getSvnDepthForUpdate() {
            return getSvnDepth(getDepthOption());
        }

        /**
         * Returns the SVNDepth to use for checking out the module.
         *
         * This is normally the requested SVN depth except when the user
         * has requested as-it-is and then we use files so that we don't check
         * everything out.
         *
         * @return {@link org.tmatesoft.svn.core.SVNDepth} value.
         */
        public SVNDepth getSvnDepthForCheckout() {
            if("unknown".equals(getDepthOption())) {
                return SVNDepth.FILES;
            } else if ("as-it-is-infinity".equals(getDepthOption())){
                return SVNDepth.INFINITY;
            } else {
                return getSvnDepth(getDepthOption());
            }
        }

        /**
         * Returns the SVNDepth to use for reverting the module if svn up with revert before is selected
         *
         * This is normally the requested SVN depth except when the user
         * has requested as-it-is and then we use infinity to actually revert everything
         *
         * @return {@link org.tmatesoft.svn.core.SVNDepth} value.
         */
        public SVNDepth getSvnDepthForRevert() {
            if("unknown".equals(getDepthOption()) || "as-it-is-infinity".equals(getDepthOption())){
                return SVNDepth.INFINITY;
            }else {
                return getSvnDepth(getDepthOption());
            }
        }

        /**
         * Determines if subversion externals definitions should be ignored.
         *
         * @return true if subversion externals definitions should be ignored.
         */
        public boolean isIgnoreExternalsOption() {
            return ignoreExternalsOption;
        }

        /**
         * Determines if the process should be cancelled when checkout/update svn:externals failed.
         *
         * @return true if the process should be cancelled when checkout/update svn:externals failed.
         */
        public boolean isCancelProcessOnExternalsFail() {
          return cancelProcessOnExternalsFail;
        }

        /**
         * Expand location value based on Build parametric execution.
         *
         * @param build Build instance for expanding parameters into their values
         * @return Output ModuleLocation expanded according to Build parameters values.
         * @deprecated Use {@link #getExpandedLocation(EnvVars)} for vars expansion
         *             to be performed on all env vars rather than just build parameters.
         */
        public ModuleLocation getExpandedLocation(AbstractBuild<?, ?> build) {
            EnvVars env = new EnvVars(EnvVars.masterEnvVars);
            env.putAll(build.getBuildVariables());
            return getExpandedLocation(env);
        }

        /**
         * Expand location value based on environment variables.
         *
         * @return Output ModuleLocation expanded according to specified env vars.
         */
        public ModuleLocation getExpandedLocation(EnvVars env) {
            return new ModuleLocation(env.expand(remote), credentialsId, env.expand(getLocalDir()), getDepthOption(),
                    isIgnoreExternalsOption(), isCancelProcessOnExternalsFail());
        }

        @Override
        public String toString() {
            return remote;
        }

        private static final long serialVersionUID = 1L;

        @Deprecated
        public static List<ModuleLocation> parse(String[] remoteLocations, String[] localLocations, String[] depthOptions, boolean[] isIgnoreExternals) {
            return parse(remoteLocations, null, localLocations, depthOptions, isIgnoreExternals, null);
        }

        @Deprecated
        public static List<ModuleLocation> parse(String[] remoteLocations, String[] credentialIds, String[] localLocations, String[] depthOptions, boolean[] isIgnoreExternals) {
          return parse(remoteLocations, credentialIds, localLocations, depthOptions, isIgnoreExternals, null);
        }

        public static List<ModuleLocation> parse(String[] remoteLocations, String[] credentialIds,
                                                 String[] localLocations, String[] depthOptions,
                                                 boolean[] isIgnoreExternals, boolean[] cancelProcessOnExternalsFails) {
            List<ModuleLocation> modules = new ArrayList<ModuleLocation>();
            if (remoteLocations != null && localLocations != null) {
                int entries = Math.min(remoteLocations.length, localLocations.length);

                for (int i = 0; i < entries; i++) {
                    // the remote (repository) location
                    String remoteLoc = Util.nullify(remoteLocations[i]);

                    if (remoteLoc != null) {// null if skipped
                        remoteLoc = Util.removeTrailingSlash(remoteLoc.trim());
                        modules.add(new ModuleLocation(remoteLoc,
                                credentialIds != null && credentialIds.length > i ? credentialIds[i] : null,
                                Util.nullify(localLocations[i]),
                            depthOptions != null ? depthOptions[i] : null,
                            isIgnoreExternals != null && isIgnoreExternals[i],
                            cancelProcessOnExternalsFails != null && cancelProcessOnExternalsFails[i]));
                    }
                }
            }
            return modules;
        }

        /**
         * If a subversion remote uses $VAR or ${VAR} as a parameterized build,
         * we expand the url. This will expand using the DEFAULT item. If there
         * is a choice parameter, it will expand with the FIRST item.
         */
        public ModuleLocation getExpandedLocation(Job<?, ?> project) {
            String url = this.getURL();
            String returnURL = url;
            for (JobProperty property : project.getProperties().values()) {
                if (property instanceof ParametersDefinitionProperty) {
                    ParametersDefinitionProperty pdp = (ParametersDefinitionProperty) property;
                    for (String propertyName : pdp.getParameterDefinitionNames()) {
                        if (url.contains(propertyName)) {
                            ParameterDefinition pd = pdp.getParameterDefinition(propertyName);
                            ParameterValue pv = pd.getDefaultParameterValue();
                            String replacement = "";
                            if (pv != null) {
                                replacement = String.valueOf(pv.createVariableResolver(null).resolve(propertyName));
                            }

                            returnURL = returnURL.replace("${" + propertyName + "}", replacement);
                            returnURL = returnURL.replace("$" + propertyName, replacement);
                        }
                    }
                }
            }

            return new ModuleLocation(returnURL, credentialsId, getLocalDir(), getDepthOption(), isIgnoreExternalsOption(),
                isCancelProcessOnExternalsFail());
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<ModuleLocation> {

            @Override
            public String getDisplayName() {
                return null;
            }

            public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String remote) {
                if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                    context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                    return new StandardListBoxModel();
                }
                return fillCredentialsIdItems(context, remote);
            }

            public ListBoxModel fillCredentialsIdItems(@CheckForNull Item context, String remote) {
                List<DomainRequirement> domainRequirements;
                if (remote == null) {
                    domainRequirements = Collections.<DomainRequirement>emptyList();
                } else {
                    domainRequirements = URIRequirementBuilder.fromUri(remote.trim()).build();
                }
                return new StandardListBoxModel()
                        .withEmptySelection()
                        .withMatching(
                                CredentialsMatchers.anyOf(
                                        CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                        CredentialsMatchers.instanceOf(StandardCertificateCredentials.class),
                                        CredentialsMatchers.instanceOf(SSHUserPrivateKey.class)
                                ),
                                CredentialsProvider.lookupCredentials(StandardCredentials.class,
                                        context,
                                        ACL.SYSTEM,
                                        domainRequirements)
                        );
            }

            /**
             * Validate the value for a remote (repository) location.
             */
            public FormValidation doCheckRemote(/* TODO unused, delete */StaplerRequest req, @AncestorInPath Item context,
                    @QueryParameter String remote) {

                // repository URL is required
                String url = Util.fixEmptyAndTrim(remote);
                if (url == null) {
                    return FormValidation.error(Messages.SubversionSCM_doCheckRemote_required());
                }

                // Is the repository URL parameterized?
                if (url.indexOf('$') != -1) {
                    return FormValidation.warning("This repository URL is parameterized, syntax validation skipped");
                }

                // repository URL syntax
                try {
                    SVNURL.parseURIEncoded(url);
                } catch (SVNException svne) {
                    LOGGER.log(Level.SEVERE, svne.getMessage());
                    return FormValidation.error(Messages.SubversionSCM_doCheckRemote_invalidUrl());
                }
                return FormValidation.ok();
            }

            /**
             * Validate the value for a remote (repository) location.
             */
            @RequirePOST
            public FormValidation doCheckCredentialsId(StaplerRequest req, @AncestorInPath Item context,
                    @QueryParameter String remote, @QueryParameter String value) {

                // Test the connection only if we may use the credentials (cf. hudson.plugins.git.UserRemoteConfig.DescriptorImpl.doCheckUrl)
                if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                    context != null && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return FormValidation.ok();
                }
                return checkCredentialsId(req, context, remote, value);
            }

            /**
             * Validate the value for a remote (repository) location.
             */
            public FormValidation checkCredentialsId(/* TODO unused, delete */StaplerRequest req, @Nonnull Item context, String remote, String value) {

                // Ignore validation if repository URL is empty
                String url = Util.fixEmptyAndTrim(remote);
                if (url == null) {
                    return FormValidation.ok();
                }

                // Is the repository URL parameterized?
                if (remote.indexOf('$') != -1) {
                    return FormValidation.warning("The repository URL is parameterized, connection check skipped");
                }

                try {
                    SVNURL repoURL = SVNURL.parseURIEncoded(remote);
                    StandardCredentials credentials = lookupCredentials(context, value, repoURL);
                    SVNRepository repo = descriptor().getRepository(context, repoURL, credentials, Collections
                            .<String, Credentials>emptyMap(), null);
                    String repoRoot = repo.getRepositoryRoot(true).toDecodedString();
                    String repoPath = repo.getLocation().toDecodedString().substring(repoRoot.length());
                    SVNPath path = new SVNPath(repoPath, true, true);
                    SVNNodeKind svnNodeKind = repo.checkPath(path.getTarget(), path.getPegRevision().getNumber());
                    if (svnNodeKind != SVNNodeKind.DIR) {
                        return FormValidation.error("Credentials looks fine but the repository URL is invalid");
                    }
                } catch (SVNException e) {
                    LOGGER.log(Level.SEVERE, e.getErrorMessage().getMessage());
                    return FormValidation.error("Unable to access the repository");
                }
                return FormValidation.ok();
            }

            /**
             * validate the value for a local location (local checkout directory).
             */
            public FormValidation doCheckLocal(@QueryParameter String value) throws IOException, ServletException {
                String v = Util.nullify(value);
                if (v == null)
                    // local directory is optional so this is ok
                    return FormValidation.ok();

                v = v.trim();

                // check if a absolute path has been supplied
                // (the last check with the regex will match windows drives)
                if (v.startsWith("/") || v.startsWith("\\") || v.startsWith("..") || v.matches("^[A-Za-z]:.*"))
                    return FormValidation.error("absolute path is not allowed");

                // all tests passed so far
                return FormValidation.ok();
            }

        }
    }

    private static final Logger LOGGER = Logger.getLogger(SubversionSCM.class.getName());

    /**
     * Network timeout in milliseconds.
     * The main point of this is to prevent infinite hang, so it should be a rather long value to avoid
     * accidental time out problem.
     */
    public static final int DEFAULT_TIMEOUT = Integer.getInteger(SubversionSCM.class.getName() + ".timeout", 3600 *
            1000);

    /**
     * Property to control whether SCM polling happens from the slave or master
     */
    private static boolean POLL_FROM_MASTER = Boolean.getBoolean(SubversionSCM.class.getName() + ".pollFromMaster");

    /**
     * If set to non-null, read configuration from this directory instead of "~/.subversion".
     */
    public static final String CONFIG_DIR = System.getProperty(SubversionSCM.class.getName() + ".configDir");

    /**
     * Enables trace logging of Ganymed SSH library.
     * <p>
     * Intended to be invoked from Groovy console.
     */
    public static void enableSshDebug(Level level) {
        if(level==null)     level= Level.FINEST; // default

        final Level lv = level;

        com.trilead.ssh2.log.Logger.enabled=true;
        com.trilead.ssh2.log.Logger.logger = new DebugLogger() {
            private final Logger LOGGER = Logger.getLogger(SCPClient.class.getPackage().getName());
            public void log(int level, String className, String message) {
                LOGGER.log(lv,className+' '+message);
            }
        };
    }

    /*package*/ static boolean compareSVNAuthentications(SVNAuthentication a1, SVNAuthentication a2) {
        if (a1==null && a2==null)       return true;
        if (a1==null || a2==null)       return false;
        if (a1.getClass()!=a2.getClass())    return false;

        try {
            return describeBean(a1).equals(describeBean(a2));
        } catch (IllegalAccessException e) {
            return false;
        } catch (InvocationTargetException e) {
            return false;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * In preparation for a comparison, char[] needs to be converted that supports value equality.
     */
    @SuppressWarnings("unchecked")
    private static Map describeBean(Object o) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Map<?,?> m = PropertyUtils.describe(o);
        for (Entry e : m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof char[]) {
                char[] chars = (char[]) v;
                e.setValue(new String(chars));
            }
        }
        return m;
    }

    /**
     * Gets the revision from a remote URL - i.e. the part after '@' if any
     *
     * @return the revision or null
     *
     * TODO: This method should be in {@link SVNURL}.
     */
    private static SVNRevision getRevisionFromRemoteUrl(String remoteUrlPossiblyWithRevision) {
        int idx = remoteUrlPossiblyWithRevision.lastIndexOf('@');
        int slashIdx = remoteUrlPossiblyWithRevision.lastIndexOf('/');
        if (idx > 0 && idx > slashIdx) {
            String n = remoteUrlPossiblyWithRevision.substring(idx + 1);
            return SVNRevision.parse(n);
        }

        return null;
    }

    private static StandardCredentials lookupCredentials(Item context, String credentialsId, SVNURL repoURL) {
        return credentialsId == null ? null :
                CredentialsMatchers.firstOrNull(CredentialsProvider
                        .lookupCredentials(StandardCredentials.class, context, ACL.SYSTEM,
                                URIRequirementBuilder.fromUri(repoURL.toString()).build()),
                        CredentialsMatchers.withId(credentialsId));
    }

    public static class AdditionalCredentials extends AbstractDescribableImpl<AdditionalCredentials> {
        @NonNull
        private final String realm;
        @CheckForNull
        private final String credentialsId;

        @DataBoundConstructor
        public AdditionalCredentials(@NonNull String realm, @CheckForNull String credentialsId) {
            realm.getClass(); // throw NPE if null
            this.realm = realm;
            this.credentialsId = credentialsId;
        }

        @NonNull
        public String getRealm() {
            return realm;
        }

        @CheckForNull
        public String getCredentialsId() {
            return credentialsId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AdditionalCredentials)) {
                return false;
            }

            AdditionalCredentials that = (AdditionalCredentials) o;

            if (!realm.equals(that.realm)) {
                return false;
            }
            if (credentialsId != null ? !credentialsId.equals(that.credentialsId) : that.credentialsId != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = realm.hashCode();
            result = 31 * result + (credentialsId != null ? credentialsId.hashCode() : 0);
            return result;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<AdditionalCredentials> {

            @Override
            public String getDisplayName() {
                return null;
            }

            public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                         @QueryParameter String realm) {
                if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                    context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                    return new StandardListBoxModel();
                }
                List<DomainRequirement> domainRequirements;
                if (realm == null) {
                    domainRequirements = Collections.<DomainRequirement>emptyList();
                } else {
                    if (realm.startsWith("<") && realm.contains(">")) {
                        int index = realm.indexOf('>');
                        assert index > 1;
                        domainRequirements = URIRequirementBuilder.fromUri(realm.substring(1, index).trim()).build();
                    } else {
                        domainRequirements = Collections.<DomainRequirement>emptyList();
                    }
                }
                return new StandardListBoxModel()
                        .withEmptySelection()
                        .withMatching(
                                CredentialsMatchers.anyOf(
                                        CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                        CredentialsMatchers.instanceOf(StandardCertificateCredentials.class),
                                        CredentialsMatchers.instanceOf(SSHUserPrivateKey.class)
                                ),
                                CredentialsProvider.lookupCredentials(StandardCredentials.class,
                                        context,
                                        ACL.SYSTEM,
                                        domainRequirements)
                        );
            }

        }
    }
}
