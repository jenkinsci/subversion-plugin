package hudson.scm.listtagsparameter;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Proc;
import hudson.model.Item;
import hudson.scm.AbstractSubversionTest;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

@WithJenkins
class ListSubversionTagsParameterDefinitionTest {

    private JenkinsRule r;

    @TempDir
    private File tmp;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    /**
     * Make sure we are actually listing tags correctly.
     */
    @Issue("JENKINS-11933")
    @Test
    void listTags() throws Exception {
        Proc p = AbstractSubversionTest.runSvnServe(tmp, getClass().getResource("JENKINS-11933.zip"));
        try {
            ListSubversionTagsParameterDefinition def = new ListSubversionTagsParameterDefinition("FOO", "svn://localhost/", null, "", "", "", false, false);
            List<String> tags = def.getTags(null);
            List<String> expected = Arrays.asList("trunk", "tags/a", "tags/b", "tags/c");

            if (!expected.equals(tags)) {
                // retry. Maybe the svnserve just didn't start up correctly, yet
                System.out.println("First attempt failed. Retrying.");
                Thread.sleep(3000L);
                tags = def.getTags(null);
                if (!expected.equals(tags)) {
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

    @Issue("SECURITY-303")
    @Test
    void credentialsAccess() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.READ, Item.READ, Item.BUILD, Item.CONFIGURE).everywhere().to("devlead").
                grant(Jenkins.READ, Item.READ, Item.BUILD).everywhere().to("user"));
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(), Collections.singletonList(
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "svncreds", null, "svn", "s3cr3t"))));
        r.createFreeStyleProject("p");
        assertSniff("devlead", "svn:s3cr3t", /* server response is bad, Jenkins should say so */ false);
        assertSniff("user", null, /* Jenkins should not even try to connect, pretend it is OK */ true);
    }

    private void assertSniff(String user, String sniffed, boolean ok) throws Exception {
        try (AuthSniffer sniffer = new AuthSniffer()) {
            URL sniffUrl = sniffer.getUrl();
            JenkinsRule.WebClient wc = r.createWebClient().login(user);
            String checkUrl = "job/p/descriptorByName/hudson.scm.listtagsparameter."
                    + "ListSubversionTagsParameterDefinition/checkCredentialsId?value=svncreds&tagsDir="
                    + URLEncoder.encode(sniffUrl.toString(), StandardCharsets.UTF_8);
            System.err.println("Connecting to " + checkUrl + " as " + user);

            // createCrumbedUrl does not handle the existing query string.
            WebRequest request = new WebRequest(new URL(r.getURL(), checkUrl), HttpMethod.POST);
            String formValidation = wc.getPage(wc.addCrumb(request))
                    .getWebResponse().getContentAsString();
            System.err.println("Response: " + formValidation);
            if (ok) {
                assertEquals("<div/>", formValidation);
            } else {
                assertNotEquals("<div/>", formValidation);
            }

            wc.assertFails(checkUrl, HttpURLConnection.HTTP_BAD_METHOD);
            assertEquals(sniffed, sniffer.getSniffed());
        }
    }

    private static final class AuthSniffer implements AutoCloseable {
        private static final String BASIC_PREFIX = "Basic ";

        private final HttpServer server;
        private volatile String sniffed;

        AuthSniffer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    // SVNKit sends a body with OPTIONS. Read it before replying.
                    exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());

                    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                    if (authorization == null
                            || !authorization.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
                        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"gotcha\"");
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_UNAUTHORIZED, -1);
                        return;
                    }

                    byte[] credentials = Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length()));
                    sniffed = new String(credentials, StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        URL getUrl() throws IOException {
            return new URL("http", "127.0.0.1", server.getAddress().getPort(), "/");
        }

        @CheckForNull
        String getSniffed() {
            return sniffed;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

}
