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

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.TaskListener;
import hudson.scm.RevisionParameterAction;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.External;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.SvnClientManager;
import org.kohsuke.stapler.export.ExportedBean;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * Encapsulates the logic of how files are obtained from a subversion repository.
 *
 * <p>
 * {@link WorkspaceUpdater} serves as a {@link Describable}, created from the UI via databinding and
 * encapsulates whatever configuration parameter. The checkout logic is in {@link UpdateTask}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.23
 */
@ExportedBean
public abstract class WorkspaceUpdater extends AbstractDescribableImpl<WorkspaceUpdater> implements ExtensionPoint, Serializable {
    private static final long serialVersionUID = 8902811304319899817L;

    /**
     * Creates the {@link UpdateTask} instance, which performs the actual check out / update.
     */
    public abstract UpdateTask createTask();

    @Override
    public WorkspaceUpdaterDescriptor getDescriptor() {
        return (WorkspaceUpdaterDescriptor)super.getDescriptor();
    }
    
    protected static boolean isAuthenticationFailedError(SVNCancelException e) {
        // this is very ugly. SVNKit (1.7.4 at least) reports missing authentication data as a cancel exception
        // "No credential to try. Authentication failed"
        // See DefaultSVNAuthenticationManager#getFirstAuthentication
        if (String.valueOf(e.getMessage()).contains("No credential to try") ||
                String.valueOf(e.getMessage()).contains("authentication cancelled")) {
            return true;
        }
        Throwable cause = e.getCause();
        if (cause instanceof SVNCancelException) {
            return isAuthenticationFailedError((SVNCancelException) cause);
        } else {
            return false;
        }
    }

    /**
     * This object gets instantiated on the master and then sent to the slave via remoting,
     * then used to {@linkplain #perform() perform the actual checkout activity}.
     *
     * <p>
     * A number of contextual objects are defined as fields, to be used by the {@link #perform()} method.
     * These fields are set by {@link SubversionSCM} before the invocation.
     */
    public static abstract class UpdateTask implements Serializable {
        // fields that are set by the caller as context for the perform method

        /**
         * @deprecated as of 1.40
         *      Use {@link #clientManager}
         */
        public SVNClientManager manager;

        /**
         * Factory for various subversion commands.
         */
        public SvnClientManager clientManager;

        /**
         * Encapusulates the authentication. Connected back to Hudson master. Never null.
         */
        public ISVNAuthenticationProvider authProvider;

        /**
         * In the absence of a revision-specific check out, we want to check out by this timestamp,
         * not just the latest to ensure consistency. Never null.
         */
        public Date timestamp;

        /**
         * Connected to build console. Never null.
         */
        public TaskListener listener;

        /**
         * Modules to check out. Never null.
         */
        public ModuleLocation location;

        /**
         * Build workspace. Never null.
         */
        public File ws;

        /**
         * --quiet for subversion operations. Default = false.
         */
        public boolean quietOperation;

        /**
         * If the build parameter is specified with specific version numbers, this field captures that. Can be null.
         */
        public RevisionParameterAction revisions;

        /**
         * Performs the checkout/update.
         *
         * <p>
         * Use the fields defined in this class that defines the parameters of the check out.
         *
         * @return
         *      Where svn:external mounting happened. Can be empty but never null.
         */
        public abstract List<External> perform() throws IOException, InterruptedException;

        protected List<External> delegateTo(UpdateTask t) throws IOException, InterruptedException {
            t.manager = this.manager;
            t.clientManager = this.clientManager;
            t.authProvider = this.authProvider;
            t.timestamp = this.timestamp;
            t.listener = this.listener;
            t.location = this.location;
            t.revisions = this.revisions;
            t.ws = this.ws;
            t.quietOperation = this.quietOperation;

            return t.perform();
        }

        /**
         * Delegates the execution to another updater. This is most often useful to fall back to the fresh check out
         * by using {@link CheckoutUpdater}.
         */
        protected final List<External> delegateTo(WorkspaceUpdater wu) throws IOException, InterruptedException {
            return delegateTo(wu.createTask());
        }

        /**
         * Determines the revision to check out for the given location.
         */
        protected SVNRevision getRevision(ModuleLocation l) {
            // for the SVN revision, we will use the first off:
            // - a @NNN suffix of the SVN url
            // - a value found in a RevisionParameterAction
            // - the revision corresponding to the build timestamp

            SVNRevision r = null;
            if (revisions != null) {
                r = revisions.getRevision(l.getURL());
            }
            if (r == null) {
                r = SVNRevision.create(timestamp);
            }
            r = l.getRevision(r);
            return r;
        }

        /**
         * Returns {@link org.tmatesoft.svn.core.SVNDepth} by string value.
         *
         * @return {@link org.tmatesoft.svn.core.SVNDepth} value.
         *
         * @deprecated as of 2.10
         *      Use SubversionSCM.ModuleLocation.getSVNDepthFor* functions to correctly interpret
         *      module location depth options or SVNDepth.fromString directly if you've taken the
         *      depth from another source.
         */
        @Deprecated
        protected static SVNDepth getSvnDepth(String name) {
            return SVNDepth.fromString(name);
        }

        private static final long serialVersionUID = 1L;
    }
}
