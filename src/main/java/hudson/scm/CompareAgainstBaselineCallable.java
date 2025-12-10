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
    private final boolean storeAuthToDisk = SubversionSCM.descriptor().isStoreAuthToDisk();
    private final int workspaceFormat = SubversionSCM.descriptor().getWorkspaceFormat();
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
    public PollingResult call() {
        listener.getLogger().println("Received SCM poll call on " + nodeName + " for " + projectName + " on " + DateFormat.getDateTimeInstance().format(new Date()) );
        final Map<String,Long> revs = new HashMap<>();
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
                ChangeState revChanges = checkInternal(url,authProvider,baseRev,revs);
                changes |= revChanges.changes;
                significantChanges |= revChanges.significantChanges;

            } catch (SVNException e) {
                boolean success = false;

                // normal auth provider handling is not working
                // we don't know which external revision belongs to which module -> we try all authproviders provided
                for(ISVNAuthenticationProvider authProvider : authProviders.values()){
                    try{
                        ChangeState revChanges = checkInternal(url,authProvider,baseRev,revs);
                        changes |= revChanges.changes;
                        significantChanges |= revChanges.significantChanges;
                        success = true;
                        break;
                    }catch(SVNException ignored){}
                }
                if(!success){
                    e.printStackTrace(listener.error(Messages.SubversionSCM_pollChanges_exception(url)));
                }
            }
        }

        assert revs.size()== baseline.revisions.size();
        return new PollingResult(baseline,new SVNRevisionState(revs),
                significantChanges ? Change.SIGNIFICANT : changes ? Change.INSIGNIFICANT : Change.NONE);
    }

    static class ChangeState{
        boolean changes = false;
        boolean significantChanges = false;
    }

    private ChangeState checkInternal(String url,ISVNAuthenticationProvider authProvider, long baseRev, Map<String,Long> revs) throws SVNException {
        ChangeState changes = new ChangeState();
        final SVNURL svnurl = SVNURL.parseURIDecoded(url);
        long nowRev = new SvnInfo(SubversionSCM.parseSvnInfo(svnurl, authProvider, storeAuthToDisk, workspaceFormat)).revision;

        changes.changes |= (nowRev>baseRev);

        listener.getLogger().println(Messages.SubversionSCM_pollChanges_remoteRevisionAt(url, nowRev));
        if(revs.containsKey(url)){
            long containingRevision = revs.get(url);
            // take maximum revision
            if(nowRev > containingRevision){
                revs.put(url, nowRev);
            }
        }else {
            revs.put(url, nowRev);
        }
        // make sure there's a change and it isn't excluded
        if (logHandler.findNonExcludedChanges(svnurl, baseRev+1, nowRev, authProvider)) {
            listener.getLogger().println(Messages.SubversionSCM_pollChanges_changedFrom(baseRev));
            changes.significantChanges = true;
        }
        return changes;
    }

}
