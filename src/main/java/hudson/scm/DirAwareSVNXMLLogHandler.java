/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package hudson.scm;

import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.xml.SVNXMLLogHandler;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Special XML Log Handler that includes the "kind" attribute for path nodes which are ommited by the original.
 * 
 *  This is a lot of copy paste code
 * @author qxa4177
 */
public class DirAwareSVNXMLLogHandler extends SVNXMLLogHandler implements ISVNLogEntryHandler {
  
  public static final String KIND_ATTR = "kind";
  
  private boolean myIsOmitLogMessage;

  private LinkedList<MergeFrame> myMergeStack;
  
  private String relativeDir;
  
  public DirAwareSVNXMLLogHandler(ContentHandler contentHandler, ISVNDebugLog log) {
    super(contentHandler, log);
  }

  public DirAwareSVNXMLLogHandler(ContentHandler contentHandler) {
    super(contentHandler);
  }
  
  public void setRelativeDir(String relDir) {
	  this.relativeDir = relDir;
  }
  
  /**
   * Sets whether log messages must be omitted or not.
   * 
   * @param omitLogMessage  <span class="javakeyword">true</span> to omit; 
   *                        otherwise <span class="javakeyword">false</span> 
   */
  public void setOmitLogMessage(boolean omitLogMessage) {
      myIsOmitLogMessage = omitLogMessage;
  }

  /**
   * Handles a next log entry producing corresponding xml.
   * 
   * @param  logEntry       log entry 
   * @throws SVNException 
   */
  public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
      try {
          sendToHandler(logEntry);
      } catch (SAXException e) {
          SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, e.getLocalizedMessage());
          SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
      }
  }

  // unfortunately, the original method is private, so we need to copy / paste it
  // copied from SVNXMLLogHandler
  //
  protected void sendToHandler(SVNLogEntry logEntry) throws SAXException {
    if (logEntry.getRevision() == 0 && logEntry.getMessage() == null) {
        return;
    }
    addAttribute(REVISION_ATTR, logEntry.getRevision() + "");
    openTag(LOGENTRY_TAG);
    if (logEntry.getAuthor() != null) {
        addTag(AUTHOR_TAG, logEntry.getAuthor());
    }
    if (logEntry.getDate() != null && logEntry.getDate().getTime() != 0) {
        addTag(DATE_TAG, SVNDate.formatDate(logEntry.getDate()));
    }
    if (logEntry.getChangedPaths() != null && !logEntry.getChangedPaths().isEmpty()) {
        openTag(PATHS_TAG);
        for (Iterator<String> paths = logEntry.getChangedPaths().keySet().iterator(); paths.hasNext();) {
            String key = paths.next();
            SVNLogEntryPath path = (SVNLogEntryPath) logEntry.getChangedPaths().get(key);
            if (relativeDir != null && path.getPath().startsWith(relativeDir)) {
                path.setPath(path.getPath().substring(relativeDir.length() + 1)); // Also remove the '/'
            }
            addAttribute(ACTION_ATTR, path.getType() + "");
            if (path.getCopyPath() != null) {
                addAttribute(COPYFROM_PATH_ATTR, path.getCopyPath());
                addAttribute(COPYFROM_REV_ATTR, path.getCopyRevision() + "");
            } 
            if (path.getKind() != null) {
                addAttribute(KIND_ATTR, path.getKind().toString());
            }
            addTag(PATH_TAG, path.getPath());
        }
        closeTag(PATHS_TAG);
    }
    
    if (!myIsOmitLogMessage) {
        String message = logEntry.getMessage();
        message = message == null ? "" : message;
        addTag(MSG_TAG, message);
    }
    
    if (myMergeStack != null && !myMergeStack.isEmpty()) {
        MergeFrame frame = (MergeFrame) myMergeStack.getLast();
        frame.myNumberOfChildrenRemaining--;
    }
    
    if (logEntry.hasChildren()) {
        MergeFrame frame = new MergeFrame();
        if (myMergeStack == null) {
            myMergeStack = new LinkedList<MergeFrame>();
        }
        myMergeStack.addLast(frame);
    } else {
        while(myMergeStack != null && !myMergeStack.isEmpty()) {
            MergeFrame frame = (MergeFrame) myMergeStack.getLast();
            if (frame.myNumberOfChildrenRemaining == 0) {
                closeTag(LOGENTRY_TAG);
                myMergeStack.removeLast();
            } else {
                break;
            }
        }
        closeTag(LOGENTRY_TAG);
    }
  }
  private class MergeFrame {
    private long myNumberOfChildrenRemaining;
  }

}
