/*
 * The MIT License
 *
 * Copyright 2017 Tobias Baum.
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
package jenkins.scm.impl.subversion;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class SvninfoStepTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public SubversionSampleRepoRule sampleRepo = new SubversionSampleRepoRule();

    @Test
    public void testGetRevision() throws Exception {
        this.sampleRepo.init();
        final WorkflowJob p = this.r.jenkins.createProject(WorkflowJob.class, "demo");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "    ws {\n" +
                "        dir('main') {\n" +
                "            def result = svninfo(path: '" + this.sampleRepo.wc().replace("\\", "\\\\") + "')\n" +
                "            echo 'TEST_REVISION=' + result.get('REVISION')\n" +
                "        }\n" +
                "    }\n" +
                "}"));
        final WorkflowRun b = this.r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        this.r.assertLogContains("TEST_REVISION=1", b);
    }

}
