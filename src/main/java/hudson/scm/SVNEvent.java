/*
 * The MIT License
 * 
 * Copyright (c) 2014 schristou88
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.scm;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;

/**
 * @author schristou88
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
