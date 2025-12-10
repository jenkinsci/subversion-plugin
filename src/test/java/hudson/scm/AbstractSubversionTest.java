package hudson.scm;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.Proc;
import hudson.util.StreamTaskListener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import org.junit.AssumptionViolatedException;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Base class for Subversion related tests.
 *
 * @author Kohsuke Kawaguchi
 */
// TODO perhaps merge into SubversionSampleRepoRule
public abstract class AbstractSubversionTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Configure the SVN workspace format - i.e. the format of the local workspace copy.
     * 
     * @param format one of the WC constants form SVNAdminAreaFactory or SubversionWorkspaceSelector.WC_FORMAT_17
     */
    protected void configureSvnWorkspaceFormat(int format) throws Exception {
    	StaplerRequest2 req = mock(StaplerRequest2.class);
    	when(req.getParameter("svn.workspaceFormat")).thenReturn(""+format);
    	
    	JSONObject formData = new JSONObject();
    	
    	r.jenkins.getDescriptorByType(SubversionSCM.DescriptorImpl.class).configure(req, formData);
    }

    protected void configureSvnWorkspaceFormat2(int format) throws Exception {
        StaplerRequest2 req = mock(StaplerRequest2.class);
        when(req.getParameter("svn.global_excluded_revprop")).thenReturn(null);
        when(req.getParameter("svn.workspaceFormat")).thenReturn(""+format);

        JSONObject formData = new JSONObject();

        r.jenkins.getDescriptorByType(SubversionSCM.DescriptorImpl.class).configure(req, formData);
    }

    public static void checkForSvnServe() throws InterruptedException {
        LocalLauncher launcher = new LocalLauncher(StreamTaskListener.fromStdout());
        try {
            launcher.launch().cmds("svnserve","--help").start().join();
        } catch (IOException e) {
            // TODO better to add a docker-fixtures test dep so CI builds can run these tests
            throw new AssumptionViolatedException("svnserve apparently not installed", e);
        }
    }

    public Proc runSvnServe(URL zip) throws Exception {
        return runSvnServe(tmp, zip);
    }

    public static Proc runSvnServe(TemporaryFolder tmp, URL zip) throws Exception {
        File target = tmp.newFolder();
        try (InputStream is = zip.openStream()) {
            new FilePath(target).unzipFrom(is);
        }
        return runSvnServe(target);
    }

    /**
     * Runs svnserve to serve the specified directory as a subversion repository.
     */
    public static Proc runSvnServe(File repo) throws Exception {
        checkForSvnServe();

        LocalLauncher launcher = new LocalLauncher(StreamTaskListener.fromStdout());

        // If there is an already existing svnserve running on the machine
        // We need to fail the build. We could change this to if the port is in use, listen to different port
        ServerSocket serverSocket = null;
        int port = 3690; // Default svnserve port is 3690.
        try (Socket s = new Socket("localhost", 3690)) {
            // If it gets this far, that means that it is able to send/receive information.
            // Since the default svnserve port is currently in use, fail the build.
            System.err.println("Port 3690 is currently in use. Using a random port.");
            serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            serverSocket.close();
        } catch (IOException e) {
            // Port is not in use
        }

        Launcher.ProcStarter cmd = launcher.launch().cmds(
                "svnserve", "-d", "--foreground", "-r", repo.getAbsolutePath(), "--listen-port", String.valueOf(port));
        Proc process = cmd.pwd(repo).start();

        waitForSvnServer(port);
        return process;
    }

    /**
     *
     * Wait for the SVN server 30 seconds, to check it try to open a port to the server.
     */
    private static void waitForSvnServer(int port) throws InterruptedException {
        boolean serverReady = false;
        int retries = 0;
        while(serverReady == false && retries < 6){
            try (Socket s = new Socket("localhost", port)) {
                // Server is up
                serverReady = true;
            } catch (IOException e) {
                // Server is down
                System.err.println("Waiting for SVN server");
                Thread.sleep(5000);
                retries++;
            }
        }
    }
}
