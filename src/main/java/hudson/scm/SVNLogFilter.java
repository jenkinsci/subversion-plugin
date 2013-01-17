package hudson.scm;

import hudson.model.TaskListener;

import java.io.PrintStream;
import java.io.Serializable;

import org.tmatesoft.svn.core.SVNLogEntry;

public interface SVNLogFilter extends Serializable {

    public abstract void setTaskListener(TaskListener listener);

    /**
     * Is there any exclusion rule?
     * @return true if the filter could possibly filter anything.
     */
    public abstract boolean hasExclusionRule();

    /**
     * Checks if the given log entry should be considered for the purposes
     * of SCM polling.
     *
     * @return <code>true</code> if the should trigger polling, <code>false</code> otherwise
     */
    public abstract boolean isIncluded(SVNLogEntry logEntry);

}