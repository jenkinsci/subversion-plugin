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

import hudson.Util;
import hudson.model.Run;
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;
import jenkins.util.xml.XMLUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * {@link ChangeLogParser} for Subversion.
 */
public class SubversionChangeLogParser extends ChangeLogParser {
    private static Logger LOGGER = Logger.getLogger(SubversionChangeLogParser.class.getName());
  
    private boolean ignoreDirPropChanges;

    @Deprecated
    public SubversionChangeLogParser() {
      this(false);
    }
    
    public SubversionChangeLogParser(boolean ignoreDirPropChanges) {
      this.ignoreDirPropChanges = ignoreDirPropChanges;
    }

    @Override public SubversionChangeLogSet parse(@SuppressWarnings("rawtypes") Run build, RepositoryBrowser<?> browser, File changelogFile) throws IOException, SAXException {
        // http://svn.apache.org/repos/asf/subversion/trunk/subversion/svn/schema/log.rnc

        ArrayList<LogEntry> r = new ArrayList<>();

        Element logE;
        try {
            logE = XMLUtils.parse(changelogFile, "UTF-8").getDocumentElement();
        } catch (IOException | SAXException e) {
            throw new IOException("Failed to parse " + changelogFile,e);
        }

        NodeList logNL = logE.getChildNodes();
        for (int i = 0; i < logNL.getLength(); i++) {
            if (logNL.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element logentryE = (Element) logNL.item(i);
            LogEntry e = new LogEntry();
            e.setRevision(Integer.parseInt(logentryE.getAttribute("revision")));
            NodeList logentryNL = logentryE.getChildNodes();
            for (int j = 0; j < logentryNL.getLength(); j++) {
                if (logentryNL.item(j).getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element otherE = (Element) logentryNL.item(j);
                String text = otherE.getTextContent();
                switch (otherE.getTagName()) {
                case "msg":
                    e.setMsg(text);
                    break;
                case "date":
                    e.setDate(text);
                    break;
                case "author":
                    e.setUser(text);
                    break;
                case "paths":
                    NodeList pathsNL = otherE.getChildNodes();
                    for (int k = 0; k < pathsNL.getLength(); k++) {
                        if (pathsNL.item(k).getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        }
                        Element pathE = (Element) pathsNL.item(k);
                        Path path = new Path();
                        path.setValue(pathE.getTextContent());
                        path.setAction(pathE.getAttribute("action"));
                        path.setLocalPath(Util.fixEmpty(pathE.getAttribute("localPath")));
                        path.setKind(Util.fixEmpty(pathE.getAttribute("kind")));
                        e.addPath(path);
                    }
                    break;
                /* If known to be exhaustive (above schema suggests not):
                default:
                    throw new IOException(otherE.getTagName());
                */
                }
            }
            r.add(e);
        }

        for (LogEntry e : r) {
            e.finish();
        }
        return new SubversionChangeLogSet(build, browser, r, ignoreDirPropChanges);
    }

}
