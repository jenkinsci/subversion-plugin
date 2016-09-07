/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCMRevisionState;
import hudson.util.StreamTaskListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class SubversionSCMSourceIntegrationTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public SubversionSampleRepoRule sampleRepo = new SubversionSampleRepoRule();

    @Test
    public void retrieve() throws Exception {
        sampleRepo.init();
        sampleRepo.write("file", "trunk");
        sampleRepo.svnkit("commit", "--message=trunk", sampleRepo.wc());
        long trunk = sampleRepo.revision();
        assertEquals(3, trunk);
        sampleRepo.svnkit("copy", "--message=branching", sampleRepo.trunkUrl(), sampleRepo.branchesUrl() + "/dev");
        sampleRepo.svnkit("switch", sampleRepo.branchesUrl() + "/dev", sampleRepo.wc());
        sampleRepo.write("file", "dev1");
        sampleRepo.svnkit("commit", "--message=dev1", sampleRepo.wc());
        long dev1 = sampleRepo.revision();
        assertEquals(5, dev1);
        sampleRepo.svnkit("copy",  "--message=tagging", sampleRepo.branchesUrl() + "/dev", sampleRepo.tagsUrl() + "/dev-1");
        sampleRepo.write("file", "dev2");
        sampleRepo.svnkit("commit", "--message=dev2", sampleRepo.wc());
        long dev2 = sampleRepo.revision();
        assertEquals(7, dev2);
        SCMSource source = new SubversionSCMSource(null, sampleRepo.prjUrl());
        TaskListener listener = StreamTaskListener.fromStdout();
        // First check fetching of all heads. SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[SCMHead{'branches/dev'}, SCMHead{'tags/dev-1'}, SCMHead{'trunk'}]", source.fetch(listener).toString());
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        // Retrieval of heads:
        assertRevision(source.fetch(new SCMHead("trunk"), listener), "trunk", source, run, listener);
        assertRevision(source.fetch(new SCMHead("branches/dev"), listener), "dev2", source, run, listener);
        assertRevision(source.fetch(new SCMHead("tags/dev-1"), listener), "dev1", source, run, listener);
        // Retrieval of revisions by head name:
        assertRevision(source.fetch("trunk", listener), "trunk", source, run, listener);
        assertRevision(source.fetch("trunk/", listener), "trunk", source, run, listener);
        assertRevision(source.fetch("branches/dev", listener), "dev2", source, run, listener);
        assertRevision(source.fetch("tags/dev-1", listener), "dev1", source, run, listener);
        // Retrieval of revisions by revision number:
        assertRevision(source.fetch("trunk@" + trunk, listener), "trunk", source, run, listener);
        assertRevision(source.fetch("trunk/@" + trunk, listener), "trunk", source, run, listener);
        assertRevision(source.fetch("branches/dev@" + dev2, listener), "dev2", source, run, listener);
        assertRevision(source.fetch("branches/dev@" + dev1, listener), "dev1", source, run, listener);
        // And nonexistent/bogus stuff:
        assertRevision(source.fetch("nonexistent", listener), null, source, run, listener);
        assertRevision(source.fetch("nonexistent/", listener), null, source, run, listener);
        assertRevision(source.fetch("nonexistent@" + trunk, listener), null, source, run, listener);
        assertRevision(source.fetch("nonexistent@999", listener), null, source, run, listener);
        assertRevision(source.fetch("trunk@999", listener), null, source, run, listener); // currently fetch succeeds, but checkout fails
        // Checks out repo root (means you have trunk/file not file):
        assertRevision(source.fetch("", listener), null, source, run, listener);
        // Other oddities:
        assertRevision(source.fetch("@", listener), null, source, run, listener);
        assertRevision(source.fetch("/", listener), null, source, run, listener);
        assertRevision(source.fetch("//", listener), null, source, run, listener);
        assertRevision(source.fetch("\n", listener), null, source, run, listener);
        // Completions of revision:
        assertThat(source.fetchRevisions(listener), hasItems("trunk", "branches/dev", "tags/dev-1"));
    }
    private void assertRevision(@CheckForNull SCMRevision rev, @CheckForNull String expectedFile, @NonNull SCMSource source, @NonNull Run<?,?> run, @NonNull TaskListener listener) throws Exception {
        if (rev == null) {
            assertNull(expectedFile);
            return;
        }
        FilePath ws = new FilePath(run.getRootDir()).child("tmp");
        try {
            source.build(rev.getHead(), rev).checkout(run, new Launcher.LocalLauncher(listener), ws, listener, null, SCMRevisionState.NONE);
        } catch (Exception x) {
            x.printStackTrace(listener.error("could not check out"));
            assertNull(expectedFile);
            return;
        }
        FilePath file = ws.child("file");
        assertEquals(expectedFile, file.exists() ? file.readToString() : null);
    }

}
