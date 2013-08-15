package hudson.scm;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.Proc;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.Bug;

import java.util.Arrays;
import java.util.Collections;

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
            SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(),
                    Arrays.<Credentials>asList(
                            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "1-alice", null, "alice", "alice")
                    )
            ));
            FreeStyleProject b = createFreeStyleProject();
            b.setScm(new SubversionSCM("svn://localhost/bob", "1-alice", "."));
            b.setAssignedNode(createSlave());

            buildAndAssertSuccess(b);
        } finally {
            p.kill();
        }
    }
}
