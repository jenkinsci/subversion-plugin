package hudson.scm;

import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertFalse;
import org.junit.Ignore;

public class SubversionEnvVarsTest {

    public static String REPO_URL = "https://svn.jenkins-ci.org/${BRANCH}/jenkins/test-projects/model-maven-project";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    /**
     * This test aims to verify that the environment variables (from Global Properties section) are available in SCM
     * Polling.
     */
    @Ignore("TODO org.tmatesoft.svn.core.SVNException: svn: E175002: PROPFIND of '/trunk/jenkins/test-projects/model-maven-project': 405 Method Not Allowed (https://svn.jenkins-ci.org)")
    @Issue("JENKINS-31067")
    @Test
    public void pollingWithEnvVars() throws Exception {
        jenkins.getInstance().getGlobalNodeProperties().add(new EnvironmentVariablesNodeProperty(new
                EnvironmentVariablesNodeProperty.Entry("BRANCH", "trunk")));
        FreeStyleProject project = jenkins.createFreeStyleProject();

        project.setScm(new SubversionSCM(REPO_URL));
        jenkins.assertBuildStatusSuccess(project.scheduleBuild2(0).get());

        TaskListener listener = jenkins.createTaskListener();
        PollingResult poll = project.poll(listener);
        assertFalse(poll.hasChanges());
    }
}
