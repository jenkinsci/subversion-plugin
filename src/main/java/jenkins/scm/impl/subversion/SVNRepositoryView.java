/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc., Stephen Connolly.
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
package jenkins.scm.impl.subversion;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.scm.*;
import jenkins.model.Jenkins;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
public class SVNRepositoryView {
    public static final int DIRENTS =
            SVNDirEntry.DIRENT_CREATED_REVISION | SVNDirEntry.DIRENT_KIND | SVNDirEntry.DIRENT_TIME;
    private static final Logger LOGGER = Logger.getLogger(SVNRepositoryView.class.getName());
    private final DB cache;
    private final SVNRepository repository;
    private final ConcurrentMap<String, NodeEntry> data;
    private final String uuid;

    public SVNRepositoryView(SVNURL repoURL, StandardCredentials credentials) throws SVNException, IOException {
        repository = SVNRepositoryFactory.create(repoURL);
        boolean success = false;
        try {
            File configDir = SVNWCUtil.getDefaultConfigurationDirectory();

            ISVNAuthenticationManager sam = new SVNAuthenticationManager(configDir, null, null);

            sam.setAuthenticationProvider(new CredentialsSVNAuthenticationProviderImpl(credentials));
            SVNAuthStoreHandlerImpl.install(sam);
            sam = new FilterSVNAuthenticationManager(sam) {
                // If there's no time out, the blocking read operation may hang forever, because TCP itself
                // has no timeout. So always use some time out. If the underlying implementation gives us some
                // value (which may come from ~/.subversion), honor that, as long as it sets some timeout value.
                @Override
                public int getReadTimeout(SVNRepository repository) {
                    int r = super.getReadTimeout(repository);
                    if (r <= 0) {
                        r = (int) TimeUnit.MINUTES.toMillis(1);
                    }
                    return r;
                }
            };
            repository.setTunnelProvider(SVNWCUtil.createDefaultOptions(true));
            repository.setAuthenticationManager(sam);

            uuid = repository.getRepositoryUUID(true);
            if (uuid == null) { // TODO is this even possible? Javadoc is unclear.
                throw new IOException("Could not find UUID for " + repoURL);
            }
            File cacheFile = new File(new File(Jenkins.getInstance().getRootDir(), "caches"), "svn-" + uuid + ".db");

            cacheFile.getParentFile().mkdirs();
            DB cache = null;
            int count = 0;
            while (cache == null) {
                try {
                    cache = DBMaker.newFileDB(cacheFile)
                            .cacheWeakRefEnable()
                            .make();
                } catch (Throwable t) { // this library seems to have nonstandard exception handling
                    cacheFile.delete();
                    LOGGER.log(Level.WARNING, "failing to make/load " + cacheFile, t);
                    if (++count >= 10) {
                        throw new IOException("failed to make/load " + cacheFile + ": " + t, t);
                    }
                }
            }
            this.cache = cache;
            this.data = this.cache.getHashMap(credentials == null ? "data" : "data-" + credentials.getId());
            cache.commit();
            success = true;
        } finally {
            if (!success) {
                repository.closeSession();
            }
        }
    }

    public SVNRepository getRepository() {
        return repository;
    }

    @NonNull
    public String getUuid() {
        return uuid;
    }

    public boolean isClosed() {
        return cache.isClosed();
    }

    public void close() {
        if (isClosed()) {
            return;
        }
        repository.closeSession();
        cache.close();
    }

    public SVNNodeKind checkPath(String path, long revision) throws SVNException {
        path = SVNPathUtil.getAbsolutePath(path);
        NodeEntry nodeEntry = getNodeEntry(path);
        if (nodeEntry == null || nodeEntry.getRevision() != revision) {
            try {
                SVNNodeKind svnNodeKind = repository.checkPath(path, revision);
                if (SVNNodeKind.DIR.equals(svnNodeKind)) {
                    ChildEntryCollector collector = new ChildEntryCollector();
                    long dirRev = repository.getDir(path, revision, null, DIRENTS, collector);
                    nodeEntry = new NodeEntry(dirRev, svnNodeKind, collector.getResult());
                } else {
                    nodeEntry = new NodeEntry(revision, svnNodeKind, null);
                }
                setNodeEntry(path, nodeEntry);
            } catch (SVNException e) {
                // if we have a cached result and the server is off-line, use the cache
                if (nodeEntry == null) {
                    throw e;
                }
            }
        }
        return nodeEntry.getType();
    }

