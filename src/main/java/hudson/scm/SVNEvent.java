package hudson.scm;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;

/**
 * User: schristou88
 * Date: 4/9/14
 * Time: 9:06 PM
 */
public class SVNEvent extends org.tmatesoft.svn.core.wc.SVNEvent {
    private SVNExternal previousExternalInfo;
    private SVNExternal externalInfo;


    public SVNEvent(File file,
                    SVNNodeKind kind,
                    String mimetype,
                    long revision,
                    SVNStatusType cstatus,
                    SVNStatusType pstatus,
                    SVNStatusType lstatus,
                    SVNLock lock,
                    SVNEventAction action,
                    SVNEventAction expected,
                    SVNErrorMessage error,
                    SVNMergeRange range,
                    String changelistName,
                    SVNProperties revisionProperties,
                    String propertyName) {
        super(file,kind, mimetype, revision, cstatus, pstatus, lstatus, lock, action, expected, error, range, changelistName, revisionProperties, propertyName);
    }

    public SVNExternal getExternalInfo() { return externalInfo;}
    public SVNExternal getPreviousExternalInfo() { return previousExternalInfo; }

    public SVNEvent setExternalInfo(SVNExternal prev, SVNExternal _new) {
        this.previousExternalInfo = prev;
        this.externalInfo = _new;
        return this;
    }
}
