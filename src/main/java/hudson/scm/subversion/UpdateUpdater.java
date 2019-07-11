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

import hudson.AbortException;
import hudson.Extension;
import hudson.scm.SubversionSCM.External;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.triggers.SCMTrigger;
import jenkins.model.Jenkins;
import org.apache.commons.lang.time.FastDateFormat;
import org.kohsuke.stapler.DataBoundConstructor;
import org.tmatesoft.svn.core.*;
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
         * Returns whether we can do a "svn update" or a "svn switch" or a "svn checkout"
         */
        protected SvnCommandToUse getSvnCommandToUse() throws IOException {
            String moduleName = location.getLocalDir();
            File module = new File(ws, moduleName).getCanonicalFile(); // canonicalize to remove ".." and ".". See #474

            if (!module.exists()) {
                listener.getLogger().println("Checking out a fresh workspace because " + module + " doesn't exist");
                return SvnCommandToUse.CHECKOUT;
            }

            try {
                SVNInfo svnInfo = parseSvnInfo(module);

                String url = location.getSVNURL().toString();
                String wcUrl = svnInfo.getURL().toString();
                
                if (!wcUrl.equals(url)) {
                    if (isSameRepository(location, svnInfo)) {
                        listener.getLogger().println("Switching from " + wcUrl + " to " + url);
                        return SvnCommandToUse.SWITCH;
                    } else {
                        listener.getLogger().println("Checking out a fresh workspace because the workspace is not " + url);
                        return SvnCommandToUse.CHECKOUT;
                    }
                }
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                    listener.getLogger().println("Checking out a fresh workspace because there's no workspace at " + module);
                } else {
                    listener.getLogger().println("Checking out a fresh workspace because Jenkins failed to detect the current workspace " + module);
                    e.printStackTrace(listener.error(e.getMessage()));
                }
                return SvnCommandToUse.CHECKOUT;
            }
            return SvnCommandToUse.UPDATE;
        }

        private boolean isSameRepository(ModuleLocation location, SVNInfo svnkitInfo) throws SVNException {
            return location.getSVNURL().toString().startsWith(svnkitInfo.getRepositoryRootURL().toString());
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
            SvnCommandToUse svnCommand = getSvnCommandToUse();
            
            if (svnCommand == SvnCommandToUse.CHECKOUT) {
                return delegateTo(new CheckoutUpdater());
            }

            final SVNUpdateClient svnuc = clientManager.getUpdateClient();
            final List<External> externals = new ArrayList<External>(); // store discovered externals to here

            try {
                File local = new File(ws, location.getLocalDir());
                SubversionUpdateEventHandler eventHandler = new SubversionUpdateEventHandler(
                    listener.getLogger(), externals, local, location.getLocalDir(), quietOperation,
                    location.isCancelProcessOnExternalsFail());
                svnuc.setEventHandler(eventHandler);
                svnuc.setExternalsHandler(eventHandler);

                SVNRevision r = getRevision(location);

                String revisionName = r.getDate() != null ?
                		fmt.format(r.getDate()) : r.toString();
                
                svnuc.setIgnoreExternals(location.isIgnoreExternalsOption());
                preUpdate(location, local);
                SVNDepth svnDepth = location.getSvnDepthForUpdate();
                
                switch (svnCommand) {
                    case UPDATE:
                        listener.getLogger().println("Updating " + location.remote + " at revision "
                            + revisionName + (quietOperation ? " --quiet" : ""));
                        svnuc.doUpdate(local.getCanonicalFile(), r, svnDepth, true, true);
                        break;
                    case SWITCH:
                        listener.getLogger().println("Switching to " + location.remote + " at revision "
                            + revisionName + (quietOperation ? " --quiet" : ""));
                        svnuc.doSwitch(local.getCanonicalFile(), location.getSVNURL(), r, r, svnDepth, true, true, true);
                        break;
                    case CHECKOUT:
                        // This case is handled by the (svnCommand == SvnCommandToUse.CHECKOUT) above.
                        break;
                }
            } catch (SVNCancelException e) {
                e.printStackTrace(listener.getLogger());
                if (isAuthenticationFailedError(e)) {
                    throw new AbortException("Failed to check out " + location.remote);
                } else {
                    listener.error("Subversion update has been canceled");
                    throw (InterruptedException)new InterruptedException().initCause(e);
                }
            } catch (final SVNException e) {
                SVNException cause = e;
                do {
                    SVNErrorCode errorCode = cause.getErrorMessage().getErrorCode();
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
                        Jenkins instance = Jenkins.getInstance();
                        if (instance != null) {
                            listener.getLogger().println("Polled jobs are " + instance.getDescriptorByType(SCMTrigger.DescriptorImpl.class).getItemsBeingPolled());
                        }
                        return delegateTo(new CheckoutUpdater());
                    }

                  // recurse as long as we encounter nested SVNException
                } while (null != (cause = getNestedSVNException(cause)));

                e.printStackTrace(listener.error("Failed to update " + location.remote));
                listener.error("Subversion update failed");
                throw (IOException) new IOException().initCause(new UpdaterException("failed to perform svn update", e));
            }

            return externals;
        }

        /**
         * Retrieve nested SVNException.
         * svnkit use to hide the root cause within nested {@link SVNException}. Also, SVNException cause in many cases
         * is a {@link SVNErrorMessage}, that itself has a lower level SVNException as cause, and so on.
         */
        private SVNException getNestedSVNException(Throwable e) {
            Throwable t = e.getCause();
            if (t instanceof SVNException) return (SVNException) t;
            return null;
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
    
    private static enum SvnCommandToUse {
        UPDATE,
        SWITCH,
        CHECKOUT
    }
}
