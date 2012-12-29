package hudson.scm;

import java.io.PrintStream;

import org.tmatesoft.svn.core.SVNLogEntry;

public interface SVNLogFilter {

    public abstract void setLog(PrintStream log);

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