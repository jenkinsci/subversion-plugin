/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc., Stephen Connolly.
 * Copyright (c) 2026 Hilscher Gesellschaft für Systemautomation.
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

import jenkins.model.Jenkins;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the MapDB databases backing the {@link SVNRepositoryView}s: at most one open database
 * instance per repository (keyed by repository UUID), reference counted by the views currently
 * using it, opened on first use and closed when the last view releases it.
 * <p>
 * MapDB 1.0.x performs no file locking of its own, so two of its instances having the same cache
 * file open at the same time would corrupt the files on disk; sharing the single instance is both
 * the fix for that and what lets views of the same repository be used by several jobs at the same
 * time without waiting for each other, as a MapDB instance is safe for concurrent use.
 * <p>
 * All state transitions, including closing the database, happen inside
 * {@link ConcurrentHashMap} compute blocks for the repository UUID, so acquiring and releasing
 * cannot interleave with closing.
 */
final class SVNRepositoryViewCache {

    private static final Logger LOGGER = Logger.getLogger(SVNRepositoryViewCache.class.getName());

    /**
     * The caches currently open, by repository UUID. Exposed package-private for tests.
     */
    static final ConcurrentMap<String, SVNRepositoryViewCache> OPEN = new ConcurrentHashMap<>();

    private static final int MAX_OPEN_ATTEMPTS = 10;

    private final String uuid;
    private final File file;
    private final DB db;
    /** Guarded by the {@link #OPEN} compute blocks for {@link #uuid}, so not volatile. */
    private int references;

    /**
     * Returns the cache file holding the cache database of the repository with the given UUID.
     */
    static File cacheFile(String uuid) {
        return new File(new File(Jenkins.get().getRootDir(), "caches"), "svn-" + uuid + ".db");
    }

    /**
     * Returns the cache of the given repository, opening the database in {@link #cacheFile(String)}
     * if no other view is using it yet, and registers the caller as a user until it calls
     * {@link #release()}.
     */
    static SVNRepositoryViewCache acquire(String uuid) throws IOException {
        File cacheFile = cacheFile(uuid);
        try {
            return OPEN.compute(uuid, (id, existing) -> {
                if (existing == null) {
                    return new SVNRepositoryViewCache(uuid, cacheFile);
                }
                existing.references++;
                return existing;
            });
        } catch (CacheOpenFailure failure) {
            throw new IOException("failed to make/load " + cacheFile + ": " + failure.getCause(),
                    failure.getCause());
        }
    }

    /**
     * Ends the calling view's use of this cache, closing the database when this was the last view
     * using it.
     */
    void release() {
        OPEN.compute(uuid, (id, existing) -> {
            if (existing == null) {
                LOGGER.log(Level.WARNING, "release() for " + file + " is called more often than acquire()");
                return null;
            }
            existing.references--;
            if (existing.references > 0) {
                return existing;
            }
            existing.closeDb();
            return null;
        });
    }

    /**
     * Returns the named cache map, creating it if it does not exist yet. The returned MapDB map is
     * safe for concurrent use.
     */
    ConcurrentMap<String, SVNRepositoryView.NodeEntry> getHashMap(String name) {
        return db.getHashMap(name);
    }

    /** Makes recent modifications to the cache maps durable. */
    void commit() {
        db.commit();
    }

    /** The underlying database, exposed package-private for tests. */
    DB getDb() {
        return db;
    }

    private SVNRepositoryViewCache(String uuid, File file) {
        this.uuid = uuid;
        this.file = file;
        this.references = 1;
        file.getParentFile().mkdirs();
        DB opened = null;
        int count = 0;
        while (opened == null) {
            try {
                opened = DBMaker.newFileDB(file)
                        .cacheWeakRefEnable()
                        .make();
            } catch (Throwable t) { // this library seems to have nonstandard exception handling
                file.delete();
                LOGGER.log(Level.WARNING, "failing to make/load " + file, t);
                if (++count >= MAX_OPEN_ATTEMPTS) {
                    throw new CacheOpenFailure(t);
                }
            }
        }
        this.db = opened;
    }

    private void closeDb() {
        try {
            db.close();
        } catch (Throwable t) { // this library seems to have nonstandard exception handling
            LOGGER.log(Level.WARNING, "failing to close " + file, t);
        }
    }

    /** Unchecked wrapper to carry an exhausted {@link #MAX_OPEN_ATTEMPTS} loop out of a compute block. */
    private static class CacheOpenFailure extends RuntimeException {
        CacheOpenFailure(Throwable cause) {
            super(cause);
        }
    }
}
