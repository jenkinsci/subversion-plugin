
/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Fulvio Cavarretta,
 * Jean-Baptiste Quenot, Luca Domenico Milanesio, Renaud Bruyeron, Stephen Connolly,
 * Tom Huybrechts, Yahoo! Inc., Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy
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

import hudson.Extension;
import hudson.Util;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.External;
import hudson.util.IOException2;
import hudson.util.StreamCopyThread;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

/**
 * {@link WorkspaceUpdater} that does a fresh check out.
 *
 * @author Kohsuke Kawaguchi
 */
public class CheckoutUpdater extends WorkspaceUpdater {
    private static final long serialVersionUID = -3502075714024708011L;

    @DataBoundConstructor
    public CheckoutUpdater() {}

    public UpdateTask createTask() {
        return new UpdateTaskImpl();
    }
    protected static class UpdateTaskImpl extends UpdateTask {
        public List<External> perform() throws IOException, InterruptedException {
            final SVNUpdateClient svnuc = manager.getUpdateClient();
            final List<External> externals = new ArrayList<External>(); // store discovered externals to here

            cleanupBeforeCheckout();

            // buffer the output by a separate thread so that the update operation
            // won't be blocked by the remoting of the data
            PipedOutputStream pos = new PipedOutputStream();
            StreamCopyThread sct = null;
            if (listener != null) {
                sct = new StreamCopyThread("svn log copier", new PipedInputStream(pos),
                        listener.getLogger());
                sct.start();
            }
            SubversionSCM.ModuleLocation location = null;
            try {
                for (final SubversionSCM.ModuleLocation l : locations) {
                    location = l;
                    SVNDepth svnDepth = getSvnDepth(l.getDepthOption());
                    SVNRevision revision = getRevision(l);
                    if (listener != null) {
                        listener.getLogger().println("Checking out " + l.remote + " revision: " +
                                (revision != null ? revision.toString() : "null") + " depth:" + svnDepth +
                                " ignoreExternals: " + l.isIgnoreExternalsOption());
                    }
                    File local = new File(ws, l.getLocalDir());
                    svnuc.setIgnoreExternals(l.isIgnoreExternalsOption());
                    svnuc.setEventHandler(
                            new SubversionUpdateEventHandler(new PrintStream(pos), externals, local, l.getLocalDir()));
                    svnuc.doCheckout(l.getSVNURL(), local.getCanonicalFile(), SVNRevision.HEAD, revision,
                            svnDepth, true);
                }
            } catch (SVNException e) {
                //TODO find better solution than this workaround, svnkit uses the same exception and
                // the same error code in case of aborted builds and builds with invalid credentials
                if (e.getMessage() != null && e.getMessage().contains(SVN_CANCEL_EXCEPTION_MESSAGE)) {
                    listener.error("Svn command was aborted");
                    throw (InterruptedException) new InterruptedException().initCause(e);
                }
                e.printStackTrace(listener.error("Failed to check out " + location.remote));
                return null;
            } finally {
                try {
                    pos.close();
                } finally {
                    try {
                        if (sct != null) {
                            sct.join(); // wait for all data to be piped.
                        }
                    } catch (InterruptedException e) {
                        throw new IOException2("interrupted", e);
                    }
                }
            }

            return externals;
        }

        /**
         * Cleans workspace.
         *
         * @throws java.io.IOException IOException
         */
        protected void cleanupBeforeCheckout() throws IOException {
            if (listener != null && listener.getLogger() != null) {
                listener.getLogger().println("Cleaning workspace " + ws.getCanonicalPath());
            }
            Util.deleteContentsRecursive(ws);
        }
    }
    @Extension
    public static class DescriptorImpl extends WorkspaceUpdaterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.CheckoutUpdater_DisplayName();
        }
    }
}
