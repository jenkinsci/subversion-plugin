package hudson.scm.listtagsparameter;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Proc;
import hudson.model.Item;
import hudson.scm.AbstractSubversionTest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Base64;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class ListSubversionTagsParameterDefinitionTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Make sure we are actually listing tags correctly.
     */
    @Issue("JENKINS-11933")
    @Test
    public void listTags() throws Exception {
        Proc p = AbstractSubversionTest.runSvnServe(tmp, getClass().getResource("JENKINS-11933.zip"));
        try {
            ListSubversionTagsParameterDefinition def = new ListSubversionTagsParameterDefinition("FOO", "svn://localhost/", null, "", "", "", false, false);
            List<String> tags = def.getTags(null);
            List<String> expected = Arrays.asList("trunk", "tags/a", "tags/b", "tags/c");
            
            if (!expected.equals(tags))  {
                // retry. Maybe the svnserve just didn't start up correctly, yet
                System.out.println("First attempt failed. Retrying.");
                Thread.sleep(3000L);
                tags = def.getTags(null);
                if (!expected.equals(tags))  {
                    /* Just throws SVNException: svn: E210003: connection refused by the server:
                    dumpRepositoryContents();
                    */
                    if (tags.size() == 1 && tags.get(0).startsWith("!")) {
                        System.err.println("failed to contact SVN server; skipping test");
                        return;
                    }
                    fail("Expected " + expected + ", but got " + tags);
                }
            }
            
        } finally {
            p.kill();
        }
    }

    private void dumpRepositoryContents() throws SVNException {
        System.out.println("Repository contents:");
        SVNURL repoURL = SVNURL.parseURIEncoded( "svn://localhost/");
        SVNLogClient logClient = new SVNLogClient((ISVNAuthenticationManager)null, null);
        logClient.doList(repoURL, SVNRevision.HEAD, SVNRevision.HEAD, false, true, dirEntry -> System.out.println(dirEntry.getRelativePath()));
    }

    @Issue("SECURITY-303")
    @Test
    public void credentialsAccess() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.READ, Item.READ, Item.BUILD, Item.CONFIGURE).everywhere().to("devlead").
            grant(Jenkins.READ, Item.READ, Item.BUILD).everywhere().to("user"));
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(), Collections.<Credentials>singletonList(
            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "svncreds", null, "svn", "s3cr3t"))));
        r.createFreeStyleProject("p");
        assertSniff("devlead", "svn:s3cr3t", /* server response is bad, Jenkins should say so */ false);
        assertSniff("user", null, /* Jenkins should not even try to connect, pretend it is OK */ true);
    }
    private void assertSniff(String user, String sniffed, boolean ok) throws Exception {
        AuthSniffer sniffer = new AuthSniffer();
        URL sniffURL = sniffer.start();
        try {
            JenkinsRule.WebClient wc = r.createWebClient().login(user);
            String checkUrl = "job/p/descriptorByName/hudson.scm.listtagsparameter.ListSubversionTagsParameterDefinition/checkCredentialsId?value=svncreds&tagsDir=" + URLEncoder.encode(sniffURL.toString(), "UTF-8");
            System.err.println("Connecting to " + checkUrl + " as " + user);
            // TODO createCrumbedUrl does not work as it does not notice the existing query string
            String formValidation = wc.getPage(wc.addCrumb(new WebRequest(new URL(r.getURL(), checkUrl), HttpMethod.POST))).getWebResponse().getContentAsString();
            System.err.println("Response: " + formValidation);
            if (ok) {
                assertEquals("<div/>", formValidation);
            } else {
                assertNotEquals("<div/>", formValidation);
            }
            // GET accesses not permitted in any event:
            wc.assertFails(checkUrl, HttpURLConnection.HTTP_BAD_METHOD);
        } finally {
            assertEquals(sniffed, sniffer.stop());
        }
    }
    private static class AuthSniffer {
        String sniffed;
        Thread thread;
        /**
         * Starts listening on a new HTTP service.
         * @return the URL it is serving
         */
        URL start() throws Exception {
            final ServerSocket serverSocket = new ServerSocket(0);
            thread = new Thread("AuthSniffer") {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Socket socket = serverSocket.accept();
                            BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            String line;
                            while ((line = r.readLine()) != null && !line.isEmpty()) {
                                System.err.println("Received: " + line);
                                String magic = "Authorization: Basic ";
                                if (line.startsWith(magic)) {
                                    sniffed = new String(Base64.decodeBase64(line.substring(magic.length())));
                                    System.err.println("decoded to: " + sniffed);
                                }
                            }
                            PrintWriter w = new PrintWriter(socket.getOutputStream());
                            if (sniffed == null) {
                                w.println("HTTP/1.0 401 Unauthorized");
                                w.println("WWW-Authenticate: Basic realm=\"gotcha\"");
                            } else {
                                w.println("HTTP/1.0 200 OK");
                                w.println("Content-Length: 0");
                            }
                            w.println();
                            w.close();
                        } catch (IOException x) {
                            x.printStackTrace();
                        }
                    }
                }
            };
            thread.start();
            return new URL("http://" + /*serverSocket.getInetAddress()*/"localhost" + ":" + serverSocket.getLocalPort() + "/");
        }
        /**
         * Stops listening.
         * @return HTTP Basic authentication {@code username:password} it grabbed, if any
         */
        @CheckForNull String stop() {
            thread.interrupt(); // probably ineffective but at least we tried
            return sniffed;
        }
    }

}
