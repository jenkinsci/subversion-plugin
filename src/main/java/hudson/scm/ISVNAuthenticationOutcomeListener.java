package hudson.scm;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.SVNAuthentication;

/**
 * User: schristou88
 * Date: 4/8/14
 * Time: 2:28 PM
 */
public interface ISVNAuthenticationOutcomeListener {
   /**
     * Accepts the given authentication if it was successfully accepted by a
     * repository server, or not if authentication failed. As a result the
     * provided credential may be cached (authentication succeeded) or deleted
     * from the cache (authentication failed).
     *
     * @param accepted       <span class="javakeyword">true</span> if
     *                       the credential was accepted by the server,
     *                       otherwise <span class="javakeyword">false</span>
     * @param kind           a credential kind ({@link #PASSWORD} or {@link #SSH} or {@link #USERNAME})
     * @param realm          a repository authentication realm
     * @param errorMessage   the reason of the authentication failure
     * @param authentication a user credential to accept/drop
     * @throws SVNException
     */
    public void acknowledgeAuthentication(boolean accepted,
                                          String kind,
                                          String realm,
                                          SVNErrorMessage errorMessage,
                                          SVNAuthentication authentication) throws SVNException;
}
