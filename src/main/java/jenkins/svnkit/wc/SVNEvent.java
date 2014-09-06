/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://lib.svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package jenkins.svnkit.wc;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;

/**
 *
 * See {@link org.tmatesoft.svn.core.wc.SVNEvent} for
 * more information.
 *
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     org.tmatesoft.svn.core.wc.ISVNEventHandler
 * @see     SVNStatusType
 * @see     SVNEventAction
 * @see     <a target="_top" href="http://lib.svnkit.com/kb/examples/">Examples</a>
 */
public class SVNEvent extends org.tmatesoft.svn.core.wc.SVNEvent {

  private SVNExternal previousEnternalInfo;
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
    super(file, kind, mimetype, revision, cstatus, pstatus, lstatus, lock, action, expected, error, range, changelistName, revisionProperties, propertyName);
  }


  public SVNExternal getPreviousEnternalInfo() {
    return previousEnternalInfo;
  }

  public SVNExternal getExternalInfo() {
    return externalInfo;
  }

  public SVNEvent setExternalInfo(SVNExternal prev, SVNExternal _new) {
    this.previousEnternalInfo = prev;
    this.externalInfo = _new;
    return this;
  }
}
