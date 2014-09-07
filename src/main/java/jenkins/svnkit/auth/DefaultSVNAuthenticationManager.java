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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */

public class DefaultSVNAuthenticationManager extends org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager
        implements AuthenticationManager {
  public DefaultSVNAuthenticationManager(
          org.tmatesoft.svn.core.auth.ISVNAuthenticationManager createDefaultAuthenticationManager) {
    super(SVNWCUtil.getDefaultConfigurationDirectory(), createDefaultAuthenticationManager.isAuthenticationForced(), null, null);
  }

  /**
   * File configDirectory, boolean storeAuth, String userName, String password, File privateKey, String passphrase)
   * @param subversionConfigDir
   * @param b
   * @param username
   * @param password
   * @param keyFile
   * @param password2
   */
  public DefaultSVNAuthenticationManager(File subversionConfigDir, boolean b,
                                         String username, String password, File keyFile, String password2) {
    super(subversionConfigDir, b, username, password, keyFile, password2);
  }

  public DefaultSVNAuthenticationManager() {
    this(SVNWCUtil.getDefaultConfigurationDirectory(),
            SVNWCUtil.createDefaultOptions(SVNWCUtil.getDefaultConfigurationDirectory(), true).isAuthStorageEnabled(),
            null, null, null, null);
  }

  /* (non-Javadoc)
       * @see hudson.scm.auth.ISVNAuthenticationManager#getAuthenticationManager()
       */
  @Override
  public org.tmatesoft.svn.core.auth.ISVNAuthenticationManager getAuthenticationManager() {
    return (org.tmatesoft.svn.core.auth.ISVNAuthenticationManager)this;
  }

  @Override
  @CheckForNull
  public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException {
    // SVNKIT DefaultAuthenticationManager ignores any credentials that are added to the manager.
      return super.getAuthenticationProvider().requestClientAuthentication(kind, url, realm, null, null, false);
  }

  @Override
  public void acknowledgeAuthentication(boolean accepted, String kind, String realm, SVNErrorMessage errorMessage, SVNAuthentication authentication) throws SVNException {
    if (outcomeListener != null)
      outcomeListener.acknowledgeAuthentication(accepted, kind, realm, errorMessage, authentication);

    super.acknowledgeAuthentication(accepted, kind, realm, errorMessage, authentication);
  }
  /* (non-Javadoc)
   * @see hudson.scm.auth.ISVNAuthenticationManager#setAuthenticationOutcomeListener(hudson.scm.auth.ISVNAuthenticationOutcomeListener)
   */
  @Override
  public void setAuthenticationOutcomeListener(
          ISVNAuthenticationOutcomeListener listener) {
    outcomeListener = listener;
  }


  private ISVNAuthenticationOutcomeListener outcomeListener;
}