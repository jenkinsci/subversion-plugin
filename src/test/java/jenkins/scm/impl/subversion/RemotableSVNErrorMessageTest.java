package jenkins.scm.impl.subversion;

import org.junit.jupiter.api.Test;
import org.tmatesoft.svn.core.SVNErrorCode;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RemotableSVNErrorMessageTest {

    @Test
    void shouldNotThrowNPEsInToString() {
        assertNotNull(new RemotableSVNErrorMessage(SVNErrorCode.APMOD_CONNECTION_ABORTED).toString());
        assertNotNull(new RemotableSVNErrorMessage(SVNErrorCode.APMOD_CONNECTION_ABORTED, "message").toString());
        assertNotNull(new RemotableSVNErrorMessage(SVNErrorCode.APMOD_CONNECTION_ABORTED, new Exception()).toString());
        assertNotNull(new RemotableSVNErrorMessage(SVNErrorCode.APMOD_CONNECTION_ABORTED, "message", new Exception()).toString());
    }

}
