package hudson.scm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.model.AbstractProject;
import hudson.scm.SubversionRepositoryStatus.JobProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.servlet.ServletException;

import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author kutzi
 */
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
            public List<AbstractProject> getAllJobs() {
                return Collections.singletonList(project);
            }
        };
        
        listener.setJobProvider(jobProvider);
        
        // WHEN: post-commit hook is triggered
        listener.onNotify(UUID.randomUUID(), -1, Collections.singleton("/somepath"));

        // THEN: disabled project is not considered at all
        verify(project, never()).getScm();
    }

}
