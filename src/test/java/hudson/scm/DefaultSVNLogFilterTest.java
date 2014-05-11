package hudson.scm;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import hudson.scm.subversion.UpdateUpdater;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
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
    
    public void setUp() throws Exception {
        super.setUp();
        File repo = new CopyExisting(DefaultSVNLogFilter.class.getResource("JENKINS-10449.zip")).allocate();
        SVNURL svnUrl = SVNURL.fromFile(repo);
        SVNClientManager svnMgr = SVNClientManager.newInstance();
        svnRepo = svnMgr.createRepository(svnUrl, false);
    }
    
    public void tearDown() throws Exception {
        svnRepo = null;
        super.tearDown();
    }
    
    private List<SVNLogEntry> doFilter(final SVNLogFilter logFilter) throws SVNException {
        final List<SVNLogEntry> log = new ArrayList<SVNLogEntry>();
        ISVNLogEntryHandler logGatherer = new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                if (logFilter.isIncluded(logEntry)) {
                    log.add(logEntry);
                }
            }
        };
        svnRepo.log(empty, 1, 5, true, false, logGatherer);
        return log;
    }
    
    private static Pattern [] compile(String ... regexes) {
        List<Pattern> patterns = new ArrayList<Pattern>();
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
    
    public void testNoExcludes() throws Exception {
        
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, null, noPatterns, false);
        assertTrue(!filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 4, 5));
    }
    
    public void testExcludes() throws Exception {
        Pattern [] excludes = compile("/z.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(excludes, noPatterns, noUsers, null, noPatterns, false);

        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 4));
    }
    
    public void testIncludes() throws Exception {
        Pattern [] includes = compile("/z.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, includes, noUsers, null, noPatterns, false);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 2, 3, 5));
    }
    
    public void testBothIncludesAndExcludes() throws Exception {
        Pattern [] includes = compile("/z.*");
        Pattern [] excludes = compile("/z/a.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(excludes, includes, noUsers, null, noPatterns, false);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 2, 5));
    }
    
    public void testExcludedUsers() throws Exception {
        Set<String> users = new HashSet<String>();
        users.add("brent");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, users, null, noPatterns, false);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 4));
    }
    
    public void testExcludedRevProp() throws Exception {
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, "ignoreme", noPatterns, false);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 4));
    }
    
    public void testExcludedCommitMessages() throws Exception {
        Pattern [] excludes = compile(".*pinned.*");
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, null, excludes, false);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3, 5));
    }
    
    public void testExcludedDirPropChanges() throws Exception {
        DefaultSVNLogFilter filter = new DefaultSVNLogFilter(noPatterns, noPatterns, noUsers, null, noPatterns, true);
        
        assertTrue(filter.hasExclusionRule());
        
        List<SVNLogEntry> entries = doFilter(filter);
        assertTrue(containsRevs(entries, 1, 2, 3));
    }

    @Bug(18099)
    public void testGlobalExclusionRevprop() throws Exception {
        SubversionSCM scm = new SubversionSCM(
                Arrays.asList(new SubversionSCM.ModuleLocation("file://some/repo", ".")),
                new UpdateUpdater(), null, null, null, null, null, null, false);
        scm.getDescriptor().setGlobalExcludedRevprop("ignoreme");

        SVNProperties p = new SVNProperties();
        p.put("ignoreme", "*");

        Map<String, SVNLogEntryPath> paths = new HashMap<String, SVNLogEntryPath>();
        paths.put("/foo", new SVNLogEntryPath("/foo", SVNLogEntryPath.TYPE_MODIFIED, null, -1));
        SVNLogEntry e = new SVNLogEntry(paths, 1234L, p, false);

        SVNLogFilter filter = scm.createSVNLogFilter();
        assertFalse(filter.isIncluded(e));
    }

}
