package hudson.scm;

import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Issue;
import org.xml.sax.SAXException;

/**
 * Tests for {@link SubversionChangeLogParser}.
 * 
 * @author kutzi
 */
public class SubversionChangeLogParserTest extends AbstractSubversionTest {
    
    private File changelogFile;
    private SubversionChangeLogSet changeLogSet;

    @Test
    @Issue("JENKINS-10324")
    public void pathsSortedAlphabetically() throws URISyntaxException, IOException, SAXException {
        givenAChangelogFileWithUnsortedPathsInLegacyFormat();
        whenChangelogFileIsParsed();
        thenAffectedPathsMustBeSortedAlphabetically();
    }

    @Test
    @Issue("JENKINS-18574")
    public void pathsEqualToValues() throws URISyntaxException, IOException, SAXException {
        givenAChangelogFileWithUnsortedPathsInLegacyFormat();
        whenChangelogFileIsParsed();
        thenPathsMustBeEqualToValues();
    }
    
    @Test
    @Issue("JENKINS-18574")
    public void valueIsRepoPath() throws URISyntaxException, IOException, SAXException {
        givenAChangelogFileWithUnsortedPathsInLegacyFormat();
        whenChangelogFileIsParsed();
        thenValuesMustStartWithSlash();
    }
    
    @Test
    @Issue("JENKINS-18574")
    public void newChangelogFileForDifferentPathAndValue() throws URISyntaxException, IOException, SAXException {
        givenAChangelogFileWithRelativePathAttributes();
        whenChangelogFileIsParsed();
        thenValuesMustStartWithSlash();
        andPathsMustNotStartWithSlash();
    }
    
    private void givenAChangelogFileWithUnsortedPathsInLegacyFormat() throws URISyntaxException {
        URL url = SubversionChangeLogParserTest.class.getResource("changelog_unsorted.xml");
        this.changelogFile = new File(url.toURI().getSchemeSpecificPart());
    }
    
    private void givenAChangelogFileWithRelativePathAttributes() throws URISyntaxException {
        URL url = SubversionChangeLogParserTest.class.getResource("changelog_relativepath.xml");
        this.changelogFile = new File(url.toURI().getSchemeSpecificPart());
    }
    
    private void whenChangelogFileIsParsed() throws IOException, SAXException {
        this.changeLogSet = new SubversionChangeLogParser(false).parse(null, null, this.changelogFile);
    }
    
    private void thenAffectedPathsMustBeSortedAlphabetically() {
        for(LogEntry entry : changeLogSet.getLogs()) {
            String previous = "";
            for(Path path : entry.getPaths()) {
                Assert.assertTrue(previous.compareTo(path.getPath()) <= 0);
                previous = path.getPath();
            }
        }
    }

    private void thenPathsMustBeEqualToValues() {
        for(LogEntry entry : changeLogSet.getLogs()) {
            for(Path path : entry.getPaths()) {
                Assert.assertEquals(path.getPath(), path.getValue());
            }
        }
    }

    private void thenValuesMustStartWithSlash() {
        for(LogEntry entry : changeLogSet.getLogs()) {
            for(Path path : entry.getPaths()) {
                Assert.assertTrue(path.getValue().startsWith("/"));
            }
        }
    }

    private void andPathsMustNotStartWithSlash() {
        for(LogEntry entry : changeLogSet.getLogs()) {
            for(Path path : entry.getPaths()) {
                Assert.assertFalse(path.getPath().startsWith("/"));
            }
        }
    }
}
