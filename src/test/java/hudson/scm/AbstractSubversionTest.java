package hudson.scm;

import hudson.ClassicPluginStrategy;
import hudson.Launcher.LocalLauncher;
import hudson.Proc;
import hudson.scm.SubversionSCM.DescriptorImpl;
import hudson.util.StreamTaskListener;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Base class for Subversion related tests.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractSubversionTest extends HudsonTestCase  {
    protected DescriptorImpl descriptor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        descriptor = hudson.getDescriptorByType(DescriptorImpl.class);
    }

    protected Proc runSvnServe(URL zip) throws Exception {
        return runSvnServe(new CopyExisting(zip).allocate());
    }

    /**
     * Runs svnserve to serve the specified directory as a subversion repository.
     */
    protected Proc runSvnServe(File repo) throws Exception {
        LocalLauncher launcher = new LocalLauncher(new StreamTaskListener(System.out));
        try {
            launcher.launch().cmds("svnserve","--help").start().join();
        } catch (IOException e) {
            // if we fail to launch svnserve, skip the test
            return null;
        }
        return launcher.launch().cmds(
                "svnserve","-d","--foreground","-r",repo.getAbsolutePath()).pwd(repo).start();
    }

    static {
        ClassicPluginStrategy.useAntClassLoader = true;
    }
}
