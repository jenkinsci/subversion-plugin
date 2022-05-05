/*
 * The MIT License
 * 
 * Copyright (c) 2016, Sun Microsystems, Inc., Kohsuke Kawaguchi, Arnost Havelka
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SubversionChangeLogSet;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.scm.SubversionRepositoryBrowser;
import hudson.scm.subversion.Messages;


/**
 * {@link RepositoryBrowser} for Subversion.
 *
 * https://issues.jenkins-ci.org/browse/JENKINS-30176
 * 
 * @author Arnost Havelka
 * @since TODO
 */
public class VisualSVN extends SubversionRepositoryBrowser {

    private static final Logger LOGGER = Logger.getLogger(VisualSVN.class.getName());

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return Messages.SubversionSCM_browsers_VisualSVN();
        }
    }


    private static final long serialVersionUID = 1L;

    /**
     * The URL of the top of the site.
     *
     * <p>Normalized to ends with '/', like <code>https://demo-server.visualsvn.com/!/#tortoisesvn</code>
     * It may contain a query parameter like <code>?root=foobar</code>, so relative
     * URL construction needs to be done with care.</p>
     */
    private final String url;

    /**
     * Creates a new VisualSVN object.
     *
     * @param                url  base URL
     *
     * @throws               MalformedURLException  when URL is not valid (empty)
     */
    @DataBoundConstructor
    public VisualSVN(String url) throws MalformedURLException {
        this.url = validateUrl(url);
    }

    private String validateUrl(String url) throws MalformedURLException {
        String ret = url;
        if (StringUtils.isBlank(url)) {
            throw new MalformedURLException(Messages.SubversionSCM_doCheckRemote_invalidUrl());
        }

        new URL(url);

        if(ret.endsWith("/")) {
            ret = ret.substring(0, ret.length() - 1);
        }
        return ret;
    }

    /*
     * (non-Javadoc)
     * @see hudson.scm.SubversionRepositoryBrowser#getDiffLink(hudson.scm.SubversionChangeLogSet.Path)
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        if (path.getEditType() != EditType.EDIT) {
            return null; // no diff if this is not an edit change
        }

        // https://demo-server.visualsvn.com/!/#tortoisesvn/commit/r27333/head/trunk/src/Utils/MiscUI/SciEdit.cpp
        int r = path.getLogEntry().getRevision();
        String value = String.format("%s/commit/r%d/head%s", url, r, path.getValue());
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("DiffLink URL: " + value);
        }
        return new URL(value);
    }

    /*
     * (non-Javadoc)
     * @see hudson.scm.SubversionRepositoryBrowser#getFileLink(hudson.scm.SubversionChangeLogSet.Path)
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        // https://demo-server.visualsvn.com/!/#tortoisesvn/view/head/svnrobots.txt
        String value = String.format("%s/view/head%s", url, path.getValue());
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("FileLink URL: " + value);
        }
        return new URL(value);
    }

    /*
     * (non-Javadoc)
     * @see hudson.scm.RepositoryBrowser#getChangeSetLink(hudson.scm.ChangeLogSet.Entry)
     */
    @Override
    public URL getChangeSetLink(SubversionChangeLogSet.LogEntry changeSet)
                                   throws IOException {
        // https://demo-server.visualsvn.com/!/#tortoisesvn/commit/r27333
        String value = String.format("%s/commit/r%d", url, changeSet.getRevision());
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("ChangeSetLink URL: " + value);
        }
        return new URL(value);
    }

    /**
     * Getter for URL property.
     * @return URL value as {@code String}
     */
    public String getUrl()	{
        return url;
    }

}
