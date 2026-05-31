package hudson.scm;

import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.envinject.EnvInjectJobProperty;
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class SubversionEnvInjectTest {

    private static final String REPO_URL = "https://svn.jenkins-ci.org/trunk/hudson/test-projects/${REPO}";

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    /**
     * This test aims to verify that the variables defined in the "Properties Content" field, are availables in SCM Polling.
     */
    @Disabled("TODO org.tmatesoft.svn.core.SVNException: svn: E175002: PROPFIND of '/trunk/hudson/test-projects/trivial-maven': 405 Method Not Allowed (https://svn.jenkins-ci.org)")
    @Issue("JENKINS-29340")
    @Test
    void pollingWithEnvInject() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();

        EnvInjectJobPropertyInfo jobPropertyInfo = new EnvInjectJobPropertyInfo(null, "REPO=trivial-maven", null, null, null, false);
        EnvInjectJobProperty envInjectJobProperty = new EnvInjectJobProperty();
        envInjectJobProperty.setOn(true);
        envInjectJobProperty.setInfo(jobPropertyInfo);
        project.addProperty(envInjectJobProperty);

        project.setScm(new SubversionSCM(REPO_URL));

        TaskListener listener = r.createTaskListener();
        PollingResult poll = project.poll(listener);
        // If true means that parameters have been replaced correctly and we have a valid repository URL.
        assertTrue(poll.hasChanges());

        r.assertBuildStatusSuccess(project.scheduleBuild2(0).get());
    }
}
