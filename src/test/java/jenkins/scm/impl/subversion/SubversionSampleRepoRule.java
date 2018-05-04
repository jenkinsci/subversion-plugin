/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import hudson.ExtensionList;
import hudson.scm.SubversionRepositoryStatus;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.scm.impl.mock.AbstractSampleRepoRule;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertEquals;
import org.jvnet.hudson.test.JenkinsRule;
import org.tmatesoft.svn.cli.svn.SVN;
import org.tmatesoft.svn.cli.svnadmin.SVNAdmin;
import org.tmatesoft.svn.core.wc.SVNClientManager;

public final class SubversionSampleRepoRule extends AbstractSampleRepoRule {

    private File repo;
    private File wc;
    @Deprecated
    private boolean checkedSvnCLI;

    @Override
    protected void before() throws Throwable {
        super.before();
        repo = tmp.newFolder();
        wc = tmp.newFolder();
    }

    public void write(String rel, String text) throws IOException {
        FileUtils.write(new File(wc, rel), text);
    }

    public void writeConf(String rel, String text) throws IOException {
        FileUtils.write(new File(new File(repo, "conf"), rel), text);
    }

    public File root() {
        return repo;
    }

    public String rootUrl() throws URISyntaxException {
        URI u = repo.toURI();
        // TODO SVN rejects File.toUri syntax (requires blank authority field)
        return new URI(u.getScheme(), "", u.getPath(), u.getFragment()).toString();
    }

    public String prjUrl() throws URISyntaxException {
        return rootUrl() + "prj";
    }

    public String trunkUrl() throws URISyntaxException {
        return prjUrl() + "/trunk";
    }

    public String branchesUrl() throws URISyntaxException {
        return prjUrl() + "/branches";
    }

    public String tagsUrl() throws URISyntaxException {
        return prjUrl() + "/tags";
    }

    @Override
    public String toString() {
        try {
            return rootUrl();
        } catch (URISyntaxException x) {
            throw new IllegalStateException(x);
        }
    }

    @Deprecated
    private void checkForSvnCLI() throws Exception {
        if (!checkedSvnCLI) {
            run(true, tmp.getRoot(), "svn", "--version");
            checkedSvnCLI = true;
        }
    }

    /**
     * For more portable tests, run {@link #svnkit} instead, using {@link #wc} to refer to the working copy where needed.
     */
    @Deprecated
    public void svn(String... cmds) throws Exception {
        checkForSvnCLI();
        List<String> args = new ArrayList<>();
        args.add("svn");
        args.addAll(Arrays.asList(cmds));
        run(false, wc, args.toArray(new String[args.size()]));
    }
    
    public String wc() {
        return wc.getAbsolutePath();
    }

    public void svnkit(String... cmds) throws Exception {
        class MySVN extends SVN {
            public void _run(String... cmds) {
                super.run(cmds);
            }
            @Override
            public void success() {
                // Unable to call setCompleted() so Cancellator will not work; probably irrelevant.
            }
            @Override
            public void failure() {
                throw new AssertionError("svn command failed");
            }
        }
        new MySVN()._run(cmds);
    }

    public void basicInit() throws Exception {
        class MySVNAdmin extends SVNAdmin {
            public void _run(String... cmds) {
                run(cmds);
            }
            @Override
            public void success() {
                // Unable to call setCompleted() so Cancellator will not work; probably irrelevant.
            }
            @Override
            public void failure() {
                throw new AssertionError("svn command failed");
            }
        }
        new MySVNAdmin()._run("create", /* do we care? "--pre-1.6-compatible",*/ repo.getAbsolutePath());
        svnkit("mkdir", "--parents", "--message=structure", trunkUrl(), branchesUrl(), tagsUrl());
        System.out.println("Initialized " + this + " and working copy " + wc);
    }

    public void init() throws Exception {
        basicInit();
        svnkit("co", trunkUrl(), wc());
        write("file", "");
        svnkit("add", wc() + "/file");
        svnkit("commit", "--message=init", wc());
        assertEquals(2, revision());
    }

    private String uuid(String url) throws Exception {
        checkForSvnCLI(); // TODO better to use SVNKit
        Process proc = new ProcessBuilder("svn", "info", "--xml", url).start();
        BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        Pattern p = Pattern.compile("<uuid>(.+)</uuid>");
        String line;
        while ((line = r.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                return m.group(1);
            }
        }
        throw new IllegalStateException("no output");
    }

    public UUID uuid() throws Exception {
        return UUID.fromString(uuid(rootUrl()));
    }

    public void notifyCommit(JenkinsRule r, String path) throws Exception {
        synchronousPolling(r);
        // Mocking the web POST, with crumb, is way too hard, and get an IllegalStateException: STREAMED from doNotifyCommit’s getReader anyway.
        for (SubversionRepositoryStatus.Listener listener : ExtensionList.lookup(SubversionRepositoryStatus.Listener.class)) {
            listener.onNotify(uuid(), -1, Collections.singleton(path));
        }
        r.waitUntilNoActivity();
    }

    /**
     * Gets the repository revision just committed.
     */
    public long revision() throws Exception {
        // .getLookClient().doGetYoungestRevision(repo) would show last committed revision but would not be sensitive to checked-out branch; which is clearer?
        return SVNClientManager.newInstance().getStatusClient().doStatus(wc, true).getRemoteRevision().getNumber(); // http://stackoverflow.com/a/2295674/12916
    }

}
