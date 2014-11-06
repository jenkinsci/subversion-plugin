package hudson.scm;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNBasicClient;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc2.SvnSetWCDbVersion;

/**
 * {@link SVNClientManager} makes it rather error prone to specify the proper expected version
 * of the working copy we need to use, so inside Jenkins we wrap it to this class to ensure
 * we won't forget to set {@link SvnWcGeneration} when using {@link SVNBasicClient}.
 *
 * @author Kohsuke Kawaguchi
 */
public class SvnClientManager {
    private final SVNClientManager core;
    private final SvnWcGeneration wcgen;

    public SvnClientManager(SVNClientManager core) {
        this.core = core;
        SubversionWorkspaceSelector.syncWorkspaceFormatFromMaster();
        wcgen = SubversionWorkspaceSelector.workspaceFormat>= ISVNWCDb.WC_FORMAT_18 ? SvnWcGeneration.V17 : SvnWcGeneration.V16;
    }

    public SVNClientManager getCore() {
        return core;
    }

    public SVNWCClient getWCClient() {
        return wrapUp(core.getWCClient());
    }

    public SVNLogClient getLogClient() {
        return wrapUp(core.getLogClient());
    }

    private <T extends SVNBasicClient> T wrapUp(T client) {
        client.getOperationsFactory().setPrimaryWcGeneration(wcgen);
        return client;
    }

    public void dispose() {
        core.dispose();
    }

    public SVNCommitClient getCommitClient() {
        return wrapUp(core.getCommitClient());
    }

    public SVNStatusClient getStatusClient() {
        return wrapUp(core.getStatusClient());
    }

    public SVNCopyClient getCopyClient() {
        return wrapUp(core.getCopyClient());
    }

    public SVNUpdateClient getUpdateClient() {
        return wrapUp(core.getUpdateClient());
    }

    public SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException {
        return core.createRepository(url,mayReuse);
    }
}
