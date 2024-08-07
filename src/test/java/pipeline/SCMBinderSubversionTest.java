/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

package pipeline;

import hudson.scm.ChangeLogSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import jenkins.branch.BranchSource;
import jenkins.scm.impl.subversion.SubversionSCMFileSystem;
import jenkins.scm.impl.subversion.SubversionSCMSource;
import jenkins.scm.impl.subversion.SubversionSampleRepoRule;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class SCMBinderSubversionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public SubversionSampleRepoRule sampleSvnRepo = new SubversionSampleRepoRule();

    static {
        System.setProperty(SubversionSCMFileSystem.DISABLE_PROPERTY, "true");
    }
    @Test public void exactRevisionSubversion() throws Exception {
        sampleSvnRepo.init();
        ScriptApproval sa = ScriptApproval.get();
        sa.approveSignature("staticField hudson.model.Items XSTREAM2");
        sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
        sampleSvnRepo.write("Jenkinsfile", "echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}");
        sampleSvnRepo.write("file", "initial content");
        sampleSvnRepo.svnkit("add", sampleSvnRepo.wc() + "/Jenkinsfile");
        sampleSvnRepo.svnkit("commit", "--message=flow", sampleSvnRepo.wc());
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new SubversionSCMSource(null, sampleSvnRepo.prjUrl())));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "trunk");
        SemaphoreStep.waitForStart("wait/1", null);
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());
        sampleSvnRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
        sampleSvnRepo.write("file", "subsequent content");
        sampleSvnRepo.svnkit("commit", "--message=tweaked", sampleSvnRepo.wc());
        SemaphoreStep.success("wait/1", null);
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(2, b2.getNumber());
        r.assertLogContains("initial content", r.waitForCompletion(b1));
        r.assertLogContains("SUBSEQUENT CONTENT", b2);
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b2.getChangeSets();
        /* TODO JENKINS-29326 analogue: currently 2 (they are the same):
        assertEquals(1, changeSets.size());
        */
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b2, changeSet.getRun());
        assertEquals("svn", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("tweaked", entry.getMsg());
        assertEquals("[Jenkinsfile, file]", new TreeSet<>(entry.getAffectedPaths()).toString());
        assertFalse(iterator.hasNext());
    }

}
