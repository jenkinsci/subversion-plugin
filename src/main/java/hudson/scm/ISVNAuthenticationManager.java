package hudson.scm;


import jenkins.scm.impl.subversion.svnkit.ISVNAuthenticationOutcomeListener;

/**
 * @see org.tmatesoft.svn.core.auth.ISVNAuthenticationManager
 *
 * @author Steven Christou
 *
 */
public interface ISVNAuthenticationManager extends org.tmatesoft.svn.core.auth.ISVNAuthenticationManager {

    /**
     * Set an outcome listener for the authentication manager.
     * @param listener
     */
    public void setAuthenticationOutcomeListener(ISVNAuthenticationOutcomeListener listener);

    /**
     * Retrieve the authentication manager.
     *
     * @return ISVNAuthenticationManager
     */

    public org.tmatesoft.svn.core.auth.ISVNAuthenticationManager getAuthenticationManager();
}