/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Bruce Chapman, Yahoo! Inc., Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.scm;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.browsers.Sventon;
import hudson.scm.subversion.CheckoutUpdater;
import hudson.scm.subversion.UpdateUpdater;
import hudson.scm.subversion.UpdateWithCleanUpdater;
import hudson.scm.subversion.UpdateWithRevertUpdater;
import hudson.scm.subversion.WorkspaceUpdater;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import hudson.slaves.DumbSlave;
import hudson.triggers.SCMTrigger;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import jenkins.scm.impl.subversion.RemotableSVNErrorMessage;
import org.dom4j.Document;
import org.dom4j.io.DOMReader;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebClient;
import org.htmlunit.WebConnection;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.Url;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Kohsuke Kawaguchi
 */
// TODO: we're relying on no less than 2 external SVN repos for this test: svn.jenkins-ci.org (now all ignored!), subversion.tigris.org
// while the 1st one is probably okay, we should look that we get rid of the other dependency
@SuppressWarnings({"rawtypes", "deprecation"})
@WithJenkins
class SubversionSCMTest extends AbstractSubversionTest {

    private static final int LOG_LIMIT = 1000;

    // in some tests we play authentication games with this repo
    private static final String REALM = "<http://subversion.tigris.org:80> CollabNet Subversion Repository";
    private static final String KIND = ISVNAuthenticationManager.PASSWORD;
    private SVNURL repo;

    private FilePath workingcopy;

    @Override
    @BeforeEach
    protected void beforeEach(JenkinsRule rule) throws Exception {
        super.beforeEach(rule);

        repo = SVNURL.parseURIDecoded("http://subversion.tigris.org/svn/subclipse");

        // during the test, don't pollute the user's configuration (esp. authentication cache).
        System.setProperty(SubversionSCM.class.getName() + ".configDir", newFolder(tmp, "junit").getAbsolutePath());
    }

