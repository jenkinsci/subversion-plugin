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

import hudson.Functions;
import hudson.scm.SubversionSCM;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import jenkins.scm.impl.subversion.SubversionSCMSource;
import jenkins.scm.impl.subversion.SubversionSampleRepoExtension;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.SCMBinder;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@WithJenkins
@WithGitSampleRepo
class ReadTrustedStepSubversionTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    private JenkinsRule r;
    private GitSampleRepoRule sampleRepo;
    @RegisterExtension
    private final SubversionSampleRepoExtension sampleRepoSvn = new SubversionSampleRepoExtension();
    private boolean heavyweightCheckoutFlag;

    @BeforeEach
    void beforeEach(JenkinsRule rule, GitSampleRepoRule repo) {
        r = rule;
        sampleRepo = repo;
        heavyweightCheckoutFlag = SCMBinder.USE_HEAVYWEIGHT_CHECKOUT;
    }

    @AfterEach
    void afterEach() {
        SCMBinder.USE_HEAVYWEIGHT_CHECKOUT = heavyweightCheckoutFlag;
    }

    @Issue("SECURITY-2463")
    @Test
    void multibranchCheckoutDirectoriesAreNotReusedByDifferentScms() throws Exception {
        SCMBinder.USE_HEAVYWEIGHT_CHECKOUT = true;
        assumeFalse(Functions.isWindows()); // Checkout hook is not cross-platform.
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "trunk"); // So we end up using the same project for both SCMs.
        sampleRepo.write("Jenkinsfile", "echo('git library'); readTrusted('Jenkinsfile')");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--message=init");
        sampleRepoSvn.init();
        sampleRepoSvn.write("Jenkinsfile", "echo('svn library'); readTrusted('Jenkinsfile')");
        // Copy .git folder from the Git repo into the SVN repo as data.
        File gitDirInSvnRepo = new File(sampleRepoSvn.wc(), ".git");
        FileUtils.copyDirectory(new File(sampleRepo.getRoot(), ".git"), gitDirInSvnRepo);
        String jenkinsRootDir = r.jenkins.getRootDir().toString();
        // Add a Git post-checkout hook to the .git folder in the SVN repo.
        Path postCheckoutHook = gitDirInSvnRepo.toPath().resolve("hooks/post-checkout");
        // Always create hooks directory for compatibility with https://github.com/jenkinsci/git-plugin/pull/1207.
        Files.createDirectories(postCheckoutHook.getParent());
        Files.writeString(postCheckoutHook, "#!/bin/sh\ntouch '" + jenkinsRootDir + "/hook-executed'\n");
        sampleRepoSvn.svnkit("add", sampleRepoSvn.wc() + "/Jenkinsfile");
        sampleRepoSvn.svnkit("add", sampleRepoSvn.wc() + "/.git");
        sampleRepoSvn.svnkit("propset", "svn:executable", "ON", sampleRepoSvn.wc() + "/.git/hooks/post-checkout");
        sampleRepoSvn.svnkit("commit", "--message=init", sampleRepoSvn.wc());
        // Run a build using the SVN repo.
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new SubversionSCMSource("", sampleRepoSvn.prjUrl())));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "trunk");
        r.waitUntilNoActivity();
        // Run a build using the Git repo. It should be checked out to a different directory than the SVN repo.
        mp.getSourcesList().clear();
        mp.getSourcesList().add(new BranchSource(new GitSCMSource("", sampleRepo.toString(), "", "*", "", false)));
        WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "trunk");
        r.waitUntilNoActivity();
        assertThat(p.getLastBuild().getNumber(), equalTo(2));
        assertThat(new File(r.jenkins.getRootDir(), "hook-executed"), not(anExistingFile()));
    }

    @Issue("SECURITY-2463")
    @Test
    void checkoutDirectoriesAreNotReusedByDifferentScms() throws Exception {
        SCMBinder.USE_HEAVYWEIGHT_CHECKOUT = true;
        assumeFalse(Functions.isWindows()); // Checkout hook is not cross-platform.
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo('git library'); readTrusted('Jenkinsfile')");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--message=init");
        sampleRepoSvn.init();
        sampleRepoSvn.write("Jenkinsfile", "echo('subversion library'); readTrusted('Jenkinsfile')");
        // Copy .git folder from the Git repo into the SVN repo as data.
        File gitDirInSvnRepo = new File(sampleRepoSvn.wc(), ".git");
        FileUtils.copyDirectory(new File(sampleRepo.getRoot(), ".git"), gitDirInSvnRepo);
        String jenkinsRootDir = r.jenkins.getRootDir().toString();
        // Add a Git post-checkout hook to the .git folder in the SVN repo.
        Path postCheckoutHook = gitDirInSvnRepo.toPath().resolve("hooks/post-checkout");
        // Always create hooks directory for compatibility with https://github.com/jenkinsci/git-plugin/pull/1207.
        Files.createDirectories(postCheckoutHook.getParent());
        Files.writeString(postCheckoutHook, "#!/bin/sh\ntouch '" + jenkinsRootDir + "/hook-executed'\n");
        sampleRepoSvn.svnkit("add", sampleRepoSvn.wc() + "/Jenkinsfile");
        sampleRepoSvn.svnkit("add", sampleRepoSvn.wc() + "/.git");
        sampleRepoSvn.svnkit("propset", "svn:executable", "ON", sampleRepoSvn.wc() + "/.git/hooks/post-checkout");
        sampleRepoSvn.svnkit("commit", "--message=init", sampleRepoSvn.wc());
        // Run a build using the SVN repo.
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsScmFlowDefinition(new SubversionSCM(sampleRepoSvn.trunkUrl()), "Jenkinsfile"));
        r.buildAndAssertSuccess(p);
        // Run a build using the Git repo. It should be checked out to a different directory than the SVN repo.
        p.setDefinition(new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "Jenkinsfile"));
        WorkflowRun b2 = r.buildAndAssertSuccess(p);
        assertThat(new File(r.jenkins.getRootDir(), "hook-executed"), not(anExistingFile()));
    }
}
