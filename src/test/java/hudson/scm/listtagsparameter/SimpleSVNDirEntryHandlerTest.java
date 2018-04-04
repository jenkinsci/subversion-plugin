/*
 * The MIT License
 *
 * Copyright (c) 2011-2012, id:kutzi, id:grahamparks
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
        } catch (ParseException | SVNException e) {
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