    @Issue("JENKINS-2380")
    @Test
    void taggingPermission() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Permission.READ).everywhere().toEveryone()
                .grant(Permission.WRITE).everywhere().toAuthenticated());

        // create a build
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(loadSvnRepo());
        final FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause()).get();
        System.out.println(b.getLog(LOG_LIMIT));
        r.assertBuildStatus(Result.SUCCESS, b);

        final SubversionTagAction action = b.getAction(SubversionTagAction.class);
        r.executeOnServer(() -> {
            assertFalse(b.hasPermission(action.getPermission()), "Shouldn't be accessible to anonymous user");
            return null;
        });

        JenkinsRule.WebClient wc = r.createWebClient();
        HtmlPage html = wc.getPage(b);

        // make sure there's no link to the 'tag this build'
        Document dom = new DOMReader().read(html);
        assertNull(dom.selectSingleNode("//A[text()='Tag this build']"));
        for (HtmlAnchor a : html.getAnchors())
            assertFalse(a.getHrefAttribute().contains("/tagBuild/"));

        // and no tag form on tagBuild page
        HtmlPage page = wc.getPage(b, "tagBuild/");
        assertThrows(ElementNotFoundException.class, () -> page.getFormByName("tag"), "should not have been found");

        // and that tagging would fail due to CSRF protection
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(b, "tagBuild/submit?name0=test&Submit=Tag"));
        // not found, wrong HTTP verb
        assertEquals(404, e.getResponse().getStatusCode());

        // Now try with POST and crumb
        e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(new WebRequest(wc.createCrumbedUrl(b.getUrl() + "tagBuild/submit?name0=test&Submit=Tag"), HttpMethod.POST)));
        // make sure the request is denied
        assertEquals(403, e.getResponse().getStatusCode());

        // now login as alice
        JenkinsRule.WebClient wcAlice = r.createWebClient();
        wcAlice.login("alice", "alice");

        // a quick detour to confirm CSRF protection is effective
        e = assertThrows(FailingHttpStatusCodeException.class, () -> wcAlice.getPage(b, "tagBuild/submit?name0=test&Submit=Tag"));
        // not found, wrong HTTP verb
        assertEquals(404, e.getResponse().getStatusCode());

        // make sure that the tagging would succeed
        html = wcAlice.getPage(b, "tagBuild/");
        HtmlForm form = html.getFormByName("tag");
        r.submit(form);
    }

    /**
     * Loads a test Subversion repository into a temporary directory, and creates {@link SubversionSCM} for it.
     */
    private SubversionSCM loadSvnRepo() throws Exception {
        return new SubversionSCM("file://" + new CopyExisting(getClass().getResource("/svn-repo.zip")).allocate().toURI().toURL().getPath() + "trunk/a", "a");
    }

    @Email("http://jenkins.361315.n4.nabble.com/Hudson-1-266-and-1-267-Subversion-authentication-broken-td375737.html")
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void httpsCheckOut() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant/"));

        FreeStyleBuild b = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause()).get());
        assertTrue(b.getWorkspace().child("build.xml").exists());
    }

    @Email("http://jenkins.361315.n4.nabble.com/Hudson-1-266-and-1-267-Subversion-authentication-broken-td375737.html")
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void httpCheckOut() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven/src/test/java/test/"));

        FreeStyleBuild b = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause()).get());
        assertTrue(b.getWorkspace().child("AppTest.java").exists());
    }

    @Url("http://hudson.pastebin.com/m3ea34eea")
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void remoteCheckOut() throws Exception {
        DumbSlave s = r.createSlave();
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedLabel(s.getSelfLabel());
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant/"));

        FreeStyleBuild b = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause()).get());
        assertTrue(b.getWorkspace().child("build.xml").exists());
        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
    }

    /**
     * Tests the "URL@REV" format in SVN URL.
     */
    @Issue("JENKINS-262")
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void revisionedCheckout() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@13000"));

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause()).get();
        System.out.println(b.getLog(LOG_LIMIT));
        r.assertLogContains("at revision 13000", b);
        r.assertBuildStatus(Result.SUCCESS, b);

        b = p.scheduleBuild2(0, new Cause.UserIdCause()).get();
        System.out.println(b.getLog(LOG_LIMIT));
        r.assertLogContains("at revision 13000", b);
        r.assertBuildStatus(Result.SUCCESS, b);
    }

    /**
     * Tests the "URL@HEAD" format in the SVN URL
     */
    @Test
    void headRevisionCheckout() throws Exception {
        File testRepo = new CopyExisting(getClass().getResource("two-revisions.zip")).allocate();
        SubversionSCM scm = new SubversionSCM("file://" + testRepo.toURI().toURL().getPath() + "@HEAD");

        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(scm);

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause()).get();
        System.out.println(b.getLog(LOG_LIMIT));
        r.assertLogContains("At revision 2", b);
        r.assertBuildStatus(Result.SUCCESS, b);
    }

    /**
     * Test parsing of @revision information from the tail of the URL
     */
    @Test
    void moduleLocationRevisions() {
        SubversionSCM.ModuleLocation m = new SubversionSCM.ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@13000", null);
        SVNRevision r = m.getRevision(null);
        assertTrue(r.isValid());
        assertEquals(13000, r.getNumber());
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", m.getURL());

        m = new SubversionSCM.ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@HEAD", null);
        r = m.getRevision(null);
        assertTrue(r.isValid());
        assertSame(SVNRevision.HEAD, r);
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", m.getURL());

        m = new SubversionSCM.ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@FAKE", null);
        r = m.getRevision(null);
        assertFalse(r.isValid());
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@FAKE", m.getURL());
    }

    @Issue("JENKINS-10942")
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void singleModuleEnvironmentVariablesWithRevision() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@HEAD"));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(builder);

        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", builder.getEnvVars().get("SVN_URL"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant").toString(), builder.getEnvVars().get("SVN_REVISION"));
    }

    @Issue("JENKINS-10942")
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void multiModuleEnvironmentVariablesWithRevision() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        ModuleLocation[] locations = {
                new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@18075", null),
                new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven@HEAD", null)
        };
        p.setScm(new SubversionSCM(Arrays.asList(locations), new CheckoutUpdater(), null, null, null, null, null, null));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(builder);

        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", builder.getEnvVars().get("SVN_URL_1"));
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven", builder.getEnvVars().get("SVN_URL_2"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant").toString(), builder.getEnvVars().get("SVN_REVISION_1"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven").toString(), builder.getEnvVars().get("SVN_REVISION_2"));

    }

    /**
     * Tests a checkout with RevisionParameterAction
     */
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void revisionParameter() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        String url = "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant";
        p.setScm(new SubversionSCM(url));

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(),
                new RevisionParameterAction(new SubversionSCM.SvnInfo(url, 13000))).get();
        System.out.println(b.getLog(LOG_LIMIT));
        r.assertLogContains("at revision 13000", b);
        r.assertBuildStatus(Result.SUCCESS, b);
    }

    @Issue("JENKINS-22568")
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void pollingWithDefaultParametersWithCurlyBraces() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        String repo = "https://svn.jenkins-ci.org/";
        String path = "trunk/hudson/test-projects/trivial-ant/";
        p.setScm(new SubversionSCM("${REPO}" + path));
        ParametersDefinitionProperty property = new ParametersDefinitionProperty(new StringParameterDefinition("REPO", repo));
        p.addProperty(property);

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(),
                new ParametersAction(new StringParameterValue("REPO", repo))).get();

        r.assertBuildStatus(Result.SUCCESS, b);
        assertTrue(b.getWorkspace().child("build.xml").exists());

        // as a baseline, this shouldn't detect any change
        TaskListener listener = r.createTaskListener();
        PollingResult poll = p.poll(listener);
        assertFalse(poll.hasChanges(), "Polling shouldn't have any changes.");
    }

    @Issue("JENKINS-22568")
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void pollingWithDefaultParametersWithOutCurlyBraces() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        String repo = "https://svn.jenkins-ci.org";
        String path = "/trunk/hudson/test-projects/trivial-ant/";
        p.setScm(new SubversionSCM("$REPO" + path));
        ParametersDefinitionProperty property = new ParametersDefinitionProperty(new StringParameterDefinition("REPO", repo));
        p.addProperty(property);

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(),
                new ParametersAction(new StringParameterValue("REPO", repo))).get();

        r.assertBuildStatus(Result.SUCCESS, b);
        assertTrue(b.getWorkspace().child("build.xml").exists());

        // as a baseline, this shouldn't detect any change
        TaskListener listener = r.createTaskListener();
        PollingResult poll = p.poll(listener);
        assertFalse(poll.hasChanges(), "Polling shouldn't have any changes.");
    }

    @Issue("JENKINS-22568")
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void pollingWithChoiceParametersWithOutCurlyBraces() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        String repo = "https://svn.jenkins-ci.org/";
        String path = "trunk/hudson/test-projects/trivial-maven/src/test/java/test";
        p.setScm(new SubversionSCM("${REPO}" + path));
        ParametersDefinitionProperty property = new ParametersDefinitionProperty(new ChoiceParameterDefinition("REPO", new String[]{repo, "test"}, ""));
        p.addProperty(property);

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(),
                new ParametersAction(new StringParameterValue("REPO", repo))).get();

        r.assertBuildStatus(Result.SUCCESS, b);
        assertTrue(b.getWorkspace().child("AppTest.java").exists());

        // as a baseline, this shouldn't detect any change
        TaskListener listener = r.createTaskListener();
        PollingResult poll = p.poll(listener);
        assertFalse(poll.hasChanges(), "Polling shouldn't have any changes.");
    }

    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void revisionParameterFolding() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        String url = "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant";
        p.setScm(new SubversionSCM(url));

        // Schedule build of a specific revision with a quiet period
        Future<FreeStyleBuild> f = p.scheduleBuild2(60, new Cause.UserIdCause(),
                new RevisionParameterAction(new SubversionSCM.SvnInfo(url, 13000)));

        // Schedule another build at a more recent revision
        p.scheduleBuild2(0, new Cause.UserIdCause(),
                new RevisionParameterAction(new SubversionSCM.SvnInfo(url, 14000)));

        FreeStyleBuild b = f.get();

        System.out.println(b.getLog(LOG_LIMIT));
        r.assertLogContains("at revision 14000", b);
        r.assertBuildStatus(Result.SUCCESS, b);
    }

    private FreeStyleProject createPostCommitTriggerJob() throws Exception {
        // Disable crumbs because HTMLUnit refuses to mix request bodies with
        // request parameters
        r.jenkins.setCrumbIssuer(null);

        FreeStyleProject p = r.createFreeStyleProject();
        String url = "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant";
        SCMTrigger trigger = new SCMTrigger("0 */6 * * *");

        p.setScm(new SubversionSCM(url));
        p.addTrigger(trigger);
        trigger.start(p, true);

        return p;
    }

    private FreeStyleProject createPostCommitTriggerJobMultipleSvnLocations() throws Exception {
        // Disable crumbs because HTMLUnit refuses to mix request bodies with
        // request parameters
        r.jenkins.setCrumbIssuer(null);

        FreeStyleProject p = r.createFreeStyleProject();
        String[] urls = new String[]{"https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant",
                "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven/"};

        p.setScm(new SubversionSCM(urls, new String[]{"", ""}));

        SCMTrigger trigger = new SCMTrigger("0 */6 * * *");
        p.addTrigger(trigger);
        trigger.start(p, true);

        return p;
    }

    // 

    private FreeStyleBuild sendCommitTrigger(FreeStyleProject p, boolean includeRevision) throws Exception {
        String repoUUID = "71c3de6d-444a-0410-be80-ed276b4c234a";

        WebClient wc = r.createWebClient();
        WebRequest wr = new WebRequest(new URL(r.getURL() + "subversion/" + repoUUID + "/notifyCommit"), HttpMethod.POST);
        wr.setRequestBody("A   trunk/hudson/test-projects/trivial-ant/build.xml");
        wr.setAdditionalHeader("Content-Type", "text/plain;charset=UTF-8");

        if (includeRevision) {
            wr.setAdditionalHeader("X-Hudson-Subversion-Revision", "13000");
        }

        WebConnection conn = wc.getWebConnection();
        WebResponse resp = conn.getResponse(wr);
        assertTrue(r.isGoodHttpStatus(resp.getStatusCode()));

        Thread.sleep(1000);
        r.waitUntilNoActivity();
        FreeStyleBuild b = p.getLastBuild();
        assertNotNull(b);
        r.assertBuildStatus(Result.SUCCESS, b);

        return b;
    }

    private FreeStyleBuild sendCommitTriggerMultipleSvnLocations(FreeStyleProject p, boolean includeRevision) throws Exception {
        String repoUUID = "71c3de6d-444a-0410-be80-ed276b4c234a";

        WebClient wc = r.createWebClient();
        WebRequest wr = new WebRequest(new URL(r.getURL() + "subversion/" + repoUUID + "/notifyCommit"), HttpMethod.POST);
        wr.setRequestBody("A   trunk/hudson/test-projects/trivial-ant/build.xml\n" +
                "M   trunk/hudson/test-projects/trivial-maven/src/main/");
        wr.setAdditionalHeader("Content-Type", "text/plain;charset=UTF-8");

        if (includeRevision) {
            wr.setAdditionalHeader("X-Hudson-Subversion-Revision", "18075");
        }

        WebConnection conn = wc.getWebConnection();
        WebResponse resp = conn.getResponse(wr);
        assertTrue(r.isGoodHttpStatus(resp.getStatusCode()));

        Thread.sleep(1000);
        r.waitUntilNoActivity();
        FreeStyleBuild b = p.getLastBuild();
        assertNotNull(b);
        r.assertBuildStatus(Result.SUCCESS, b);

        return b;
    }

    public Long getActualRevision(FreeStyleBuild b, String url) throws Exception {
        SVNRevisionState revisionState = b.getAction(SVNRevisionState.class);
        if (revisionState == null) {
            throw new Exception("No revision found!");
        }

        return revisionState.revisions.get(url);

    }

    /**
     * Tests a checkout triggered from the post-commit hook
     */
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void postCommitTrigger() throws Exception {
        FreeStyleProject p = createPostCommitTriggerJob();
        FreeStyleBuild b = sendCommitTrigger(p, true);

        assertTrue(getActualRevision(b, "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant") <= 13000);
    }

    /**
     * Tests a checkout triggered from the post-commit hook
     */
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void postCommitTriggerMultipleSvnLocations() throws Exception {
        FreeStyleProject p = createPostCommitTriggerJobMultipleSvnLocations();
        FreeStyleBuild b = sendCommitTriggerMultipleSvnLocations(p, true);

        assertTrue(getActualRevision(b, "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant") <= 18075);
        Long actualRevision = getActualRevision(b, "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven");
        assertEquals(Long.valueOf(18075), actualRevision);

        List<RevisionParameterAction> actions = b.getActions(RevisionParameterAction.class);
        assertEquals(1, actions.size());

        RevisionParameterAction action = actions.get(0);
        assertEquals(2, action.getRevisions().size());
    }

    /**
     * Tests a checkout triggered from the post-commit hook without revision
     * information.
     */
    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void postCommitTriggerNoRevision() throws Exception {
        FreeStyleProject p = createPostCommitTriggerJob();
        FreeStyleBuild b = sendCommitTrigger(p, false);

        assertTrue(getActualRevision(b, "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant") > 13000);
    }

    /**
     * {@link SubversionSCM#pollChanges(AbstractProject, Launcher, FilePath, TaskListener)} should notice
     * if the workspace and the current configuration is inconsistent and schedule a new build.
     */
    @Email("http://www.nabble.com/Proper-way-to-switch---relocate-SVN-tree---tt21173306.html")
    @Test
    void pollingAfterRelocation() throws Exception {
        // fetch the current workspace
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(loadSvnRepo());
        p.scheduleBuild2(0, new Cause.UserIdCause()).get();

        // as a baseline, this shouldn't detect any change
        TaskListener listener = r.createTaskListener();
        assertFalse(p.poll(listener).hasChanges());

        // now switch the repository to a new one.
        // this time the polling should indicate that we need a new build
        p.setScm(loadSvnRepo());
        assertTrue(p.poll(listener).hasChanges());

        // build it once again to switch
        p.scheduleBuild2(0, new Cause.UserIdCause()).get();

        // then no more change should be detected
        assertFalse(p.poll(listener).hasChanges());
    }

    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void urlWithVariable() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        // --- 1st case: URL with a variable ---

        String repo = "https://svn.jenkins-ci.org";
        String path = "/trunk/hudson/test-projects/trivial-maven/src/test/java/test";
        p.setScm(new SubversionSCM("$REPO" + path));

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(),
                new ParametersAction(new StringParameterValue("REPO", repo))).get();
        System.out.println(b.getLog(LOG_LIMIT));
        r.assertBuildStatus(Result.SUCCESS, b);
        assertTrue(b.getWorkspace().child("AppTest.java").exists());

        // --- 2nd case: URL with an empty variable ---

        p.setScm(new SubversionSCM(repo + path + "$EMPTY_VAR"));

        b = p.scheduleBuild2(0, new Cause.UserIdCause(),
                new ParametersAction(new StringParameterValue("EMPTY_VAR", ""))).get();
        r.assertBuildStatus(Result.SUCCESS, b);
        assertTrue(b.getWorkspace().child("AppTest.java").exists());
    }

    /**
     * Test that multiple repository URLs are all polled.
     */
    @Issue("JENKINS-3168")
    @Test
    void pollMultipleRepositories() throws Exception {
        // fetch the current workspaces
        FreeStyleProject p = r.createFreeStyleProject();
        String svnBase = "file://" + new CopyExisting(getClass().getResource("/svn-repo.zip")).allocate().toURI().toURL().getPath();
        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(new ModuleLocation(svnBase + "trunk", null), new ModuleLocation(svnBase + "branches", null)),
                new CheckoutUpdater(), null, null, null, null, null, null);
        p.setScm(scm);
        p.scheduleBuild2(0, new Cause.UserIdCause()).get();

        // as a baseline, this shouldn't detect any change
        TaskListener listener = r.createTaskListener();
        assertFalse(p.poll(listener).hasChanges());

        createCommit(scm, "branches/foo");
        assertTrue(p.poll(listener).hasChanges(), "any change in any of the repository should be detected");
        assertFalse(p.poll(listener).hasChanges(), "no change since the last polling");
        createCommit(scm, "trunk/foo");
        assertTrue(p.poll(listener).hasChanges(), "another change in the repo should be detected separately");
    }


    /**
     * Test that multiple repository URLs are all polled.
     */
    @Issue("JENKINS-7461")
    @Test
    void multipleRepositories() throws Exception {
        // fetch the current workspaces
        FreeStyleProject p = r.createFreeStyleProject();
        String svnBase = "file://" + new CopyExisting(getClass().getResource("/svn-repo.zip")).allocate().toURI().toURL().getPath();
        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(new ModuleLocation(svnBase + "trunk", "trunk")),
                new UpdateUpdater(), null, null, null, null, null, null);
        p.setScm(scm);
        Run r1 = p.scheduleBuild2(0, new Cause.UserIdCause()).get();
        r.assertLogContains("Cleaning local Directory trunk", r1);

        scm = new SubversionSCM(
                Arrays.asList(new ModuleLocation(svnBase + "trunk", "trunk"), new ModuleLocation(svnBase + "branches", "branches")),
                new UpdateUpdater(), null, null, null, null, null, null);
        p.setScm(scm);
        Run r2 = p.scheduleBuild2(0, new Cause.UserIdCause()).get();
        r.assertLogContains("Updating " + svnBase + "trunk", r2);
        r.assertLogContains("Cleaning local Directory branches", r2);
    }

    @Test
    void multipleRepositoriesSvn17() throws Exception {
        configureSvnWorkspaceFormat2(SubversionWorkspaceSelector.WC_FORMAT_17);
        multipleRepositories();
    }

    @Test
    void configRoundtrip() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(
                        new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", "c"),
                        new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", "d")),
                new UpdateUpdater(), new Sventon(new URL("http://www.sun.com/"), "test"), "exclude", "user", "revprop", "excludeMessage", null);
        p.setScm(scm);

        r.configRoundtrip(p);
        verify(scm, (SubversionSCM) p.getScm());

        scm = new SubversionSCM(
                Arrays.asList(
                        new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", "c")),
                new CheckoutUpdater(), null, "", "", "", "", null);
        p.setScm(scm);

        r.configRoundtrip(p);
        verify(scm, (SubversionSCM) p.getScm());
    }

    @Issue("JENKINS-7944")
    @Test
    void configRoundtrip2() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(
                        new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", "")),
                new UpdateUpdater(), null, null, null, null, null, null);
        p.setScm(scm);
        r.configRoundtrip((Item) p);
        verify(scm, (SubversionSCM) p.getScm());
    }

    @Issue("JENKINS-9143")
    @Test
    void checkEmptyRemoteRemoved() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        List<ModuleLocation> locs = new ArrayList<>();
        locs.add(new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", "c"));
        locs.add(new ModuleLocation("", "d"));
        locs.add(new ModuleLocation("    ", "e"));

        SubversionSCM scm = new SubversionSCM(
                locs,
                new UpdateUpdater(), new Sventon(new URL("http://www.sun.com/"), "test"), "exclude", "user", "revprop", "excludeMessage", null);
        p.setScm(scm);

        r.configRoundtrip(p);
        ModuleLocation[] ml = ((SubversionSCM) p.getScm()).getLocations();
        assertEquals(1, ml.length);
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", ml[0].remote);
    }

    @Issue("JENKINS-5684")
    @Test
    void doCheckExcludedUsers() throws Exception {
        String[] validUsernames = new String[]{
                "DOMAIN\\user",
                "user",
                "us_er",
                "user123",
                "User",
                "User-Name",
                "Do-Main\\User-Name",
                "", // this one is ignored
                "DOmain12\\User34"};

        for (String validUsername : validUsernames) {
            assertEquals(
                    FormValidation.Kind.OK,
                    new SubversionSCM.DescriptorImpl().doCheckExcludedUsers(validUsername).kind,
                    "User " + validUsername + " isn't OK (but it's valid).");
        }

        String[] invalidUsernames = new String[]{
                "\\user",
                "DOMAIN\\",
                "DOMAIN@user",
                "DOMAIN.user"};

        for (String invalidUsername : invalidUsernames) {
            assertEquals(
                    FormValidation.Kind.ERROR,
                    new SubversionSCM.DescriptorImpl().doCheckExcludedUsers(invalidUsername).kind,
                    "User " + invalidUsername + " isn't ERROR (but it's not valid).");
        }

    }

    // could not be simplified with r.assertEqualDataBoundBeans(lhs, rhs) due to locations type mismatch between constructor arg and field.
    private void verify(SubversionSCM lhs, SubversionSCM rhs) {
        ModuleLocation[] ll = lhs.getLocations();
        ModuleLocation[] rl = rhs.getLocations();
        assertEquals(ll.length, rl.length);
        for (int i = 0; i < ll.length; i++) {
            assertEquals(ll[i].local, rl[i].local);
            assertEquals(ll[i].remote, rl[i].remote);
        }

        assertNullEquals(lhs.getExcludedRegions(), rhs.getExcludedRegions());
        assertNullEquals(lhs.getExcludedUsers(), rhs.getExcludedUsers());
        assertNullEquals(lhs.getExcludedRevprop(), rhs.getExcludedRevprop());
        assertNullEquals(lhs.getExcludedCommitMessages(), rhs.getExcludedCommitMessages());
        assertNullEquals(lhs.getIncludedRegions(), rhs.getIncludedRegions());
        assertEquals(lhs.getWorkspaceUpdater().getClass(), rhs.getWorkspaceUpdater().getClass());
    }

    private void assertNullEquals(String left, String right) {
        if (left == null)
            left = "";
        if (right == null)
            right = "";
        assertEquals(left, right);
    }

    @WithoutJenkins
    @Test
    void svnUrlParsing() {
        check("http://foobar/");
        check("https://foobar/");
        check("file://foobar/");
        check("svn://foobar/");
        check("svn+ssh://foobar/");
    }

    private void check(String url) {
        assertTrue(SubversionSCM.URL_PATTERN.matcher(url).matches());
    }

    /**
     * Makes sure that Subversion doesn't check out workspace in 1.6
     */
    @Email("http://www.nabble.com/SVN-1.6-td24081571.html")
    @Test
    void workspaceVersion() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(loadSvnRepo());
        FreeStyleBuild b = p.scheduleBuild2(0).get();

        SvnClientManager wc = SubversionSCM.createClientManager((AbstractProject) null);
        SVNStatus st = wc.getStatusClient().doStatus(new File(b.getWorkspace().getRemote() + "/a"), false);
        int wcf = st.getWorkingCopyFormat();
        System.out.println(wcf);
        assertEquals(SVNAdminAreaFactory.WC_FORMAT_14, wcf);
    }

    private static String readFileAsString(File file)
            throws java.io.IOException {
        StringBuilder fileData = new StringBuilder(1000);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        char[] buf = new char[1024];
        int numRead = 0;
        while ((numRead = reader.read(buf)) != -1) {
            fileData.append(buf, 0, numRead);
        }
        reader.close();
        return fileData.toString();
    }

    /**
     * Makes sure that quiet operation shows lesser output.
     */
    @Issue("JENKINS-14541")
    @Test
    void testQuietCheckout() throws Exception {
        SubversionSCM local = loadSvnRepo();
        local.setWorkspaceUpdater(new CheckoutUpdater());
        FreeStyleProject p = r.createFreeStyleProject("quietOperation");
        p.setScm(local);

        local.setQuietOperation(true);
        FreeStyleBuild bQuiet = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause()).get());
        List<String> logsQuiet = bQuiet.getLog(LOG_LIMIT);
        //  This line in log should end with --quiet
        assertTrue(logsQuiet.get(5).endsWith("--quiet"));
        assertEquals("At revision 1", logsQuiet.get(6));

        local.setQuietOperation(false);
        FreeStyleBuild bVerbose = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause()).get());
        List<String> logsVerbose = bVerbose.getLog(LOG_LIMIT);
        //  This line in log should NOT end with --quiet
        assertFalse(logsVerbose.get(4).endsWith("--quiet"));
        assertTrue(logsVerbose.get(6).endsWith("readme.txt"));
        assertEquals("At revision 1", logsVerbose.get(7));
    }

    /**
     * Makes sure the symbolic link is checked out correctly. There seems to be
     */
    @Issue("JENKINS-3904")
    @Test
    void symbolicLinkCheckout() throws Exception {
        // Only perform if symlink behavior is enabled
        if (!"true".equals(System.getProperty("svnkit.symlinks"))) {
            return;
        }

        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/issue-3904"));

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause()).get();
        File source = new File(b.getWorkspace().getRemote() + "/readme.txt");
        File linked = new File(b.getWorkspace().getRemote() + "/linked.txt");
        assertEquals(readFileAsString(source), readFileAsString(linked), "Files '" + source + "' and '" + linked + "' are not identical from user view.");
    }

    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void excludeByUser() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("testExcludeByUser");
        p.setScm(new SubversionSCM(
                Arrays.asList(new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusions@19438", null)),
                new UpdateUpdater(), null, "", "dty", "", "", null)
        );
        // Do a build to force the creation of the workspace. This works around
        // pollChanges returning true when the workspace does not exist.
        p.scheduleBuild2(0).get();

        boolean foundChanges = p.poll(r.createTaskListener()).hasChanges();
        assertFalse(foundChanges, "Polling found changes that should have been ignored");
    }

    /**
     * Test excluded regions
     */
    @Issue("JENKINS-6030")
    @Test
    void excludedRegions() throws Exception {
//        SLAVE_DEBUG_PORT = 8001;
        File repo = new CopyExisting(getClass().getResource("HUDSON-6030.zip")).allocate();
        SubversionSCM scm = new SubversionSCM(ModuleLocation.parse(new String[]{"file://" + repo.toURI().toURL().getPath()},
                new String[]{"."}, null, null),
                new UpdateUpdater(), null, ".*/bar", "", "", "", "");

        FreeStyleProject p = r.createFreeStyleProject("testExcludedRegions");
        p.setScm(scm);
        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        // initial polling on the slave for the code path that doesn't find any change
        assertFalse(p.poll(r.createTaskListener()).hasChanges());

        createCommit(scm, "bar");

        // polling on the slave for the code path that does have a change but should be excluded.
        assertFalse(p.poll(r.createTaskListener()).hasChanges(),
                "Polling found changes that should have been ignored");

        createCommit(scm, "foo");

        // polling on the slave for the code path that doesn't find any change
        assertTrue(p.poll(r.createTaskListener()).hasChanges(),
                "Polling didn't find a change it should have found.");

    }

    /**
     * Test included regions
     */
    @Issue("JENKINS-6030")
    @Test
    void includedRegions() throws Exception {
//        SLAVE_DEBUG_PORT = 8001;
        File repo = new CopyExisting(getClass().getResource("HUDSON-6030.zip")).allocate();
        SubversionSCM scm = new SubversionSCM(ModuleLocation.parse(new String[]{"file://" + repo.toURI().toURL().getPath()},
                new String[]{"."}, null, null),
                new UpdateUpdater(), null, "", "", "", "", ".*/foo");

        FreeStyleProject p = r.createFreeStyleProject("testExcludedRegions");
        p.setScm(scm);
        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        // initial polling on the slave for the code path that doesn't find any change
        assertFalse(p.poll(r.createTaskListener()).hasChanges());

        createCommit(scm, "bar");

        // polling on the slave for the code path that does have a change but should be excluded.
        assertFalse(p.poll(r.createTaskListener()).hasChanges(),
                "Polling found changes that should have been ignored");

        createCommit(scm, "foo");

        // polling on the slave for the code path that doesn't find any change
        assertTrue(p.poll(r.createTaskListener()).hasChanges(),
                "Polling didn't find a change it should have found.");

    }

    @Issue("JENKINS-10449")
    @Test
    void testFilterChangelog() throws Exception {
        verifyChangelogFilter(true);
        verifyChangelogFilter(false);
    }

    private void verifyChangelogFilter(boolean shouldFilterLog) throws Exception {
        File repo = new CopyExisting(getClass().getResource("JENKINS-10449.zip")).allocate();
        SubversionSCM scm = new SubversionSCM(ModuleLocation.parse(new String[]{"file://" + repo.toURI().toURL().getPath()},
                new String[]{"."}, null, null),
                new UpdateUpdater(), null, "/z.*", "", "", "", "", false, shouldFilterLog, null);

        FreeStyleProject p = r.createFreeStyleProject(String.format("testFilterChangelog-%s", shouldFilterLog));
        p.setScm(scm);
        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        // initial polling on the slave for the code path that doesn't find any change
        assertFalse(p.poll(r.createTaskListener()).hasChanges());

        createCommit(scm, "z/q");

        // polling on the slave for the code path that does have a change but should be excluded.
        assertFalse(p.poll(r.createTaskListener()).hasChanges(),
                "Polling found changes that should have been ignored");

        createCommit(scm, "foo");

        assertTrue(p.poll(r.createTaskListener()).hasChanges(),
                "Polling didn't find a change it should have found.");

        AbstractBuild build = p.scheduleBuild2(0).get();
        r.assertBuildStatusSuccess(build);
        boolean ignored = true, included = false;
        @SuppressWarnings("unchecked")
        ChangeLogSet<Entry> cls = build.getChangeSet();
        for (Entry e : cls) {
            Collection<String> paths = e.getAffectedPaths();
            if (paths.contains("/z/q"))
                ignored = false;
            if (paths.contains("/foo"))
                included = true;
        }

        boolean result = ignored && included;
        assertEquals(shouldFilterLog, result, "Changelog included or excluded entries it shouldn't have.");
    }

    /**
     * Do the polling on the slave and make sure it works.
     */
    @Issue("JENKINS-4299")
    @Test
    void polling() throws Exception {
//        SLAVE_DEBUG_PORT = 8001;
        File repo = new CopyExisting(getClass().getResource("two-revisions.zip")).allocate();
        SubversionSCM scm = new SubversionSCM("file://" + repo.toURI().toURL().getPath());

        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(scm);
        p.setAssignedLabel(r.createSlave().getSelfLabel());
        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        // initial polling on the slave for the code path that doesn't find any change
        assertFalse(p.poll(StreamTaskListener.fromStdout()).hasChanges());

        createCommit(scm, "foo");

        // polling on the slave for the code path that doesn't find any change
        assertTrue(p.poll(StreamTaskListener.fromStdout()).hasChanges());
    }

    @Test
    void ignorePropertyOnlyDirChanges() throws Exception {
        File repo = new CopyExisting(getClass().getResource("ignoreProps.zip")).allocate();
        FreeStyleProject p = r.createFreeStyleProject("testIgnorePropertyOnlyDirChanges");
        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(new ModuleLocation("file://" + repo.toURI().toURL().getPath() + "/p", ".")),
                new UpdateUpdater(), null, null, null, null, null, null, true);
        p.setScm(scm);
        // Do a build to force the creation of the workspace. This works around
        // pollChanges returning true when the workspace does not exist.
        p.scheduleBuild2(0).get();

        createWorkingCopy(scm);
        changeProperties("");
        commitWorkingCopy("meta only");

        boolean foundChanges = p.poll(r.createTaskListener()).hasChanges();
        assertFalse(foundChanges, "Property only changes commit should have been ignored.");

        p.scheduleBuild2(0).get();
        changeProperties("");
        addFiles("x", "y");
        commitWorkingCopy("meta + add");

        foundChanges = p.poll(r.createTaskListener()).hasChanges();
        assertTrue(foundChanges, "Non Property only changes (adds) commit should not be ignored.");

        p.scheduleBuild2(0).get();

        changeProperties("", "c1");
        changeFiles("x", "y", "c1/f2.txt");
        commitWorkingCopy("meta + files");

        foundChanges = p.poll(r.createTaskListener()).hasChanges();
        assertTrue(foundChanges, "Non Property only changes (modify) commit should not be ignored.");

        // ignored commit followed by not ignored commit

        p.scheduleBuild2(0).get();
        changeProperties("");
        commitWorkingCopy("meta only");
        changeFiles("x", "y");
        commitWorkingCopy("files");

        foundChanges = p.poll(r.createTaskListener()).hasChanges();
        assertTrue(foundChanges, "Non Property only changes commit should not be ignored.");

        p.scheduleBuild2(0).get();
        changeProperties("c1");
        commitWorkingCopy("meta only");

        foundChanges = p.poll(r.createTaskListener()).hasChanges();
        assertFalse(foundChanges, "Property only changes commit should be ignored.");
    }

    /**
     * Manufactures commits including metadata
     *
     * @return
     */
    private void createWorkingCopy(SubversionSCM scm) throws Exception {
        FreeStyleProject forCommit = r.createFreeStyleProject();
        forCommit.setScm(scm);
        forCommit.setAssignedLabel(r.jenkins.getSelfLabel());
        FreeStyleBuild b = r.assertBuildStatusSuccess(forCommit.scheduleBuild2(0).get());
        workingcopy = b.getWorkspace();
    }

    private void commitWorkingCopy(String comment) throws Exception {
        SvnClientManager svnm = SubversionSCM.createClientManager((AbstractProject) null);
        svnm
                .getCommitClient()
                .doCommit(new File[]{new File(workingcopy.getRemote())}, false, comment, null, null, false, false, SVNDepth.INFINITY);
        svnm
                .getUpdateClient()
                .doUpdate(new File(workingcopy.getRemote()), SVNRevision.HEAD, SVNDepth.INFINITY, false, false);
    }

    private void addFiles(String... paths) throws Exception {
        SvnClientManager svnm = SubversionSCM.createClientManager((AbstractProject) null);
        for (String path : paths) {
            FilePath newFile = workingcopy.child(path);
            newFile.touch(System.currentTimeMillis());
            svnm.getWCClient().doAdd(new File(newFile.getRemote()), false, false, false, SVNDepth.INFINITY, false, false);
        }
    }

    private void changeFiles(String... paths) throws Exception {
        SvnClientManager svnm = SubversionSCM.createClientManager((AbstractProject) null);
        for (String path : paths) {
            FilePath newFile = workingcopy.child(path);
            newFile.write(new Date().toString(), "UTF-8");
        }
    }

    private void changeProperties(String... paths) throws Exception {
        SvnClientManager svnm = SubversionSCM.createClientManager((AbstractProject) null);
        for (String path : paths) {
            FilePath newFile = workingcopy.child(path);
            svnm.getWCClient().doSetProperty(
                    new File(newFile.getRemote()), "date",
                    SVNPropertyValue.create(new Date().toString()),
                    true, SVNDepth.EMPTY, null, null);
        }
    }

    /**
     * Manufactures commits by adding files in the given names.
     */
    private void createCommit(SubversionSCM scm, String... paths) throws Exception {
        FreeStyleProject forCommit = r.createFreeStyleProject();
        forCommit.setScm(scm);
        forCommit.setAssignedLabel(r.jenkins.getSelfLabel());
        FreeStyleBuild b = r.assertBuildStatusSuccess(forCommit.scheduleBuild2(0).get());
        SvnClientManager svnm = SubversionSCM.createClientManager((AbstractProject) null);

        List<File> added = new ArrayList<>();
        for (String path : paths) {
            FilePath newFile = b.getWorkspace().child(path);
            added.add(new File(newFile.getRemote()));
            if (!newFile.exists()) {
                newFile.touch(System.currentTimeMillis());
                svnm.getWCClient().doAdd(new File(newFile.getRemote()), false, false, false, SVNDepth.INFINITY, false, false);
            } else
                newFile.write("random content", "UTF-8");
        }
        SVNCommitClient cc = svnm.getCommitClient();
        cc.doCommit(added.toArray(new File[0]), false, "added", null, null, false, false, SVNDepth.EMPTY);
    }


    @Test
    void masterPolling() throws Exception {
        File repo = new CopyExisting(getClass().getResource("two-revisions.zip")).allocate();
        SubversionSCM scm = new SubversionSCM("file://" + repo.toURI().toURL().getPath());
        scm.setPollFromMaster(true);

        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(scm);
        p.setAssignedLabel(r.createSlave().getSelfLabel());
        r.assertBuildStatusSuccess(p.scheduleBuild2(2).get());

        // initial polling on the master for the code path that doesn't find any change
        assertFalse(p.poll(StreamTaskListener.fromStdout()).hasChanges());

        // create a commit
        FreeStyleProject forCommit = r.createFreeStyleProject();
        forCommit.setScm(scm);
        forCommit.setAssignedLabel(r.jenkins.getSelfLabel());
        FreeStyleBuild b = r.assertBuildStatusSuccess(forCommit.scheduleBuild2(0).get());
        FilePath newFile = b.getWorkspace().child("foo");
        newFile.touch(System.currentTimeMillis());
        SvnClientManager svnm = SubversionSCM.createClientManager(p);
        svnm.getWCClient().doAdd(new File(newFile.getRemote()), false, false, false, SVNDepth.INFINITY, false, false);
        SVNCommitClient cc = svnm.getCommitClient();
        cc.doCommit(new File[]{new File(newFile.getRemote())}, false, "added", null, null, false, false, SVNDepth.INFINITY);

        // polling on the master for the code path that doesn't find any change
        assertTrue(p.poll(StreamTaskListener.fromStdout()).hasChanges());
    }


    @Test
    void compareSVNAuthentications() {
        assertFalse(SubversionSCM.compareSVNAuthentications(new SVNUserNameAuthentication("me", true), new SVNSSHAuthentication("me", "me", 22, true)));
        // same object should compare equal
        _idem(new SVNUserNameAuthentication("me", true));
        _idem(new SVNSSHAuthentication("me", "pass", 22, true));
        _idem(new SVNSSHAuthentication("me", new File("./some.key"), null, 23, false));
        _idem(new SVNSSHAuthentication("me", "key".toCharArray(), "phrase", 0, false));
        _idem(new SVNPasswordAuthentication("me", "pass", true));
        _idem(new SVNSSLAuthentication(new File("./some.key"), "", true));

        // make sure two Files and char[]s compare the same 
        assertTrue(SubversionSCM.compareSVNAuthentications(
                new SVNSSHAuthentication("me", new File("./some.key"), null, 23, false),
                new SVNSSHAuthentication("me", new File("./some.key"), null, 23, false)));
        assertTrue(SubversionSCM.compareSVNAuthentications(
                new SVNSSHAuthentication("me", "key".toCharArray(), "phrase", 0, false),
                new SVNSSHAuthentication("me", "key".toCharArray(), "phrase", 0, false)));

        // negative cases
        assertFalse(SubversionSCM.compareSVNAuthentications(
                new SVNSSHAuthentication("me", new File("./some1.key"), null, 23, false),
                new SVNSSHAuthentication("me", new File("./some2.key"), null, 23, false)));
        assertFalse(SubversionSCM.compareSVNAuthentications(
                new SVNSSHAuthentication("me", "key".toCharArray(), "phrase", 0, false),
                new SVNSSHAuthentication("yo", "key".toCharArray(), "phrase", 0, false)));

    }

    private void _idem(SVNAuthentication a) {
        assertTrue(SubversionSCM.compareSVNAuthentications(a, a));
    }

    /**
     * Make sure that a failed credential doesn't result in an infinite loop
     */
    @Issue("JENKINS-2909")
    @Disabled("TODO verify that this test case is invalid for new credentials based world order")
    @Test
    void infiniteLoop() throws Exception {
        // creates a purely in memory auth manager
        ISVNAuthenticationManager m = createInMemoryManager();

        // double check that it really knows nothing about the fake repo
        assertThrows(SVNCancelException.class, () -> m.getFirstAuthentication(KIND, REALM, repo));

        // let Jenkins have the credential
        r.jenkins.getDescriptorByType(SubversionSCM.DescriptorImpl.class).postCredential(null, repo.toDecodedString(), "guest", "", null, new PrintWriter(System.out));

        // emulate the call flow where the credential fails
        List<SVNAuthentication> attempted = new ArrayList<>();
        SVNAuthentication a = m.getFirstAuthentication(KIND, REALM, repo);
        assertNotNull(a);
        attempted.add(a);
        for (int i = 0; i < 10; i++) {
            m.acknowledgeAuthentication(false, KIND, REALM, new RemotableSVNErrorMessage(SVNErrorCode.RA_NOT_AUTHORIZED), a);
            try {
                a = m.getNextAuthentication(KIND, REALM, repo);
                assertNotNull(a);
                attempted.add(a);
            } catch (SVNCancelException e) {
                // make sure we've tried our fake credential
                for (SVNAuthentication aa : attempted) {
                    if (aa instanceof SVNPasswordAuthentication pa) {
                        if (pa.getUserName().equals("guest") && pa.getPassword().isEmpty())
                            return; // yep
                    }
                }
                fail("Jenkins didn't try authentication");
            }
        }
        fail("Looks like we went into an infinite loop");
    }

    /**
     * Even if the default providers remember bogus passwords, Jenkins should still attempt what it knows.
     */
    @Issue("JENKINS-3936")
    @Disabled("TODO verify that this test case is invalid for new credentials based world order")
    @Test
    void retainBogusPasswords() throws Exception {
        // creates a purely in memory auth manager
        ISVNAuthenticationManager m = createInMemoryManager();

        // double check that it really knows nothing about the fake repo
        assertThrows(SVNCancelException.class, () -> m.getFirstAuthentication(KIND, REALM, repo));

        // teach a bogus credential and have SVNKit store it.
        SVNPasswordAuthentication bogus = new SVNPasswordAuthentication("bogus", "bogus", true);
        m.acknowledgeAuthentication(true, KIND, REALM, null, bogus);
        assertTrue(SubversionSCM.compareSVNAuthentications(m.getFirstAuthentication(KIND, REALM, repo), bogus));

        assertThrows(SVNCancelException.class, () -> attemptAccess(m), "SVNKit shouldn't yet know how to access");

        // make sure the failure didn't clean up the cache,
        // since what we want to test here is Jenkins trying to supply its credential, despite the failed cache
        assertTrue(SubversionSCM.compareSVNAuthentications(m.getFirstAuthentication(KIND, REALM, repo), bogus));

        // now let Jenkins have the real credential
        // can we now access the repo?
        r.jenkins.getDescriptorByType(SubversionSCM.DescriptorImpl.class).postCredential(null, repo.toDecodedString(), "guest", "", null, new PrintWriter(System.out));
        attemptAccess(m);
    }

    private void attemptAccess(ISVNAuthenticationManager m) throws SVNException {
        SVNRepository repository = SVNRepositoryFactory.create(repo);
        try {
            repository.setAuthenticationManager(m);
            repository.testConnection();
        } finally {
            repository.closeSession();
        }
    }

    private ISVNAuthenticationManager createInMemoryManager() {
        ISVNAuthenticationManager m = new SVNAuthenticationManager(r.jenkins.root, null, null);
        m.setAuthenticationProvider(r.jenkins.getDescriptorByType(SubversionSCM.DescriptorImpl.class).createAuthenticationProvider(null));
        return m;
    }

    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void multiModuleEnvironmentVariables() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        ModuleLocation[] locations = {
                new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", null),
                new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven", null)
        };
        p.setScm(new SubversionSCM(Arrays.asList(locations), new CheckoutUpdater(), null, null, null, null, null, null));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(builder);

        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", builder.getEnvVars().get("SVN_URL_1"));
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven", builder.getEnvVars().get("SVN_URL_2"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant").toString(), builder.getEnvVars().get("SVN_REVISION_1"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven").toString(), builder.getEnvVars().get("SVN_REVISION_2"));

    }

    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void singleModuleEnvironmentVariables() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant"));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(builder);

        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", builder.getEnvVars().get("SVN_URL"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant").toString(), builder.getEnvVars().get("SVN_REVISION"));
    }

    @Test
    @Disabled("TODO use docker-fixtures for a reproducible test case")
    void recursiveEnvironmentVariables() throws Exception {
        EnvironmentContributor.all().add(new EnvironmentContributor() {
            @Override
            public void buildEnvironmentFor(Run run, EnvVars ev, TaskListener tl) {
                ev.put("TOOL", "ant");
                ev.put("ROOT", "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-${TOOL}");
            }
        });
        FreeStyleProject p = r.createFreeStyleProject("job-with-envs");
        p.setScm(new SubversionSCM("$ROOT"));
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(builder);
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertTrue(p.getLastBuild().getWorkspace().child("build.xml").exists());
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", builder.getEnvVars().get("SVN_URL"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant").toString(), builder.getEnvVars().get("SVN_REVISION"));
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", builder.getEnvVars().get("SVN_URL_1"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant").toString(), builder.getEnvVars().get("SVN_REVISION_1"));
    }

    @Issue("JENKINS-1379")
    @Test
    void multipleCredentialsPerRepo() throws Exception {
        Proc p = runSvnServe(getClass().getResource("HUDSON-1379.zip"));
        try {
            SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(),
                    Collections.emptyList()
            ));

            FreeStyleProject b = r.createFreeStyleProject();
            b.setScm(new SubversionSCM("svn://localhost/bob", "1-bob", "."));

            FreeStyleProject c = r.createFreeStyleProject();
            c.setScm(new SubversionSCM("svn://localhost/charlie", "2-charlie", "."));

            // should fail without a credential
            r.assertBuildStatus(Result.FAILURE, b.scheduleBuild2(0).get());
            SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(),
                    Arrays.asList(
                            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "1-bob", null, "bob", "bob")
                    )
            ));
            r.buildAndAssertSuccess(b);

            r.assertBuildStatus(Result.FAILURE, c.scheduleBuild2(0).get());
            SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(),
                    Arrays.asList(
                            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "1-bob", null, "bob", "bob"),
                            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "2-charlie", null, "charlie", "charlie")
                    )
            ));
            r.buildAndAssertSuccess(c);

            // b should still build fine.
            r.buildAndAssertSuccess(b);
        } finally {
            p.kill();
        }
    }

    /**
     * Subversion externals to a file. Requires 1.6 workspace.
     */
    @Issue("JENKINS-7539")
    @Test
    void externalsToFile() throws Exception {
        Proc server = runSvnServe(getClass().getResource("HUDSON-7539.zip"));
        try {
            // enable 1.6 mode
            HtmlForm f = r.createWebClient().goTo("configure").getFormByName("config");
            f.getSelectByName("svn.workspaceFormat").setSelectedAttribute("10", true);
            r.submit(f);

            FreeStyleProject p = r.createFreeStyleProject();
            p.setScm(new SubversionSCM("svn://localhost/dir1"));
            FreeStyleBuild b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

            assertTrue(b.getWorkspace().child("2").exists());
            assertTrue(b.getWorkspace().child("3").exists());
            assertTrue(b.getWorkspace().child("test.x").exists());

            r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        } finally {
            server.kill();
        }
    }

    @Issue("JENKINS-1379")
    @Test
    void superUserForAllRepos() throws Exception {
        Proc p = runSvnServe(getClass().getResource("HUDSON-1379.zip"));
        try {
            SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(),
                    Arrays.asList(
                            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "1-alice", null, "alice", "alice")
                    )
            ));
            FreeStyleProject b = r.createFreeStyleProject();
            b.setScm(new SubversionSCM("svn://localhost/bob"));

            FreeStyleProject c = r.createFreeStyleProject();
            c.setScm(new SubversionSCM("svn://localhost/charlie"));

            // should fail without a credential
            r.assertBuildStatus(Result.FAILURE, b.scheduleBuild2(0).get());
            r.assertBuildStatus(Result.FAILURE, c.scheduleBuild2(0).get());

            b.setScm(new SubversionSCM("svn://localhost/bob", "1-alice", "."));
            c.setScm(new SubversionSCM("svn://localhost/charlie", "1-alice", "."));
            // but with the super user credential both should work now
            r.buildAndAssertSuccess(b);
            r.buildAndAssertSuccess(c);
        } finally {
            p.kill();
        }
    }

    /**
     * Ensures that the introduction of {@link WorkspaceUpdater} maintains backward compatibility with
     * existing data.
     */
    @Test
    void workspaceUpdaterCompatibility() throws Exception {
        Proc p = runSvnServe(getClass().getResource("small.zip"));
        try {
            verifyCompatibility("legacy-update.xml", UpdateUpdater.class);
            verifyCompatibility("legacy-checkout.xml", CheckoutUpdater.class);
            verifyCompatibility("legacy-revert.xml", UpdateWithRevertUpdater.class);
        } finally {
            p.kill();
        }
    }

    private void verifyCompatibility(String resourceName, Class<? extends WorkspaceUpdater> expected) throws Exception {
        TopLevelItem item = r.jenkins.getItem("update");
        if (item != null) {
            item.delete();
        }
        AbstractProject job = (AbstractProject) r.jenkins.createProjectFromXML("update", getClass().getResourceAsStream(resourceName));
        assertEquals(expected, ((SubversionSCM) job.getScm()).getWorkspaceUpdater().getClass());
    }

    @Test
    void updateWithCleanUpdater() throws Exception {
        // this contains an empty "a" file and svn:ignore that ignores b
        Proc srv = runSvnServe(getClass().getResource("clean-update-test.zip"));
        try {
            FreeStyleProject p = r.createFreeStyleProject();
            SubversionSCM scm = new SubversionSCM("svn://localhost/");
            scm.setWorkspaceUpdater(new UpdateWithCleanUpdater());
            p.setScm(scm);

            p.getBuildersList().add(new TestBuilder() {
                @Override
                public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                    FilePath ws = build.getWorkspace();
                    // create two files
                    ws.child("b").touch(0);
                    ws.child("c").touch(0);
                    return true;
                }
            });
            FreeStyleBuild b = r.buildAndAssertSuccess(p);

            // this should have created b and c
            FilePath ws = b.getWorkspace();
            assertTrue(ws.child("b").exists());
            assertTrue(ws.child("c").exists());

            // now, remove the builder that makes the workspace dirty and rebuild
            p.getBuildersList().clear();
            b = r.buildAndAssertSuccess(p);
            System.out.println(b.getLog());

            // those files should have been cleaned
            ws = b.getWorkspace();
            assertFalse(ws.child("b").exists());
            assertFalse(ws.child("c").exists());
        } finally {
            srv.kill();
        }
    }

    /**
     * Used for experimenting the memory leak problem.
     * This test by itself doesn't detect that, but I'm leaving it in anyway.
     */
    @Issue("JENKINS-8061")
    @Test
    void pollingLeak() throws Exception {
        Proc p = runSvnServe(getClass().getResource("small.zip"));
        try {
            FreeStyleProject b = r.createFreeStyleProject();
            b.setScm(new SubversionSCM("svn://localhost/"));
            b.setAssignedNode(r.createSlave());

            r.assertBuildStatusSuccess(b.scheduleBuild2(0));

            b.poll(new StreamTaskListener(System.out, Charset.defaultCharset()));
        } finally {
            p.kill();
        }
    }


    /**
     * Check out a pinned external and the same url unpinned.
     * See that we can poll afterward w/o getting confused.
     */
    @Issue("JENKINS-6209")
    @Test
    void pinnedExternals() throws Exception {
        Proc p = runSvnServe(getClass().getResource("JENKINS-6209.zip"));
        try {
            FreeStyleProject b = r.createFreeStyleProject();

            ModuleLocation[] locations = {
                    new ModuleLocation("svn://localhost/y", null),
                    new ModuleLocation("svn://localhost/z", null)
            };

            b.setScm(new SubversionSCM(Arrays.asList(locations), new CheckoutUpdater(), null, null, null, null, null, null));

            FreeStyleBuild build = r.assertBuildStatusSuccess(b.scheduleBuild2(0));
            FilePath ws = build.getWorkspace();
            assertEquals("za 2\n", ws.child("z").child("a").readToString());
            assertEquals("za 1\n", ws.child("y").child("z").child("a").readToString());

            assertEquals(PollingResult.Change.NONE, b.poll(StreamTaskListener.fromStdout()).change);
        } finally {
            p.kill();
        }
    }

    @Issue("JENKINS-10943")
    @Test
    void getLocalDirWithAtRevision() {
        // remote is not configured.
        SubversionSCM scm = new SubversionSCM("http://localhost/project@100", null);
        ModuleLocation[] locs = scm.getLocations();
        assertEquals(1, locs.length);
        assertEquals("project", locs[0].getLocalDir());
    }

    @Issue("JENKINS-777")
    @Test
    void ignoreExternals() throws Exception {
        Proc p = runSvnServe(getClass().getResource("JENKINS-777.zip"));

        try {
            FreeStyleProject b = r.createFreeStyleProject();

            ModuleLocation[] locations = {
                    new ModuleLocation("svn://localhost/jenkins-777/proja", "no_externals", "infinity", true),
                    new ModuleLocation("svn://localhost/jenkins-777/proja", "with_externals", "infinity", false)
            };

            b.setScm(new SubversionSCM(Arrays.asList(locations), new CheckoutUpdater(), null, null, null, null, null, null));

            FreeStyleBuild build = r.assertBuildStatusSuccess(b.scheduleBuild2(0));
            FilePath ws = build.getWorkspace();

            // Check that the external exists
            assertTrue(ws.child("with_externals").child("externals").child("projb").exists());

            // Check that the external doesn't exist
            assertFalse(ws.child("no_externals").child("externals").child("projb").exists());
        } finally {
            p.kill();
        }
    }

    @Issue("JENKINS-777")
    @Test
    void depthOptions() throws Exception {
        Proc p = runSvnServe(getClass().getResource("JENKINS-777.zip"));

        try {
            FreeStyleProject b = r.createFreeStyleProject();

            ModuleLocation[] locations = {
                    new ModuleLocation("svn://localhost/jenkins-777/proja", "empty", "empty", true),
                    new ModuleLocation("svn://localhost/jenkins-777/proja", "files", "files", true),
                    new ModuleLocation("svn://localhost/jenkins-777/proja", "immediates", "immediates", true),
                    new ModuleLocation("svn://localhost/jenkins-777/proja", "infinity", "infinity", true)
            };

            b.setScm(new SubversionSCM(Arrays.asList(locations), new CheckoutUpdater(), null, null, null, null, null, null));

            FreeStyleBuild build = r.assertBuildStatusSuccess(b.scheduleBuild2(0));
            FilePath ws = build.getWorkspace();

            // Test if file file1 exists for various depths
            assertFalse((ws.child("empty").child("file1").exists()));

            assertTrue(ws.child("files").child("file1").exists());
            assertTrue(ws.child("immediates").child("file1").exists());
            assertTrue(ws.child("infinity").child("file1").exists());

            // Test if directory subdir exists for various depths
            assertFalse((ws.child("empty").child("subdir").exists()));
            assertFalse((ws.child("files").child("subdir").exists()));

            assertTrue(ws.child("immediates").child("subdir").exists());
            assertTrue(ws.child("infinity").child("subdir").exists());

            // Test if file subdir/file3 exists for various depths
            assertFalse((ws.child("empty").child("subdir").child("file3").exists()));
            assertFalse((ws.child("files").child("subdir").child("file3").exists()));
            assertFalse((ws.child("immediates").child("subdir").child("file3").exists()));

            assertTrue(ws.child("infinity").child("subdir").child("file3").exists());

        } finally {
            p.kill();
        }
    }

    @Issue("JENKINS-777")
    @Test
    void changingDepthWithUpdateUpdater() throws Exception {
        Proc p = runSvnServe(getClass().getResource("JENKINS-777.zip"));

        try {
            // enable 1.6 mode
            HtmlForm f = r.createWebClient().goTo("configure").getFormByName("config");
            f.getSelectByName("svn.workspaceFormat").setSelectedAttribute("10", true);
            r.submit(f);

            FreeStyleProject b = r.createFreeStyleProject();

            ModuleLocation[] locations = {
                    new ModuleLocation("svn://localhost/jenkins-777/proja", "proja", "infinity", true)
            };

            // Do initial update with infinite depth and check that file1 exists
            b.setScm(new SubversionSCM(Arrays.asList(locations), new UpdateUpdater(), null, null, null, null, null, null));
            FreeStyleBuild build = r.assertBuildStatusSuccess(b.scheduleBuild2(0));
            FilePath ws = build.getWorkspace();
            assertTrue(ws.child("proja").child("file1").exists());

            // Trigger new build with depth empty and check that file1 no longer exists
            ModuleLocation[] locations2 = {
                    new ModuleLocation("svn://localhost/jenkins-777/proja", "proja", "empty", true)
            };
            b.setScm(new SubversionSCM(Arrays.asList(locations2), new UpdateUpdater(), null, null, null, null, null, null));
            FreeStyleBuild build2 = r.assertBuildStatusSuccess(b.scheduleBuild2(0));
            ws = build2.getWorkspace();
            assertFalse((ws.child("proja").child("file1").exists()));

        } finally {
            p.kill();
        }
    }

    @Issue("JENKINS-17974")
    @Test
    void changingDepthInJob() throws Exception {
        Proc p = runSvnServe(getClass().getResource("JENKINS-777.zip"));

        try {
            // enable 1.6 mode
            HtmlForm f = r.createWebClient().goTo("configure").getFormByName("config");
            f.getSelectByName("svn.workspaceFormat").setSelectedAttribute("10", true);
            r.submit(f);

            FreeStyleProject b = r.createFreeStyleProject();

            ModuleLocation[] locations = {
                    new ModuleLocation("svn://localhost/jenkins-777/proja", "proja", "infinity", true)
            };

            // Do initial update with infinite depth and check that subdir exists
            b.setScm(new SubversionSCM(Arrays.asList(locations), new UpdateUpdater(), null, null, null, null, null, null));
            FreeStyleBuild build = r.assertBuildStatusSuccess(b.scheduleBuild2(0));
            FilePath ws = build.getWorkspace();
            assertTrue(ws.child("proja").child("subdir").exists());

            // Simulate job using 'svn update --set-depth=files' and check that subdir no longer exists
            SvnClientManager svnm = SubversionSCM.createClientManager(b);
            svnm
                    .getUpdateClient()
                    .doUpdate(new File(ws.child("proja").getRemote()), SVNRevision.HEAD, SVNDepth.FILES, false, true);

            assertTrue(ws.child("proja").exists());
            assertFalse((ws.child("proja").child("subdir").exists()));

            // Trigger new build with depth unknown and check that subdir still does not exist
            ModuleLocation[] locations2 = {
                    new ModuleLocation("svn://localhost/jenkins-777/proja", "proja", "undefined", true)
            };
            b.setScm(new SubversionSCM(Arrays.asList(locations2), new UpdateUpdater(), null, null, null, null, null, null));
            FreeStyleBuild build2 = r.assertBuildStatusSuccess(b.scheduleBuild2(0));
            ws = build2.getWorkspace();
            assertFalse((ws.child("proja").child("subdir").exists()));

        } finally {
            p.kill();
        }
    }

    @Issue("JENKINS-16533")
    @Test
    void pollingRespectExternalsWithRevision() throws Exception {
        // trunk has svn:externals="-r 1 ^/vendor vendor" (pinned)
        // latest commit on vendor is r3 (> r1)
        File repo = new CopyExisting(getClass().getResource("JENKINS-16533.zip")).allocate();
        SubversionSCM scm = new SubversionSCM("file://" + repo.toURI().toURL().getPath() + "trunk");

        // pinned externals should be recorded with ::p in revisions.txt
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(scm);
        p.setAssignedLabel(r.createSlave().getSelfLabel());
        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        // should not find any change (pinned externals should be skipped on poll)
        // fail if it checks the revision of external URL larger than the pinned revision
        assertFalse(p.poll(StreamTaskListener.fromStdout()).hasChanges());
    }

    @Issue("JENKINS-20165")
    @Test
    void pollingExternalsForFileSvn16() throws Exception {
        configureSvnWorkspaceFormat2(10 /* 1.6 (svn:externals to file) */);
        invokeTestPollingExternalsForFile();
    }

    @Issue("JENKINS-20165")
    @Test
    void pollingExternalsForFileSvn17() throws Exception {
        configureSvnWorkspaceFormat2(SubversionWorkspaceSelector.WC_FORMAT_17);
        invokeTestPollingExternalsForFile();
    }

    private void invokeTestPollingExternalsForFile() throws Exception {
        // trunk has svn:externals="^/vendor/target.txt target.txt"
        File repo = new CopyExisting(getClass().getResource("JENKINS-20165.zip")).allocate();
        String path = "file://" + repo.toURI().toURL().getPath();
        SubversionSCM scm = new SubversionSCM(path + "trunk");

        // first checkout
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(scm);
        p.setAssignedLabel(r.createSlave().getSelfLabel());
        r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        // update target.txt in vendor
        SubversionSCM vendor = new SubversionSCM(path + "vendor");
        createWorkingCopy(vendor);
        changeFiles("target.txt");
        commitWorkingCopy("update");

        // should detect change
        assertTrue(p.poll(StreamTaskListener.fromStdout()).hasChanges());
    }

    @Test
    void manageShouldAccessGlobalConfig() {
        final String USER = "user";
        final String MANAGER = "manager";
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                // Read access
                .grant(Jenkins.READ).everywhere().to(USER)

                // Read and Manage
                .grant(Jenkins.READ).everywhere().to(MANAGER)
                .grant(Jenkins.MANAGE).everywhere().to(MANAGER)
        );

        try (ACLContext c = ACL.as(User.getById(USER, true))) {
            Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
            assertEquals(0, descriptors.size(), "Global configuration should not be accessible to READ users");
        }
        try (ACLContext c = ACL.as(User.getById(MANAGER, true))) {
            Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
            Optional<Descriptor> found = descriptors.stream().filter(descriptor -> descriptor instanceof SubversionSCM.DescriptorImpl).findFirst();
            assertTrue(found.isPresent(), "Global configuration should be accessible to MANAGE users");
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
