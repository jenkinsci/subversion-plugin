package hudson.scm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.model.AbstractProject;
import hudson.scm.SubversionRepositoryStatus.JobProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletException;

import org.junit.Test;
import org.junit.Assert;
import org.jvnet.hudson.test.Issue;

/**
 * @author kutzi
 */
public class SubversionRepositoryStatusTest {
    
    @SuppressWarnings("rawtypes")
    @Test
    @Issue("JENKINS-15794")
    public void shouldIgnoreDisabledJobs() throws ServletException, IOException {
        SubversionRepositoryStatus.JobTriggerListenerImpl listener = new SubversionRepositoryStatus.JobTriggerListenerImpl();

        // GIVEN: a disabled project
        final AbstractProject project = mock(AbstractProject.class);
        JobProvider jobProvider = () -> Collections.singletonList(project);
        
        listener.setJobProvider(jobProvider);
        
        // WHEN: post-commit hook is triggered
        listener.onNotify(UUID.randomUUID(), -1, Collections.singleton("/somepath"));

        // EXPECT: disabled project is not considered at all
        verify(project, never()).getScm();
    }

    @Test
    public void testDoModuleLocationHasAPathFromAffectedPath_affectedPathIsInConfiguredRepo() {
    	// Given
    	SubversionRepositoryStatus.JobTriggerListenerImpl listener = new SubversionRepositoryStatus.JobTriggerListenerImpl();
    	String configuredRepoFullPath = "https://svn.company.com/project/trunk";
    	String rootRepoPath = "https://svn.company.com/project";

    	Set<String> affectedPath = Collections.singleton("trunk/src/Test.java");

    	// When
    	boolean containsAffectedPath = listener.doModuleLocationHasAPathFromAffectedPath(configuredRepoFullPath, rootRepoPath, affectedPath);

    	// Expect
    	Assert.assertTrue("affected path should be true", containsAffectedPath);
    }

    @Test
    public void testDoModuleLocationHasAPathFromAffectedPath_affectedPathIsNotInConfiguredRepo() {
    	// Given
    	SubversionRepositoryStatus.JobTriggerListenerImpl listener = new SubversionRepositoryStatus.JobTriggerListenerImpl();
    	String configuredRepoFullPath = "https://svn.company.com/project/trunk";
    	String rootRepoPath = "https://svn.company.com/project";

    	Set<String> affectedPath = Collections.singleton("tags/src/");

    	// When
    	boolean containsAffectedPath = listener.doModuleLocationHasAPathFromAffectedPath(configuredRepoFullPath, rootRepoPath, affectedPath);

    	// Expect
    	Assert.assertFalse("affected path should be false", containsAffectedPath);
    }

    @Test
    public void testDoModuleLocationHasAPathFromAffectedPath_affectedPathIsNotInSameRepo() {
    	// Given
    	SubversionRepositoryStatus.JobTriggerListenerImpl listener = new SubversionRepositoryStatus.JobTriggerListenerImpl();
    	String configuredRepoFullPath = "https://svn.company.com/project/trunk";
    	String rootRepoPath = "https://svn.company.com/projecttwo";

    	Set<String> affectedPath = Collections.singleton("trunk/src/Test.java");

    	// When
    	boolean containsAffectedPath = listener.doModuleLocationHasAPathFromAffectedPath(configuredRepoFullPath, rootRepoPath, affectedPath);

    	// Expect
    	Assert.assertFalse("affected path should be false", containsAffectedPath);
    }
}
