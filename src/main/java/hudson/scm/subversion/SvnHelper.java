package hudson.scm.subversion;

import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Provides various helper methods.
 * 
 * @author kutzi
 */
public class SvnHelper {

    /**
     * Cuts off any optional '@revisionnr' from the end of the url string.
     */
    public static String getUrlWithoutRevision(String remoteUrlPossiblyWithRevision) {
        int idx = remoteUrlPossiblyWithRevision.lastIndexOf('@');
        int slashIdx = remoteUrlPossiblyWithRevision.lastIndexOf('/');
        if (idx > 0 && idx > slashIdx) {
            String n = remoteUrlPossiblyWithRevision.substring(idx + 1);
            SVNRevision r = SVNRevision.parse(n);
            if ((r != null) && (r.isValid())) {
                return remoteUrlPossiblyWithRevision.substring(0, idx);
            }
        }
        return remoteUrlPossiblyWithRevision;
    }
}