    @CheckForNull
    private NodeEntry getNodeEntry(String path) {
        try {
            return data.get(path);
        } catch (Throwable t) {
            // ignore, it's only a cache
            return null;
        }
    }

    private void setNodeEntry(String path, NodeEntry nodeEntry) {
        try {
            data.put(path, nodeEntry);
            cache.commit();
        } catch (Throwable t) {
            // ignore, it's only a cache
        }
    }

    public long getLatestRevision() throws SVNException {
        return repository.getLatestRevision();
    }

    public NodeEntry getNode(String path, long revision) throws SVNException {
        path = SVNPathUtil.getAbsolutePath(path);
        NodeEntry nodeEntry = getNodeEntry(path);
        if (nodeEntry == null || nodeEntry.getRevision() != revision) {
            try {
                SVNNodeKind svnNodeKind = repository.checkPath(path, revision);
                if (revision == -1) {
                    if (SVNNodeKind.DIR.equals(svnNodeKind)) {
                        ChildEntryCollector collector = new ChildEntryCollector();
                        long dirRev = repository.getDir(path, revision, null, DIRENTS, collector);
                        nodeEntry = new NodeEntry(dirRev, svnNodeKind, collector.getResult());
                    } else if (SVNNodeKind.FILE.equals(svnNodeKind)) {
                        long fileRev = repository.getFile(path, revision, null, null);
                        nodeEntry = new NodeEntry(fileRev, svnNodeKind, null);
                    } else {
                        nodeEntry = new NodeEntry(revision, svnNodeKind, null);
                    }
                } else {
                    nodeEntry = new NodeEntry(revision, svnNodeKind, null);
                }
                if (!nodeEntry.getType().equals(SVNNodeKind.DIR)) {
                    setNodeEntry(path, nodeEntry);
                }
            } catch (SVNException e) {
                // if we have a cached result and the server is off-line, use the cache
                if (nodeEntry == null) {
                    throw e;
                }
            }
        }
        if (nodeEntry.getType().equals(SVNNodeKind.DIR) && nodeEntry.getChildren() == null) {
            // if the cached result does not have a list of children and the server is off-line, bomb out
            ChildEntryCollector collector = new ChildEntryCollector();
            long dirRev = repository.getDir(path, revision, null, DIRENTS, collector);
            nodeEntry = new NodeEntry(dirRev, nodeEntry.getType(), collector.getResult());
            setNodeEntry(path, nodeEntry);
        }
        return nodeEntry;
    }

    public static class NodeEntry implements Serializable {
        private final long revision;
        private final SVNNodeKind type;
        private final ChildEntry[] children;

        public NodeEntry(long revision, SVNNodeKind type, ChildEntry[] children) {
            this.revision = revision;
            this.type = type;
            this.children = children;
        }

        public SVNNodeKind getType() {
            return type;
        }

        public long getRevision() {
            return revision;
        }

        public ChildEntry[] getChildren() {
            return children;
        }
    }

    public static class ChildEntry implements Serializable {
        private final long revision;
        private final SVNNodeKind type;
        private final String name;
        private final long lastModified;

        public ChildEntry(long revision, long lastModified, SVNNodeKind type, String name) {
            this.revision = revision;
            this.type = type;
            this.name = name;
            this.lastModified = lastModified;
        }

        public SVNNodeKind getType() {
            return type;
        }

        public long getRevision() {
            return revision;
        }

        public String getName() {
            return name;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    private static class ChildEntryCollector implements ISVNDirEntryHandler {
        private final List<ChildEntry> children = new ArrayList<ChildEntry>();

        public void handleDirEntry(SVNDirEntry entry) throws SVNException {
            children.add(
                    new ChildEntry(entry.getRevision(), entry.getDate().getTime(), entry.getKind(), entry.getName()));
        }

        public ChildEntry[] getResult() {
            return children.toArray(new ChildEntry[children.size()]);
        }
    }
}
