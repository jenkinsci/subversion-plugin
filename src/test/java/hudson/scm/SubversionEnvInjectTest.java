package hudson.scm;

import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.envinject.EnvInjectJobProperty;
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertTrue;
import org.junit.Ignore;

public class SubversionEnvInjectTest {

    public static String REPO_URL = "https://svn.jenkins-ci.org/trunk/hudson/test-projects/${REPO}";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    /**
     * This test aims to verify that the variables defined in the "Properties Content" field, are availables in SCM Polling.
     */
    @Ignore("TODO org.tmatesoft.svn.core.SVNException: svn: E175002: PROPFIND of '/trunk/hudson/test-projects/trivial-maven': 405 Method Not Allowed (https://svn.jenkins-ci.org)")
    @Issue("JENKINS-29340")
    @Test
    public void pollingWithEnvInject() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        EnvInjectJobPropertyInfo jobPropertyInfo = new EnvInjectJobPropertyInfo(null, "REPO=trivial-maven", null, null, null, false);
        EnvInjectJobProperty envInjectJobProperty = new EnvInjectJobProperty();
        envInjectJobProperty.setOn(true);
        envInjectJobProperty.setInfo(jobPropertyInfo);
        project.addProperty(envInjectJobProperty);

        project.setScm(new SubversionSCM(REPO_URL));

        TaskListener listener = jenkins.createTaskListener();
        PollingResult poll = project.poll(listener);
        // If true means that parameters have been replaced correctly and we have a valid repository URL.
        assertTrue(poll.hasChanges());

        jenkins.assertBuildStatusSuccess(project.scheduleBuild2(0).get());
    }
}
