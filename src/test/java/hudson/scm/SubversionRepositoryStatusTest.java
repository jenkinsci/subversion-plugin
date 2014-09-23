package hudson.scm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.scm.SubversionRepositoryStatus.JobProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletException;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.experimental.runners.Enclosed;
import org.jvnet.hudson.test.Bug;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author kutzi
 */
@RunWith(Enclosed.class)
public class SubversionRepositoryStatusTest {
    
    @SuppressWarnings("rawtypes")
    @Test
    @Bug(15794)
    public void shouldIgnoreDisabledJobs() throws ServletException, IOException {
        SubversionRepositoryStatus.JobTriggerListenerImpl listener = new SubversionRepositoryStatus.JobTriggerListenerImpl();

        // GIVEN: a disabled project
        final AbstractProject project = mock(AbstractProject.class);
        when(project.isDisabled()).thenReturn(true);
        
        JobProvider jobProvider = new JobProvider() {
            public List<Job> getAllJobs() {
                return Collections.<Job>singletonList(project);
            }
        };
        
        listener.setJobProvider(jobProvider);
        
        // WHEN: post-commit hook is triggered
        listener.onNotify(UUID.randomUUID(), -1, Collections.singleton("/somepath"));

        // EXPECT: disabled project is not considered at all
        verify(project, never()).getScm();
    }

    public static class JobTriggerListenerImplTest extends TestCase {
    	
    	private SubversionRepositoryStatus.JobTriggerListenerImpl listener;
    	
    	
    	public void setUp() {
    		this.listener = new SubversionRepositoryStatus.JobTriggerListenerImpl();
    	}
    	
    	public void tearDown() {
    		this.listener = null;
    	}
    	
    	public void testDoModuleLocationContainsAPathFromAffectedPath_affectedPathIsInConfiguredRepo() {
    		// Given
    		String configuredRepoFullPath = "https://svn.company.com/project/trunk";
    		String rootRepoPath = "https://svn.company.com/project";

    		Set<String> affectedPath = Collections.singleton("trunk/src/Test.java");

    		// When
    		boolean containsAffectedPath = listener.doModuleLocationContainsAPathFromAffectedPath(configuredRepoFullPath, rootRepoPath, affectedPath);

    		// Expect
    		assertTrue("affected path should be true", containsAffectedPath);        
    	}
    	
    	public void testDoModuleLocationContainsAPathFromAffectedPath_affectedPathIsNotInConfiguredRepo() {
    		// Given
    		String configuredRepoFullPath = "https://svn.company.com/project/trunk";
    		String rootRepoPath = "https://svn.company.com/project";

    		Set<String> affectedPath = Collections.singleton("tags/src/");

    		// When
    		boolean containsAffectedPath = listener.doModuleLocationContainsAPathFromAffectedPath(configuredRepoFullPath, rootRepoPath, affectedPath);

    		// Expect
    		assertFalse("affected path should be false", containsAffectedPath);        
    	}
    	
    	public void testDoModuleLocationContainsAPathFromAffectedPath_affectedPathIsNotInSameRepo() {
    		// Given
    		String configuredRepoFullPath = "https://svn.company.com/project/trunk";
    		String rootRepoPath = "https://svn.company.com/projecttwo";

    		Set<String> affectedPath = Collections.singleton("trunk/src/Test.java");

    		// When
    		boolean containsAffectedPath = listener.doModuleLocationContainsAPathFromAffectedPath(configuredRepoFullPath, rootRepoPath, affectedPath);

    		// Expect
    		assertFalse("affected path should be false", containsAffectedPath);        
    	}
    }
}
