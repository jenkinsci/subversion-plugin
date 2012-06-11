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

import static hudson.scm.SubversionSCM.compareSVNAuthentications;
import static org.jvnet.hudson.test.recipes.PresetData.DataSet.ANONYMOUS_READONLY;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.browsers.Sventon;
import hudson.scm.subversion.CheckoutUpdater;
import hudson.scm.subversion.UpdateUpdater;
import hudson.scm.subversion.UpdateWithCleanUpdater;
import hudson.scm.subversion.UpdateWithRevertUpdater;
import hudson.scm.subversion.WorkspaceUpdater;
import hudson.slaves.DumbSlave;
import hudson.triggers.SCMTrigger;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.dom4j.Document;
import org.dom4j.io.DOMReader;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.Url;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.recipes.PresetData;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
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
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebConnection;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Run;

/**
 * @author Kohsuke Kawaguchi
 */
// TODO: we're relying on no less than 3 external SVN repos for this test: svn.jenkins-ci.org, subversion.tigris.org and svn.codehaus.org
// while the 1st one is probably okay, we should look that we get rid of the other 2 dependencies
@SuppressWarnings("rawtypes")
public class SubversionSCMTest extends AbstractSubversionTest {

    private static final int LOG_LIMIT = 1000;

    // in some tests we play authentication games with this repo
    String realm = "<http://subversion.tigris.org:80> CollabNet Subversion Repository";
    String kind = ISVNAuthenticationManager.PASSWORD;
    SVNURL repo;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repo = SVNURL.parseURIDecoded("http://subversion.tigris.org/svn/subclipse");

