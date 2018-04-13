/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, David Seymore, Renaud Bruyeron, Yahoo! Inc.
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
package hudson.scm.subversion;

import hudson.scm.SubversionEventHandlerImpl;
import hudson.scm.SubversionSCM.External;
import java.util.HashMap;
import java.util.Map;

import jenkins.scm.impl.subversion.RemotableSVNErrorMessage;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNExternalsHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Just prints out the progress of svn update/checkout operation in a way similar to
 * the svn CLI.
 *
 * This code also records all the referenced external locations.
 */
final class SubversionUpdateEventHandler extends SubversionEventHandlerImpl implements ISVNExternalsHandler {
    /**
     * Staged map of svn:externals details.
     */
    private final Map<File, SVNExternalDetails> externalDetails = new HashMap<File, SVNExternalDetails>();
    /**
     * External urls that are fetched through svn:externals.
     * We add to this collection as we find them.
     */
    private final List<External> externals;
    /**
     * Relative path from the workspace root to the module root. 
     */
    private final String modulePath;

    /**
     * Flag to cancel the process when checkout/update svn:externals failed.
     */
    private final boolean cancelProcessOnExternalsFailed;

    /**
     * @deprecated Use {@link #SubversionUpdateEventHandler(PrintStream, List, File, String, boolean, boolean)}
     */
    @Deprecated
    public SubversionUpdateEventHandler(PrintStream out, List<External> externals, File moduleDir,
                                        String modulePath) {
        this(out, externals, moduleDir, modulePath, false, false);
    }

    /**
     * @deprecated Use {@link #SubversionUpdateEventHandler(PrintStream, List, File, String, boolean, boolean)}
     */
    @Deprecated
    public SubversionUpdateEventHandler(PrintStream out, List<External> externals, File moduleDir,
                                        String modulePath, boolean quietOperation) {
      this(out, externals, moduleDir, modulePath, quietOperation, false);
    }

    /**
     * @since 2.10
     */
    public SubversionUpdateEventHandler(PrintStream out, List<External> externals, File moduleDir,
                                        String modulePath, boolean quietOperation, boolean cancelProcessOnExternalsFailed) {
        super(out, moduleDir, quietOperation);
        this.externals = externals;
        this.modulePath = modulePath;
        this.cancelProcessOnExternalsFailed = cancelProcessOnExternalsFailed;
    }

    public SVNRevision[] handleExternal(File externalPath, SVNURL externalURL, SVNRevision externalRevision,
                                        SVNRevision externalPegRevision, String externalsDefinition,
                                        SVNRevision externalsWorkingRevision) {
        long revisionNumber = -1;
        if (SVNRevision.isValidRevisionNumber(externalRevision.getNumber())) {
            revisionNumber = externalRevision.getNumber();
        } else if (SVNRevision.isValidRevisionNumber(externalPegRevision.getNumber())) {
            revisionNumber = externalPegRevision.getNumber();
        }
        SVNExternalDetails details = new SVNExternalDetails(externalURL, revisionNumber);

        out.println("\n<-- Got one external: " + externalPath.getName() + ", svn url: " + details.getUrl() + " -->");
        externalDetails.put(externalPath, details);
        return new SVNRevision[] {externalRevision, externalPegRevision};
    }

    @Override
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        SVNEventAction action = event.getAction();
        if (action == SVNEventAction.UPDATE_EXTERNAL || action == SVNEventAction.UPDATE_COMPLETED) {
            File file = event.getFile();
            SVNExternalDetails details = externalDetails.remove(file);
            if (details != null) {
                String path;
                try {
                    path = getLocalPath(getRelativePath(file));
                } catch (IOException e) {
                    throw new SVNException(new RemotableSVNErrorMessage(SVNErrorCode.FS_GENERAL, e));
                }

                out.println(Messages.SubversionUpdateEventHandler_FetchExternal(details.getUrl(), event.getRevision(), file));
                externals.add(new External(modulePath + '/' + path, details.getUrl(), details.getRevision()));
            }
        } else if (action == SVNEventAction.FAILED_EXTERNAL) {
            File file = event.getFile();
            SVNExternalDetails details = externalDetails.get(file);
            if (details != null) {
              out.println(Messages.SubversionUpdateEventHandler_FetchExternal(details.getUrl(), event.getRevision(), file)
                  + " failed!");
            }

            if (cancelProcessOnExternalsFailed) {
              throw new SVNException(new RemotableSVNErrorMessage(SVNErrorCode.CL_ERROR_PROCESSING_EXTERNALS,
                  SVNErrorCode.CL_ERROR_PROCESSING_EXTERNALS.getDescription() + ": <" + file.getName() + ">"));
            }
        }

        super.handleEvent(event, progress);
    }

    public void checkCancelled() throws SVNCancelException {
        if(Thread.interrupted())
            throw new SVNCancelException();
    }

    private static class SVNExternalDetails {
        private final SVNURL url;
        private final long revision;

        private SVNExternalDetails(SVNURL url, long revision) {
            this.url = url;
            this.revision = revision;
        }

        public SVNURL getUrl() {
            return url;
        }

        public long getRevision() {
            return revision;
        }
    }
}
