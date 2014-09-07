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

/**
 * See {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} for
 * implementation details.
 *
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     org.tmatesoft.svn.core.io.SVNRepository
 */
public interface AuthenticationManager extends org.tmatesoft.svn.core.auth.ISVNAuthenticationManager {

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