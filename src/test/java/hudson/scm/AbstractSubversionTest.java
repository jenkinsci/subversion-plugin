package hudson.scm;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.Proc;
import hudson.util.StreamTaskListener;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.opentest4j.TestAbortedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for Subversion related tests.
 *
 * @author Kohsuke Kawaguchi
 */
// TODO perhaps merge into SubversionSampleRepoRule
@WithJenkins
public abstract class AbstractSubversionTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    protected JenkinsRule r;

    @TempDir
    protected File tmp;

    @BeforeEach
    protected void beforeEach(JenkinsRule rule) throws Exception {
        r = rule;
    }

    /**
     * Configure the SVN workspace format - i.e. the format of the local workspace copy.
     *
     * @param format one of the WC constants form SVNAdminAreaFactory or SubversionWorkspaceSelector.WC_FORMAT_17
     */
    protected void configureSvnWorkspaceFormat(int format) throws Exception {
        StaplerRequest2 req = mock(StaplerRequest2.class);
        when(req.getParameter("svn.workspaceFormat")).thenReturn("" + format);

        JSONObject formData = new JSONObject();

        r.jenkins.getDescriptorByType(SubversionSCM.DescriptorImpl.class).configure(req, formData);
    }

    public static void checkForSvnServe() throws InterruptedException {
        LocalLauncher launcher = new LocalLauncher(StreamTaskListener.fromStdout());
        try {
            launcher.launch().cmds("svnserve", "--help").start().join();
        } catch (IOException e) {
            // TODO better to add a docker-fixtures test dep so CI builds can run these tests
            throw new TestAbortedException("svnserve apparently not installed", e);
        }
    }

    public Proc runSvnServe(URL zip) throws Exception {
        return runSvnServe(tmp, zip);
    }

    public static Proc runSvnServe(File tmp, URL zip) throws Exception {
        File target = newFolder(tmp, "junit-" + System.currentTimeMillis());
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
        while (!serverReady && retries < 6) {
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

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
