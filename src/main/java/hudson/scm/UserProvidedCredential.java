/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Fulvio Cavarretta,
 * Jean-Baptiste Quenot, Luca Domenico Milanesio, Renaud Bruyeron, Stephen Connolly,
 * Tom Huybrechts, Yahoo! Inc.
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

import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import hudson.scm.SubversionSCM.DescriptorImpl.Credential;
import hudson.scm.SubversionSCM.DescriptorImpl.PasswordCredential;
import hudson.scm.SubversionSCM.DescriptorImpl.SshPublicKeyCredential;
import hudson.scm.SubversionSCM.DescriptorImpl.SslClientCertificateCredential;
import hudson.security.csrf.CrumbIssuer;
import hudson.util.MultipartFormDataParser;
import jenkins.model.Jenkins;
import org.apache.commons.fileupload.FileItem;
import org.kohsuke.putty.PuTTYKey;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;

/**
 * Represents the SVN authentication credential given by the user via the {@code <enterCredential>} form fragment.
 * This is just a value object.
 *
 * @author Kohsuke Kawaguchi
 */
public class UserProvidedCredential implements Closeable {
    private final String username;
    private final String password;
    private final File keyFile;
    /**
     * If non-null, this credential is submitted primarily to be used with this project.
     * This actually doesn't prevent Hudson from trying it with other projects.
     */
    public final AbstractProject inContextOf;

    /**
     * @deprecated as of 1.18
     *      Use {@link #UserProvidedCredential(String, String, File, AbstractProject)}
     */
    public UserProvidedCredential(String username, String password, File keyFile) {
        this(username,password,keyFile,null);
    }

    public UserProvidedCredential(String username, String password, File keyFile, AbstractProject inContextOf) {
        this.username = username;
        this.password = password;
        this.keyFile = keyFile;
        this.inContextOf = inContextOf;
    }

    /**
     * Parses the credential information from a form submission.
     */
    public static UserProvidedCredential fromForm(StaplerRequest req, MultipartFormDataParser parser) throws IOException {
        CrumbIssuer crumbIssuer = Jenkins.getInstance().getCrumbIssuer();
        if (crumbIssuer!=null && !crumbIssuer.validateCrumb(req, parser))
            throw HttpResponses.error(SC_FORBIDDEN,new IOException("No crumb found"));

        String kind = parser.get("kind");
        int idx = Arrays.asList("","password","publickey","certificate").indexOf(kind);

        String username = parser.get("username"+idx);
        String password = parser.get("password"+idx);


        // SVNKit wants a key in a file
        final File keyFile;
        final FileItem item;
        if(idx <= 1) {
            keyFile = null;
            item = null;
        } else {
            item = parser.getFileItem(kind.equals("publickey")?"privateKey":"certificate");
            keyFile = File.createTempFile("hudson","key");
            if(item!=null) {
                try {
                    item.write(keyFile);
                } catch (Exception e) {
                    throw new IOException(e);
                }
                if(PuTTYKey.isPuTTYKeyFile(keyFile)) {
                    // TODO: we need a passphrase support
                    LOGGER.info("Converting "+keyFile+" from PuTTY format to OpenSSH format");
                    new PuTTYKey(keyFile,null).toOpenSSH(keyFile);
                }
            }
        }

        return new UserProvidedCredential(username,password,keyFile,req.findAncestorObject(AbstractProject.class)) {
            @Override
            public void close() throws IOException {
                if(keyFile!=null)
                    keyFile.delete();
                if(item!=null)
                    item.delete();
            }
        };
    }

    public void close() throws IOException {}

    /**
     * {@link ISVNAuthenticationManager} that uses the user provided credential.
     */
    public class AuthenticationManagerImpl extends DefaultSVNAuthenticationManager {
        private Credential cred;
        private final PrintWriter logWriter;

