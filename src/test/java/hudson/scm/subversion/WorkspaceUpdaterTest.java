package hudson.scm.subversion;

import static org.junit.Assert.*;
import hudson.scm.RevisionParameterAction;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.External;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.subversion.WorkspaceUpdater.UpdateTask;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class WorkspaceUpdaterTest {
    
    private static final Date NOW = new Date();
    
    @Test
    public void testGetRevisionFromTimestamp() {
        UpdateTask updateTask = createUpdateTask();
        
        ModuleLocation l = new ModuleLocation("remote", "local");
        
        SVNRevision revision = updateTask.getRevision(l);
        assertEquals(NOW, revision.getDate());
        assertEquals(-1L, revision.getNumber());
    }
    
    @Test
    public void testRevisionFromRevisionParametersOverrideTimestamp() {
        UpdateTask updateTask = createUpdateTask();
        
        updateTask.revisions = new RevisionParameterAction(new SubversionSCM.SvnInfo("remote", 4711));
        
        ModuleLocation l = new ModuleLocation("remote", "local");
        
        SVNRevision revision = updateTask.getRevision(l);
        assertEquals(4711L, revision.getNumber());
        assertNull(revision.getDate());
    }

    @Test
    public void testRevisionInUrlOverridesEverything() {
        UpdateTask updateTask = createUpdateTask();

        updateTask.revisions = new RevisionParameterAction(new SubversionSCM.SvnInfo("remote", 4711));
        
        ModuleLocation l = new ModuleLocation("remote@12345", "local");
        
        SVNRevision revision = updateTask.getRevision(l);
        assertEquals(12345L, revision.getNumber());
        assertNull(revision.getDate());
    }
    
    @Test
    public void testRevisionInUrlOverridesEverything_HEAD() {
        UpdateTask updateTask = createUpdateTask();

        updateTask.revisions = new RevisionParameterAction(new SubversionSCM.SvnInfo("remote", 4711));
        
        ModuleLocation l = new ModuleLocation("remote@HEAD", "local");
        
        SVNRevision revision = updateTask.getRevision(l);
        assertEquals(SVNRevision.HEAD.getName(), revision.getName());
        assertEquals(-1L, revision.getNumber());
        assertNull(revision.getDate());
    }

    private WorkspaceUpdater.UpdateTask createUpdateTask() {
         UpdateTask updateTask = new WorkspaceUpdater.UpdateTask() {
            private static final long serialVersionUID = 1L;
            @Override
            public List<External> perform() throws IOException, InterruptedException {
                return null;
            }
        };
        updateTask.timestamp = NOW;
        return updateTask;
    }
    
}
