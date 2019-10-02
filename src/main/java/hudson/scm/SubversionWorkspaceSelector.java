/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.remoting.Channel;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNAdminAreaFactorySelector;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.SlaveToMasterCallable;

/**
 * {@link ISVNAdminAreaFactorySelector} that uses 1.4 compatible workspace for new check out,
 * but still supports 1.5 workspace, if asked to work with it.
 *
 * <p>
 * Since there are many tools out there that still don't support Subversion 1.5 (including
 * all the major Unix distributions that haven't bundled Subversion 1.5), using 1.4 as the
 * default would reduce the likelihood of the user running into "this SVN client can't work
 * with this workspace version..." problem when using other SVN tools.
 *
 * <p>
 * The primary scenario of this is the use of command-line SVN client, either from shell
 * script, Ant, or Maven.
 *
 * <p>
 * Working copy changes from Subversion 1.6 to 1.7 was so big that they introduced a separate
 * {@link SvnWcGeneration} constant to represent that. So this class alone is no longer sufficient
 * to make SVNKit sticks to the version we want it to use. See {@link SvnClientManager} that
 * controls the other half of this.
 *
 * @author Kohsuke Kawaguchi
 * @see SvnClientManager
 */
public class SubversionWorkspaceSelector implements ISVNAdminAreaFactorySelector {
    public SubversionWorkspaceSelector() {
        // don't upgrade the workspace.
        SVNAdminAreaFactory.setUpgradeEnabled(false);
    }

    @SuppressWarnings({"cast", "unchecked", "rawtypes"})
    public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
        if(!writeAccess)    // for reading, use all our available factories
            return factories;

        // for writing, use the version the user has selected
        Collection<SVNAdminAreaFactory> enabledFactories = new ArrayList<SVNAdminAreaFactory>();
        for (SVNAdminAreaFactory factory : (Collection<SVNAdminAreaFactory>)factories)
            if (factory.getSupportedVersion() == workspaceFormat)
                enabledFactories.add(factory);

        if (enabledFactories.isEmpty() && workspaceFormat!=SVNAdminArea14.WC_FORMAT) {
            // if the workspaceFormat value is invalid, fall back to 1.4
            workspaceFormat = SVNAdminArea14.WC_FORMAT;
            return getEnabledFactories(path,factories,writeAccess);
        }

        return enabledFactories;
    }

    /**
     * {@link #getEnabledFactories(File, Collection, boolean)} method is called quite a few times
     * during a Subversion operation, so consulting this value back with master each time is not practical
     * performance wise. Therefore, we have {@link SubversionSCM} set this value, even though it's error prone.
     *
     * <p>
     * Internally in SVNKit, these constants go up only to 1.6. We use {@link #WC_FORMAT_17} to indicate
     * 1.7 (but when that value is chosen, it is really {@link SvnClientManager} that does the work, not
     * {@link ISVNAdminAreaFactorySelector}).
     */
    public static volatile int workspaceFormat = SVNAdminArea14.WC_FORMAT;

    public static void syncWorkspaceFormatFromMaster() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j!=null)
            workspaceFormat = j.getDescriptorByType(SubversionSCM.DescriptorImpl.class).getWorkspaceFormat();
        else {
            Channel c = Channel.current();
            if (c!=null)    // just being defensive. cannot be null.
                try {
                    workspaceFormat = c.call(new GetWorkspaceFormatSlaveToMasterCallable());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to retrieve Subversion workspace format",e);
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Failed to retrieve Subversion workspace format",e);
                }
        }
    }

    /**
     * Constant for {@link #workspaceFormat} that indicates we opt for 1.7 working copy.
     *
     * @deprecated Use {@link org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb#WC_FORMAT_17}
     */
    public static final int WC_FORMAT_17 = ISVNWCDb.WC_FORMAT_17;

    /**
     * @deprecated Pre (non-inclusive) 2.5 the working copy format for 1.7 was 100, however
     * that has been changed to the official {@link ISVNWCDb#WC_FORMAT_17}.
     */
    public static final int OLD_WC_FORMAT_17 = 100;

    private static final Logger LOGGER = Logger.getLogger(SubversionWorkspaceSelector.class.getName());

    private static class GetWorkspaceFormatSlaveToMasterCallable extends SlaveToMasterCallable<Integer, RuntimeException> {  // TODO JENKINS-48543 bad design
        private static final long serialVersionUID = 6494337549896104453L;

        public Integer call()  {
            return Jenkins.getInstance().getDescriptorByType(SubversionSCM.DescriptorImpl.class).getWorkspaceFormat();
        }
    }
}
