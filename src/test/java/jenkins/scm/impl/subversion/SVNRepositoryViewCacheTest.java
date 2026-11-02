/*
 * The MIT License
 *
 * Copyright (c) 2026 Hilscher Gesellschaft für Systemautomation mbH.
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the concurrent-access behaviour of {@link SVNRepositoryViewCache}, the reference
 * counted MapDB cache behind {@link SVNRepositoryView}. MapDB 1.0.x does no file locking, so
 * opening the same cache file from several jobs polling the same repository at the same time used
 * to corrupt the cache files on disk; there is now one shared, reference counted instance per
 * repository.
 */
@WithJenkins
class SVNRepositoryViewCacheTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 6;
    private static final int DIRECTORIES = 5;
    private static final int FILES_PER_DIRECTORY = 2;

    @RegisterExtension
    final SubversionSampleRepoExtension sampleRepo = new SubversionSampleRepoExtension();

    /**
     * Many views over the same repository use the shared cache concurrently; afterwards the cache
     * must still be a well-formed MapDB database containing everything that was written to it. On
     * the unpatched code this fails either with "failed to make/load" style exceptions from the
     * view constructor or with a cache that has lost entries and can no longer be opened.
     */
    @Test
    void concurrentViewsKeepPersistentCacheConsistent(JenkinsRule r) throws Exception {
        sampleRepo.init();
        for (int dir = 0; dir < DIRECTORIES; dir++) {
            sampleRepo.mkdirs("dir" + dir);
            sampleRepo.svnkit("add", sampleRepo.wc() + "/dir" + dir);
            for (int file = 0; file < FILES_PER_DIRECTORY; file++) {
                sampleRepo.write("dir" + dir + "/file" + file, "content " + dir + ' ' + file);
                sampleRepo.svnkit("add", sampleRepo.wc() + "/dir" + dir + "/file" + file);
            }
        }
        sampleRepo.svnkit("commit", "--message=populate", sampleRepo.wc());

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < THREADS; t++) {
                final int dir = t % DIRECTORIES;
                futures.add(pool.submit(() -> {
                    for (int round = 0; round < ROUNDS; round++) {
                        // exactly what SubversionSCMSource does per polling/indexing operation,
                        // including closing on the thread that opened the view
                        SVNRepositoryView view = new SVNRepositoryView(repositoryUrl(), null);
                        try {
                            assertNotNull(view.getNode("/trunk/dir" + dir + "/file" + round
                                    % FILES_PER_DIRECTORY, -1).getType());
                            assertNotNull(view.getNode("/trunk/dir" + dir, -1));
                        } finally {
                            view.close();
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }

        File cacheFile = cacheFile();
        assertTrue(cacheFile.isFile(), "expected a persistent cache at " + cacheFile);
        assertNull(SVNRepositoryViewCache.OPEN.get(repositoryUuid()),
                "the cache must be closed once all views are closed");
        // no live instance remains, so a fresh DBMaker instance can safely reopen the cache
        DB db = DBMaker.newFileDB(cacheFile).cacheWeakRefEnable().make();
        try {
            ConcurrentMap<String, SVNRepositoryView.NodeEntry> map = db.getHashMap("data");
            for (int d = 0; d < DIRECTORIES; d++) {
                for (int f = 0; f < FILES_PER_DIRECTORY; f++) {
                    assertNotNull(map.get("/trunk/dir" + d + "/file" + f), "cache lost an entry written concurrently");
                }
            }
        } finally {
            db.close();
        }
    }

    /**
     * Two views of the same repository must be usable at the very same time: the shared MapDB
     * instance is safe for concurrent use, so operations on one repository no longer wait for each
     * other, and their interleaved commits must not corrupt the cache. The open barriers would
     * expire if a second view had to wait for the first one's cache.
     */
    @Test
    void viewsOfTheSameRepositoryCanUseTheCacheAtTheSameTime(JenkinsRule r) throws Exception {
        sampleRepo.init();
        for (int dir = 0; dir < 2; dir++) {
            sampleRepo.mkdirs("dir" + dir);
            sampleRepo.svnkit("add", sampleRepo.wc() + "/dir" + dir);
            sampleRepo.write("dir" + dir + "/file", "content " + dir);
            sampleRepo.svnkit("add", sampleRepo.wc() + "/dir" + dir + "/file");
        }
        sampleRepo.svnkit("commit", "--message=populate", sampleRepo.wc());

        CyclicBarrier bothOpened = new CyclicBarrier(2);
        CyclicBarrier bothRead = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int dir = 0; dir < 2; dir++) {
                final int dirIndex = dir;
                futures.add(pool.submit(() -> {
                    for (int round = 0; round < 5; round++) {
                        SVNRepositoryView view = new SVNRepositoryView(repositoryUrl(), null);
                        try {
                            bothOpened.await(2, TimeUnit.MINUTES);
                            assertNotNull(view.getNode("/trunk/dir" + dirIndex + "/file", -1));
                            assertNotNull(view.getNode("/trunk/dir" + (1 - dirIndex) + "/file", -1));
                            bothRead.await(2, TimeUnit.MINUTES);
                        } finally {
                            view.close();
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }

        File cacheFile = cacheFile();
        assertNull(SVNRepositoryViewCache.OPEN.get(repositoryUuid()),
                "the cache must be closed once all views are closed");
        DB db = DBMaker.newFileDB(cacheFile).cacheWeakRefEnable().make();
        try {
            ConcurrentMap<String, SVNRepositoryView.NodeEntry> map = db.getHashMap("data");
            assertNotNull(map.get("/trunk/dir0/file"), "cache lost an entry written concurrently");
            assertNotNull(map.get("/trunk/dir1/file"), "cache lost an entry written concurrently");
        } finally {
            db.close();
        }
    }

    /**
     * The cache is reference counted by the views using it: it stays open and the same instance
     * while any view uses it, and is closed and unregistered when the last view releases it.
     */
    @Test
    void viewsOnTheSameRepositoryShareOneReferenceCountedCache(JenkinsRule r) throws Exception {
        sampleRepo.init();
        sampleRepo.write("file", "content");
        sampleRepo.svnkit("commit", "--message=content", sampleRepo.wc());

        String uuid = repositoryUuid();

        SVNRepositoryView first = new SVNRepositoryView(repositoryUrl(), null);
        try {
            assertNotNull(first.getNode("/trunk/file", -1));

            SVNRepositoryViewCache opened = SVNRepositoryViewCache.OPEN.get(uuid);
            assertNotNull(opened, "expected the cache to have been created");
            assertFalse(opened.getDb().isClosed());

            SVNRepositoryView second = new SVNRepositoryView(repositoryUrl(), null);
            try {
                assertSame(opened, SVNRepositoryViewCache.OPEN.get(uuid),
                        "a concurrent view must reuse the cache instance that is already in use");
                assertNotNull(second.getNode("/trunk/file", -1), "the new view must work on the shared cache");

                first.close();
                assertTrue(first.isClosed());
                assertSame(opened, SVNRepositoryViewCache.OPEN.get(uuid),
                        "the cache must stay open while another view uses it");
                assertFalse(opened.getDb().isClosed(), "closing one view must not close the shared cache");
                assertNotNull(second.getNode("/trunk", -1), "the remaining view must keep working");
            } finally {
                second.close();
            }
            assertTrue(second.isClosed());

            assertNull(SVNRepositoryViewCache.OPEN.get(uuid),
                    "the cache must be closed and unregistered when the last view releases it");
        } finally {
            first.close(); // the extra close is guarded by the view's closed flag and must be a no-op
        }
    }

    private SVNURL repositoryUrl() throws Exception {
        return SVNURL.parseURIEncoded(sampleRepo.prjUrl());
    }

    private String repositoryUuid() throws Exception {
        SVNRepository repository = SVNRepositoryFactory.create(repositoryUrl());
        try {
            return repository.getRepositoryUUID(true);
        } finally {
            repository.closeSession();
        }
    }

    private File cacheFile() throws Exception {
        return new File(new File(Jenkins.get().getRootDir(), "caches"), "svn-" + repositoryUuid() + ".db");
    }
}
