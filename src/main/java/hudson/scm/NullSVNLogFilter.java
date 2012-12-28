package hudson.scm;

import java.io.PrintStream;

import org.tmatesoft.svn.core.SVNLogEntry;

/**
 * An implementation of {@link SVNLogFilter} that never filters a {@link SVNLogEntry}.
 */
public class NullSVNLogFilter implements SVNLogFilter {

    public void setLog(PrintStream log) {
    }

    public boolean hasExclusionRule() {
        return false;
    }

    public boolean isIncluded(SVNLogEntry logEntry) {
        return true;
    }

}
