/*
 * The MIT License
 * 
 * Copyright (c) 2015 Michael Schuele.
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
package hudson.scm.browsers;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.scm.SubversionChangeLogSet;
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.scm.SubversionRepositoryBrowser;
import hudson.scm.SubversionSCM;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import static java.util.logging.Level.FINER;
import jenkins.triggers.SCMTriggerItem;
import org.kohsuke.stapler.DataBoundConstructor;

/*
 * RepositoryBrowser for TortoiseSVN installed on the client
 *  http://tortoisesvn.net/docs/release/TortoiseSVN_en/tsvn-automation-urlhandler.html
 *
 */
public class TortoiseSVN extends SubversionRepositoryBrowser {
      
    private final int DEFAULT_FILE_HISTORY_REVISIONS = 100;
    
    @DataBoundConstructor
    public TortoiseSVN()
    {}
    
    @Override
    public URL getDiffLink(SubversionChangeLogSet.Path path) throws IOException {
        if (path.getEditType() != EditType.EDIT)
            return null;
        final int revision = path.getLogEntry().getRevision();
        final String remoteRepository = getRepositoryPath(path.getLogEntry(), path.getPath());
        if (remoteRepository == null)
            return null;
        final String fullPath = normalizeToEndWithSlash(remoteRepository) + trimHeadSlash(path.getValue()); 
        return getTortoiseUrl(String.format("diff?path:%s?startrev:%d?endrev:%d", fullPath, revision-1, revision)); 
    }
    
    @Override
    public URL getFileLink(SubversionChangeLogSet.Path path) throws IOException {
        
        final int revision = path.getLogEntry().getRevision();
        final int firstRevision = revision > DEFAULT_FILE_HISTORY_REVISIONS ? revision - DEFAULT_FILE_HISTORY_REVISIONS : 0;
        final String remoteRepository = getRepositoryPath(path.getLogEntry(), path.getPath());
        if (remoteRepository == null)
            return null;
        final String fullPath = normalizeToEndWithSlash(remoteRepository) + trimHeadSlash(path.getValue());
        return getTortoiseUrl(String.format("log?path:%s?startrev:%d?endrev:%d", fullPath, firstRevision,revision));
    }

    @Override
    public URL getChangeSetLink(SubversionChangeLogSet.LogEntry e) throws IOException {
        
        if (e.getPaths().isEmpty())
            return null;
        final int revision = e.getRevision();
        
        final Path path = e.getPaths().get(0);
        final String remoteRepository = getRepositoryPath(e, path.getPath());
        if (remoteRepository == null)
            return null;
        return getTortoiseUrl(String.format("log?path:%s?startrev:%d?endrev:%d", remoteRepository, revision, revision));
    }
    
    /*
    * The format of the tsvncmd: URL is like this:
    * 
    * tsvncmd:command:cmd?parameter:paramvalue?parameter:paramvalue 
    * 
    * with cmd being one of the allowed commands, parameter being the name of a parameter like path or revision, and paramvalue being the value to use for that parameter. The list of parameters allowed depends on the command used.
    *
    *   The following commands are allowed with tsvncmd: URLs:
    *   :update
    *   :commit
    *   :diff
    *   :repobrowser
    *   :checkout
    *   :export
    *   :blame
    *   :repostatus
    *   :revisiongraph
    *   :showcompare
    *   :log
    *
    *  A simple example URL might look like this:
    *
    * <a href="tsvncmd:command:update?path:c:\svn_wc?rev:1234">Update</a>
    */
    private URL getTortoiseUrl(String commandWithParameters) throws IOException
    {
        return new URL("tsvncmd",
                null,
                -1,
                String.format("command:%s", commandWithParameters),
                new URLStreamHandler() { // only required to satisfy URL constructor implementation
                    @Override
                    protected URLConnection openConnection(URL u) throws IOException {
                        return new URLConnection(u) {
                            public void connect() throws IOException {
                                // nothing
                            }
                        };
                    }
                }
            );
    }
    
    private String getRepositoryPath(LogEntry logEntry, String workspacePath) {
        final Job job = logEntry.getParent().getRun().getParent();
        final SCMTriggerItem s = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);
        if (s == null) {
            return null;
        }
        for (SCM scm : s.getSCMs()) {
            if (!(scm instanceof SubversionSCM)) {
                continue;
            }
            final SubversionSCM.ModuleLocation[] locations = ((SubversionSCM)scm).getLocations();
            for (SubversionSCM.ModuleLocation location : locations) {
                if (hasSameDirectoryPath(workspacePath, location.getLocalDir())) {
                    return location.getURL();
                }
            }
        }
        if (LOGGER.isLoggable(FINER)) {
            LOGGER.finer("can't find a corresponding subversion URL for workspace path " + workspacePath);
        }
        return null;
    }
    
    private static String normalizeToEndWithSlash(String url) {
        if(url.endsWith("/")) {
            return url;
        }
        return url + "/";
    }
    
    private boolean hasSameDirectoryPath(String filePath, String directoryPath)
    {
        if (filePath == null || directoryPath == null)
            return false;
        filePath = trimHeadSlash(filePath);
        directoryPath = trimHeadSlash(directoryPath);
        int lastSlash = filePath.lastIndexOf("/");
        if (lastSlash == -1)
            return true;
        filePath = filePath.substring(0, lastSlash);
        if (filePath.isEmpty())
            return true;
        
        return filePath.startsWith(directoryPath);
    }

    private static final long serialVersionUID = 1L;
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(TortoiseSVN.class.getName());
    
    @Extension
    public static final class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "TortoiseSVN";
        }
    }
}
