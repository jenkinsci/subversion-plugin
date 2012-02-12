package hudson.scm;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;

/**
 * Unit tests for {@link SubversionSCM}.
 * 
 * ({@link SubversionSCMTest} ist more an integration test)
 * 
 * @author kutzi
 */
public class SubversionSCMUnitTest {
    
    @Test
    @Bug(12113)
    public void testLocalDirectoryIsExpandedWithEnvVars() {
        FilePath root = new FilePath((VirtualChannel)null, "root");
        
        EnvVars envVars = new EnvVars();
        envVars.put("BRANCH", "test");
        
        SubversionSCM scm = new SubversionSCM("dummyUrl");
        
        FilePath resolvedRoot = scm._getModuleRoot(root, "$BRANCH/someMorePath", envVars);
        
        Assert.assertEquals("root/test/someMorePath", resolvedRoot.getRemote());
    }
}
