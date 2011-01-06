package hudson.scm;

import hudson.Proc;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.Bug;

import java.io.PrintWriter;

/**
 * @author Kohsuke Kawaguchi
 */
public class PerJobCredentialStoreTest extends AbstractSubversionTest {
    /**
     * There was a bug that credentials stored in the remote call context was serialized wrongly.
     */
    @Bug(8061)
    public void testRemoteBuild() throws Exception {
        Proc p = runSvnServe(SubversionSCMTest.class.getResource("HUDSON-1379.zip"));
        try {
            FreeStyleProject b = createFreeStyleProject();
            b.setScm(new SubversionSCM("svn://localhost/bob"));
            b.setAssignedNode(createSlave());

            descriptor.postCredential(b,"svn://localhost/bob","alice","alice",null,new PrintWriter(System.out));
            
            buildAndAssertSuccess(b);

            PerJobCredentialStore store = new PerJobCredentialStore(b);
            assertFalse(store.isEmpty());   // credential store should contain a valid entry
        } finally {
            p.kill();
        }
    }
}