        /**
         * Set to true if SVNKit asked for a {@link SVNAuthentication}.
         * False indicates that the server didn't attempt to authenticate the client.
         */
        boolean authenticationAttempted;
        /**
         * Set to true if SVNKit acknowledged us whether the credential has worked or not.
         * I'm not sure when this won't happen, but presumably under some error conditions.
         */
        boolean authenticationAcknowledged;

        public AuthenticationManagerImpl(PrintWriter logWriter) {
            super(SVNWCUtil.getDefaultConfigurationDirectory(), true, username, password, keyFile, password);
            this.logWriter = logWriter;
            SVNAuthStoreHandlerImpl.install(this);
        }

        public AuthenticationManagerImpl(Writer w) {
            this(new PrintWriter(w));
        }

        public AuthenticationManagerImpl(TaskListener listener) {
            this(new PrintWriter(listener.getLogger(),true));
        }

        @Override
        public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException {
            authenticationAttempted = true;
            if (kind.equals(ISVNAuthenticationManager.USERNAME))
                // when using svn+ssh, svnkit first asks for ISVNAuthenticationManager.SSH
                // authentication to connect via SSH, then calls this method one more time
                // to get the user name. Perhaps svn takes user name on its own, separate
                // from OS user name? In any case, we need to return the same user name.
                // I don't set the cred field here, so that the 1st credential for ssh
                // won't get clobbered.
                return new SVNUserNameAuthentication(username, false);
            if (kind.equals(ISVNAuthenticationManager.PASSWORD)) {
                logWriter.println("Passing user name " + username + " and password you entered");
                cred = new PasswordCredential(username, password);
            }
            if (kind.equals(ISVNAuthenticationManager.SSH)) {
                if (keyFile == null) {
                    logWriter.println("Passing user name " + username + " and password you entered to SSH");
                    cred = new PasswordCredential(username, password);
                } else {
                    logWriter.println("Attempting a public key authentication with username " + username);
                    cred = new SshPublicKeyCredential(username, password, keyFile);
                }
            }
            if (kind.equals(ISVNAuthenticationManager.SSL)) {
                logWriter.println("Attempting an SSL client certificate authentcation");
                try {
                    cred = new SslClientCertificateCredential(keyFile, password);
                } catch (IOException e) {
                    e.printStackTrace(logWriter);
                    return null;
                }
            }

            if (cred == null) {
                logWriter.println("Unknown authentication method: " + kind);
                return null;
            }
            return cred.createSVNAuthentication(kind);
        }

        /**
         * Getting here means the authentication tried in {@link #getFirstAuthentication(String, String, SVNURL)}
         * didn't work.
         */
        @Override
        public SVNAuthentication getNextAuthentication(String kind, String realm, SVNURL url) throws SVNException {
            SVNErrorManager.authenticationFailed("Authentication failed for " + url, null);
            return null;
        }

        @Override
        public void acknowledgeAuthentication(boolean accepted, String kind, String realm, SVNErrorMessage errorMessage, SVNAuthentication authentication) throws SVNException {
            authenticationAcknowledged = true;
            if (accepted) {
                assert cred != null;
                onSuccess(realm,cred);
            } else {
                logWriter.println("Failed to authenticate: " + errorMessage);
                if (errorMessage.getCause() != null)
                    errorMessage.getCause().printStackTrace(logWriter);
            }
            super.acknowledgeAuthentication(accepted, kind, realm, errorMessage, authentication);
        }

        /**
         * Called upon a successful acceptance of the credential.
         */
        protected void onSuccess(String realm, Credential cred) {
        }

        /**
         * Verifies that the expected authentication happened.
         */
        public void checkIfProtocolCompleted() throws SVNCancelException {
            if(!authenticationAttempted) {
                logWriter.println("No authentication was attempted.");
                throw new SVNCancelException();
            }
            if (!authenticationAcknowledged) {
                logWriter.println("Authentication was not acknowledged.");
                throw new SVNCancelException();
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(UserProvidedCredential.class.getName());
}
