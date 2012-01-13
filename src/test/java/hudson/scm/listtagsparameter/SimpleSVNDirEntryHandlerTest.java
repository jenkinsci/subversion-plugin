package hudson.scm.listtagsparameter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;

public class SimpleSVNDirEntryHandlerTest {
    
    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

    @Test
    public void testSortByName() {
        SimpleSVNDirEntryHandler handler = new SimpleSVNDirEntryHandler(null);
        addEntries(handler);
        
        // TODO: the semantics of using both parameters for handler.getDirs(boolean,boolean)
        // doesn't seem to have been defined properly
        // Actually, giving false,false seems to mean to sort by name ascending
        
        List<String> dirs = handler.getDirs(false, false);
        
        Assert.assertEquals(4, dirs.size());
        Assert.assertEquals("trunk/a", dirs.get(0));
        Assert.assertEquals("trunk/b", dirs.get(1));
        Assert.assertEquals("trunk/c", dirs.get(2));
        Assert.assertEquals("trunk/x", dirs.get(3));
    }
    
    @Test
    public void testReverseSortByDate() {
        SimpleSVNDirEntryHandler handler = new SimpleSVNDirEntryHandler(null);
        addEntries(handler);
        List<String> dirs = handler.getDirs(true, false);
        
        Assert.assertEquals(4, dirs.size());
        Assert.assertEquals("trunk/a", dirs.get(0));
        Assert.assertEquals("trunk/b", dirs.get(1));
        Assert.assertEquals("trunk/x", dirs.get(2));
        Assert.assertEquals("trunk/c", dirs.get(3));
    }
    
    @Test
    public void testReverseSortByName() {
        SimpleSVNDirEntryHandler handler = new SimpleSVNDirEntryHandler(null);
        addEntries(handler);
        List<String> dirs = handler.getDirs(false, true);
        
        Assert.assertEquals(4, dirs.size());
        Assert.assertEquals("trunk/x", dirs.get(0));
        Assert.assertEquals("trunk/c", dirs.get(1));
        Assert.assertEquals("trunk/b", dirs.get(2));
        Assert.assertEquals("trunk/a", dirs.get(3));
    }
    
    @Test
    public void testFilter() {
        SimpleSVNDirEntryHandler handler = new SimpleSVNDirEntryHandler(".*a.*");
        addEntries(handler);
        List<String> dirs = handler.getDirs();
        
        Assert.assertEquals(1, dirs.size());
        Assert.assertEquals("trunk/a", dirs.get(0));
    }
    
    private void addEntries(SimpleSVNDirEntryHandler handler) {
        try {
            handler.handleDirEntry(getEntry("2011-11-01", "trunk/a"));
            handler.handleDirEntry(getEntry("2011-11-01", "trunk/b"));
            handler.handleDirEntry(getEntry("2011-10-01", "trunk/x"));
            handler.handleDirEntry(getEntry("2011-09-01", "trunk/c"));
        } catch (ParseException e) {
            Assert.fail(e.toString());
        } catch (SVNException e) {
            Assert.fail(e.toString());
        }
    }
    
    private SVNDirEntry getEntry(String lastChanged, String directoryName) throws ParseException {
        SVNDirEntry entry = Mockito.mock(SVNDirEntry.class);
        Mockito.when(entry.getDate()).thenReturn(df.parse(lastChanged));
        Mockito.when(entry.getName()).thenReturn(directoryName);
        return entry;
    }
}
