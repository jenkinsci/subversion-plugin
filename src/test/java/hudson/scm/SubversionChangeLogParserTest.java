package hudson.scm;

import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SubversionChangeLogParser}.
 *
 * @author kutzi
 */
@WithJenkins
class SubversionChangeLogParserTest extends AbstractSubversionTest {

    private File changelogFile;
    private SubversionChangeLogSet changeLogSet;

    @Test
    @Issue("JENKINS-10324")
    void pathsSortedAlphabetically() throws URISyntaxException, IOException, SAXException {
        givenAChangelogFileWithUnsortedPathsInLegacyFormat();
        whenChangelogFileIsParsed();
        thenAffectedPathsMustBeSortedAlphabetically();
    }

    @Test
    @Issue("JENKINS-18574")
    void pathsEqualToValues() throws URISyntaxException, IOException, SAXException {
        givenAChangelogFileWithUnsortedPathsInLegacyFormat();
        whenChangelogFileIsParsed();
        thenPathsMustBeEqualToValues();
    }

    @Test
    @Issue("JENKINS-18574")
    void valueIsRepoPath() throws URISyntaxException, IOException, SAXException {
        givenAChangelogFileWithUnsortedPathsInLegacyFormat();
        whenChangelogFileIsParsed();
        thenValuesMustStartWithSlash();
    }

    @Test
    @Issue("JENKINS-18574")
    void newChangelogFileForDifferentPathAndValue() throws URISyntaxException, IOException, SAXException {
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
        for (LogEntry entry : changeLogSet.getLogs()) {
            String previous = "";
            for (Path path : entry.getPaths()) {
                assertTrue(previous.compareTo(path.getPath()) <= 0);
                previous = path.getPath();
            }
        }
    }

    private void thenPathsMustBeEqualToValues() {
        for (LogEntry entry : changeLogSet.getLogs()) {
            for (Path path : entry.getPaths()) {
                assertEquals(path.getPath(), path.getValue());
            }
        }
    }

    private void thenValuesMustStartWithSlash() {
        for (LogEntry entry : changeLogSet.getLogs()) {
            for (Path path : entry.getPaths()) {
                assertTrue(path.getValue().startsWith("/"));
            }
        }
    }

    private void andPathsMustNotStartWithSlash() {
        for (LogEntry entry : changeLogSet.getLogs()) {
            for (Path path : entry.getPaths()) {
                assertFalse(path.getPath().startsWith("/"));
            }
        }
    }
}
