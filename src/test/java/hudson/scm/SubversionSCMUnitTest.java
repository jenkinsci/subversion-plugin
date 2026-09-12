package hudson.scm;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.scm.SubversionSCM.ModuleLocation;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private ModuleLocation moduleFactory(String remote, String local) {
        // mapping to the current constructor
        return new ModuleLocation(remote, null, local, null, false, false);
    }
}
