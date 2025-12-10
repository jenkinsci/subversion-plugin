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

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.Functions;
import hudson.model.Result;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.logging.Level;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.subversion.SubversionSCMSource;
import jenkins.scm.impl.subversion.SubversionSampleRepoRule;
import org.apache.commons.io.FileUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMBasedRetriever;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class SCMSourceRetrieverSubversionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();
    @Rule public SubversionSampleRepoRule sampleRepoSvn = new SubversionSampleRepoRule();
    @Rule public FlagRule<Boolean> includeSrcTest = new FlagRule<>(() -> SCMBasedRetriever.INCLUDE_SRC_TEST_IN_LIBRARIES, v -> SCMBasedRetriever.INCLUDE_SRC_TEST_IN_LIBRARIES = v);
    @Rule public LoggerRule logging = new LoggerRule().record(SCMBasedRetriever.class, Level.FINE);

    @Issue("SECURITY-2441")
    @Test public void libraryNamesAreNotUsedAsCheckoutDirectories() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/globalLibVar.groovy", "def call() { echo('global library') }");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        LibraryConfiguration globalLib = new LibraryConfiguration("library",
                new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        globalLib.setDefaultVersion("master");
        globalLib.setImplicit(true);
        GlobalLibraries.get().setLibraries(Collections.singletonList(globalLib));
        // Create a folder library with the same name as the global library so it takes precedence.
        sampleRepoSvn.init();
        sampleRepoSvn.write("vars/folderLibVar.groovy", "def call() { jenkins.model.Jenkins.get().setSystemMessage('folder library') }");
        // Copy .git folder from the Git repo for the global library into the SVN repo for the folder library as data.
        FileUtils.copyDirectory(new File(sampleRepo.getRoot(), ".git"), new File(sampleRepoSvn.wc(), ".git"));
        sampleRepoSvn.svnkit("add", sampleRepoSvn.wc() + "/vars");
        sampleRepoSvn.svnkit("add", sampleRepoSvn.wc() + "/.git");
        sampleRepoSvn.svnkit("commit", "--message=init", sampleRepoSvn.wc());
        LibraryConfiguration folderLib = new LibraryConfiguration("library",
                new SCMSourceRetriever(new SubversionSCMSource(null, sampleRepoSvn.prjUrl())));
        folderLib.setDefaultVersion("trunk");
        folderLib.setImplicit(true);
        Folder f = r.jenkins.createProject(Folder.class, "folder1");
        f.getProperties().add(new FolderLibraries(Collections.singletonList(folderLib)));
        // Create a job that uses the folder library, which will take precedence over the global library, since they have the same name.
        WorkflowJob p = f.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("folderLibVar()", true));
        // First build fails as expected since it is not trusted. The folder library is checked out.
        WorkflowRun b1 = r.buildAndAssertStatus(Result.FAILURE, p);
        r.assertLogContains("Only using first definition of library library", b1);
        r.assertLogContains("Scripts not permitted to use staticMethod jenkins.model.Jenkins get", b1);
        // Attacker deletes the folder library, then reruns the build.
        // The existing checkout of the SVN repo should not be reused as the Git repo for the global library.
        f.getProperties().clear();
        WorkflowRun b2 = r.buildAndAssertStatus(Result.FAILURE, p);
        r.assertLogContains("No such DSL method 'folderLibVar'", b2);
        assertThat(r.jenkins.getSystemMessage(), nullValue());
    }

    @Issue("SECURITY-2463")
    @Test public void checkoutDirectoriesAreNotReusedByDifferentScms() throws Exception {
        assumeFalse(Functions.isWindows()); // Checkout hook is not cross-platform.
        sampleRepo.init();
        sampleRepo.write("vars/foo.groovy", "def call() { echo('using global lib') }");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        LibraryConfiguration globalLib = new LibraryConfiguration("library",
                new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        globalLib.setDefaultVersion("master");
        globalLib.setImplicit(true);
        GlobalLibraries.get().setLibraries(Collections.singletonList(globalLib));
        // Create a folder library with the same name as the global library so it takes precedence.
        sampleRepoSvn.init();
        sampleRepoSvn.write("vars/foo.groovy", "def call() { echo('using folder lib') }");
        // Copy .git folder from the Git repo for the global library into the SVN repo for the folder library as data.
        File gitDirInSvnRepo = new File(sampleRepoSvn.wc(), ".git");
        FileUtils.copyDirectory(new File(sampleRepo.getRoot(), ".git"), gitDirInSvnRepo);
        String jenkinsRootDir = r.jenkins.getRootDir().toString();
        // Add a Git post-checkout hook to the .git folder in the SVN repo.
        Path postCheckoutHook = gitDirInSvnRepo.toPath().resolve("hooks/post-checkout");
        // Always create hooks directory for compatibility with https://github.com/jenkinsci/git-plugin/pull/1207.
        Files.createDirectories(postCheckoutHook.getParent());
        Files.write(postCheckoutHook, ("#!/bin/sh\ntouch '" + jenkinsRootDir + "/hook-executed'\n").getBytes(StandardCharsets.UTF_8));
        sampleRepoSvn.svnkit("add", sampleRepoSvn.wc() + "/vars");
        sampleRepoSvn.svnkit("add", sampleRepoSvn.wc() + "/.git");
        sampleRepoSvn.svnkit("propset", "svn:executable", "ON", sampleRepoSvn.wc() + "/.git/hooks/post-checkout");
        sampleRepoSvn.svnkit("commit", "--message=init", sampleRepoSvn.wc());
        LibraryConfiguration folderLib = new LibraryConfiguration("library",
                new SCMSourceRetriever(new SubversionSCMSource(null, sampleRepoSvn.prjUrl())));
        folderLib.setDefaultVersion("trunk");
        folderLib.setImplicit(true);
        Folder f = r.jenkins.createProject(Folder.class, "folder1");
        f.getProperties().add(new FolderLibraries(Collections.singletonList(folderLib)));
        // Run the build using the folder library (which uses the SVN repo).
        WorkflowJob p = f.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("foo()", true));
        r.buildAndAssertSuccess(p);
        // Delete the folder library, and rerun the build so the global library is used.
        f.getProperties().clear();
        WorkflowRun b2 = r.buildAndAssertSuccess(p);
        assertFalse("Git checkout should not execute hooks from SVN repo", new File(r.jenkins.getRootDir(), "hook-executed").exists());
    }
}
