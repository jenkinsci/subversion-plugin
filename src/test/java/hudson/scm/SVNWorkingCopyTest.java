/*
 * The MIT License
 * 
 * Copyright (c) 2014 schristou88
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

import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.subversion.WorkspaceUpdater;
import hudson.scm.subversion.WorkspaceUpdaterDescriptor;
import org.jvnet.hudson.test.Issue;
import org.tmatesoft.svn.core.internal.wc.SVNStatusUtil;
import org.tmatesoft.svn.core.internal.wc.admin.*;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;

import java.io.File;
import org.junit.Ignore;

/**
 * @author schristou88
 */
@Ignore("TODO org.tmatesoft.svn.core.SVNException: svn: E175002: PROPFIND of '/trunk/hudson/test-projects/trivial-ant': 405 Method Not Allowed (https://svn.jenkins-ci.org)")
public class SVNWorkingCopyTest extends AbstractSubversionTest {
  public void testCheckoutWorkingCopyFormat14() throws Exception {
    checkoutAndVerifyWithFormat(SVNAdminArea14.WC_FORMAT);
  }

  public void testCheckoutWorkingCopyFormat15() throws Exception {
    checkoutAndVerifyWithFormat(SVNAdminArea15.WC_FORMAT);
  }

  public void testCheckoutWorkingCopyFormat16() throws Exception {
    checkoutAndVerifyWithFormat(SVNAdminArea16.WC_FORMAT);
  }

    /**
     * SVN 1.7 in jenkins uses a WC format of {@link SubversionWorkspaceSelector#WC_FORMAT_17}.
     * However we still need to check against the actual working copy format of {@link ISVNWCDb#WC_FORMAT_17}
     */
  public void testCheckoutWorkingCopyFormat17() throws Exception {
      int checkoutFormat = checkoutWithFormat(SubversionWorkspaceSelector.WC_FORMAT_17);
      assertEquals(ISVNWCDb.WC_FORMAT_17, checkoutFormat);
  }

  public void testCheckoutWorkingCopyFormat18() throws Exception {
    checkoutAndVerifyWithFormat(ISVNWCDb.WC_FORMAT_18);
  }

    @Issue("JENKINS-26458")
    public void testCheckoutWorkingCopyFormat100() throws Exception {
        assertEquals("Working copy of 100 should checkout 1.7",
                ISVNWCDb.WC_FORMAT_17, checkoutWithFormat(100));
    }

  private void checkoutAndVerifyWithFormat(int format) throws Exception {
    assertEquals("Checkout and workspace format do not match", format, checkoutWithFormat(format));
  }

  private int checkoutWithFormat(int format) throws Exception {
    super.configureSvnWorkspaceFormat(format);

    FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "svntest" + format);
    SubversionSCM subversionSCM = new SubversionSCM("https://svn.jenkins-ci.org/trunk/hudson/test-projects/trivial-ant");

    project.setScm(subversionSCM);
    assertBuildStatusSuccess(project.scheduleBuild2(0));

    // Create a status client and get the working copy format.
    SVNClientManager testWCVerseion = SVNClientManager.newInstance(null, "testWCVerseion", null);
    File path = new File(project.getWorkspace().getRemote());
    return testWCVerseion.getStatusClient().doStatus(path,
            true).getWorkingCopyFormat();
  }
}
