package hudson.scm;

import hudson.scm.subversion.UpdateUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNClientManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the behavior of {@link DefaultSVNLogFilter}
 *
 */
@WithJenkins
class DefaultSVNLogFilterTest extends AbstractSubversionTest {

    private final String[] empty = {};
    private final Pattern[] noPatterns = {};
    @SuppressWarnings("unchecked")
    private final Set<String> noUsers = Collections.EMPTY_SET;
    private SVNRepository svnRepo;

    @Override
    @BeforeEach
    protected void beforeEach(JenkinsRule rule) throws Exception {
        super.beforeEach(rule);
        File repo = new CopyExisting(DefaultSVNLogFilter.class.getResource("JENKINS-10449.zip")).allocate();
        SVNURL svnUrl = SVNURL.fromFile(repo);
        SVNClientManager svnMgr = SVNClientManager.newInstance();
        svnRepo = svnMgr.createRepository(svnUrl, false);
    }

    private List<SVNLogEntry> doFilter(final SVNLogFilter logFilter) throws SVNException {
        final List<SVNLogEntry> log = new ArrayList<>();
        ISVNLogEntryHandler logGatherer = logEntry -> {
            if (logFilter.isIncluded(logEntry)) {
                log.add(logEntry);
            }
        };
        svnRepo.log(empty, 1, 5, true, false, logGatherer);
        return log;
    }

    private static Pattern[] compile(String... regexes) {
        List<Pattern> patterns = new ArrayList<>();
        for (String re : regexes) {
            patterns.add(Pattern.compile(re));
        }
        return patterns.toArray(new Pattern[]{});
    }

    private static boolean containsRevs(List<SVNLogEntry> logs, long... revs) {
        if (revs.length != logs.size())
            return false;
        nextrev:
        for (long r : revs) {
            for (SVNLogEntry l : logs) {
                if (r == l.getRevision()) ;
                continue nextrev;
            }
            return false;
        }
        return true;
    }

    @Test
    void noExcludes() throws Exception {
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, null, noPatterns, false);
        assertFalse(filter.hasExclusionRule());

        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 4, 5));
    }

    @Test
    void excludes() throws Exception {
        Pattern[] excludes = compile("/z.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(excludes, noPatterns, noUsers, null, noPatterns, false);

        assertTrue(filter.hasExclusionRule());

        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 4));
    }

    @Test
    void includes() throws Exception {
        Pattern[] includes = compile("/z.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, includes, noUsers, null, noPatterns, false);

        assertTrue(filter.hasExclusionRule());

        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 2, 3, 5));
    }

    @Test
    void bothIncludesAndExcludes() throws Exception {
        Pattern[] includes = compile("/z.*");
        Pattern[] excludes = compile("/z/a.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(excludes, includes, noUsers, null, noPatterns, false);

        assertTrue(filter.hasExclusionRule());

        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 2, 5));
    }

    @Test
    void excludedUsers() throws Exception {
        Set<String> users = new HashSet<>();
        users.add("brent");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, users, null, noPatterns, false);

        assertTrue(filter.hasExclusionRule());

        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 4));
    }

    @Test
    void excludedRevProp() throws Exception {
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, "ignoreme", noPatterns, false);

        assertTrue(filter.hasExclusionRule());

        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 4));
    }

    @Test
    void excludedCommitMessages() throws Exception {
        Pattern[] excludes = compile(".*pinned.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, null, excludes, false);

        assertTrue(filter.hasExclusionRule());

        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 5));
    }

    @Test
    void excludedDirPropChanges() throws Exception {
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, null, noPatterns, true);

        assertTrue(filter.hasExclusionRule());

        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3));
    }

    @Issue("JENKINS-18099")
    @Test
    void globalExclusionRevprop() {
        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(new SubversionSCM.ModuleLocation("file://some/repo", ".")),
                new UpdateUpdater(), null, null, null, null, null, null, false);
        scm.getDescriptor().setGlobalExcludedRevprop("ignoreme");

        SVNProperties p = new SVNProperties();
        p.put("ignoreme", "*");

        Map<String, SVNLogEntryPath> paths = new HashMap<>();
        paths.put("/foo", new SVNLogEntryPath("/foo", SVNLogEntryPath.TYPE_MODIFIED, null, -1));
        SVNLogEntry e = new SVNLogEntry(paths, 1234L, p, false);

        SVNLogFilter filter = scm.createSVNLogFilter();
        assertFalse(filter.isIncluded(e));
    }

}
