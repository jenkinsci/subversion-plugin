package hudson.scm;

import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertFalse;

@WithJenkins
class SubversionEnvVarsTest {

    private static final String REPO_URL = "https://svn.jenkins-ci.org/${BRANCH}/jenkins/test-projects/model-maven-project";

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    /**
     * This test aims to verify that the environment variables (from Global Properties section) are available in SCM
     * Polling.
     */
    @Disabled("TODO org.tmatesoft.svn.core.SVNException: svn: E175002: PROPFIND of '/trunk/jenkins/test-projects/model-maven-project': 405 Method Not Allowed (https://svn.jenkins-ci.org)")
    @Issue("JENKINS-31067")
    @Test
    void pollingWithEnvVars() throws Exception {
        r.getInstance().getGlobalNodeProperties().add(new EnvironmentVariablesNodeProperty(new
                EnvironmentVariablesNodeProperty.Entry("BRANCH", "trunk")));
        FreeStyleProject project = r.createFreeStyleProject();

        project.setScm(new SubversionSCM(REPO_URL));
        r.assertBuildStatusSuccess(project.scheduleBuild2(0).get());

        TaskListener listener = r.createTaskListener();
        PollingResult poll = project.poll(listener);
        assertFalse(poll.hasChanges());
    }
}
