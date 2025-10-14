package hudson.scm;

import hudson.model.AbstractProject;
import hudson.scm.SubversionRepositoryStatus.JobProvider;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author kutzi
 */
class SubversionRepositoryStatusTest {

    @SuppressWarnings("rawtypes")
    @Test
    @Issue("JENKINS-15794")
    void shouldIgnoreDisabledJobs() {
        SubversionRepositoryStatus.JobTriggerListenerImpl listener = new SubversionRepositoryStatus.JobTriggerListenerImpl();

        // GIVEN: a disabled project
        final AbstractProject project = mock(AbstractProject.class);
        when(project.isDisabled()).thenReturn(true);

        JobProvider jobProvider = () -> Collections.singletonList(project);

        listener.setJobProvider(jobProvider);

        // WHEN: post-commit hook is triggered
        listener.onNotify(UUID.randomUUID(), -1, Collections.singleton("/somepath"));

        // EXPECT: disabled project is not considered at all
        verify(project, never()).getScm();
    }

    @Test
    void testDoModuleLocationHasAPathFromAffectedPath_affectedPathIsInConfiguredRepo() {
        // Given
        SubversionRepositoryStatus.JobTriggerListenerImpl listener = new SubversionRepositoryStatus.JobTriggerListenerImpl();
        String configuredRepoFullPath = "https://svn.company.com/project/trunk";
        String rootRepoPath = "https://svn.company.com/project";

        Set<String> affectedPath = Collections.singleton("trunk/src/Test.java");

        // When
        boolean containsAffectedPath = listener.doModuleLocationHasAPathFromAffectedPath(configuredRepoFullPath, rootRepoPath, affectedPath);

        // Expect
        assertTrue(containsAffectedPath, "affected path should be true");
    }

    @Test
    void testDoModuleLocationHasAPathFromAffectedPath_affectedPathIsNotInConfiguredRepo() {
        // Given
        SubversionRepositoryStatus.JobTriggerListenerImpl listener = new SubversionRepositoryStatus.JobTriggerListenerImpl();
        String configuredRepoFullPath = "https://svn.company.com/project/trunk";
        String rootRepoPath = "https://svn.company.com/project";

        Set<String> affectedPath = Collections.singleton("tags/src/");

        // When
        boolean containsAffectedPath = listener.doModuleLocationHasAPathFromAffectedPath(configuredRepoFullPath, rootRepoPath, affectedPath);

        // Expect
        assertFalse(containsAffectedPath, "affected path should be false");
    }

    @Test
    void testDoModuleLocationHasAPathFromAffectedPath_affectedPathIsNotInSameRepo() {
        // Given
        SubversionRepositoryStatus.JobTriggerListenerImpl listener = new SubversionRepositoryStatus.JobTriggerListenerImpl();
        String configuredRepoFullPath = "https://svn.company.com/project/trunk";
        String rootRepoPath = "https://svn.company.com/projecttwo";

        Set<String> affectedPath = Collections.singleton("trunk/src/Test.java");

        // When
        boolean containsAffectedPath = listener.doModuleLocationHasAPathFromAffectedPath(configuredRepoFullPath, rootRepoPath, affectedPath);

        // Expect
        assertFalse(containsAffectedPath, "affected path should be false");
    }
}
