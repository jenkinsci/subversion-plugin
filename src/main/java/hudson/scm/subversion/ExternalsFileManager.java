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
package hudson.scm.subversion;

import com.thoughtworks.xstream.XStream;
import hudson.XmlFile;
import hudson.model.AbstractProject;
import hudson.scm.SubversionSCM;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Implements local file storage of externals information.
 * Most of functionality has been copied from {@link SubversionSCM}. 
 * The class also prevents conflicts between 
 * @author Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 * @since TODO
 */
//TODO: synchronize data
public class ExternalsFileManager {

    private static final String SVN_EXTERNALS_FILE = "svnexternals.txt";
    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("external", SubversionSCM.External.class);
    }

    /**
     * Gets the file that stores the externals.
     */
    public static File getExternalsFile(AbstractProject project) {
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
    /*package*/ @SuppressWarnings("unchecked")
    public static List<SubversionSCM.External> parseExternalsFile(AbstractProject project) throws IOException {
        File file = getExternalsFile(project);
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

    public static void writeExternalsFile(AbstractProject project, List<SubversionSCM.External> externals) throws IOException {
        new XmlFile(XSTREAM, getExternalsFile(project)).write(externals);
    }
}
