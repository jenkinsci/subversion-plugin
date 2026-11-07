package hudson.scm;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.SubversionSCM.ModuleLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubversionSCM}.
 * <p>
 * ({@link SubversionSCMTest} is more like an integration test)
 *
 * @author kutzi
 */
class SubversionSCMUnitTest {

    @Test
    @Issue("JENKINS-12113")
    void testLocalDirectoryIsExpandedWithEnvVars() {
        FilePath root = new FilePath((VirtualChannel) null, "root");

        EnvVars envVars = new EnvVars();
        envVars.put("BRANCH", "test");

        SubversionSCM scm = new SubversionSCM("dummyUrl");

        FilePath resolvedRoot = scm._getModuleRoot(root, "$BRANCH/someMorePath", envVars);

        // Be sure that paths is plateform independant.
        String fileSeparator = FileSystems.getDefault().getSeparator();
        String expected = String.format("root%stest%ssomeMorePath", fileSeparator, fileSeparator);

        assertEquals(expected, resolvedRoot.getRemote());
    }

    @Test
    void shouldSetEnvironmentVariablesWithSingleSvnModule() throws IOException {
        // GIVEN an scm with a single module location
        SubversionSCM scm = new SubversionSCM("http://127.0.0.1"); // whatever

        ModuleLocation[] locations = new ModuleLocation[]{
            moduleFactory("/remotepath", "")
        };

        Map<String, Long> revisions = new HashMap<>();
        revisions.put("/remotepath", 4711L);

        // WHEN envVars are build
        Map<String, String> envVars = new HashMap<>();
        scm.envSetup(revisions, locations, envVars);

        // THEN: we have the (legacy) SVN_URL and SVN_REVISION vars
        assertThat(envVars.get("SVN_URL"), is("/remotepath"));
        assertThat(envVars.get("SVN_REVISION"), is("4711"));

        // AND: also the index-based vars
        assertThat(envVars.get("SVN_URL_1"), is("/remotepath"));
        assertThat(envVars.get("SVN_REVISION_1"), is("4711"));
    }

    @Test
    void shouldSetEnvironmentVariablesWithMultipleSvnModules() throws IOException {
        // GIVEN an scm with a 2 module locations
        SubversionSCM scm = new SubversionSCM("http://127.0.0.1"); // whatever

        ModuleLocation[] locations = new ModuleLocation[]{
                moduleFactory("/remotepath1", ""),
                moduleFactory("/remotepath2", "")};

        Map<String, Long> revisions = new HashMap<>();
        revisions.put("/remotepath1", 4711L);
        revisions.put("/remotepath2", 42L);

        // WHEN envVars are build
        Map<String, String> envVars = new HashMap<>();
        scm.envSetup(revisions, locations, envVars);

        // THEN: we have the SVN_URL_n and SVN_REVISION_n vars
        assertThat(envVars.get("SVN_URL_1"), is("/remotepath1"));
        assertThat(envVars.get("SVN_REVISION_1"), is("4711"));

        assertThat(envVars.get("SVN_URL_2"), is("/remotepath2"));
        assertThat(envVars.get("SVN_REVISION_2"), is("42"));
    }
    
    @Test
    public void shouldSetEnvironmentVariablesMaximumRevision() throws IOException {
        SubversionSCM scm = new SubversionSCM("http://127.0.0.1"); // whatever

        // GIVEN an scm with various module locations
        ModuleLocation[] locations = new ModuleLocation[] {
            moduleFactory("/remotepath1", ""),
            moduleFactory("/remotepath2", ""),
            moduleFactory("/remotepath3", "")
        };

        Map<String, Long> revisions = new HashMap<>();
        revisions.put("/remotepath1", 4711L);
        revisions.put("/remotepath2", 42L);
        revisions.put("/remotepath3", 9920L);

        // WHEN envVars are build
        Map<String, String> envVars = new HashMap<>();
        scm.envSetup(revisions, locations, envVars);

        // THEN: we have the var
        assertThat(envVars.get("SVN_REVISION_MAX"), is("9920"));

        // GIVEN an scm with various module locations, 1st being latest
        revisions = new HashMap<>();
        revisions.put("/remotepath1", 4711L);
        revisions.put("/remotepath2", 42L);
        revisions.put("/remotepath3", 920L);

        // WHEN envVars are build
        envVars = new HashMap<>();
        scm.envSetup(revisions, locations, envVars);

        // THEN: we have the var
        assertThat(envVars.get("SVN_REVISION_MAX"), is("4711"));
    }

    @Issue("JENKINS-58068")
    @Test
    void parseRevisionFileSkipsInvalidLines(@TempDir File dir) throws Exception {
        Run<?, ?> build = mockBuild(dir, null);
        writeRevisionFile(dir,
                "svn://host/a/42",
                "no revision here",
                "svn://host/b/NaN",
                "svn://host/c/7::p",
                "svn://host/a/40");

        Map<String, Long> expected = new HashMap<>();
        expected.put("svn://host/a", 40L); // the minimum revision
        expected.put("svn://host/c", 7L);
        assertEquals(expected, SubversionSCM.parseRevisionFile(build));
    }

    @Issue("JENKINS-58068")
    @Test
    void calcRevisionsFromBuildFallsBackToRevisionFile(@TempDir File dir) throws Exception {
        // this SCM instance did not check out the build, e.g. after a restart, so the revision file of the
        // closest build having one is used, without the svn:externals pinned to a revision
        File previousDir = new File(dir, "1");
        File currentDir = new File(dir, "2");
        assertThat(previousDir.mkdir(), is(true));
        assertThat(currentDir.mkdir(), is(true));
        writeRevisionFile(previousDir, "svn://host/a/42", "svn://host/c/7::p");
        Run<?, ?> build = mockBuild(currentDir, mockBuild(previousDir, null));

        SVNRevisionState state = (SVNRevisionState) new SubversionSCM("svn://host/a")
                .calcRevisionsFromBuild(build, null, null, TaskListener.NULL);

        assertEquals(Collections.singletonMap("svn://host/a", 42L), state.revisions);
    }

    @Test
    void changeLogBuilderReadsRevisionFileOfBuild() throws Exception {
        Run<?, ?> build = mockBuild(null, null);
        SubversionSCM scm = spy(new SubversionSCM("svn://host/a"));
        doReturn(Collections.emptyMap()).when(scm).parseSvnRevisionFile(build);

        new SubversionChangeLogBuilder(build, null, new SVNRevisionState(Collections.emptyMap()), null, TaskListener.NULL, scm);

        verify(scm).parseSvnRevisionFile(build);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Run<?, ?> mockBuild(File rootDir, Run<?, ?> previousBuild) {
        Run build = mock(Run.class);
        when(build.getRootDir()).thenReturn(rootDir);
        when(build.getPreviousBuild()).thenReturn(previousBuild);
        return build;
    }

    private static void writeRevisionFile(File rootDir, String... lines) throws IOException {
        Files.write(new File(rootDir, "revision.txt").toPath(), Arrays.asList(lines), Charset.defaultCharset());
    }

    private ModuleLocation moduleFactory(String remote, String local) {
        // mapping to the current constructor
        return new ModuleLocation(remote, null, local, null, false, false);
    }
}
