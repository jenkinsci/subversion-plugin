package hudson.scm.listtagsparameter;

import hudson.Proc;
import hudson.scm.AbstractSubversionTest;
import org.jvnet.hudson.test.Bug;

import java.util.Arrays;

/**
 * @author Kohsuke Kawaguchi
 */
public class ListSubversionTagsParameterDefinitionTest extends AbstractSubversionTest {
    /**
     * Make sure we are actually listing tags correctly.
     */
    @Bug(11933)
    public void testListTags() throws Exception {
        Proc p = runSvnServe(getClass().getResource("JENKINS-11933.zip"));
        try {
            ListSubversionTagsParameterDefinition def = new ListSubversionTagsParameterDefinition("FOO", "svn://localhost/", "", "", "", false, false, null);
            assertEquals(Arrays.asList("trunk", "tags/a", "tags/b", "tags/c"), def.getTags());
        } finally {
            p.kill();
        }
    }
}
