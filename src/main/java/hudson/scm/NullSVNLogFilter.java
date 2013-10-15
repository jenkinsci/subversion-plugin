package hudson.scm;

import hudson.model.TaskListener;

import org.tmatesoft.svn.core.SVNLogEntry;

/**
 * An implementation of {@link SVNLogFilter} that never filters a {@link SVNLogEntry}.
 */
public class NullSVNLogFilter implements SVNLogFilter {

	private static final long serialVersionUID = 1L;

    public boolean hasExclusionRule() {
        return false;
    }

    public boolean isIncluded(SVNLogEntry logEntry) {
        return true;
    }

	public void setTaskListener(TaskListener listener) {
	}

}
