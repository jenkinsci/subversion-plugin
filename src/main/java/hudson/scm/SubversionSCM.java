/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Fulvio Cavarretta,
 * Jean-Baptiste Quenot, Luca Domenico Milanesio, Renaud Bruyeron, Stephen Connolly,
 * Tom Huybrechts, Yahoo! Inc., Manufacture Francaise des Pneumatiques Michelin,
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
package hudson.scm;

import com.thoughtworks.xstream.XStream;
import com.trilead.ssh2.DebugLogger;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.crypto.Base64;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Hudson.MasterComputer;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.DelegatingCallable;
import hudson.remoting.VirtualChannel;
import static hudson.scm.PollingResult.*;
import hudson.scm.UserProvidedCredential.AuthenticationManagerImpl;
import hudson.scm.subversion.CheckoutUpdater;
import hudson.scm.subversion.Messages;
import hudson.scm.subversion.UpdateUpdater;
import hudson.scm.subversion.UpdateWithRevertUpdater;
import hudson.scm.subversion.WorkspaceUpdater;
import hudson.scm.subversion.WorkspaceUpdater.UpdateTask;
import hudson.scm.subversion.WorkspaceUpdaterDescriptor;
import hudson.util.EditDistance;
import hudson.util.FormValidation;
import hudson.util.MultipartFormDataParser;
import hudson.util.Scrambler;
import hudson.util.Secret;
import hudson.util.TimeUnit2;
import hudson.util.XStream2;
import net.sf.json.JSONObject;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Chmod;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationOutcomeListener;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.dav.http.DefaultHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import javax.servlet.ServletException;
import javax.xml.transform.stream.StreamResult;
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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static hudson.Util.*;
import static java.util.logging.Level.*;

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
        this(ModuleLocation.parse(remoteLocations,localLocations), useUpdate, false, browser, excludedRegions, null, null, null);
    }

    /**
     * @deprecated as of 1.315
     */
     public SubversionSCM(String[] remoteLocations, String[] localLocations,
                         boolean useUpdate, SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers, String excludedRevprop) {
        this(ModuleLocation.parse(remoteLocations,localLocations), useUpdate, false, browser, excludedRegions, excludedUsers, excludedRevprop, null);
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

    @DataBoundConstructor
    public SubversionSCM(List<ModuleLocation> locations, WorkspaceUpdater workspaceUpdater,
                         SubversionRepositoryBrowser browser, String excludedRegions, String excludedUsers, String excludedRevprop, String excludedCommitMessages,
                         String includedRegions) {
        for (Iterator<ModuleLocation> itr = locations.iterator(); itr.hasNext();) {
            ModuleLocation ml = itr.next();
            String remote = Util.fixEmptyAndTrim(ml.remote);
            if(remote==null) itr.remove();
        }
        this.locations = locations.toArray(new ModuleLocation[locations.size()]);

        this.workspaceUpdater = workspaceUpdater;
        this.browser = browser;
        this.excludedRegions = excludedRegions;
        this.excludedUsers = excludedUsers;
        this.excludedRevprop = excludedRevprop;
        this.excludedCommitMessages = excludedCommitMessages;
        this.includedRegions = includedRegions;
    }

    /**
     * Convenience constructor, especially during testing.
     */
    public SubversionSCM(String svnUrl) {
        this(svnUrl,".");
    }

    /**
     * Convenience constructor, especially during testing.
     */
    public SubversionSCM(String svnUrl, String local) {
        this(new String[]{svnUrl},new String[]{local},true,null,null,null,null);
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
     * @deprecated Use {@link #getLocations(EnvVars, AbstractBuild)} for vars
     *             expansion to be performed on all env vars rather than just
     *             build parameters.
     */
    public ModuleLocation[] getLocations(AbstractBuild<?,?> build) {
        return getLocations(null, build);
    }

    /**
     * List of all configured svn locations, expanded according to all env vars
     * or, if none defined, according to only build parameters values.
     *
     * @param env If non-null, variable expansions are performed against these vars
     * @param build If non-null (and if env is null), variable expansions are
     *              performed against the build parameters
     */
    public ModuleLocation[] getLocations(EnvVars env, AbstractBuild<?,?> build) {
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
        if(env != null) {
            for (int i = 0; i < outLocations.length; i++) {
                outLocations[i] = locations[i].getExpandedLocation(env);
            }
        }
        else {
            for (int i = 0; i < outLocations.length; i++) {
                outLocations[i] = locations[i].getExpandedLocation(build);
            }
        }

        return outLocations;
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

    /**
     * Sets the <tt>SVN_REVISION</tt> environment variable during the build.
     */
    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env) {
        super.buildEnvVars(build, env);
        
        ModuleLocation[] svnLocations = getLocations(build);

        try {
            Map<String,Long> revisions = parseRevisionFile(build);
            if(svnLocations.length==1) {
                Long rev = revisions.get(svnLocations[0].remote);
                if(rev!=null) {
                    env.put("SVN_REVISION",rev.toString());
                    env.put("SVN_URL",svnLocations[0].getURL());
                }
            } else if(svnLocations.length>1) {
                for(int i=0;i<svnLocations.length;i++) {
                    Long rev = revisions.get(svnLocations[i].remote);
                    if(rev!=null) {
                        env.put("SVN_REVISION_"+(i+1),rev.toString());
                        env.put("SVN_URL_"+(i+1),svnLocations[i].getURL());
                    }
                }
            }

        } catch (IOException e) {
            // ignore this error
        }
    }

    /**
     * Called after checkout/update has finished to compute the changelog.
     */
    private boolean calcChangeLog(AbstractBuild<?,?> build, File changelogFile, BuildListener listener, List<External> externals) throws IOException, InterruptedException {
        if(build.getPreviousBuild()==null) {
            // nothing to compare against
            return createEmptyChangeLog(changelogFile, listener, "log");
        }

        // some users reported that the file gets created with size 0. I suspect
        // maybe some XSLT engine doesn't close the stream properly.
        // so let's do it by ourselves to be really sure that the stream gets closed.
        OutputStream os = new BufferedOutputStream(new FileOutputStream(changelogFile));
        boolean created;
        try {
            created = new SubversionChangeLogBuilder(build, listener, this).run(externals, new StreamResult(os));
        } finally {
            os.close();
        }
        if(!created)
            createEmptyChangeLog(changelogFile, listener, "log");

        return true;
    }


    /*package*/ static Map<String,Long> parseRevisionFile(AbstractBuild<?,?> build) throws IOException {
        return parseRevisionFile(build,false,false);
    }

    /**
     * Reads the revision file of the specified build (or the closest, if the flag is so specified.)
     *
     * @param findClosest
     *      If true, this method will go back the build history until it finds a revision file.
     *      A build may not have a revision file for any number of reasons (such as failure, interruption, etc.)
     * @return
     *      map from {@link SvnInfo#url Subversion URL} to its revision.  If there is more than one, choose
     *      the one with the least revision
     */
    /*package*/ static Map<String,Long> parseRevisionFile(AbstractBuild<?,?> build, boolean findClosest, boolean prunePinnedExternals) throws IOException {
        Map<String,Long> revisions = new HashMap<String,Long>(); // module -> revision

        if (findClosest) {
            for (AbstractBuild<?,?> b=build; b!=null; b=b.getPreviousBuild()) {
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
                    		// perhaps a corrupted line. ignore
                    	}
                }
            } finally {
                br.close();
            }
        }

        return revisions;
    }

    /**
     * Parses the file that stores the locations in the workspace where modules loaded by svn:external
     * is placed.
     *
     * <p>
     * Note that the format of the file has changed in 1.180 from simple text file to XML.
     *
     * @return
     *      immutable list. Can be empty but never null.
     */
    /*package*/ static List<External> parseExternalsFile(AbstractProject project) throws IOException {
        File file = getExternalsFile(project);
        if(file.exists()) {
            try {
                return (List<External>)new XmlFile(External.XSTREAM,file).read();
            } catch (IOException e) {
                // in < 1.180 this file was a text file, so it may fail to parse as XML,
                // in which case let's just fall back
            }
        }

        return Collections.emptyList();
    }

    /**
     * Polling can happen on the master and does not require a workspace.
     */
    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }
    
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, final BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        EnvVars env = build.getEnvironment(listener);
        EnvVarsUtils.overrideAll(env, build.getBuildVariables());

        List<External> externals = checkout(build,workspace,listener,env);

        if(externals==null)
            return false;

        // write out the revision file
        PrintWriter w = new PrintWriter(new FileOutputStream(getRevisionFile(build)));
        try {
            List<SvnInfoP> pList = workspace.act(new BuildRevisionMapTask(build, this, listener, externals));
            List<SvnInfo> revList= new ArrayList<SvnInfo>(pList.size());
            for (SvnInfoP p: pList) {
            	if (p.pinned) 
            		w.println( p.info.url +'/'+ p.info.revision + "::p");
            	else
            		w.println( p.info.url +'/'+ p.info.revision);
            }
            build.addAction(new SubversionTagAction(build,revList));
        } finally {
            w.close();
        }

        // write out the externals info
        new XmlFile(External.XSTREAM,getExternalsFile(build.getProject())).write(externals);

        return calcChangeLog(build, changelogFile, listener, externals);
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
    private List<External> checkout(AbstractBuild build, FilePath workspace, TaskListener listener, EnvVars env) throws IOException, InterruptedException {
        if (repositoryLocationsNoLongerExist(build, listener)) {
            Run lsb = build.getProject().getLastSuccessfulBuild();
            if (lsb != null && build.getNumber()-lsb.getNumber()>10
            && build.getTimestamp().getTimeInMillis()-lsb.getTimestamp().getTimeInMillis() > TimeUnit2.DAYS.toMillis(1)) {
                // Disable this project if the location doesn't exist any more, see issue #763
                // but only do so if there was at least some successful build,
                // to make sure that initial configuration error won't disable the build. see issue #1567
                // finally, only disable a build if the failure persists for some time.
                // see http://www.nabble.com/Should-Hudson-have-an-option-for-a-content-fingerprint--td24022683.html

                listener.getLogger().println("One or more repository locations do not exist anymore for " + build.getProject().getName() + ", project will be disabled.");
                build.getProject().makeDisabled(true);
                return null;
            }
        }

        return workspace.act(new CheckOutTask(build, this, build.getTimestamp().getTime(), listener, env));
    }


    /**
     * Either run "svn co" or "svn up" equivalent.
     */
    private static class CheckOutTask extends UpdateTask implements FileCallable<List<External>> {
        private final UpdateTask task;

        public CheckOutTask(AbstractBuild<?, ?> build, SubversionSCM parent, Date timestamp, TaskListener listener, EnvVars env) {
            this.authProvider = parent.getDescriptor().createAuthenticationProvider(build.getParent());
            this.timestamp = timestamp;
            this.listener = listener;
            this.locations = parent.getLocations(env, build);
            this.revisions = build.getAction(RevisionParameterAction.class);
            this.task = parent.getWorkspaceUpdater().createTask();
        }

        public List<External> invoke(File ws, VirtualChannel channel) throws IOException {
            manager = createSvnClientManager(authProvider);
            this.ws = ws;
            try {
                List<External> externals = perform();

                checkClockOutOfSync();

                return externals;

            } catch (InterruptedException e) {
                throw (InterruptedIOException)new InterruptedIOException().initCause(e);
            } finally {
                manager.dispose();
            }
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
                for (ModuleLocation l : locations) {
                    SVNDirEntry dir = manager.createRepository(l.getSVNURL(),true).info("/",-1);
                    if(dir!=null) {// I don't think this can ever be null, but be defensive
                        if(dir.getDate()!=null && dir.getDate().after(new Date())) // see http://www.nabble.com/NullPointerException-in-SVN-Checkout-Update-td21609781.html that reported this being null.
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
     * Creates {@link SVNClientManager}.
     *
     * <p>
     * This method must be executed on the slave where svn operations are performed.
     *
     * @param authProvider
     *      The value obtained from {@link DescriptorImpl#createAuthenticationProvider(AbstractProject)}.
     *      If the operation runs on slaves,
     *      (and properly remoted, if the svn operations run on slaves.)
     */
    public static SVNClientManager createSvnClientManager(ISVNAuthenticationProvider authProvider) {
        SubversionWorkspaceSelector.syncWorkspaceFormatFromMaster();
        ISVNAuthenticationManager sam = createSvnAuthenticationManager(authProvider);
        return SVNClientManager.newInstance(createDefaultSVNOptions(), sam);
    }

    /**
     * Creates the {@link DefaultSVNOptions}.
     *
     * @return the {@link DefaultSVNOptions}.
     */
    public static DefaultSVNOptions createDefaultSVNOptions() {
        DefaultSVNOptions defaultOptions = SVNWCUtil.createDefaultOptions(true);
        DescriptorImpl descriptor = Hudson.getInstance() == null ? null : Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
        if (defaultOptions != null && descriptor != null) {
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
        
        ISVNAuthenticationManager sam = SVNWCUtil.createDefaultAuthenticationManager(configDir, null, null);
        sam.setAuthenticationProvider(authProvider);
        SVNAuthStoreHandlerImpl.install(sam);
        return sam;
    }

    /**
     * Creates {@link SVNClientManager} for code running on the master.
     * <p>
     * CAUTION: this code only works when invoked on master. On slaves, use
     * {@link #createSvnClientManager(ISVNAuthenticationProvider)} and get {@link ISVNAuthenticationProvider}
     * from the master via remoting. 
     */
    public static SVNClientManager createSvnClientManager(AbstractProject context) {
        return createSvnClientManager(Hudson.getInstance().getDescriptorByType(DescriptorImpl.class).createAuthenticationProvider(context));
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

    public static final class SvnInfoP implements Serializable {
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

        /**
         * @param modulePath
         *      The root of the current module that svn was checking out when it hits 'ext'.
         *      Since we call svnkit multiple times in general case to check out from multiple locations,
         *      we use this to make the path relative to the entire workspace, not just the particular module.
         */
        public External(String modulePath,SVNExternal ext) {
            this.path = modulePath+'/'+ext.getPath();
            this.url = ext.getResolvedURL().toDecodedString();
            this.revision = ext.getRevision().getNumber();
        }

        /**
         * Returns true if this reference is to a fixed revision.
         */
        public boolean isRevisionFixed() {
            return revision!=-1;
        }

        private static final long serialVersionUID = 1L;

        private static final XStream XSTREAM = new XStream2();
        static {
            XSTREAM.alias("external",External.class);
        }
    }


    /**
     * Gets the SVN metadata for the remote repository.
     *
     * @param remoteUrl
     *      The target to run "svn info".
     */
    private static SVNInfo parseSvnInfo(SVNURL remoteUrl, ISVNAuthenticationProvider authProvider) throws SVNException {
        final SVNClientManager manager = createSvnClientManager(authProvider);
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
     *
     * @return
     *      null if the parsing somehow fails. Otherwise a map from the repository URL to revisions.
     */
    private static class BuildRevisionMapTask implements FileCallable<List<SvnInfoP>> {
        private final ISVNAuthenticationProvider authProvider;
        private final TaskListener listener;
        private final List<External> externals;
        private final ModuleLocation[] locations;

        public BuildRevisionMapTask(AbstractBuild<?, ?> build, SubversionSCM parent, TaskListener listener, List<External> externals) {
            this.authProvider = parent.getDescriptor().createAuthenticationProvider(build.getParent());
            this.listener = listener;
            this.externals = externals;
            this.locations = parent.getLocations(build);
        }

        public List<SvnInfoP> invoke(File ws, VirtualChannel channel) throws IOException {
            List<SvnInfoP> revisions = new ArrayList<SvnInfoP>();

            final SVNClientManager manager = createSvnClientManager(authProvider);
            try {
                final SVNWCClient svnWc = manager.getWCClient();
                // invoke the "svn info"
                for( ModuleLocation module : locations ) {
                    try {
                        SvnInfo info = new SvnInfo(svnWc.doInfo(new File(ws,module.getLocalDir()), SVNRevision.WORKING));
                        revisions.add(new SvnInfoP(info, false));
                    } catch (SVNException e) {
                        e.printStackTrace(listener.error("Failed to parse svn info for "+module.remote));
                    }
                }
                for(External ext : externals){
                    try {
                        SvnInfo info = new SvnInfo(svnWc.doInfo(new File(ws,ext.path),SVNRevision.WORKING));
                        revisions.add(new SvnInfoP(info, ext.isRevisionFixed()));
                    } catch (SVNException e) {
                        e.printStackTrace(listener.error("Failed to parse svn info for external "+ext.url+" at "+ext.path));
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
    public static File getRevisionFile(AbstractBuild build) {
        return new File(build.getRootDir(),"revision.txt");
    }

    /**
     * Gets the file that stores the externals.
     */
    private static File getExternalsFile(AbstractProject project) {
        return new File(project.getRootDir(),"svnexternals.txt");
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        // exclude locations that are svn:external-ed with a fixed revision.
        Map<String,Long> wsRev = parseRevisionFile(build,true,true);
        return new SVNRevisionState(wsRev);
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?,?> project, Launcher launcher, FilePath workspace, final TaskListener listener, SCMRevisionState _baseline) throws IOException, InterruptedException {
        final SVNRevisionState baseline;
        if (_baseline instanceof SVNRevisionState) {
            baseline = (SVNRevisionState)_baseline;
        }
        else if (project.getLastBuild()!=null) {
            baseline = (SVNRevisionState)calcRevisionsFromBuild(project.getLastBuild(), launcher, listener);
        }
        else {
            baseline = new SVNRevisionState(null);
        }
        
        if (project.getLastBuild() == null) {
            listener.getLogger().println(Messages.SubversionSCM_pollChanges_noBuilds());
            return BUILD_NOW;
        }

        AbstractBuild<?,?> lastCompletedBuild = project.getLastCompletedBuild();
        if (lastCompletedBuild!=null) {
            if (repositoryLocationsNoLongerExist(lastCompletedBuild, listener)) {
                // Disable this project, see HUDSON-763
                listener.getLogger().println(
                        Messages.SubversionSCM_pollChanges_locationsNoLongerExist(project));
                project.makeDisabled(true);
                return NO_CHANGES;
            }

            // are the locations checked out in the workspace consistent with the current configuration?
            for (ModuleLocation loc : getLocations(lastCompletedBuild)) {
                if (!baseline.revisions.containsKey(loc.getURL())) {
                    listener.getLogger().println(
                            Messages.SubversionSCM_pollChanges_locationNotInWorkspace(loc.getURL()));
                    return BUILD_NOW;
                }
            }
        }

        // determine where to perform polling. prefer the node where the build happened,
        // in case a cluster is non-uniform. see http://www.nabble.com/svn-connection-from-slave-only-td24970587.html
        VirtualChannel ch=null;
        Node n = lastCompletedBuild!=null ? lastCompletedBuild.getBuiltOn() : null;
        if (POLL_FROM_MASTER) {
            n = null;
        }
        if (n!=null) {
            Computer c = n.toComputer();
            if (c!=null)    ch = c.getChannel();
        }
        if (ch==null)   ch= MasterComputer.localChannel;
        final String nodeName = n!=null ? n.getNodeName() : "master";
        final String projectName = project.getName();

        final SVNLogHandler logHandler = new SVNLogHandler(listener);
        // figure out the remote revisions
        final ISVNAuthenticationProvider authProvider = getDescriptor().createAuthenticationProvider(project);

        return ch.call(new DelegatingCallable<PollingResult,IOException> () {
            public ClassLoader getClassLoader() {
                return Hudson.getInstance().getPluginManager().uberClassLoader;
            }

            /**
             * Computes {@link PollingResult}. Note that we allow changes that match the certain paths to be excluded,
             * so
             */
            public PollingResult call() throws IOException {
                listener.getLogger().println("Received SCM poll call on " + nodeName + " for " + projectName + " on " + DateFormat.getDateTimeInstance().format(new Date()) );
                final Map<String,Long> revs = new HashMap<String,Long>();
                boolean changes = false;
                boolean significantChanges = false;

                for (Map.Entry<String,Long> baselineInfo : baseline.revisions.entrySet()) {
                    String url = baselineInfo.getKey();
                    long baseRev = baselineInfo.getValue();
                    /*
                        If we fail to check the remote revision, assume there's no change.
                        In this way, a temporary SVN server problem won't result in bogus builds,
                        which will fail anyway. So our policy in the error handling in the polling
                        is not to fire off builds. see HUDSON-6136.
                     */
                    revs.put(url, baseRev);
                    try {
                        final SVNURL svnurl = SVNURL.parseURIDecoded(url);
                        long nowRev = new SvnInfo(parseSvnInfo(svnurl,authProvider)).revision;

                        changes |= (nowRev>baseRev);

                        listener.getLogger().println(Messages.SubversionSCM_pollChanges_remoteRevisionAt(url, nowRev));
                        revs.put(url, nowRev);
                        // make sure there's a change and it isn't excluded
                        if (logHandler.findNonExcludedChanges(svnurl,
                                baseRev+1, nowRev, authProvider)) {
                            listener.getLogger().println(Messages.SubversionSCM_pollChanges_changedFrom(baseRev));
                            significantChanges = true;
                        }
                    } catch (SVNException e) {
                        e.printStackTrace(listener.error(Messages.SubversionSCM_pollChanges_exception(url)));
                    }
                }
                assert revs.size()== baseline.revisions.size();
                return new PollingResult(baseline,new SVNRevisionState(revs),
                        significantChanges ? Change.SIGNIFICANT : changes ? Change.INSIGNIFICANT : Change.NONE);
            }
        });
    }

    /**
     * Goes through the changes between two revisions and see if all the changes
     * are excluded.
     */
    private final class SVNLogHandler implements ISVNLogEntryHandler, Serializable {
        private boolean changesFound = false;

        private final TaskListener listener;
        private final Pattern[] excludedPatterns = getExcludedRegionsPatterns();
        private final Pattern[] includedPatterns = getIncludedRegionsPatterns();
        private final Set<String> excludedUsers = getExcludedUsersNormalized();
        private final String excludedRevprop = getExcludedRevpropNormalized();
        private final Pattern[] excludedCommitMessages = getExcludedCommitMessagesPatterns();

        private SVNLogHandler(TaskListener listener) {
            this.listener = listener;
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
            if (!hasExclusionRule())    return true;

            final SVNClientManager manager = createSvnClientManager(authProvider);
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
         * Is there any exclusion rule?
         */
        private boolean hasExclusionRule() {
            return excludedPatterns.length>0 || !excludedUsers.isEmpty() || excludedRevprop != null || excludedCommitMessages.length>0 || includedPatterns.length>0;
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
            if (checkLogEntry(logEntry)) {
                changesFound = true;
            }
        }

        /**
         * Checks if the given log entry should be considered for the purposes
         * of SCM polling.
         *
         * @return <code>true</code> if the should trigger polling, <code>false</code> otherwise
         */
        private boolean checkLogEntry(SVNLogEntry logEntry) {
            if (excludedRevprop != null) {
                // If the entry includes the exclusion revprop, don't count it as a change
                SVNProperties revprops = logEntry.getRevisionProperties();
                if (revprops != null && revprops.containsName(excludedRevprop)) {
                    listener.getLogger().println(Messages.SubversionSCM_pollChanges_ignoredRevision(
                            logEntry.getRevision(),
                            Messages.SubversionSCM_pollChanges_ignoredRevision_revprop(excludedRevprop)));
                    return false;
                }
            }

            String author = logEntry.getAuthor();
            if (excludedUsers.contains(author)) {
                // If the author is an excluded user, don't count this entry as a change
                listener.getLogger().println(Messages.SubversionSCM_pollChanges_ignoredRevision(
                        logEntry.getRevision(),
                        Messages.SubversionSCM_pollChanges_ignoredRevision_author(author)));
                return false;
            }

            if (excludedCommitMessages != null) {
                // If the commit message contains one of the excluded messages, don't count it as a change
                String commitMessage = logEntry.getMessage();
                for (Pattern pattern : excludedCommitMessages) {
                    if (pattern.matcher(commitMessage).find()) {
                        return false;
                    }
                }
            }

            // If there were no changes, don't count this entry as a change
            Map changedPaths = logEntry.getChangedPaths();
            if (changedPaths.isEmpty()) {
                return false;
            }

            // If there are included patterns, see which paths are included
            List<String> includedPaths = new ArrayList<String>();
            if (includedPatterns.length > 0) {
                for (String path : (Set<String>)changedPaths.keySet()) {
                    for (Pattern pattern : includedPatterns) {
                        if (pattern.matcher(path).matches()) {
                            includedPaths.add(path);
                            break;
                        }
                    }
                }
            } else {
                includedPaths = new ArrayList<String>(changedPaths.keySet());
            }

            // If no paths are included don't count this entry as a change
            if (includedPaths.isEmpty()) {
                listener.getLogger().println(Messages.SubversionSCM_pollChanges_ignoredRevision(
                        logEntry.getRevision(),
                        Messages.SubversionSCM_pollChanges_ignoredRevision_noincpath()));
                return false;
            }

            // Else, check each changed path
            List<String> excludedPaths = new ArrayList<String>();
            if (excludedPatterns.length > 0) {
                for (String path : includedPaths) {
                    for (Pattern pattern : excludedPatterns) {
                        if (pattern.matcher(path).matches()) {
                            excludedPaths.add(path);
                            break;
                        }
                    }
                }
            }

            // If all included paths are in an excluded region, don't count this entry as a change
            if (includedPaths.size() == excludedPaths.size()) {
                listener.getLogger().println(Messages.SubversionSCM_pollChanges_ignoredRevision(
                        logEntry.getRevision(),
                        Messages.SubversionSCM_pollChanges_ignoredRevision_path(Util.join(excludedPaths, ", "))));
                return false;
            }

            // Otherwise, a change is a change
            return true;
        }

        private static final long serialVersionUID = 1L;
    }

    public ChangeLogParser createChangeLogParser() {
        return new SubversionChangeLogParser();
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public FilePath getModuleRoot(FilePath workspace) {
        if (getLocations().length > 0)
            return workspace.child(getLocations()[0].getLocalDir());
        return workspace;
    }

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

    private static String getLastPathComponent(String s) {
        String[] tokens = s.split("/");
        return tokens[tokens.length-1]; // return the last token
    }

    @Extension
    public static class DescriptorImpl extends SCMDescriptor<SubversionSCM> implements hudson.model.ModelObject {
        /**
         * SVN authentication realm to its associated credentials.
         * This is the global credential repository.
         */
        private final Map<String,Credential> credentials = new Hashtable<String,Credential>();

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
             * @param kind
             *      One of the constants defined in {@link ISVNAuthenticationManager},
             *      indicating what subtype of {@link SVNAuthentication} is expected.
             */
            public abstract SVNAuthentication createSVNAuthentication(String kind) throws SVNException;
        }

        /**
         * Username/password based authentication.
         */
        public static final class PasswordCredential extends Credential {
            private final String userName;
            private final String password; // scrambled by base64

            public PasswordCredential(String userName, String password) {
                this.userName = userName;
                this.password = Scrambler.scramble(password);
            }

            @Override
            public SVNAuthentication createSVNAuthentication(String kind) {
                if(kind.equals(ISVNAuthenticationManager.SSH))
                    return new SVNSSHAuthentication(userName,Scrambler.descramble(password),-1,false);
                else
                    return new SVNPasswordAuthentication(userName,Scrambler.descramble(password),false);
            }
        }

        /**
         * Public key authentication for Subversion over SSH.
         */
        public static final class SshPublicKeyCredential extends Credential {
            private final String userName;
            private final String passphrase; // scrambled by base64
            private final String id;

            /**
             * @param keyFile
             *      stores SSH private key. The file will be copied.
             */
            public SshPublicKeyCredential(String userName, String passphrase, File keyFile) throws SVNException {
                this.userName = userName;
                this.passphrase = Scrambler.scramble(passphrase);

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
                    throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE,"Unable to save private key"),e);
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
                            privateKey = channel.call(new Callable<String,IOException>() {
                                public String call() throws IOException {
                                    return FileUtils.readFileToString(getKeyFile(),"iso-8859-1");
                                }
                            });
                        } else {
                            privateKey = FileUtils.readFileToString(getKeyFile(),"iso-8859-1");
                        }
                        return new SVNSSHAuthentication(userName, privateKey.toCharArray(), Scrambler.descramble(passphrase),-1,false);
                    } catch (IOException e) {
                        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE,"Unable to load private key"),e);
                    } catch (InterruptedException e) {
                        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE,"Unable to load private key"),e);
                    }
                } else
                    return null; // unknown
            }
        }

        /**
         * SSL client certificate based authentication.
         */
        public static final class SslClientCertificateCredential extends Credential {
            private final Secret certificate;
            private final String password; // scrambled by base64

            public SslClientCertificateCredential(File certificate, String password) throws IOException {
                this.password = Scrambler.scramble(password);
                this.certificate = Secret.fromString(new String(Base64.encode(FileUtils.readFileToByteArray(certificate))));
            }

            @Override
            public SVNAuthentication createSVNAuthentication(String kind) {
                if(kind.equals(ISVNAuthenticationManager.SSL))
                    try {
                        return new SVNSSLAuthentication(
                                Base64.decode(certificate.getPlainText().toCharArray()),
                                Scrambler.descramble(password),false);
                    } catch (IOException e) {
                        throw new Error(e); // can't happen
                    }
                else
                    return null; // unexpected authentication type
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

        /**
         * See {@link DescriptorImpl#createAuthenticationProvider(AbstractProject)}.
         */
        private static final class SVNAuthenticationProviderImpl implements ISVNAuthenticationProvider, ISVNAuthenticationOutcomeListener, Serializable {
            /**
             * Project-scoped authentication source. For historical reasons, can be null.
             */
            private final RemotableSVNAuthenticationProvider local;

            /**
             * System-wide authentication source. Used as a fallback.
             */
            private final RemotableSVNAuthenticationProvider global;

            /**
             * The {@link Credential} used to create the last {@link SVNAuthentication} that we've tried.
             */
            private Credential lastCredential;

            public SVNAuthenticationProviderImpl(RemotableSVNAuthenticationProvider local, RemotableSVNAuthenticationProvider global) {
                this.global = global;
                this.local = local;
            }

            private SVNAuthentication fromProvider(SVNURL url, String realm, String kind, RemotableSVNAuthenticationProvider src, String debugName) throws SVNException {
                if (src==null)  return null;
                
                Credential cred = src.getCredential(url,realm);
                LOGGER.fine(String.format("%s.requestClientAuthentication(%s,%s,%s)=>%s",debugName,kind,url,realm,cred));
                this.lastCredential = cred;
                if(cred!=null)  return cred.createSVNAuthentication(kind);
                return null;
            }

            public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {

                try {
                    SVNAuthentication auth=fromProvider(url,realm,kind,local,"local");

                    // first try the local credential, then the global credential.
                    if (auth==null || compareSVNAuthentications(auth,previousAuth))
                        auth = fromProvider(url,realm,kind,global,"global");

                    if(previousAuth!=null && compareSVNAuthentications(auth,previousAuth)) {
                        // See HUDSON-2909
                        // this comparison is necessary, unlike the original fix of HUDSON-2909, since SVNKit may use
                        // other ISVNAuthenticationProviders and their failed auth might be passed to us.
                        // see HUDSON-3936
                        LOGGER.log(FINE, "Previous authentication attempt failed, so aborting: {0}", previousAuth);
                        return null;
                    }

                    if(auth==null && ISVNAuthenticationManager.USERNAME.equals(kind)) {
                        // this happens with file:// URL and svn+ssh (in this case this method gets invoked twice.)
                        // The base class does this, too.
                        // user auth shouldn't be null.
                        return new SVNUserNameAuthentication("",false);
                    }

                    return auth;
                } catch (SVNException e) {
                    LOGGER.log(Level.SEVERE, "Failed to authorize",e);
                    throw new RuntimeException("Failed to authorize",e);
                }
            }

            public void acknowledgeAuthentication(boolean accepted, String kind, String realm, SVNErrorMessage errorMessage, SVNAuthentication authentication) throws SVNException {
                if (accepted && local!=null && lastCredential!=null)
                    local.acknowledgeAuthentication(realm,lastCredential);
            }

            public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
                return ACCEPTED_TEMPORARY;
            }

            private static final long serialVersionUID = 1L;
        }

        @Override
        public SCM newInstance(StaplerRequest staplerRequest, JSONObject jsonObject) throws FormException {
            return super.newInstance(staplerRequest, jsonObject);
        }

        public DescriptorImpl() {
            super(SubversionRepositoryBrowser.class);
            load();
        }

        protected DescriptorImpl(Class clazz, Class<? extends RepositoryBrowser> repositoryBrowser) {
            super(clazz,repositoryBrowser);
        }

        public String getDisplayName() {
            return "Subversion";
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
         */
        public ISVNAuthenticationProvider createAuthenticationProvider(AbstractProject<?,?> inContextOf) {
            return new SVNAuthenticationProviderImpl(
                    inContextOf==null ? null : new PerJobCredentialStore(inContextOf),remotableProvider);
        }

        /**
         * @deprecated as of 1.18
         *      Now that Hudson allows different credentials to be given in different jobs,
         *      The caller should use {@link #createAuthenticationProvider(AbstractProject)} to indicate
         *      the project in which the subversion operation is performed.
         */
        public ISVNAuthenticationProvider createAuthenticationProvider() {
            return new SVNAuthenticationProviderImpl(null,remotableProvider);
        }

        /**
         * Submits the authentication info.
         */
        // TODO: stapler should do multipart/form-data handling 
        public void doPostCredential(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            Hudson.getInstance().checkPermission(Hudson.ADMINISTER);

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
                AuthenticationManagerImpl authManager = upc.new AuthenticationManagerImpl(logWriter) {
                    @Override
                    protected void onSuccess(String realm, Credential cred) {
                        LOGGER.info("Persisted "+cred+" for "+realm);
                        credentials.put(realm, cred);
                        save();
                        if (upc.inContextOf!=null)
                            new PerJobCredentialStore(upc.inContextOf).acknowledgeAuthentication(realm,cred);

                    }
                };
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
         * validate the value for a remote (repository) location.
         */
        public FormValidation doCheckRemote(StaplerRequest req, @AncestorInPath AbstractProject context, @QueryParameter String value) {
            // syntax check first
            String url = Util.fixEmptyAndTrim(value);
            if (url == null)
                return FormValidation.error(Messages.SubversionSCM_doCheckRemote_required()); 

            if(isValidateRemoteUpToVar()) {
                url = (url.indexOf('$') != -1) ? url.substring(0, url.indexOf('$')) : url;
            }

            if(!URL_PATTERN.matcher(url).matches())
                return FormValidation.errorWithMarkup(
                    Messages.SubversionSCM_doCheckRemote_invalidUrl());

            // Test the connection only if we have admin permission
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                return FormValidation.ok();

            try {
                String urlWithoutRevision = getUrlWithoutRevision(url);
            	
                SVNURL repoURL = SVNURL.parseURIDecoded(urlWithoutRevision);
                if (checkRepositoryPath(context,repoURL)!=SVNNodeKind.NONE) {
                    // something exists; now check revision if any
                    
                    SVNRevision revision = getRevisionFromRemoteUrl(url);
                    if (revision != null && !revision.isValid()) {
                        return FormValidation.errorWithMarkup(Messages.SubversionSCM_doCheckRemote_invalidRevision());
                    }
                    
                    return FormValidation.ok();
                }
                
                SVNRepository repository = null;
                try {
                    repository = getRepository(context,repoURL);
                    long rev = repository.getLatestRevision();
                    // now go back the tree and find if there's anything that exists
                    String repoPath = getRelativePath(repoURL, repository);
                    String p = repoPath;
                    while(p.length()>0) {
                        p = SVNPathUtil.removeTail(p);
                        if(repository.checkPath(p,rev)==SVNNodeKind.DIR) {
                            // found a matching path
                            List<SVNDirEntry> entries = new ArrayList<SVNDirEntry>();
                            repository.getDir(p,rev,false,entries);

                            // build up the name list
                            List<String> paths = new ArrayList<String>();
                            for (SVNDirEntry e : entries)
                                if(e.getKind()==SVNNodeKind.DIR)
                                    paths.add(e.getName());

                            String head = SVNPathUtil.head(repoPath.substring(p.length() + 1));
                            String candidate = EditDistance.findNearest(head,paths);

                            return FormValidation.error(
                                Messages.SubversionSCM_doCheckRemote_badPathSuggest(p, head,
                                    candidate != null ? "/" + candidate : ""));
                        }
                    }

                    return FormValidation.error(
                        Messages.SubversionSCM_doCheckRemote_badPath(repoPath));
                } finally {
                    if (repository != null)
                        repository.closeSession();
                }
            } catch (SVNException e) {
                LOGGER.log(Level.INFO, "Failed to access subversion repository "+url,e);
                String message = Messages.SubversionSCM_doCheckRemote_exceptionMsg1(
                    Util.escape(url), Util.escape(e.getErrorMessage().getFullMessage()),
                    "javascript:document.getElementById('svnerror').style.display='block';"
                      + "document.getElementById('svnerrorlink').style.display='none';"
                      + "return false;")
                  + "<br/><pre id=\"svnerror\" style=\"display:none\">"
                  + Functions.printThrowable(e) + "</pre>"
                  + Messages.SubversionSCM_doCheckRemote_exceptionMsg2(
                      "descriptorByName/"+SubversionSCM.class.getName()+"/enterCredential?" + url);
                return FormValidation.errorWithMarkup(message);
            }
        }

        public SVNNodeKind checkRepositoryPath(AbstractProject context, SVNURL repoURL) throws SVNException {
            SVNRepository repository = null;

            try {
                repository = getRepository(context,repoURL);
                repository.testConnection();

                long rev = repository.getLatestRevision();
                String repoPath = getRelativePath(repoURL, repository);
                return repository.checkPath(repoPath, rev);
            } finally {
                if (repository != null)
                    repository.closeSession();
            }
        }

        protected SVNRepository getRepository(AbstractProject context, SVNURL repoURL) throws SVNException {
            SVNRepository repository = SVNRepositoryFactory.create(repoURL);

            ISVNAuthenticationManager sam = createSvnAuthenticationManager(createAuthenticationProvider(context));
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
            String repoPath = repoURL.getPath().substring(repository.getRepositoryRoot(false).getPath().length());
            if(!repoPath.startsWith("/"))    repoPath="/"+repoPath;
            return repoPath;
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
        public FormValidation doCheckRevisionPropertiesSupported(@AncestorInPath AbstractProject context, @QueryParameter String value) throws IOException, ServletException {
            String v = Util.fixNull(value).trim();
            if (v.length() == 0)
                return FormValidation.ok();

            // Test the connection only if we have admin permission
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                return FormValidation.ok();

            try {
                SVNURL repoURL = SVNURL.parseURIDecoded(v);
                if (checkRepositoryPath(context,repoURL)!=SVNNodeKind.NONE)
                    // something exists
                    return FormValidation.ok();

                SVNRepository repository = null;
                try {
                    repository = getRepository(context,repoURL);
                    if (repository.hasCapability(SVNCapability.LOG_REVPROPS))
                        return FormValidation.ok();
                } finally {
                    if (repository != null)
                        repository.closeSession();
                }
            } catch (SVNException e) {
                String message="";
                message += "Unable to access "+Util.escape(v)+" : "+Util.escape( e.getErrorMessage().getFullMessage());
                LOGGER.log(Level.INFO, "Failed to access subversion repository "+v,e);
                return FormValidation.errorWithMarkup(message);
            }

            return FormValidation.warning(Messages.SubversionSCM_excludedRevprop_notSupported(v));
        }
        
        static {
            new Initializer();
        }

    }

    public boolean repositoryLocationsNoLongerExist(AbstractBuild<?,?> build, TaskListener listener) {
        PrintStream out = listener.getLogger();

        for (ModuleLocation l : getLocations(build))
            try {
                if (getDescriptor().checkRepositoryPath(build.getProject(), l.getSVNURL()) == SVNNodeKind.NONE) {
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
    public static final class ModuleLocation implements Serializable {
        /**
         * Subversion URL to check out.
         *
         * This may include "@NNN" at the end to indicate a fixed revision.
         */
        @Exported
        public final String remote;

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
         * Cache of the repository UUID.
         */
        private transient volatile UUID repositoryUUID;
        private transient volatile SVNURL repositoryRoot;

        @DataBoundConstructor
        public ModuleLocation(String remote, String local) {
            this.remote = Util.removeTrailingSlash(Util.fixNull(remote).trim());
            this.local = fixEmptyAndTrim(local);
        }

        /**
         * Local directory to place the file to.
         * Relative to the workspace root.
         */
        public String getLocalDir() {
            if(local==null)
                return getLastPathComponent(remote);
            return local;
        }

        /**
         * Returns the pure URL portion of {@link #remote} by removing
         * possible "@NNN" suffix.
         */
        public String getURL() {
        	return getUrlWithoutRevision(remote);
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
        public UUID getUUID(AbstractProject context) throws SVNException {
            if(repositoryUUID==null || repositoryRoot==null) {
                synchronized (this) {
                    SVNRepository r = openRepository(context);
                    r.testConnection(); // make sure values are fetched
                    repositoryUUID = UUID.fromString(r.getRepositoryUUID(false));
                    repositoryRoot = r.getRepositoryRoot(false);
                }
            }
            return repositoryUUID;
        }

        public SVNRepository openRepository(AbstractProject context) throws SVNException {
            return Hudson.getInstance().getDescriptorByType(DescriptorImpl.class).getRepository(context,getSVNURL());
        }

        public SVNURL getRepositoryRoot(AbstractProject context) throws SVNException {
            getUUID(context);
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
         * Expand location value based on Build parametric execution.
         *
         * @param build Build instance for expanding parameters into their values
         * @return Output ModuleLocation expanded according to Build parameters values.
         * @deprecated Use {@link #getExpandedLocation(EnvVars)} for vars expansion
         *             to be performed on all env vars rather than just build parameters.
         */
        public ModuleLocation getExpandedLocation(AbstractBuild<?, ?> build) {
            return new ModuleLocation(getExpandedRemote(build), getLocalDir());
        }
        
        /**
         * Expand location value based on environment variables.
         *
         * @return Output ModuleLocation expanded according to specified env vars.
         */
        public ModuleLocation getExpandedLocation(EnvVars env) {
            return new ModuleLocation(env.expand(remote), getLocalDir());
        }

        @Override
        public String toString() {
            return remote;
        }

        private static final long serialVersionUID = 1L;

        public static List<ModuleLocation> parse(String[] remoteLocations, String[] localLocations) {
            List<ModuleLocation> modules = new ArrayList<ModuleLocation>();
            if (remoteLocations != null && localLocations != null) {
                int entries = Math.min(remoteLocations.length, localLocations.length);

                for (int i = 0; i < entries; i++) {
                    // the remote (repository) location
                    String remoteLoc = Util.nullify(remoteLocations[i]);

                    if (remoteLoc != null) {// null if skipped
                        remoteLoc = Util.removeTrailingSlash(remoteLoc.trim());
                        modules.add(new ModuleLocation(remoteLoc, Util.nullify(localLocations[i])));
                    }
                }
            }
            return modules;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SubversionSCM.class.getName());

    /**
     * Network timeout in milliseconds.
     * The main point of this is to prevent infinite hang, so it should be a rather long value to avoid
     * accidental time out problem.
     */
    public static int DEFAULT_TIMEOUT = Integer.getInteger(SubversionSCM.class.getName()+".timeout",3600*1000);

    /**
     * Property to control whether SCM polling happens from the slave or master
     */
    public static boolean POLL_FROM_MASTER = Boolean.getBoolean(SubversionSCM.class.getName()+".pollFromMaster");

    /**
     * If set to non-null, read configuration from this directory instead of "~/.subversion".
     */
    public static String CONFIG_DIR = System.getProperty(SubversionSCM.class.getName()+".configDir");
    
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

    private static String getUrlWithoutRevision(
            String remoteUrlPossiblyWithRevision) {
        int idx = remoteUrlPossiblyWithRevision.lastIndexOf('@');
        int slashIdx = remoteUrlPossiblyWithRevision.lastIndexOf('/');
        if (idx > 0 && idx > slashIdx) {
            String n = remoteUrlPossiblyWithRevision.substring(idx + 1);
            SVNRevision r = SVNRevision.parse(n);
            if ((r != null) && (r.isValid())) {
                return remoteUrlPossiblyWithRevision.substring(0, idx);
            }
        }
        return remoteUrlPossiblyWithRevision;
    }

    /**
     * Gets the revision from a remote URL - i.e. the part after '@' if any
     * 
     * @return the revision or null
     */
    private static SVNRevision getRevisionFromRemoteUrl(
            String remoteUrlPossiblyWithRevision) {
        int idx = remoteUrlPossiblyWithRevision.lastIndexOf('@');
        int slashIdx = remoteUrlPossiblyWithRevision.lastIndexOf('/');
        if (idx > 0 && idx > slashIdx) {
            String n = remoteUrlPossiblyWithRevision.substring(idx + 1);
            return SVNRevision.parse(n);
        }

        return null;
    }

}
