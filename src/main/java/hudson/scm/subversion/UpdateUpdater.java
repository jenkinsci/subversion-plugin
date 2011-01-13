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
import org.kohsuke.stapler.DataBoundConstructor;
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
    @DataBoundConstructor
    public UpdateUpdater() {
    }

    @Override
    public UpdateTask createTask() {
        return new TaskImpl();
    }

    public static class TaskImpl extends UpdateTask {
        /**
         * Returns true if we can use "svn update" instead of "svn checkout"
         */
        protected boolean isUpdatable() throws IOException {
            for (ModuleLocation l : locations) {
                String moduleName = l.getLocalDir();
                File module = new File(ws,moduleName).getCanonicalFile(); // canonicalize to remove ".." and ".". See #474

                if(!module.exists()) {
                    listener.getLogger().println("Checking out a fresh workspace because "+module+" doesn't exist");
                    return false;
                }

                try {
                    SVNInfo svnkitInfo = parseSvnInfo(module);
                    SvnInfo svnInfo = new SvnInfo(svnkitInfo);

                    String url = l.getURL();
                    if(!svnInfo.url.equals(url)) {
                        listener.getLogger().println("Checking out a fresh workspace because the workspace is not "+url);
                        return false;
                    }
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode()==SVNErrorCode.WC_NOT_DIRECTORY) {
                        listener.getLogger().println("Checking out a fresh workspace because there's no workspace at "+module);
                    } else {
                        listener.getLogger().println("Checking out a fresh workspace because Hudson failed to detect the current workspace "+module);
                        e.printStackTrace(listener.error(e.getMessage()));
                    }
                    return false;
                }
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
            final SVNWCClient svnWc = manager.getWCClient();
            return svnWc.doInfo(workspace,SVNRevision.WORKING);
        }

        @Override
        public List<External> perform() throws IOException, InterruptedException {
            if (!isUpdatable())
                return delegateTo(new CheckoutUpdater());


            final SVNUpdateClient svnuc = manager.getUpdateClient();
            final List<External> externals = new ArrayList<External>(); // store discovered externals to here

            for (final ModuleLocation l : locations) {
                try {
                    File local = new File(ws, l.getLocalDir());
                    svnuc.setEventHandler(new SubversionUpdateEventHandler(listener.getLogger(), externals, local, l.getLocalDir()));

                    SVNRevision r = getRevision(l);

                    preUpdate(l, local);
                    listener.getLogger().println("Updating " + l.remote);
                    svnuc.doUpdate(local.getCanonicalFile(), r, SVNDepth.INFINITY, true, false);

                } catch (final SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LOCKED) {
                        // work space locked. try fresh check out
                        listener.getLogger().println("Workspace appear to be locked, so getting a fresh workspace");
                        return delegateTo(new CheckoutUpdater());
                    }
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_OBSTRUCTED_UPDATE) {
                        // HUDSON-1882. If existence of local files cause an update to fail,
                        // revert to fresh check out
                        listener.getLogger().println(e.getMessage()); // show why this happened. Sometimes this is caused by having a build artifact in the repository.
                        listener.getLogger().println("Updated failed due to local files. Getting a fresh workspace");
                        return delegateTo(new CheckoutUpdater());
                    }

                    e.printStackTrace(listener.error("Failed to update " + l.remote));
                    // trouble-shooting probe for #591
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                        listener.getLogger().println("Polled jobs are " + Hudson.getInstance().getDescriptorByType(SCMTrigger.DescriptorImpl.class).getItemsBeingPolled());
                    }
                    return null;
                }
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
