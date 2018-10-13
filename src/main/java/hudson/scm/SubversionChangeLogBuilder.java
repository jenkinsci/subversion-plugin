/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Jean-Baptiste Quenot, Luca Domenico Milanesio, Renaud Bruyeron
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

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.FilePath.FileCallable;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.transform.Result;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import java.io.IOException;
import java.io.PrintStream;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import jenkins.MasterToSlaveFileCallable;

/**
 * Builds <tt>changelog.xml</tt> for {@link SubversionSCM}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SubversionChangeLogBuilder {
    /**
     * Revisions of the workspace before the update/checkout.
     */
    private final Map<String,Long> previousRevisions;
    /**
     * Revisions of the workspace after the update/checkout.
     */
    private final Map<String,Long> thisRevisions;

    private final TaskListener listener;
    private final SubversionSCM scm;
    private final Run<?,?> build;
    private final FilePath workspace;
    private final EnvVars env;

    /**
     * @deprecated 1.34
     */
    public SubversionChangeLogBuilder(AbstractBuild<?,?> build, BuildListener listener, SubversionSCM scm) throws IOException {
        this(build, build.getWorkspace(), build.getPreviousBuild().getAction(SVNRevisionState.class), null, listener, scm);
    }

    /**
     * @since  1.34
     */
    public SubversionChangeLogBuilder(Run<?,?> build, FilePath workspace, @Nonnull SVNRevisionState baseline, EnvVars env, TaskListener listener, SubversionSCM scm) throws IOException {
        previousRevisions = baseline.revisions;
        thisRevisions     = scm.parseSvnRevisionFile(build);
        this.listener = listener;
        this.scm = scm;
        this.build = build;
        this.workspace = workspace;
        this.env = env;
    }

    public boolean run(@Nonnull Map<String, List<SubversionSCM.External>> externalsMap, Result changeLog) throws IOException, InterruptedException {
        boolean changelogFileCreated = false;

        TransformerHandler th = createTransformerHandler();
        th.setResult(changeLog);
        SVNLogFilter logFilter = scm.isFilterChangelog() ? scm.createSVNLogFilter() : new NullSVNLogFilter();
        DirAwareSVNXMLLogHandler logHandler = new DirAwareSVNXMLLogHandler(th, logFilter);
        // work around for http://svnkit.com/tracker/view.php?id=175
        th.setDocumentLocator(DUMMY_LOCATOR);
        logHandler.startDocument();

        for (ModuleLocation l : scm.getLocations(env, build)) {
            ISVNAuthenticationProvider authProvider =
                    CredentialsSVNAuthenticationProviderImpl
                            .createAuthenticationProvider(build.getParent(), scm, l, listener);
            final SVNClientManager manager = SubversionSCM.createClientManager(authProvider).getCore();
            try {
                SVNLogClient svnlc = manager.getLogClient();
                PathContext context = getUrlForPath(workspace.child(l.getLocalDir()), authProvider);
                context.moduleWorkspacePath = l.getLocalDir();
                changelogFileCreated |= buildModule(context, svnlc, logHandler);

                // externals for this module location
                List<SubversionSCM.External> externals = externalsMap.get(l.remote);
                if (externals != null) {
                  for (SubversionSCM.External ext : externals) {
                    PathContext extContext = getUrlForPath(workspace.child(ext.path), authProvider);
                    extContext.moduleWorkspacePath = ext.path;
                    changelogFileCreated |= buildModule(extContext, svnlc, logHandler);
                  }
                }
            } finally {
                manager.dispose();
            }
        }

        if(changelogFileCreated) {
            logHandler.endDocument();
        }

        return changelogFileCreated;
    }

    private PathContext getUrlForPath(FilePath path, ISVNAuthenticationProvider authProvider) throws IOException, InterruptedException {
        return path.act(new GetContextForPath(authProvider));
    }

    private boolean buildModule(PathContext context, SVNLogClient svnlc, DirAwareSVNXMLLogHandler logHandler) throws IOException {
        String url = context.url;
        PrintStream logger = listener.getLogger();

        try {
            SVNURL repoURL = SVNURL.parseURIEncoded(url);

            Long prevRev = previousRevisions.get(url);
            if (prevRev == null) {
                logger.println("No revision recorded for " + repoURL + " in the previous build");
                return false;
            }

            Long thisRev = thisRevisions.get(url);
            if (thisRev == null) {
                listener.error("No revision found for " + repoURL + " in " + SubversionSCM.getRevisionFile(build) + "" +
                        ". Revision file contains: " + thisRevisions.keySet());
                return false;
            }

            if (thisRev.equals(prevRev)) {
                logger.println("No changes for " + repoURL + " since the previous build");
                return false;
            }

            // handle case where previous workspace revision is newer than this revision
            if (prevRev.compareTo(thisRev) > 0) {
                long temp = thisRev;
                thisRev = prevRev;
                prevRev = temp;
            }

            logHandler.setContext(context);

            if (debug) {
                listener.getLogger().printf("Computing changelog of %1s from %2s to %3s%n",
                        SVNURL.parseURIEncoded(url), prevRev + 1, thisRev);
            }

            svnlc.doLog(SVNURL.parseURIEncoded(url),
                    null,
                    SVNRevision.UNDEFINED,
                    SVNRevision.create(prevRev + 1),
                    SVNRevision.create(thisRev),
                    false, // Don't stop on copy.
                    true, // Report paths.
                    0, // Retrieve log entries for unlimited number of revisions.
                    debug ? new DebugSVNLogHandler(logHandler) : logHandler);

            if (debug) {
                listener.getLogger().println("done");
            }
        } catch (SVNException e) {
            throw new IOException("revision check failed on " + url, e);
        }
        return true;
    }

    /**
     * Filter {@link ISVNLogEntryHandler} that dumps information. Used only for debugging.
     */
    private class DebugSVNLogHandler implements ISVNLogEntryHandler {
        private final ISVNLogEntryHandler core;

        private DebugSVNLogHandler(ISVNLogEntryHandler core) {
            this.core = core;
        }

        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            listener.getLogger().println("SVNLogEntry="+logEntry);
            core.handleLogEntry(logEntry);
        }
    }

    /**
     * Creates an identity transformer.
     */
    private static TransformerHandler createTransformerHandler() {
        try {
            return ((SAXTransformerFactory) SAXTransformerFactory.newInstance()).newTransformerHandler();
        } catch (TransformerConfigurationException e) {
            throw new Error(e); // impossible
        }
    }

    private static final LocatorImpl DUMMY_LOCATOR = new LocatorImpl();

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "MS_SHOULD_BE_FINAL",
    justification = "Debugging environment variable is made editable, so it can be modified through the groovy console.")
    public static boolean debug = false;

    static {
        DUMMY_LOCATOR.setLineNumber(-1);
        DUMMY_LOCATOR.setColumnNumber(-1);
    }

    private static class GetContextForPath extends MasterToSlaveFileCallable<PathContext> {
        private final ISVNAuthenticationProvider authProvider;

        public GetContextForPath(ISVNAuthenticationProvider authProvider) {
            this.authProvider = authProvider;
        }

        public PathContext invoke(File p, VirtualChannel channel) throws IOException {
            final SvnClientManager manager = SubversionSCM.createClientManager(authProvider);
            try {
                final SVNWCClient svnwc = manager.getWCClient();

                SVNInfo info;
                try {
                    info = svnwc.doInfo(p, SVNRevision.WORKING);
                    String url = info.getURL().toDecodedString();
                    String repoRoot = info.getRepositoryRootURL().toDecodedString();
                    return new PathContext(url, repoRoot, null);
                } catch (SVNException e) {
                    e.printStackTrace();
                    return null;
                }
            } finally {
                manager.dispose();
            }
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * This class encapsulates context information for the paths in the change log.
     */
    @Restricted(NoExternalUse.class)
    public static class PathContext implements Serializable {
        private PathContext(String url, String repoUrl, String moduleWorkspacePath) {
            this.url = url;
            this.moduleWorkspacePath = moduleWorkspacePath;
            this.repoUrl = repoUrl;
        }
        public String url; // full URL to file
        public String repoUrl; // full URL to module root
        public String moduleWorkspacePath;  // path to module root relative from workspace root
        private static final long serialVersionUID = 1L;
    }
}
