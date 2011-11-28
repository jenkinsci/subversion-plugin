package hudson.scm;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthStoreHandler;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthenticationStorageOptions;
import org.tmatesoft.svn.core.internal.wc.ISVNGnomeKeyringPasswordProvider;

/**
 * {@link ISVNAuthStoreHandler} implementation that always return true.
 *
 * <p>
 * This is to convince Subversion to store passwords, in relation to JENKINS-8059.
 *
 * @author Kohsuke Kawaguchi
 */
public class SVNAuthStoreHandlerImpl implements ISVNAuthStoreHandler {
    public boolean canStorePlainTextPasswords(String realm, SVNAuthentication auth) throws SVNException {
        return true;
    }

    public boolean canStorePlainTextPassphrases(String realm, SVNAuthentication auth) throws SVNException {
        return true;
    }

    /**
     * {@link ISVNAuthenticationManager} doesn't expose the setAuthStoreHandler, so we need to downcast.
     */
    public static void install(ISVNAuthenticationManager sam) {
        if (sam instanceof DefaultSVNAuthenticationManager) {
            DefaultSVNAuthenticationManager dsam = (DefaultSVNAuthenticationManager) sam;
            dsam.setAuthenticationStorageOptions(new ISVNAuthenticationStorageOptions() {
                public boolean isNonInteractive() throws SVNException {
                    return true;
                }

                public ISVNAuthStoreHandler getAuthStoreHandler() throws SVNException {
                    return new SVNAuthStoreHandlerImpl();
                }

                public boolean isSSLPassphrasePromptSupported() {
                    return false;
                }

                public ISVNGnomeKeyringPasswordProvider getGnomeKeyringPasswordProvider() {
                    return null;
                }
            });
        }
    }
}
