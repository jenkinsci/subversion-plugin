package hudson.scm;

import hudson.ClassicPluginStrategy;
import hudson.Launcher.LocalLauncher;
import hudson.Proc;
import hudson.scm.SubversionSCM.DescriptorImpl;
import hudson.util.StreamTaskListener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import net.sf.json.JSONObject;

import org.junit.Assert;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerRequest;

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
    
    /**
     * Configure the SVN workspace format - i.e. the format of the local workspace copy.
     * 
     * @param format one of the WC constants form SVNAdminAreaFactory or SubversionWorkspaceSelector.WC_FORMAT_17
     */
    protected void configureSvnWorkspaceFormat(int format) throws Exception {
    	StaplerRequest req = mock(StaplerRequest.class);
    	when(req.getParameter("svn.workspaceFormat")).thenReturn(""+format);
    	
    	JSONObject formData = new JSONObject();
    	
    	this.descriptor.configure(req, formData);
    }

    protected Proc runSvnServe(URL zip) throws Exception {
        return runSvnServe(new CopyExisting(zip).allocate());
    }

    /**
     * Runs svnserve to serve the specified directory as a subversion repository.
     */
    protected Proc runSvnServe(File repo) throws Exception {
        LocalLauncher launcher = new LocalLauncher(StreamTaskListener.fromStdout());
        try {
            launcher.launch().cmds("svnserve","--help").start().join();
        } catch (IOException e) {
        	Assert.fail("Failed to launch svnserve. Do you have subversion installed?\n" + e);
        }
        return launcher.launch().cmds(
                "svnserve","-d","--foreground","-r",repo.getAbsolutePath()).pwd(repo).start();
    }

    static {
        ClassicPluginStrategy.useAntClassLoader = true;
    }
}
