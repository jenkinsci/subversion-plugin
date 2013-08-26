package hudson.scm.parameter.listdirs;

import hudson.Proc;
import hudson.scm.AbstractSubversionTest;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;

import org.jvnet.hudson.test.Bug;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @author Kohsuke Kawaguchi
 */
public class ListDirectoriesParameterDefinitionTest extends AbstractSubversionTest {
    /**
     * Make sure we are actually listing tags correctly.
     */
    @Bug(11933)
    public void testListTags() throws Exception {
        Proc p = runSvnServe(getClass().getResource("JENKINS-11933.zip"));
        try {
            ListDirectoriesParameterDefinition def = new ListDirectoriesParameterDefinition("FOO", "svn://localhost/", "", "", "", false, false, null);
            List<String> tags = def.getTags();
            List<String> expected = Arrays.asList("trunk", "tags/a", "tags/b", "tags/c");
            
            if (!expected.equals(tags))  {
                // retry. Maybe the svnserve just didn't start up correctly, yet
                System.out.println("First attempt failed. Retrying.");
                Thread.sleep(300L);
                tags = def.getTags();
                if (!expected.equals(tags))  {
                    dumpRepositoryContents();
                
                    Assert.fail("Expected " + expected + ", but got " + tags);
                }
            }
            
        } finally {
            p.kill();
        }
    }

    private void dumpRepositoryContents() throws SVNException {
        System.out.println("Repository contents:");
        SVNURL repoURL = SVNURL.parseURIEncoded( "svn://localhost/");
        SVNLogClient logClient = new SVNLogClient((ISVNAuthenticationManager)null, null);
        logClient.doList(repoURL, SVNRevision.HEAD, SVNRevision.HEAD, false, true, new ISVNDirEntryHandler() {
            public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
                System.out.println(dirEntry.getRelativePath());
            }
        });
    }
}
