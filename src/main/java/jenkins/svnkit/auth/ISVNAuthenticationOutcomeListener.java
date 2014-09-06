/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://lib.svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package jenkins.svnkit.auth;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.SVNAuthentication;

/**
 * Listens to the outcome of the authentication.
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