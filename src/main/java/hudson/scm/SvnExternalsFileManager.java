/*
 * The MIT License
 * 
 * Copyright (c) 2004-2013, Sun Microsystems, Inc., Kohsuke Kawaguchi, Fulvio Cavarretta,
 * Jean-Baptiste Quenot, Luca Domenico Milanesio, Renaud Bruyeron, Stephen Connolly,
 * Tom Huybrechts, Yahoo! Inc., Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy, OHTAKE Tomohiro (original method implementations) 
 * Copyright (c) 2013, Synopsys Inc., Oleg Nenashev
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

import com.thoughtworks.xstream.XStream;
import hudson.XmlFile;
import hudson.model.Job;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;

/**
 * Implements local file storage of externals information.
 * Most of functionality has been copied from {@link SubversionSCM}. 
 * The class also prevents conflicts between read/write operations using
 * {@link SVN_EXTERNALS_FILE}.
 * @author Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 * @since TODO
 */
//TODO: This class should also handle MultipleSCMs (JENKINS-20450)
class SvnExternalsFileManager {
    private static final String SVN_EXTERNALS_FILE = "svnexternals.txt";
    private static final XStream XSTREAM = new XStream2();
    private static Map<Job, Object> projectExternalsCache;
    static {
        XSTREAM.alias("external", SubversionSCM.External.class);
    }

    /**
     * Provides a lock item for the project.
     * @param project Project to be used
     * @return A lock object (will be created on-demand)
     */
    @Nonnull
    private static synchronized Object getFileLockItem(Job project) {
        if (projectExternalsCache == null) {
            projectExternalsCache = new WeakHashMap<Job, Object>();
        }
                
        Object item = projectExternalsCache.get(project);
        if (item == null) {
            item = new Object();
            projectExternalsCache.put(project, item);
        }
        return item;
    }
    
    /**
     * Gets the file that stores the externals.
     */
    @Nonnull
    private static File getExternalsFile(Job project) {
        return new File(project.getRootDir(), SVN_EXTERNALS_FILE);
    }

    /**
     * Parses the file that stores the locations in the workspace where modules
     * loaded by svn:external is placed.
     *
     * <p>
     * Note that the format of the file has changed in 1.180 from simple text
     * file to XML.
     *
     * @return immutable list. Can be empty but never null.
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public static List<SubversionSCM.External> parseExternalsFile(Job project) throws IOException {
        File file = getExternalsFile(project);
        Object lock = getFileLockItem(project);
        
        synchronized(lock) {
            if (file.exists()) {
                try {
                    return (List<SubversionSCM.External>) new XmlFile(XSTREAM, file).read();
                } catch (IOException e) {
                    // in < 1.180 this file was a text file, so it may fail to parse as XML,
                    // in which case let's just fall back
                }
            }
            return Collections.emptyList();
        }
    }

    /**
     * Writes a list of externals to the file.
     * @param project Project, which uses provided externals.
     * @param externals List of externals
     * @throws IOException File write error
     */
    public static void writeExternalsFile(Job project, List<SubversionSCM.External> externals) throws IOException {
        Object lock = getFileLockItem(project);
        
        synchronized (lock) {
            new XmlFile(XSTREAM, getExternalsFile(project)).write(externals);
        }
    }
}
