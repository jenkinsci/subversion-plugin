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
import hudson.scm.SubversionSCM.External;
import hudson.util.IOException2;
import hudson.util.StreamCopyThread;
import org.kohsuke.stapler.DataBoundConstructor;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link WorkspaceUpdater} that does a fresh check out.
 *
 * @author Kohsuke Kawaguchi
 */
public class CheckoutUpdater extends WorkspaceUpdater {
    private static final long serialVersionUID = -3502075714024708011L;

    @DataBoundConstructor
    public CheckoutUpdater() {}

    @Override
    public UpdateTask createTask() {
        return new UpdateTask() {
            private static final long serialVersionUID = 8349986526712487762L;

            @Override
            public List<External> perform() throws IOException, InterruptedException {
                final SVNUpdateClient svnuc = clientManager.getUpdateClient();
                final List<External> externals = new ArrayList<External>(); // store discovered externals to here

                listener.getLogger().println("Cleaning local Directory " + location.getLocalDir());
                Util.deleteContentsRecursive(new File(ws, location.getLocalDir()));

                // buffer the output by a separate thread so that the update operation
                // won't be blocked by the remoting of the data
                PipedOutputStream pos = new PipedOutputStream();
                StreamCopyThread sct = new StreamCopyThread("svn log copier", new PipedInputStream(pos), listener.getLogger());
                sct.start();

                try {
                    listener.getLogger().println("Checking out " + location.remote);

                    File local = new File(ws, location.getLocalDir());
                    svnuc.setEventHandler(new SubversionUpdateEventHandler(new PrintStream(pos), externals, local, location.getLocalDir()));
                    svnuc.doCheckout(location.getSVNURL(), local.getCanonicalFile(), SVNRevision.HEAD, getRevision(location), SVNDepth.INFINITY, true);
                } catch (SVNCancelException e) {
                    listener.error("Subversion checkout has been canceled");
                    throw (InterruptedException)new InterruptedException().initCause(e);
                } catch (SVNException e) {
                    e.printStackTrace(listener.error("Failed to check out " + location.remote));
                    return null;
                } finally {
                    try {
                        pos.close();
                    } finally {
                        try {
                            sct.join(); // wait for all data to be piped.
                        } catch (InterruptedException e) {
                            throw new IOException2("interrupted", e);
                        }
                    }
                }

                return externals;
            }
        };
    }

    @Extension
    public static class DescriptorImpl extends WorkspaceUpdaterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.CheckoutUpdater_DisplayName();
        }
    }
}