        // during the test, don't pollute the global authentication cache.
        SubversionSCM.CONFIG_DIR = createTmpDir().getAbsolutePath();
    }

    @PresetData(ANONYMOUS_READONLY)
    @Bug(2380)
    public void testTaggingPermission() throws Exception {
        // create a build
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(loadSvnRepo());
        final FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause()).get();
        System.out.println(b.getLog(LOG_LIMIT));
        assertBuildStatus(Result.SUCCESS,b);

        final SubversionTagAction action = b.getAction(SubversionTagAction.class);
        executeOnServer(new Callable<Object>() {
            public Object call() throws Exception {
                assertFalse("Shouldn't be accessible to anonymous user",b.hasPermission(action.getPermission()));
                return null;
            }
        });

        WebClient wc = new WebClient();
        HtmlPage html = wc.getPage(b);

        // make sure there's no link to the 'tag this build'
        Document dom = new DOMReader().read(html);
        assertNull(dom.selectSingleNode("//A[text()='Tag this build']"));
        for( HtmlAnchor a : html.getAnchors() )
            assertFalse(a.getHrefAttribute().contains("/tagBuild/"));

        // and no tag form on tagBuild page
        html = wc.getPage(b,"tagBuild/");
        try {
            html.getFormByName("tag");
            fail("should not have been found");
        } catch (ElementNotFoundException e) {
        }

        // and that tagging would fail
        try {
            wc.getPage(b,"tagBuild/submit?name0=test&Submit=Tag");
            fail("should have been denied");
        } catch (FailingHttpStatusCodeException e) {
            // make sure the request is denied
            assertEquals(e.getResponse().getStatusCode(),403);
        }

        // now login as alice and make sure that the tagging would succeed
        wc = new WebClient();
        wc.login("alice","alice");
        html = wc.getPage(b,"tagBuild/");
        HtmlForm form = html.getFormByName("tag");
        submit(form);
    }

    /**
     * Loads a test Subversion repository into a temporary directory, and creates {@link SubversionSCM} for it.
     */
    private SubversionSCM loadSvnRepo() throws Exception {
        return new SubversionSCM("file://" + new CopyExisting(getClass().getResource("/svn-repo.zip")).allocate().toURI().toURL().getPath() + "trunk/a","a");
    }

    @Email("http://jenkins.361315.n4.nabble.com/Hudson-1-266-and-1-267-Subversion-authentication-broken-td375737.html")
    public void testHttpsCheckOut() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant/"));

        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserCause()).get());
        assertTrue(b.getWorkspace().child("build.xml").exists());
    }

    @Email("http://jenkins.361315.n4.nabble.com/Hudson-1-266-and-1-267-Subversion-authentication-broken-td375737.html")
    public void testHttpCheckOut() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM("http://svn.codehaus.org/sxc/tags/sxc-0.5/sxc-core/src/test/java/com/envoisolutions/sxc/builder/"));

        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserCause()).get());
        assertTrue(b.getWorkspace().child("Node.java").exists());
    }

    @Url("http://hudson.pastebin.com/m3ea34eea")
    public void testRemoteCheckOut() throws Exception {
        DumbSlave s = createSlave();
        FreeStyleProject p = createFreeStyleProject();
        p.setAssignedLabel(s.getSelfLabel());
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant/"));

        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserCause()).get());
        assertTrue(b.getWorkspace().child("build.xml").exists());
        assertBuildStatusSuccess(p.scheduleBuild2(0).get());
    }

    /**
     * Tests the "URL@REV" format in SVN URL.
     */
    @Bug(262)
    public void testRevisionedCheckout() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@13000"));

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause()).get();
        System.out.println(b.getLog(LOG_LIMIT));
        assertTrue(b.getLog(LOG_LIMIT).contains("At revision 13000"));
        assertBuildStatus(Result.SUCCESS,b);

        b = p.scheduleBuild2(0, new Cause.UserCause()).get();
        System.out.println(b.getLog(LOG_LIMIT));
        assertTrue(b.getLog(LOG_LIMIT).contains("At revision 13000"));
        assertBuildStatus(Result.SUCCESS,b);
    }

    /**
     * Tests the "URL@HEAD" format in the SVN URL
     */
    public void testHeadRevisionCheckout() throws Exception {
        File testRepo = new CopyExisting(getClass().getResource("two-revisions.zip")).allocate();
        SubversionSCM scm = new SubversionSCM("file://" + testRepo.getPath() + "@HEAD");

        FreeStyleProject p = createFreeStyleProject();
        p.setScm(scm);
        
        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause()).get();
        System.out.println(b.getLog(LOG_LIMIT));
        assertTrue(b.getLog(LOG_LIMIT).contains("At revision 2"));
        assertBuildStatus(Result.SUCCESS,b);
    }

    /**
     * Test parsing of @revision information from the tail of the URL
     */
    public void testModuleLocationRevisions() throws Exception {
        SubversionSCM.ModuleLocation m = new SubversionSCM.ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@13000", null);
        SVNRevision r = m.getRevision(null);
        assertTrue(r.isValid());
        assertEquals(13000, r.getNumber());
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", m.getURL());

        m = new SubversionSCM.ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@HEAD", null);
        r = m.getRevision(null);
        assertTrue(r.isValid());
        assertTrue(r == SVNRevision.HEAD);
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", m.getURL());

        m = new SubversionSCM.ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@FAKE", null);
        r = m.getRevision(null);
        assertFalse(r.isValid());
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@FAKE", m.getURL());
    }

    @Bug(10942)
    public void testSingleModuleEnvironmentVariablesWithRevision() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@HEAD"));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(builder);

        assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", builder.getEnvVars().get("SVN_URL"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant").toString(), builder.getEnvVars().get("SVN_REVISION"));
    }
    
    @Bug(10942)
    public void testMultiModuleEnvironmentVariablesWithRevision() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        ModuleLocation[] locations = {
            new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant@18075", null),
            new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven@HEAD", null)
        };
        p.setScm(new SubversionSCM(Arrays.asList(locations), new CheckoutUpdater(), null, null, null, null, null, null));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(builder);

        assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", builder.getEnvVars().get("SVN_URL_1"));
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven", builder.getEnvVars().get("SVN_URL_2"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant").toString(), builder.getEnvVars().get("SVN_REVISION_1"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven").toString(), builder.getEnvVars().get("SVN_REVISION_2"));

    }    
    
    /**
     * Tests a checkout with RevisionParameterAction
     */
    public void testRevisionParameter() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        String url = "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant";
        p.setScm(new SubversionSCM(url));

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause(), 
        		new RevisionParameterAction(new SubversionSCM.SvnInfo(url, 13000))).get();
        System.out.println(b.getLog(LOG_LIMIT));
        assertTrue(b.getLog(LOG_LIMIT).contains("At revision 13000"));
        assertBuildStatus(Result.SUCCESS,b);
    }

    public void testRevisionParameterFolding() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        String url = "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant";
        p.setScm(new SubversionSCM(url));

	// Schedule build of a specific revision with a quiet period
        Future<FreeStyleBuild> f = p.scheduleBuild2(60, new Cause.UserCause(),
        		new RevisionParameterAction(new SubversionSCM.SvnInfo(url, 13000)));

	// Schedule another build at a more recent revision
        p.scheduleBuild2(0, new Cause.UserCause(),
        		new RevisionParameterAction(new SubversionSCM.SvnInfo(url, 14000)));

        FreeStyleBuild b = f.get();
	
        System.out.println(b.getLog(LOG_LIMIT));
        assertTrue(b.getLog(LOG_LIMIT).contains("At revision 14000"));
        assertBuildStatus(Result.SUCCESS,b);
    }

    private FreeStyleProject createPostCommitTriggerJob() throws Exception {
        // Disable crumbs because HTMLUnit refuses to mix request bodies with
        // request parameters
        hudson.setCrumbIssuer(null);

        FreeStyleProject p = createFreeStyleProject();
        String url = "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant";
        SCMTrigger trigger = new SCMTrigger("0 */6 * * *");

        p.setScm(new SubversionSCM(url));
        p.addTrigger(trigger);
        trigger.start(p, true);

        return p;
    }

    private FreeStyleBuild sendCommitTrigger(FreeStyleProject p, boolean includeRevision) throws Exception {
        String repoUUID = "71c3de6d-444a-0410-be80-ed276b4c234a";

        WebClient wc = new WebClient();
        WebRequestSettings wr = new WebRequestSettings(new URL(getURL() + "subversion/" + repoUUID + "/notifyCommit"), HttpMethod.POST);
        wr.setRequestBody("A   trunk/hudson/test-projects/trivial-ant/build.xml");
        wr.setAdditionalHeader("Content-Type", "text/plain;charset=UTF-8");

        if (includeRevision) {
            wr.setAdditionalHeader("X-Hudson-Subversion-Revision", "13000");
        }
        
        WebConnection conn = wc.getWebConnection();
        WebResponse resp = conn.getResponse(wr);
        assertTrue(isGoodHttpStatus(resp.getStatusCode()));

        waitUntilNoActivity();
        FreeStyleBuild b = p.getLastBuild();
        assertNotNull(b);
        assertBuildStatus(Result.SUCCESS,b);

        return b;
    }

    public Long getActualRevision(FreeStyleBuild b, String url) throws Exception {
        SVNRevisionState revisionState = b.getAction(SVNRevisionState.class);
        if (revisionState == null) {
            throw new Exception("No revision found!");
        }

        return revisionState.revisions.get(url).longValue();

    }
    /**
     * Tests a checkout triggered from the post-commit hook
     */
    public void testPostCommitTrigger() throws Exception {
        FreeStyleProject p = createPostCommitTriggerJob();
        FreeStyleBuild b = sendCommitTrigger(p, true);

        assertTrue(getActualRevision(b, "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant") <= 13000);
    }
    
    /**
     * Tests a checkout triggered from the post-commit hook without revision
     * information.
     */
    public void testPostCommitTriggerNoRevision() throws Exception {
        FreeStyleProject p = createPostCommitTriggerJob();
        FreeStyleBuild b = sendCommitTrigger(p, false);

        assertTrue(getActualRevision(b, "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant") > 13000);
    }

    /**
     * {@link SubversionSCM#pollChanges(AbstractProject , Launcher , FilePath, TaskListener)} should notice
     * if the workspace and the current configuration is inconsistent and schedule a new build.
     */
    @Email("http://www.nabble.com/Proper-way-to-switch---relocate-SVN-tree---tt21173306.html")
    public void testPollingAfterRelocation() throws Exception {
        // fetch the current workspace
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(loadSvnRepo());
        p.scheduleBuild2(0, new Cause.UserCause()).get();

        // as a baseline, this shouldn't detect any change
        TaskListener listener = createTaskListener();
        assertFalse(p.poll(listener).hasChanges());

        // now switch the repository to a new one.
        // this time the polling should indicate that we need a new build
        p.setScm(loadSvnRepo());
        assertTrue(p.poll(listener).hasChanges());

        // build it once again to switch
        p.scheduleBuild2(0, new Cause.UserCause()).get();

        // then no more change should be detected
        assertFalse(p.poll(listener).hasChanges());
    }

    public void testURLWithVariable() throws Exception {
        FreeStyleProject p = createFreeStyleProject();

        // --- 1st case: URL with a variable ---

        String url = "http://svn.codehaus.org/sxc/tags/sxc-0.5/sxc-core/src/test/java/com/envoisolutions/sxc/builder/";
        p.setScm(new SubversionSCM("$REPO" + url.substring(10)));
        String var = url.substring(0, 10);

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause(),
                new ParametersAction(new StringParameterValue("REPO", var))).get();
        System.out.println(b.getLog(LOG_LIMIT));
        assertBuildStatus(Result.SUCCESS,b);
        assertTrue(b.getWorkspace().child("Node.java").exists());

        // --- 2nd case: URL with an empty variable ---

        p.setScm(new SubversionSCM(url + "$EMPTY_VAR"));

        b = p.scheduleBuild2(0, new Cause.UserCause(),
                new ParametersAction(new StringParameterValue("EMPTY_VAR", ""))).get();
        assertBuildStatus(Result.SUCCESS,b);
        assertTrue(b.getWorkspace().child("Node.java").exists());
    }

    /**
     * Test that multiple repository URLs are all polled.
     */
    @Bug(3168)
    public void testPollMultipleRepositories() throws Exception {
        // fetch the current workspaces
        FreeStyleProject p = createFreeStyleProject();
        String svnBase = "file://" + new CopyExisting(getClass().getResource("/svn-repo.zip")).allocate().toURI().toURL().getPath();
        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(new ModuleLocation(svnBase + "trunk", null), new ModuleLocation(svnBase + "branches", null)),
                new CheckoutUpdater(), null, null, null, null, null, null);
        p.setScm(scm);
        p.scheduleBuild2(0, new Cause.UserCause()).get();

        // as a baseline, this shouldn't detect any change
        TaskListener listener = createTaskListener();
        assertFalse(p.poll(listener).hasChanges());

        createCommit(scm,"branches/foo");
        assertTrue("any change in any of the repository should be detected",p.poll(listener).hasChanges());
        assertFalse("no change since the last polling",p.poll(listener).hasChanges());
        createCommit(scm,"trunk/foo");
        assertTrue("another change in the repo should be detected separately",p.poll(listener).hasChanges());
    }
    
    /**
     * Test that multiple repository URLs are all polled.
     */
    @Bug(7461)
    public void testMultipleRepositories() throws Exception {
        // fetch the current workspaces
        FreeStyleProject p = createFreeStyleProject();
        String svnBase = "file://" + new CopyExisting(getClass().getResource("/svn-repo.zip")).allocate().toURI().toURL().getPath();
        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(new ModuleLocation(svnBase + "trunk", "trunk")),
                new UpdateUpdater(), null, null, null, null, null, null);
        p.setScm(scm);
        Run r1 = p.scheduleBuild2(0, new Cause.UserCause()).get();
        assertLogContains("Cleaning local Directory trunk", r1);

        scm = new SubversionSCM(
                Arrays.asList(new ModuleLocation(svnBase + "trunk", "trunk"), new ModuleLocation(svnBase + "branches", "branches")),
                new UpdateUpdater(), null, null, null, null, null, null);
        p.setScm(scm);
        Run r2 = p.scheduleBuild2(0, new Cause.UserCause()).get();
        assertLogContains("Updating " + svnBase + "trunk", r2);
        assertLogContains("Cleaning local Directory branches", r2);
    }
    
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();

        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(
                		new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", "c"),
                		new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", "d")),
                new UpdateUpdater(),new Sventon(new URL("http://www.sun.com/"),"test"),"exclude","user","revprop","excludeMessage",null);
        p.setScm(scm);
        submit(new WebClient().getPage(p,"configure").getFormByName("config"));
        verify(scm,(SubversionSCM)p.getScm());

        scm = new SubversionSCM(
        		Arrays.asList(
                		new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", "c")),
        		new CheckoutUpdater(),null,"","","","",null);
        p.setScm(scm);
        submit(new WebClient().getPage(p,"configure").getFormByName("config"));
        verify(scm,(SubversionSCM)p.getScm());
    }

    @Bug(7944)
    public void testConfigRoundtrip2() throws Exception {
        FreeStyleProject p = createFreeStyleProject();

        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(
                		new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", "")),
                new UpdateUpdater(),null,null,null,null,null,null);
        p.setScm(scm);
        configRoundtrip((Item)p);
        verify(scm,(SubversionSCM)p.getScm());
    }

    @Bug(9143)
    public void testCheckEmptyRemoteRemoved() throws Exception {
        FreeStyleProject p = createFreeStyleProject();

        List<ModuleLocation> locs = new ArrayList<ModuleLocation>();
        locs.add(new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", "c"));
        locs.add(new ModuleLocation("", "d"));
        locs.add(new ModuleLocation("    ", "e"));
                
        SubversionSCM scm = new SubversionSCM(
                locs,
                new UpdateUpdater(), new Sventon(new URL("http://www.sun.com/"), "test"), "exclude", "user", "revprop", "excludeMessage",null);
        p.setScm(scm);
        submit(new WebClient().getPage(p, "configure").getFormByName("config"));
        ModuleLocation[] ml = ((SubversionSCM) p.getScm()).getLocations();
        assertEquals(1, ml.length);
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusion", ml[0].remote);
    }

    @Bug(5684)
    public void testDoCheckExcludedUsers() throws Exception {
        String[] validUsernames = new String[] {
            "DOMAIN\\user",
            "user",
            "us_er",
            "user123",
            "User",
            "", // this one is ignored
            "DOmain12\\User34"};

        for (String validUsername : validUsernames) {
            assertEquals(
                "User " + validUsername + " isn't OK (but it's valid).", 
                FormValidation.Kind.OK, 
                new SubversionSCM.DescriptorImpl().doCheckExcludedUsers(validUsername).kind);
        }

        String[] invalidUsernames = new String[] {
            "\\user",
            "DOMAIN\\",
            "DOMAIN@user",
            "DOMAIN.user" };

        for (String invalidUsername : invalidUsernames) {
            assertEquals(
                "User " + invalidUsername + " isn't ERROR (but it's not valid).", 
                FormValidation.Kind.ERROR, 
                new SubversionSCM.DescriptorImpl().doCheckExcludedUsers(invalidUsername).kind);
        }

    }

    private void verify(SubversionSCM lhs, SubversionSCM rhs) {
        ModuleLocation[] ll = lhs.getLocations();
        ModuleLocation[] rl = rhs.getLocations();
        assertEquals(ll.length, rl.length);
        for(int i=0; i<ll.length; i++) {
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
    
    private void assertNullEquals (String left, String right) {
    	if (left == null)
    		left = "";
    	if (right == null)
    		right = "";
    	assertEquals(left, right);
    }
    
    public void testSvnUrlParsing() {
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
    public void testWorkspaceVersion() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(loadSvnRepo());
        FreeStyleBuild b = p.scheduleBuild2(0).get();

        SVNClientManager wc = SubversionSCM.createSvnClientManager((AbstractProject)null);
        SVNStatus st = wc.getStatusClient().doStatus(new File(b.getWorkspace().getRemote()+"/a"), false);
        int wcf = st.getWorkingCopyFormat();
        System.out.println(wcf);
        assertEquals(SVNAdminAreaFactory.WC_FORMAT_14,wcf);
    }

    private static String readFileAsString(File file)
    throws java.io.IOException{
        StringBuilder fileData = new StringBuilder(1000);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        char[] buf = new char[1024];
        int numRead=0;
        while((numRead=reader.read(buf)) != -1){
            fileData.append(buf, 0, numRead);
        }
        reader.close();
        return fileData.toString();
    }

    /**
     * Makes sure the symbolic link is checked out correctly. There seems to be
     */
    @Bug(3904)
    public void testSymbolicLinkCheckout() throws Exception {
        // Only perform if symlink behavior is enabled
        if (!"true".equals(System.getProperty("svnkit.symlinks"))) {
            return;
        }

        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/issue-3904"));

        FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause()).get();
        File source = new File(b.getWorkspace().getRemote() + "/readme.txt");
        File linked = new File(b.getWorkspace().getRemote() + "/linked.txt");
        assertEquals("Files '" + source + "' and '" + linked + "' are not identical from user view.", readFileAsString(source), readFileAsString(linked));
    }

    public void testExcludeByUser() throws Exception {
        FreeStyleProject p = createFreeStyleProject( "testExcludeByUser" );
        p.setScm(new SubversionSCM(
                Arrays.asList( new ModuleLocation( "https://svn.jenkins-ci.org/trunk/hudson/test-projects/testSubversionExclusions@19438", null )),
                new UpdateUpdater(), null, "", "dty", "", "", null)
                );
        // Do a build to force the creation of the workspace. This works around
        // pollChanges returning true when the workspace does not exist.
        p.scheduleBuild2(0).get();

        boolean foundChanges = p.poll(createTaskListener()).hasChanges();
        assertFalse("Polling found changes that should have been ignored", foundChanges);
    }

    /**
     * Test excluded regions
     */
    @Bug(6030)
    public void testExcludedRegions() throws Exception {
//        SLAVE_DEBUG_PORT = 8001;
        File repo = new CopyExisting(getClass().getResource("HUDSON-6030.zip")).allocate();
        SubversionSCM scm = new SubversionSCM(ModuleLocation.parse(new String[]{"file://" + repo.getPath()},
                                                                   new String[]{"."}, null),
                                              new UpdateUpdater(), null, ".*/bar", "", "", "", "");

        FreeStyleProject p = createFreeStyleProject("testExcludedRegions");
        p.setScm(scm);
        assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        // initial polling on the slave for the code path that doesn't find any change
        assertFalse(p.poll(createTaskListener()).hasChanges());

        createCommit(scm, "bar");

        // polling on the slave for the code path that does have a change but should be excluded.
        assertFalse("Polling found changes that should have been ignored",
                p.poll(createTaskListener()).hasChanges());

        createCommit(scm, "foo");

        // polling on the slave for the code path that doesn't find any change
        assertTrue("Polling didn't find a change it should have found.",
                p.poll(createTaskListener()).hasChanges());

    }
    
    /**
     * Test included regions
     */
    @Bug(6030)
    public void testIncludedRegions() throws Exception {
//        SLAVE_DEBUG_PORT = 8001;
        File repo = new CopyExisting(getClass().getResource("HUDSON-6030.zip")).allocate();
        SubversionSCM scm = new SubversionSCM(ModuleLocation.parse(new String[]{"file://" + repo.getPath()},
                                                                   new String[]{"."}, null),
                                              new UpdateUpdater(), null, "", "", "", "", ".*/foo");

        FreeStyleProject p = createFreeStyleProject("testExcludedRegions");
        p.setScm(scm);
        assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        // initial polling on the slave for the code path that doesn't find any change
        assertFalse(p.poll(createTaskListener()).hasChanges());

        createCommit(scm, "bar");

        // polling on the slave for the code path that does have a change but should be excluded.
        assertFalse("Polling found changes that should have been ignored",
                p.poll(createTaskListener()).hasChanges());

        createCommit(scm, "foo");

        // polling on the slave for the code path that doesn't find any change
        assertTrue("Polling didn't find a change it should have found.",
                p.poll(createTaskListener()).hasChanges());

    }
    
    /**
     * Do the polling on the slave and make sure it works.
     */
    @Bug(4299)
    public void testPolling() throws Exception {
//        SLAVE_DEBUG_PORT = 8001;
        File repo = new CopyExisting(getClass().getResource("two-revisions.zip")).allocate();
        SubversionSCM scm = new SubversionSCM("file://" + repo.getPath());

        FreeStyleProject p = createFreeStyleProject();
        p.setScm(scm);
        p.setAssignedLabel(createSlave().getSelfLabel());
        assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        // initial polling on the slave for the code path that doesn't find any change
        assertFalse(p.poll(StreamTaskListener.fromStdout()).hasChanges());

        createCommit(scm, "foo");

        // polling on the slave for the code path that doesn't find any change
        assertTrue(p.poll(StreamTaskListener.fromStdout()).hasChanges());
    }

    /**
     * Manufactures commits by adding files in the given names.
     */
    private void createCommit(SubversionSCM scm, String... paths) throws Exception {
        FreeStyleProject forCommit = createFreeStyleProject();
        forCommit.setScm(scm);
        forCommit.setAssignedLabel(hudson.getSelfLabel());
        FreeStyleBuild b = assertBuildStatusSuccess(forCommit.scheduleBuild2(0).get());
        SVNClientManager svnm = SubversionSCM.createSvnClientManager((AbstractProject)null);

        List<File> added = new ArrayList<File>();
        for (String path : paths) {
            FilePath newFile = b.getWorkspace().child(path);
            added.add(new File(newFile.getRemote()));
            if (!newFile.exists()) {
                newFile.touch(System.currentTimeMillis());
                svnm.getWCClient().doAdd(new File(newFile.getRemote()),false,false,false, SVNDepth.INFINITY, false,false);
            } else
                newFile.write("random content","UTF-8");
        }
        SVNCommitClient cc = svnm.getCommitClient();
        cc.doCommit(added.toArray(new File[added.size()]),false,"added",null,null,false,false,SVNDepth.EMPTY);
    }

    public void testMasterPolling() throws Exception {
        File repo = new CopyExisting(getClass().getResource("two-revisions.zip")).allocate();
        SubversionSCM scm = new SubversionSCM("file://" + repo.getPath());
        SubversionSCM.POLL_FROM_MASTER = true;

        FreeStyleProject p = createFreeStyleProject();
        p.setScm(scm);
        p.setAssignedLabel(createSlave().getSelfLabel());
        assertBuildStatusSuccess(p.scheduleBuild2(2).get());

        // initial polling on the master for the code path that doesn't find any change
        assertFalse(p.poll(StreamTaskListener.fromStdout()).hasChanges());

        // create a commit
        FreeStyleProject forCommit = createFreeStyleProject();
        forCommit.setScm(scm);
        forCommit.setAssignedLabel(hudson.getSelfLabel());
        FreeStyleBuild b = assertBuildStatusSuccess(forCommit.scheduleBuild2(0).get());
        FilePath newFile = b.getWorkspace().child("foo");
        newFile.touch(System.currentTimeMillis());
        SVNClientManager svnm = SubversionSCM.createSvnClientManager(p);
        svnm.getWCClient().doAdd(new File(newFile.getRemote()),false,false,false, SVNDepth.INFINITY, false,false);
        SVNCommitClient cc = svnm.getCommitClient();
        cc.doCommit(new File[]{new File(newFile.getRemote())},false,"added",null,null,false,false,SVNDepth.INFINITY);

        // polling on the master for the code path that doesn't find any change
        assertTrue(p.poll(StreamTaskListener.fromStdout()).hasChanges());
    }


    public void testCompareSVNAuthentications() throws Exception {
        assertFalse(compareSVNAuthentications(new SVNUserNameAuthentication("me",true),new SVNSSHAuthentication("me","me",22,true)));
        // same object should compare equal
        _idem(new SVNUserNameAuthentication("me",true));
        _idem(new SVNSSHAuthentication("me","pass",22,true));
        _idem(new SVNSSHAuthentication("me",new File("./some.key"),null,23,false));
        _idem(new SVNSSHAuthentication("me","key".toCharArray(),"phrase",0,false));
        _idem(new SVNPasswordAuthentication("me","pass",true));
        _idem(new SVNSSLAuthentication("certificate".getBytes(),null,true));

        // make sure two Files and char[]s compare the same 
        assertTrue(compareSVNAuthentications(
                new SVNSSHAuthentication("me",new File("./some.key"),null,23,false),
                new SVNSSHAuthentication("me",new File("./some.key"),null,23,false)));
        assertTrue(compareSVNAuthentications(
                new SVNSSHAuthentication("me","key".toCharArray(),"phrase",0,false),
                new SVNSSHAuthentication("me","key".toCharArray(),"phrase",0,false)));

        // negative cases
        assertFalse(compareSVNAuthentications(
                new SVNSSHAuthentication("me",new File("./some1.key"),null,23,false),
                new SVNSSHAuthentication("me",new File("./some2.key"),null,23,false)));
        assertFalse(compareSVNAuthentications(
                new SVNSSHAuthentication("me","key".toCharArray(),"phrase",0,false),
                new SVNSSHAuthentication("yo","key".toCharArray(),"phrase",0,false)));

    }

    private void _idem(SVNAuthentication a) {
        assertTrue(compareSVNAuthentications(a,a));
    }

    /**
     * Make sure that a failed credential doesn't result in an infinite loop
     */
    @Bug(2909)
    public void testInfiniteLoop() throws Exception {
        // creates a purely in memory auth manager
        ISVNAuthenticationManager m = createInMemoryManager();

        // double check that it really knows nothing about the fake repo
        try {
            m.getFirstAuthentication(kind, realm, repo);
            fail();
        } catch (SVNCancelException e) {
            // yep
        }

        // let Jenkins have the credential
        descriptor.postCredential(null,repo.toDecodedString(),"guest","",null,new PrintWriter(System.out));

        // emulate the call flow where the credential fails
        List<SVNAuthentication> attempted = new ArrayList<SVNAuthentication>();
        SVNAuthentication a = m.getFirstAuthentication(kind, realm, repo);
        assertNotNull(a);
        attempted.add(a);
        for (int i=0; i<10; i++) {
            m.acknowledgeAuthentication(false,kind,realm,SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED),a);
            try {
                a = m.getNextAuthentication(kind,realm,repo);
                assertNotNull(a);
                attempted.add(a);
            } catch (SVNCancelException e) {
                // make sure we've tried our fake credential
                for (SVNAuthentication aa : attempted) {
                    if (aa instanceof SVNPasswordAuthentication) {
                        SVNPasswordAuthentication pa = (SVNPasswordAuthentication) aa;
                        if(pa.getUserName().equals("guest") && pa.getPassword().equals(""))
                            return; // yep
                    }
                }
                fail("Hudson didn't try authentication");
            }
        }
        fail("Looks like we went into an infinite loop");
    }

    /**
     * Even if the default providers remember bogus passwords, Hudson should still attempt what it knows.
     */
    @Bug(3936)
    public void test3936()  throws Exception {
        // creates a purely in memory auth manager
        ISVNAuthenticationManager m = createInMemoryManager();

        // double check that it really knows nothing about the fake repo
        try {
            m.getFirstAuthentication(kind, realm, repo);
            fail();
        } catch (SVNCancelException e) {
            // yep
        }

        // teach a bogus credential and have SVNKit store it.
        SVNPasswordAuthentication bogus = new SVNPasswordAuthentication("bogus", "bogus", true);
        m.acknowledgeAuthentication(true, kind, realm, null, bogus);
        assertTrue(compareSVNAuthentications(m.getFirstAuthentication(kind, realm, repo), bogus));
        try {
            attemptAccess(m);
            fail("SVNKit shouldn't yet know how to access");
        } catch (SVNCancelException e) {
        }

        // make sure the failure didn't clean up the cache,
        // since what we want to test here is Hudson trying to supply its credential, despite the failed cache
        assertTrue(compareSVNAuthentications(m.getFirstAuthentication(kind, realm, repo),bogus));

        // now let Hudson have the real credential
        // can we now access the repo?
        descriptor.postCredential(null,repo.toDecodedString(),"guest","",null,new PrintWriter(System.out));
        attemptAccess(m);
    }

    private void attemptAccess(ISVNAuthenticationManager m) throws SVNException {
        SVNRepository repository = SVNRepositoryFactory.create(repo);
        repository.setAuthenticationManager(m);
        repository.testConnection();
    }

    private ISVNAuthenticationManager createInMemoryManager() {
        ISVNAuthenticationManager m = SVNWCUtil.createDefaultAuthenticationManager(hudson.root,null,null,false);
        m.setAuthenticationProvider(descriptor.createAuthenticationProvider(null));
        return m;
    }

    public void testMultiModuleEnvironmentVariables() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        ModuleLocation[] locations = {
            new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", null),
            new ModuleLocation("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven", null)
        };
        p.setScm(new SubversionSCM(Arrays.asList(locations), new CheckoutUpdater(), null, null, null, null, null, null));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(builder);

        assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", builder.getEnvVars().get("SVN_URL_1"));
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven", builder.getEnvVars().get("SVN_URL_2"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant").toString(), builder.getEnvVars().get("SVN_REVISION_1"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-maven").toString(), builder.getEnvVars().get("SVN_REVISION_2"));

    }

    public void testSingleModuleEnvironmentVariables() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant"));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(builder);

        assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        assertEquals("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant", builder.getEnvVars().get("SVN_URL"));
        assertEquals(getActualRevision(p.getLastBuild(), "https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant").toString(), builder.getEnvVars().get("SVN_REVISION"));
    }

    @Bug(1379)
    public void testMultipleCredentialsPerRepo() throws Exception {
        Proc p = runSvnServe(getClass().getResource("HUDSON-1379.zip"));
        try {
            FreeStyleProject b = createFreeStyleProject();
            b.setScm(new SubversionSCM("svn://localhost/bob"));

            FreeStyleProject c = createFreeStyleProject();
            c.setScm(new SubversionSCM("svn://localhost/charlie"));

            // should fail without a credential
            assertBuildStatus(Result.FAILURE,b.scheduleBuild2(0).get());
            descriptor.postCredential(b,"svn://localhost/bob","bob","bob",null,new PrintWriter(System.out));
            buildAndAssertSuccess(b);

            assertBuildStatus(Result.FAILURE,c.scheduleBuild2(0).get());
            descriptor.postCredential(c,"svn://localhost/charlie","charlie","charlie",null,new PrintWriter(System.out));
            buildAndAssertSuccess(c);

            // b should still build fine.
            buildAndAssertSuccess(b);
        } finally {
            p.kill();
        }
    }

    /**
     * Subversion externals to a file. Requires 1.6 workspace.
     */
    @Bug(7539)
    public void testExternalsToFile() throws Exception {
        Proc server = runSvnServe(getClass().getResource("HUDSON-7539.zip"));
        try {
            // enable 1.6 mode
            HtmlForm f = createWebClient().goTo("configure").getFormByName("config");
            f.getSelectByName("svn.workspaceFormat").setSelectedAttribute("10",true);
            submit(f);

            FreeStyleProject p = createFreeStyleProject();
            p.setScm(new SubversionSCM("svn://localhost/dir1"));
            FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0));
            System.out.println(getLog(b));

            assertTrue(b.getWorkspace().child("2").exists());
            assertTrue(b.getWorkspace().child("3").exists());
            assertTrue(b.getWorkspace().child("test.x").exists());

            assertBuildStatusSuccess(p.scheduleBuild2(0));
        } finally {
            server.kill();
        }
    }

    @Bug(1379)
    public void testSuperUserForAllRepos() throws Exception {
        Proc p = runSvnServe(getClass().getResource("HUDSON-1379.zip"));
        try {
            FreeStyleProject b = createFreeStyleProject();
            b.setScm(new SubversionSCM("svn://localhost/bob"));

            FreeStyleProject c = createFreeStyleProject();
            c.setScm(new SubversionSCM("svn://localhost/charlie"));

            // should fail without a credential
            assertBuildStatus(Result.FAILURE,b.scheduleBuild2(0).get());
            assertBuildStatus(Result.FAILURE,c.scheduleBuild2(0).get());

            // but with the super user credential both should work now
            descriptor.postCredential(b,"svn://localhost/bob","alice","alice",null,new PrintWriter(System.out));
            buildAndAssertSuccess(b);
            buildAndAssertSuccess(c);
        } finally {
            p.kill();
        }
    }

    /**
     * Ensures that the introduction of {@link WorkspaceUpdater} maintains backward compatibility with
     * existing data.
     */
    public void testWorkspaceUpdaterCompatibility() throws Exception {
        Proc p = runSvnServe(getClass().getResource("small.zip"));
        try {
            verifyCompatibility("legacy-update.xml", UpdateUpdater.class);
            verifyCompatibility("legacy-checkout.xml", CheckoutUpdater.class);
            verifyCompatibility("legacy-revert.xml", UpdateWithRevertUpdater.class);
        } finally {
            p.kill();
        }
    }

    private void verifyCompatibility(String resourceName, Class<? extends WorkspaceUpdater> expected) throws IOException {
        AbstractProject job = (AbstractProject) hudson.createProjectFromXML("update", getClass().getResourceAsStream(resourceName));
        assertEquals(expected, ((SubversionSCM)job.getScm()).getWorkspaceUpdater().getClass());
    }

    public void testUpdateWithCleanUpdater() throws Exception {
        // this contains an empty "a" file and svn:ignore that ignores b
        Proc srv = runSvnServe(getClass().getResource("clean-update-test.zip"));
        try {
            FreeStyleProject p = createFreeStyleProject();
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
            FreeStyleBuild b = buildAndAssertSuccess(p);

            // this should have created b and c
            FilePath ws = b.getWorkspace();
            assertTrue(ws.child("b").exists());
            assertTrue(ws.child("c").exists());

            // now, remove the builder that makes the workspace dirty and rebuild
            p.getBuildersList().clear();
            b = buildAndAssertSuccess(p);
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
    @Bug(8061)
    public void testPollingLeak() throws Exception {
        Proc p = runSvnServe(getClass().getResource("small.zip"));
        try {
            FreeStyleProject b = createFreeStyleProject();
            b.setScm(new SubversionSCM("svn://localhost/"));
            b.setAssignedNode(createSlave());

            assertBuildStatusSuccess(b.scheduleBuild2(0));

            b.poll(new StreamTaskListener(System.out,Charset.defaultCharset()));
        } finally {
            p.kill();
        }
    }


    /**
     * Check out a pinned external and the same url unpinned.
     * See that we can poll afterward w/o getting confused.
     */
    @Bug(6209)
    public void testPinnedExternals() throws Exception {
        Proc p = runSvnServe(getClass().getResource("JENKINS-6209.zip"));
        try {
                FreeStyleProject b = createFreeStyleProject();

            ModuleLocation[] locations = {
                    new ModuleLocation("svn://localhost/y", null),
                    new ModuleLocation("svn://localhost/z", null)
                };

            b.setScm(new SubversionSCM(Arrays.asList(locations), new CheckoutUpdater(), null, null, null, null, null, null));

            FreeStyleBuild build = assertBuildStatusSuccess(b.scheduleBuild2(0));
            FilePath ws = build.getWorkspace();
            assertEquals(ws.child("z").child("a").readToString(),"za 2\n");
            assertEquals(ws.child("y").child("z").child("a").readToString(),"za 1\n");

            assertEquals(b.poll(StreamTaskListener.fromStdout()).change, PollingResult.Change.NONE);
        } finally {
            p.kill();
        }
    }

    @Bug(10943)
    public void testGetLocalDirWithAtRevision() throws Exception {
        // remote is not configured.
        SubversionSCM scm = new SubversionSCM("http://localhost/project@100", null);
        ModuleLocation[] locs = scm.getLocations();
        assertEquals(1, locs.length);
        assertEquals("project", locs[0].getLocalDir());
    }
    
}    
