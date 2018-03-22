package jenkins.scm.impl.subversion;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;

/**
 * Version of {@link SVNErrorMessage}, which can be serialized over the channel.
 * This version does not serialize random {@link Object} instances.
 * @author Oleg Nenashev
 * @since TODO
 */
public class RemotableSVNErrorMessage extends SVNErrorMessage {

    public RemotableSVNErrorMessage(SVNErrorCode code) {
        super(code, null, null, null, 0);
    }

    public RemotableSVNErrorMessage(SVNErrorCode code, String message) {
        super(code, message, null, null, 0);
    }

    public RemotableSVNErrorMessage(SVNErrorCode code, Throwable cause) {
        super(code, null, null, cause, 0);
    }

    public RemotableSVNErrorMessage(SVNErrorCode code, String message, Throwable cause) {
        super(code, message, null, cause, 0);
    }
}
