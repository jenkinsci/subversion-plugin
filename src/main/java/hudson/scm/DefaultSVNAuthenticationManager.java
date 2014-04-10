/*
 * The MIT License
 *
 * Copyright (c) 2012, Steven Christou
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

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;


/**
 * @author Steven
 *
 */
public class DefaultSVNAuthenticationManager extends org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager
        implements ISVNAuthenticationManager {

    public DefaultSVNAuthenticationManager(File configDirectory,
                                           boolean storeAuth, String userName, String password) {
        super(configDirectory, storeAuth, userName, password);
    }

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
    public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        try {
            return super.getFirstAuthentication(kind, realm, url);
        } catch (SVNException e) {
            SVNErrorManager.cancel("No Credentials to try. Authentication failed.", SVNLogType.WC);
            throw e;
        }
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