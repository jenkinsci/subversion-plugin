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
import java.net.ServerSocket;
import java.net.Socket;
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

        // If there is an already existing svnserve running on the machine
        // We need to fail the build. We could change this to if the port is in use, listen to different port
        Socket s = null;
        ServerSocket serverSocket = null;
        int port = 3690; // Default svnserve port is 3690.
        try {
          s = new Socket("localhost", 3690);
          // If it gets this far, that means that it is able to send/receive information.
          // Since the default svnserve port is currently in use, fail the build.
          System.err.println("Port 3690 is currently in use. Using a random port.");
          serverSocket = new ServerSocket(0);
          port = serverSocket.getLocalPort();
          serverSocket.close();
        } catch (IOException e) {
          // Port is not in use
        } finally {
          if (s != null) {
            s.close();
          }
        }

        return launcher.launch().cmds(
                "svnserve","-d","--foreground","-r",repo.getAbsolutePath(), "--listen-port", String.valueOf(port)).pwd(repo).start();
    }

    static {
        ClassicPluginStrategy.useAntClassLoader = true;
    }
}
