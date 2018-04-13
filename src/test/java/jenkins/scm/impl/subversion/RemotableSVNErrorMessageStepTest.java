/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.remoting.Callable;
import hudson.slaves.DumbSlave;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

/**
 * @author Oleg Nenashev
 */
@For(SVNErrorMessage.class)
public class RemotableSVNErrorMessageStepTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-50339")
    public void shouldSerializeException() throws Exception {
        DumbSlave agent = j.createOnlineSlave();
        try {
            agent.getChannel().call(new ErrorCallable());
        } catch (SVNException e) {
            return; // fine
        }
    }

    private static class ErrorCallable extends MasterToSlaveCallable<Void, SVNException> {

        @Override
        public Void call() throws SVNException {
            SVNErrorMessage err = new RemotableSVNErrorMessage(SVNErrorCode.UNKNOWN, "Just a test exception",
                    new IllegalStateException("foo"));
            throw new SVNException(err);
        }
    }
}
