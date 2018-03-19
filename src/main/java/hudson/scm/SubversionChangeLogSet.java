/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt
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
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.User;
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionSCM.ModuleLocation;

import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.triggers.SCMTriggerItem;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.tmatesoft.svn.core.internal.util.SVNDate;

/**
 * {@link ChangeLogSet} for Subversion.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SubversionChangeLogSet extends ChangeLogSet<LogEntry> {
    private final List<LogEntry> logs;

    /**
     * @GuardedBy this
     */
    private Map<String,Long> revisionMap;

    private boolean ignoreDirPropChanges;
    
    @Deprecated
    /*package*/ SubversionChangeLogSet(AbstractBuild<?,?> build, List<LogEntry> logs) {
      this(build, build.getProject().getScm().getEffectiveBrowser(), logs, false);
    }

    /*package*/ SubversionChangeLogSet(Run<?,?> build, RepositoryBrowser<?> browser, List<LogEntry> logs, boolean ignoreDirPropChanges) {
        super(build, browser);
        this.ignoreDirPropChanges = ignoreDirPropChanges;
        this.logs = prepareChangeLogEntries(logs);
    }

    public boolean isEmptySet() {
        return logs.isEmpty();
    }

    public List<LogEntry> getLogs() {
        return logs;
    }


    public Iterator<LogEntry> iterator() {
        return logs.iterator();
    }

    @Override
    public String getKind() {
        return "svn";
    }

    public synchronized Map<String,Long> getRevisionMap() throws IOException {
        if(revisionMap==null)
            revisionMap = SubversionSCM.parseRevisionFile(getRun());
        return revisionMap;
    }
    
    private List<LogEntry> prepareChangeLogEntries(List<LogEntry> items) {
        items = removeDuplicatedEntries(items);
        
        if (ignoreDirPropChanges) items = removePropertyOnlyChanges(items);
        
        // we want recent changes first
        Collections.sort(items, new ReverseByRevisionComparator());
        for (LogEntry log : items) {
            log.setParent(this);
        }
        return Collections.unmodifiableList(items);
    }

    static List<LogEntry> removePropertyOnlyChanges(List<LogEntry> items) {

      for (LogEntry entry : items) {
        entry.removePropertyOnlyPaths();
      }
      
      return items;
    }

    /**
     * Removes duplicate entries, e.g. those coming form svn:externals.
     *
     * @param items list of items
     * @return filtered list without duplicated entries
     */
    static List<LogEntry> removeDuplicatedEntries(List<LogEntry> items) {
        Set<LogEntry> entries = new HashSet<LogEntry>(items);
        for (LogEntry sourceEntry : items) {
            // LogEntry equality does not consider paths, but some might have localPath attributes
            // that would get lost by HashSet duplicate removal
            for (LogEntry destinationEntry  : entries) {
                if (sourceEntry.equals(destinationEntry)) {
                    // get all local paths and set in destination
                    for (Path sourcePath : sourceEntry.getPaths()) {
                        if (sourcePath.localPath != null) {
                            for (Path destinationPath : destinationEntry.getPaths()) {
                                if (sourcePath.value.equals(destinationPath.value)) {
                                    destinationPath.localPath = sourcePath.localPath;
                                }
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<LogEntry>(entries);
    }

    @Exported
    public List<RevisionInfo> getRevisions() throws IOException {
        List<RevisionInfo> r = new ArrayList<RevisionInfo>();
        for (Map.Entry<String, Long> e : getRevisionMap().entrySet())
            r.add(new RevisionInfo(e.getKey(),e.getValue()));
        return r;
    }

    @ExportedBean(defaultVisibility=999)
    public static final class RevisionInfo {
        @Exported public final String module;
        @Exported public final long revision;
        public RevisionInfo(String module, long revision) {
            this.module = module;
            this.revision = revision;
        }
    }

    /**
     * One commit.
     * <p>
     * Setter methods are public only so that the objects can be constructed from Digester.
     * So please consider this object read-only.
     */
    public static class LogEntry extends ChangeLogSet.Entry {
        private int revision;
        private User author;
        private String date;
        private String msg;
        private List<Path> paths = new ArrayList<Path>();

        /**
         * Gets the {@link SubversionChangeLogSet} to which this change set belongs.
         */
        public SubversionChangeLogSet getParent() {
            return (SubversionChangeLogSet)super.getParent();
        }

        protected void removePropertyOnlyPaths() {
          for (Iterator<Path> it = paths.iterator(); it.hasNext();) {
            Path path = it.next();
            if (path.isPropOnlyChange()) it.remove();
          }
        }

        // because of the classloader difference, we need to extend this method to make it accessible
        // to the rest of SubversionSCM
        @Override
        @SuppressWarnings("rawtypes")
        protected void setParent(ChangeLogSet changeLogSet) {
            super.setParent(changeLogSet);
        }

        /**
         * Gets the revision of the commit.
         *
         * <p>
         * If the commit made the repository revision 1532, this
         * method returns 1532.
         */
        @Exported
        public int getRevision() {
            return revision;
        }

        public void setRevision(int revision) {
            this.revision = revision;
        }
        
        @Override
        public String getCommitId() {
            return String.valueOf(revision);
        }

        @Override
        public long getTimestamp() {
            return date!=null ? SVNDate.parseDate(date).getTime() : -1;
        }

        @Override
        public User getAuthor() {
            if(author==null)
                return User.getUnknown();
            return author;
        }

        @Override
        public Collection<String> getAffectedPaths() {
            return new AbstractList<String>() {
                public String get(int index) {
                    return preparePath(paths.get(index).value);
                }
                public int size() {
                    return paths.size();
                }
            };
        }
        
        private String preparePath(String path) {
            Job job = getParent().getRun().getParent();
            SCMTriggerItem s = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);
            if (s == null) {
                return path;
            }
            for (SCM scm : s.getSCMs()) {
                if (!(scm instanceof SubversionSCM)) {
                    continue;
                }
            ModuleLocation[] locations = ((SubversionSCM)scm).getLocations();
            for (int i = 0; i < locations.length; i++) {
                ModuleLocation expandedLocation = locations[i].getExpandedLocation(job);
                // If the remote URL features a trailing '@REV' entry, strip it off before looking for common part
                String expandedRemote = expandedLocation.remote;
                if (expandedLocation.getRevision(null) != null) {
                    int idx = expandedRemote.lastIndexOf('@');
                    if (idx >= 0) {
                        expandedRemote = expandedRemote.substring(0, idx);
                    }
                }
                String commonPart = findCommonPart(expandedRemote, path);
                if (commonPart != null) {
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    String newPath = path.substring(commonPart.length());
                    if (newPath.startsWith("/")) {
                        newPath = newPath.substring(1);
                    }
                    return newPath;
                }
            }
            }
            return path;
        }
        
        private String findCommonPart(String folder, String filePath) {
            if (folder == null || filePath == null) {
                return null;
            }
            if (filePath.startsWith("/")) {
                filePath = filePath.substring(1);
            }
            for (int i = 0; i < folder.length(); i++) {
                String part = folder.substring(i);
                if (filePath.startsWith(part)) {
                    return part;
                }
            }
            return null;
        }

        public void setUser(String author) {
            this.author = User.get(author);
        }

        @Exported
        public String getUser() {// digester wants read/write property, even though it never reads. Duh.
            return author!=null ? author.getDisplayName() : "unknown";
        }

        @Exported
        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        @Override @Exported
        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public void addPath( Path p ) {
            p.entry = this;
            paths.add(p);
        }

        /**
         * Gets the files that are changed in this commit.
         * @return
         *      can be empty but never null.
         */
        @Exported
        public List<Path> getPaths() {
            return paths;
        }
        
        @Override
        public Collection<Path> getAffectedFiles() {
            Collection<Path> affectedFiles = new ArrayList<Path>();
            for (Path p : paths) {
                if (p.hasLocalPath()) {
                    affectedFiles.add(p);
                }
            }
            // FIXME backwards compatibility?
            return affectedFiles;
        }
        
        void finish() {
            Collections.sort(paths, new Comparator<Path>() {
                @Override
                public int compare(Path o1, Path o2) {
                    String path1 = Util.fixNull(o1.getValue());
                    String path2 = Util.fixNull(o2.getValue());
                    return path1.compareTo(path2);
                }
            });
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            LogEntry that = (LogEntry) o;

            if (revision != that.revision) {
                return false;
            }
            if (author != null ? !author.equals(that.author) : that.author != null) {
                return false;
            }
            if (date != null ? !date.equals(that.date) : that.date != null) {
                return false;
            }
            if (msg != null ? !msg.equals(that.msg) : that.msg != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = revision;
            result = 31 * result + (author != null ? author.hashCode() : 0);
            result = 31 * result + (date != null ? date.hashCode() : 0);
            result = 31 * result + (msg != null ? msg.hashCode() : 0);
            return result;
        }
    }

    /**
     * A file in a commit.
     * <p>
     * Setter methods are public only so that the objects can be constructed from Digester.
     * So please consider this object read-only.
     */
    @ExportedBean(defaultVisibility=999)
    public static class Path implements AffectedFile {
        private LogEntry entry;
        private char action;

        /**
         * full path to file within SVN repository, e.g. /trunk/project/foo/bar.txt
         */
        private String value;

        /**
         * Path to file within workspace, e.g. stuff/foo/bar.txt
         */
        private String localPath;
        private String kind;
        
        /**
         * Gets the {@link LogEntry} of which this path is a member.
         */
        public LogEntry getLogEntry() {
            return entry;
        }

        /**
         * Sets the {@link LogEntry} of which this path is a member.
         */
        public void setLogEntry(LogEntry entry) {
            this.entry = entry;
        }

        public void setAction(String action) {
            this.action = action.charAt(0);
        }

        /**
         * Path in the repository. Such as <tt>/test/trunk/foo.c</tt>
         */
        @Exported(name="file")
        public String getValue() {
            return value;
        }

        /**
         * Inherited from AffectedFile
         *
         * Since 2.TODO this no longer returns the path relative to repository root,
         * but the path relative to the workspace root. Use getValue() instead.
         */
        public String getPath() {
            if (localPath == null) {
                // compatibility to older versions that did not store this path
                return value;
            }
	          return localPath;
        }

        @Restricted(NoExternalUse.class)
        public void setLocalPath(String path) {
            this.localPath = path;
        }

        public boolean hasLocalPath() {
            return localPath != null;
        }
        
        public void setValue(String value) {
            this.value = value;
        }
        
        public boolean isPropOnlyChange() {
            return action == 'M' && "dir".equals(kind);
        }
        
        public String getKind() {
          return kind;
        }
        
        public void setKind(String kind) {
            this.kind = kind;
        }
        
        @Exported
        public EditType getEditType() {
            if( action=='A' )
                return EditType.ADD;
            if( action=='D' )
                return EditType.DELETE;
            return EditType.EDIT;
        }
    }

    private static final class ReverseByRevisionComparator implements Comparator<LogEntry>, Serializable {
        private static final long serialVersionUID = 1L;

        public int compare(LogEntry a, LogEntry b) {
            return b.getRevision() - a.getRevision();
        }
    }
}
