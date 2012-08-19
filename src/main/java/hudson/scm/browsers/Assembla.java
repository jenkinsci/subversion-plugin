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
package hudson.scm.browsers;

import hudson.model.Descriptor;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SubversionChangeLogSet;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.scm.SubversionRepositoryBrowser;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * {@link RepositoryBrowser} for Subversion.
 * 
 * @author Shay Erlichmen
 * @since 1.90
 */
// See http://viewvc.tigris.org/source/browse/*checkout*/viewvc/trunk/docs/url-reference.html
public class Assembla extends SubversionRepositoryBrowser {
    public final String spaceName;

    @DataBoundConstructor
    public Assembla(String spaceName) throws MalformedURLException {
        this.spaceName = spaceName;
    }

    private URL getAssemblaBasePath(String path) throws IOException {
    	String fullPath = String.format("https://www.assembla.com/code/%s/subversion/%s/", this.spaceName, path);
    	return new URL(fullPath);
    }
    
    @Override
    public URL getDiffLink(Path path) throws IOException {
        if (path.getEditType() != EditType.EDIT) {
            return null;    // no diff if this is not an edit change
        }
        
        int revision = path.getLogEntry().getRevision();        
        
        String command = String.format("node/diff/%d/%d", revision, revision-1);
        
        String pathWithoutSlash = trimHeadSlash(path.getValue());
        
        return new URL(getAssemblaBasePath(command), pathWithoutSlash + params());
    }

    @Override
    public URL getFileLink(Path path) throws IOException {
        int revision = path.getLogEntry().getRevision();
        
        String pathWithoutSlash = trimHeadSlash(path.getValue());
        
        String command = pathWithoutSlash + params().add("rev=" + revision);
        
        return new URL(getAssemblaBasePath("nodes"), command);
    }
    
    @Override
    public URL getChangeSetLink(SubversionChangeLogSet.LogEntry changeSet) throws IOException {
    	int revision = changeSet.getRevision();
    	String path = String.format("changesets/%d", revision);
    	return getAssemblaBasePath(path);
    }

    private QueryBuilder params() {
        return new QueryBuilder("");
    }
    
    private static final long serialVersionUID = 1L;

    @Extension
    public static final class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "Assembla";
        }
    }
}
