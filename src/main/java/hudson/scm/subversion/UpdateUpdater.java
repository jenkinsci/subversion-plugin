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
import hudson.model.Hudson;
import hudson.scm.SubversionSCM.External;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.triggers.SCMTrigger;

import org.apache.commons.lang.time.FastDateFormat;
import org.kohsuke.stapler.DataBoundConstructor;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link WorkspaceUpdater} that uses "svn update" as much as possible.
 * 
 * @author Kohsuke Kawaguchi
 */
public class UpdateUpdater extends WorkspaceUpdater {
    private static final long serialVersionUID = 1451258464864424355L;

    
    private static final FastDateFormat fmt = FastDateFormat.getInstance("''yyyy-MM-dd'T'HH:mm:ss.SSS Z''");
    
    @DataBoundConstructor
    public UpdateUpdater() {
    }

    @Override
    public UpdateTask createTask() {
        return new TaskImpl();
    }

    public static class TaskImpl extends UpdateTask {
        /**
         * 
         */
        private static final long serialVersionUID = -5766470969352844330L;

        /**
         * Returns true if we can use "svn update" instead of "svn checkout"
         */
        protected boolean isUpdatable() throws IOException {
            String moduleName = location.getLocalDir();
            File module = new File(ws, moduleName).getCanonicalFile(); // canonicalize to remove ".." and ".". See #474

            if (!module.exists()) {
                listener.getLogger().println("Checking out a fresh workspace because " + module + " doesn't exist");
                return false;
            }

            try {
                SVNInfo svnkitInfo = parseSvnInfo(module);
                SvnInfo svnInfo = new SvnInfo(svnkitInfo);

                String url = location.getURL();
                if (!svnInfo.url.equals(url)) {
                    listener.getLogger().println("Checking out a fresh workspace because the workspace is not " + url);
                    return false;
                }
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                    listener.getLogger().println("Checking out a fresh workspace because there's no workspace at " + module);
                } else {
                    listener.getLogger().println("Checking out a fresh workspace because Jenkins failed to detect the current workspace " + module);
                    e.printStackTrace(listener.error(e.getMessage()));
                }
                return false;
            }
            return true;
        }

        /**
         * Gets the SVN metadata for the given local workspace.
         *
         * @param workspace
         *      The target to run "svn info".
         */
        private SVNInfo parseSvnInfo(File workspace) throws SVNException {
            final SVNWCClient svnWc = clientManager.getWCClient();
            return svnWc.doInfo(workspace,SVNRevision.WORKING);
        }

        @Override
        public List<External> perform() throws IOException, InterruptedException {
            if (!isUpdatable()) {
                return delegateTo(new CheckoutUpdater());
            }


            final SVNUpdateClient svnuc = clientManager.getUpdateClient();
            final List<External> externals = new ArrayList<External>(); // store discovered externals to here

            try {
                File local = new File(ws, location.getLocalDir());
                svnuc.setEventHandler(new SubversionUpdateEventHandler(listener.getLogger(), externals, local, location.getLocalDir()));

                SVNRevision r = getRevision(location);

                String revisionName = r.getDate() != null ?
                		fmt.format(r.getDate()) : r.toString();
                
                svnuc.setIgnoreExternals(location.isIgnoreExternalsOption());
                preUpdate(location, local);
                listener.getLogger().println("Updating " + location.remote + " at revision " + revisionName +
                    " to depth " + location.getDepthOption() + " and ignoring externals: " + location.isIgnoreExternalsOption());
                SVNDepth svnDepth = getSvnDepth(location.getDepthOption());
                svnuc.doUpdate(local.getCanonicalFile(), r, svnDepth, true, true);
            } catch (SVNCancelException e) {
                if (isAuthenticationFailedError(e)) {
                    e.printStackTrace(listener.error("Failed to check out " + location.remote));
                    return null;
                } else {
                    listener.error("Subversion update has been canceled");
                    throw (InterruptedException)new InterruptedException().initCause(e);
                }
            } catch (final SVNException e) {
                Throwable cause = e;
                do {
                    SVNErrorCode errorCode = ((SVNException)cause).getErrorMessage().getErrorCode();
                    if (errorCode == SVNErrorCode.WC_LOCKED) {
                        // work space locked. try fresh check out
                        listener.getLogger().println("Workspace appear to be locked, so getting a fresh workspace");
                        return delegateTo(new CheckoutUpdater());
                    }
                    if (errorCode == SVNErrorCode.WC_OBSTRUCTED_UPDATE) {
                        // HUDSON-1882. If existence of local files cause an update to fail,
                        // revert to fresh check out
                        listener.getLogger().println(e.getMessage()); // show why this happened. Sometimes this is caused by having a build artifact in the repository.
                        listener.getLogger().println("Updated failed due to local files. Getting a fresh workspace");
                        return delegateTo(new CheckoutUpdater());
                    }
                    if (errorCode == SVNErrorCode.WC_CORRUPT_TEXT_BASE || errorCode == SVNErrorCode.WC_CORRUPT || errorCode == SVNErrorCode.WC_UNWIND_EMPTY) {
                        // JENKINS-14550. if working copy is corrupted, revert to fresh check out
                        listener.getLogger().println(e.getMessage()); // show why this happened. Sometimes this is caused by having a build artifact in the repository.
                        listener.getLogger().println("Updated failed due to working copy corruption. Getting a fresh workspace");
                        return delegateTo(new CheckoutUpdater());
                    }
                    // trouble-shooting probe for #591
                    if (errorCode == SVNErrorCode.WC_NOT_LOCKED) {
                        listener.getLogger().println("Polled jobs are " + Hudson.getInstance().getDescriptorByType(SCMTrigger.DescriptorImpl.class).getItemsBeingPolled());
                    }

                  // recurse as long as we encounter nested SVNException
                } while ((cause = cause.getCause()) instanceof SVNException);

                e.printStackTrace(listener.error("Failed to update " + location.remote));
                listener.error("Subversion update failed");
                throw (IOException) new IOException().initCause(new UpdaterException("failed to perform svn update", e));
            }

            return externals;
        }

        /**
         * Hook for subtype to perform some cleanup activity before "svn update" takes place.
         *
         * @param module
         *      Remote repository that corresponds to the workspace.
         * @param local
         *      Local directory that gets the update from the module.
         */
        protected void preUpdate(ModuleLocation module, File local) throws SVNException, IOException {
            // noop by default
        }
    }

    @Extension(ordinal=100) // this is the default, so given a higher ordinal
    public static class DescriptorImpl extends WorkspaceUpdaterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.UpdateUpdater_DisplayName();
        }
    }
}
