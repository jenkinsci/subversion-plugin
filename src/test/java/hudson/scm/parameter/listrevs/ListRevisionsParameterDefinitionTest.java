package hudson.scm.parameter.listrevs;

import hudson.Proc;
import hudson.scm.AbstractSubversionTest;

import java.util.Arrays;
import java.util.List;

public class ListRevisionsParameterDefinitionTest extends AbstractSubversionTest {
    /**
     * Make sure we are actually listing revisions correctly.
     */
    public void testListTags() throws Exception {
        Proc p = runSvnServe(getClass().getResource("../../small.zip"));
        try {
            ListRevisionsParameterDefinition def = new ListRevisionsParameterDefinition("FOO", "svn://localhost/", null);
            List<Long> actual = def.getFirstLastRevisions();
            List<Long> expected = Arrays.asList(0L, 1L);
            if (!expected.equals(actual))  {
                // retry. Maybe the svnserve just didn't start up correctly, yet
                System.out.println("First attempt failed. Retrying.");
                Thread.sleep(300L);
                actual = def.getFirstLastRevisions();
            }

            assertEquals(expected, actual);
        } finally {
            p.kill();
        }
    }
}
