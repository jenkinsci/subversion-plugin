package hudson.scm;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNAuthentication;

import java.io.File;

/**
 * User: schristou88
 * Date: 4/21/14
 * Time: 2:56 PM
 */
public class BasicAuthenticationManager extends org.tmatesoft.svn.core.auth.BasicAuthenticationManager {
    public BasicAuthenticationManager(String userName,
                                      File keyFile,
                                      String passphrase,
                                      int portNumber) {
        super(userName, keyFile, passphrase, portNumber);
    }

    public static void acknowledgeAuthentication (boolean accepted,
                                                  String kind,
                                                  String realm,
                                                  SVNErrorMessage errorMessage,
                                                  SVNAuthentication authentication,
                                                  SVNURL accessedUrl,
                                                  ISVNAuthenticationManager authenticationManager) throws SVNException {
        if (outcomeListener != null)
            outcomeListener.acknowledgeAuthentication(accepted, kind, realm, errorMessage, authentication);
    }

    public void setAuthenticationOutcomeListener(ISVNAuthenticationOutcomeListener listener) {
        outcomeListener = listener;
    }

    private static ISVNAuthenticationOutcomeListener outcomeListener;
}
