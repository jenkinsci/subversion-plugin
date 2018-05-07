/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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
package hudson.scm;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import jenkins.scm.impl.subversion.SubversionSampleRepoRule;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class CredentialsExternalsTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public SubversionSampleRepoRule main = new SubversionSampleRepoRule();

    @Rule
    public SubversionSampleRepoRule ext = new SubversionSampleRepoRule();

    @Issue("JENKINS-32167")
    @Test
    public void smokes() throws Exception {
        main.init();
        main.writeConf("svnserve.conf",
            "[general]\n" +
            "password-db = passwd\n" +
            "authz-db = authz\n" +
            "anon-access = none\n"); // https://bugzilla.redhat.com/show_bug.cgi?id=556712
        main.writeConf("passwd",
            "[users]\n" +
            "alice = alice\n");
        main.writeConf("authz",
            "[/]\n" +
            "alice = rw\n");
        // Adapted from AbstractSubversionTest.runSvnServe:
        int mainPort;
        ServerSocket serverSocket = new ServerSocket(0);
        try {
            mainPort = serverSocket.getLocalPort();
        } finally {
            serverSocket.close();
        }
        AbstractSubversionTest.checkForSvnServe();
        Process mainSrv = new ProcessBuilder("svnserve", "-d", "--foreground", "-r", main.root().getAbsolutePath(), "--listen-port", String.valueOf(mainPort)).start();
        try {
            System.err.println("Running svnserve on <svn://localhost:" + mainPort + "> " + main.uuid());
            ext.init();
            ext.writeConf("svnserve.conf",
                "[general]\n" +
                "password-db = passwd\n" +
                "authz-db = authz\n" +
                "anon-access = none\n");
            ext.writeConf("passwd",
                "[users]\n" +
                "bob = bob\n");
            ext.writeConf("authz",
                "[/]\n" +
                "bob = rw\n");
            int extPort;
            serverSocket = new ServerSocket(0);
            try {
                extPort = serverSocket.getLocalPort();
            } finally {
                serverSocket.close();
            }
            Process extSrv = new ProcessBuilder("svnserve", "-d", "--foreground", "-r", ext.root().getAbsolutePath(), "--listen-port", String.valueOf(extPort)).start();
            try {
                System.err.println("Running svnserve on <svn://localhost:" + extPort + "> " + ext.uuid());
                main.svnkit("update", main.wc());
                main.svnkit("propset", "svn:externals", "ext svn://localhost:" + extPort + "/prj/trunk\n", main.wc());
                main.svnkit("commit", "--message=externals", main.wc());
                FreeStyleProject p = r.createFreeStyleProject("p");
                SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(), Arrays.<Credentials>asList(
                    new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "main-creds", null, "alice", "alice"),
                    new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "ext-creds", null, "bob", "bob"))));
                p.setScm(new SubversionSCM(
                    Collections.singletonList(new SubversionSCM.ModuleLocation("svn://localhost:" + mainPort + "/prj/trunk", "main-creds", ".", "", false)),
                    null, null, null, null, null, null, null, false, false, // WTF was all that?
                    Collections.singletonList(new SubversionSCM.AdditionalCredentials("<svn://localhost:" + extPort + "> " + ext.uuid(), "ext-creds"))));
                FreeStyleBuild b = r.buildAndAssertSuccess(p);
                assertEquals("", b.getWorkspace().child("file").readToString());
                assertEquals("", b.getWorkspace().child("ext/file").readToString());
                main.write("file", "mainrev");
                main.svnkit("commit", "--message=mainrev", main.wc());
                ext.write("file", "extrev");
                ext.svnkit("commit", "--message=extrev", ext.wc());
                b = r.buildAndAssertSuccess(p);
                assertEquals("mainrev", b.getWorkspace().child("file").readToString());
                assertEquals("extrev", b.getWorkspace().child("ext/file").readToString());
                Set<String> messages = new TreeSet<>();
                for (ChangeLogSet.Entry entry : b.getChangeSet()) {
                    messages.add(entry.getMsg());
                }
                assertEquals("[extrev, mainrev]", messages.toString());
            } finally {
                extSrv.destroy();
            }
        } finally {
            mainSrv.destroy();
        }
    }

}
