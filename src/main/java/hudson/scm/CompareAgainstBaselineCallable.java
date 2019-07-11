package hudson.scm;

import hudson.model.TaskListener;
import hudson.remoting.DelegatingCallable;
import hudson.scm.PollingResult.Change;
import hudson.scm.SubversionSCM.SVNLogHandler;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.scm.subversion.Messages;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
/**
 * Callable which compares the given baseline against the current state of the svn repository and returns the
 * appropriate {@link PollingResult} as answer.
 * 
 * @author kutzi
 */
final class CompareAgainstBaselineCallable extends MasterToSlaveCallable<PollingResult,IOException> implements DelegatingCallable<PollingResult, IOException> {
    private final SVNLogHandler logHandler;
    private final String projectName;
    private final SVNRevisionState baseline;
    private final TaskListener listener;
    private final ISVNAuthenticationProvider defaultAuthProvider;
    private final Map<String, ISVNAuthenticationProvider> authProviders;
    private final String nodeName;
    private static final long serialVersionUID = 8200959096894789583L;

    CompareAgainstBaselineCallable(SVNRevisionState baseline, SVNLogHandler logHandler, String projectName,
                                   TaskListener listener, ISVNAuthenticationProvider defaultAuthProvider,
                                   Map<String, ISVNAuthenticationProvider> authProviders, String nodeName) {
        this.logHandler = logHandler;
        this.projectName = projectName;
        this.baseline = baseline;
        this.listener = listener;
        this.defaultAuthProvider = defaultAuthProvider;
        this.authProviders = authProviders;
        this.nodeName = nodeName;
    }

    public ClassLoader getClassLoader() {
        return Jenkins.getInstance().getPluginManager().uberClassLoader;
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
                ISVNAuthenticationProvider authProvider = authProviders.get(url);
                if (authProvider == null) {
                    authProvider = defaultAuthProvider;
                }
                final SVNURL svnurl = SVNURL.parseURIDecoded(url);
                long nowRev = new SvnInfo(SubversionSCM.parseSvnInfo(svnurl, authProvider)).revision;

                changes |= (nowRev>baseRev);

                listener.getLogger().println(Messages.SubversionSCM_pollChanges_remoteRevisionAt(url, nowRev));
                revs.put(url, nowRev);
                // make sure there's a change and it isn't excluded
                if (logHandler.findNonExcludedChanges(svnurl, baseRev+1, nowRev, authProvider)) {
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
}
