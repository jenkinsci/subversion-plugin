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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNAdminAreaFactorySelector;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

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

    private int workspaceFormat;

    public SubversionWorkspaceSelector(int workspaceFormat) {
        this.workspaceFormat = workspaceFormat;
        // don't upgrade the workspace.
        SVNAdminAreaFactory.setUpgradeEnabled(false);
    }

    @SuppressWarnings({"cast", "unchecked", "rawtypes"})
    public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
        if(!writeAccess)    // for reading, use all our available factories
            return factories;

        // for writing, use the version the user has selected
        Collection<SVNAdminAreaFactory> enabledFactories = new ArrayList<>();
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
     * Constant for {@link #workspaceFormat} that indicates we opt for 1.7 working copy.
     *
     * <p>Internally in SVNKit, these constants go up only to 1.6. We use {@link #WC_FORMAT_17} to indicate
     * 1.7 (but when that value is chosen, it is really {@link SvnClientManager} that does the work, not
     * {@link ISVNAdminAreaFactorySelector}).
     *
     * @deprecated Use {@link org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb#WC_FORMAT_17}
     */
    public static final int WC_FORMAT_17 = ISVNWCDb.WC_FORMAT_17;

    /**
     * @deprecated Pre (non-inclusive) 2.5 the working copy format for 1.7 was 100, however
     * that has been changed to the official {@link ISVNWCDb#WC_FORMAT_17}.
     */
    public static final int OLD_WC_FORMAT_17 = 100;

}
