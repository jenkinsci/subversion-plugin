package hudson.scm.subversion;

import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Provides various helper methods.
 * 
 * @author kutzi
 */
public class SvnHelper {
    private static final String REGEX_END_SLASHES = "\\/*$";

    /**
     * Cuts off any optional '@revisionnr' and slashes from the end of the url string.
     */
    public static String getUrlWithoutRevision(String remoteUrlPossiblyWithRevision) {
        int idx = remoteUrlPossiblyWithRevision.lastIndexOf('@');
        int slashIdx = remoteUrlPossiblyWithRevision.lastIndexOf('/');
        
        // Substitute optional '@revisionnr'
        String substititedString = remoteUrlPossiblyWithRevision;
        if (idx > 0 && idx > slashIdx) {
            String n = remoteUrlPossiblyWithRevision.substring(idx + 1);
            SVNRevision r = SVNRevision.parse(n);
            if ((r != null) && (r.isValid())) {
                substititedString = remoteUrlPossiblyWithRevision.substring(0, idx);
            }
        }
        
        // Substitute slashes at the end
        substititedString = substititedString.replaceAll(REGEX_END_SLASHES, "");
        
        return substititedString;
    }
}
