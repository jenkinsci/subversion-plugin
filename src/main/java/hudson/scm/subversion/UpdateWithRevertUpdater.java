/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
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
import hudson.scm.SubversionSCM.ModuleLocation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.io.IOException;

/**
 * {@link WorkspaceUpdater} that performs "svn revert" + "svn update"
 *
 * @author Kohsuke Kawaguchi
 */
public class UpdateWithRevertUpdater extends WorkspaceUpdater {
    private static final long serialVersionUID = 485917115133281105L;

    @DataBoundConstructor
    public UpdateWithRevertUpdater() {}

    @Override
    public UpdateTask createTask() {
        return new TaskImpl();
    }

    // mostly "svn update" plus extra
    public static class TaskImpl extends UpdateUpdater.TaskImpl {
        /**
         * 
         */
        private static final long serialVersionUID = -8562813147341259328L;

        @Override
        protected void preUpdate(ModuleLocation module, File local) throws SVNException, IOException {
            listener.getLogger().println("Reverting " + local + " to depth " + module.getDepthOption() + " with ignoreExternals: " + module.isIgnoreExternalsOption());
            final SVNWCClient svnwc = manager.getWCClient();
            svnwc.setIgnoreExternals(module.isIgnoreExternalsOption());
            svnwc.doRevert(new File[]{local.getCanonicalFile()}, module.getSvnDepthForRevert(), null);
        }
    }

    @Extension
    public static class DescriptorImpl extends WorkspaceUpdaterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.UpdateWithRevertUpdater_DisplayName();
        }
    }
}
