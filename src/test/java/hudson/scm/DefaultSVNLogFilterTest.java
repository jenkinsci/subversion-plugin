package hudson.scm;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import hudson.scm.subversion.UpdateUpdater;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.Issue;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNClientManager;

/**
 * Tests the behavior of {@link DefaultSVNLogFilter}
 *
 */
public class DefaultSVNLogFilterTest extends AbstractSubversionTest {

    String [] empty = {};
    Pattern [] noPatterns = {};
    @SuppressWarnings("unchecked")
    Set<String> noUsers = Collections.EMPTY_SET;
    SVNRepository svnRepo;

    @Before
    public void setUp() throws Exception {
        File repo = new CopyExisting(DefaultSVNLogFilter.class.getResource("JENKINS-10449.zip")).allocate();
        SVNURL svnUrl = SVNURL.fromFile(repo);
        SVNClientManager svnMgr = SVNClientManager.newInstance();
        svnRepo = svnMgr.createRepository(svnUrl, false);
    }

    @After
    public void tearDown() throws Exception {
        svnRepo = null;
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
    
    private static Pattern [] compile(String ... regexes) {
        List<Pattern> patterns = new ArrayList<>();
        for (String re : regexes) {
            patterns.add(Pattern.compile(re));
        }
        return patterns.toArray(new Pattern [] {});
    }
    
    private static boolean containsRevs(List<SVNLogEntry> logs, long ... revs) {
        if (revs.length != logs.size())
            return false;
        nextrev: for (long r : revs) {
            for (SVNLogEntry l : logs) {
                if (r == l.getRevision());
                continue nextrev;
            }
            return false;
        }
        return true;
    }

    @Test
    public void noExcludes() throws Exception {
        
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, null, noPatterns, false);
        assertTrue(!filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 4, 5));
    }
    
    @Test
    public void excludes() throws Exception {
        Pattern [] excludes = compile("/z.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(excludes, noPatterns, noUsers, null, noPatterns, false);

        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 4));
    }
    
    @Test
    public void includes() throws Exception {
        Pattern [] includes = compile("/z.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, includes, noUsers, null, noPatterns, false);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 2, 3, 5));
    }
    
    @Test
    public void bothIncludesAndExcludes() throws Exception {
        Pattern [] includes = compile("/z.*");
        Pattern [] excludes = compile("/z/a.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(excludes, includes, noUsers, null, noPatterns, false);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 2, 5));
    }
    
    @Test
    public void excludedUsers() throws Exception {
        Set<String> users = new HashSet<>();
        users.add("brent");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, users, null, noPatterns, false);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 4));
    }
    
    @Test
    public void excludedRevProp() throws Exception {
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, "ignoreme", noPatterns, false);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 4));
    }
    
    @Test
    public void excludedCommitMessages() throws Exception {
        Pattern [] excludes = compile(".*pinned.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, null, excludes, false);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 5));
    }
    
    @Test
    public void excludedDirPropChanges() throws Exception {
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, null, noPatterns, true);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3));
    }

    @Issue("JENKINS-18099")
    @Test
    public void globalExclusionRevprop() throws Exception {
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
