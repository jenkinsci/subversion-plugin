package hudson.scm;

import hudson.Util;
import hudson.model.TaskListener;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;

/**
 * Determines whether a log entry contains changes within the included paths for a project.
 */
public class DefaultSVNLogFilter implements SVNLogFilter {

    private TaskListener listener;

    private final Pattern[] excludedPatterns;
    private final Pattern[] includedPatterns;
    private final Set<String> excludedUsers;
    private final String excludedRevprop;
    private final Pattern[] excludedCommitMessages;
    private final boolean ignoreDirPropChanges;

    public DefaultSVNLogFilter(Pattern[] excludedPatterns, Pattern[] includedPatterns, Set<String> excludedUsers, String excludedRevProp, Pattern[] excludedCommitMessages, boolean ignoreDirPropChanges) {
        this.excludedPatterns = excludedPatterns.clone();
        this.includedPatterns = includedPatterns.clone();
        this.excludedUsers = excludedUsers;
        this.excludedRevprop = excludedRevProp;
        this.excludedCommitMessages = excludedCommitMessages.clone();
        this.ignoreDirPropChanges = ignoreDirPropChanges;
    }

    public void setTaskListener(TaskListener listener) {
        this.listener = listener;
    }

	private PrintStream getLog() {
		return this.listener != null ? this.listener.getLogger() : null;
	}

    /* (non-Javadoc)
	 * @see hudson.scm.SVNLogFilter#hasExclusionRule()
	 */
    public boolean hasExclusionRule() {
        return excludedPatterns.length > 0 || !excludedUsers.isEmpty() || excludedRevprop != null || excludedCommitMessages.length > 0 || includedPatterns.length > 0 || ignoreDirPropChanges;
    }

    /* (non-Javadoc)
	 * @see hudson.scm.SVNLogFilter#isIncluded(org.tmatesoft.svn.core.SVNLogEntry)
	 */
    public boolean isIncluded(SVNLogEntry logEntry) {
        if (excludedRevprop != null) {
            // If the entry includes the exclusion revprop, don't count it as a change
            SVNProperties revprops = logEntry.getRevisionProperties();
            if (revprops != null && revprops.containsName(excludedRevprop)) {
                if (getLog() != null) {
                    getLog().println(hudson.scm.subversion.Messages.SubversionSCM_pollChanges_ignoredRevision(
                        logEntry.getRevision(),
                        hudson.scm.subversion.Messages.SubversionSCM_pollChanges_ignoredRevision_revprop(excludedRevprop)));
                }
                return false;
            }
        }

        String author = logEntry.getAuthor();
        if (excludedUsers.contains(author)) {
            // If the author is an excluded user, don't count this entry as a change
            if (getLog() != null) {
            	getLog().println(hudson.scm.subversion.Messages.SubversionSCM_pollChanges_ignoredRevision(
                    logEntry.getRevision(),
                    hudson.scm.subversion.Messages.SubversionSCM_pollChanges_ignoredRevision_author(author)));
            }
            return false;
        }

        if (excludedCommitMessages != null) {
            // If the commit message contains one of the excluded messages, don't count it as a change
            String commitMessage = logEntry.getMessage();
            for (Pattern pattern : excludedCommitMessages) {
                if (pattern.matcher(commitMessage).find()) {
                    return false;
                }
            }
        }

        // If there were no changes, don't count this entry as a change
        Map<String, SVNLogEntryPath> changedPaths = logEntry.getChangedPaths();
        if (changedPaths.isEmpty()) {
            return false;
        }

        // dirPropChanges are changes that modifiy ('M') a directory, i.e. only
        // exclude if there are NO changes on files or Adds/Removals
        if (ignoreDirPropChanges) {
            boolean contentChanged = false;
            for (SVNLogEntryPath path : changedPaths.values()) {
                if (path.getType() != 'M' || path.getKind() != SVNNodeKind.DIR) {
                    contentChanged = true;
                    break;
                }
            }
            if (!contentChanged) {
                if (getLog() != null) {
                	getLog().println(hudson.scm.subversion.Messages.SubversionSCM_pollChanges_ignoredRevision(
                        logEntry.getRevision(),
                        hudson.scm.subversion.Messages.SubversionSCM_pollChanges_ignoredRevision_onlydirprops()));
                }
                return false;
            }
        }

        // If there are included patterns, see which paths are included
        List<String> includedPaths = new ArrayList<>();
        if (includedPatterns.length > 0) {
            for (String path : changedPaths.keySet()) {
                for (Pattern pattern : includedPatterns) {
                    if (pattern.matcher(path).matches()) {
                        includedPaths.add(path);
                        break;
                    }
                }
            }
        } else {
            includedPaths = new ArrayList<>(changedPaths.keySet());
        }

        // If no paths are included don't count this entry as a change
        if (includedPaths.isEmpty()) {
            if (getLog() != null) {
            	getLog().println(hudson.scm.subversion.Messages.SubversionSCM_pollChanges_ignoredRevision(
                    logEntry.getRevision(),
                    hudson.scm.subversion.Messages.SubversionSCM_pollChanges_ignoredRevision_noincpath()));
            }
            return false;
        }

        // Else, check each changed path
        List<String> excludedPaths = new ArrayList<>();
        if (excludedPatterns.length > 0) {
            for (String path : includedPaths) {
                for (Pattern pattern : excludedPatterns) {
                    if (pattern.matcher(path).matches()) {
                        excludedPaths.add(path);
                        break;
                    }
                }
            }
        }

        // If all included paths are in an excluded region, don't count this entry as a change
        if (includedPaths.size() == excludedPaths.size()) {
            if (getLog() != null) {
            	getLog().println(hudson.scm.subversion.Messages.SubversionSCM_pollChanges_ignoredRevision(
                    logEntry.getRevision(),
                    hudson.scm.subversion.Messages.SubversionSCM_pollChanges_ignoredRevision_path(Util.join(excludedPaths, ", "))));
            }
            return false;
        }

        // Otherwise, a change is a change
        return true;
    }

    private static final long serialVersionUID = 1L;
}
