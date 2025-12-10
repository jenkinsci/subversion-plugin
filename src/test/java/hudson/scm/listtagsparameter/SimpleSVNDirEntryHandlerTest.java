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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.tmatesoft.svn.core.SVNDirEntry;

import java.text.SimpleDateFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleSVNDirEntryHandlerTest {

    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

    @Test
    void testSortByName() throws Exception {
        SimpleSVNDirEntryHandler handler = new SimpleSVNDirEntryHandler(null);
        addEntries2(handler);

        // TODO: the semantics of using both parameters for handler.getDirs(boolean,boolean)
        // doesn't seem to have been defined properly
        // Actually, giving false,false seems to mean to sort by name ascending

        List<String> dirs = handler.getDirs(false, false);

        assertEquals(4, dirs.size());
        assertEquals("trunk/a", dirs.get(0));
        assertEquals("trunk/b", dirs.get(1));
        assertEquals("trunk/c", dirs.get(2));
        assertEquals("trunk/x", dirs.get(3));
    }

    @Test
    void testReverseSortByDate() throws Exception {
        SimpleSVNDirEntryHandler handler = new SimpleSVNDirEntryHandler(null);
        addEntries(handler);
        List<String> dirs = handler.getDirs(true, false);

        assertEquals(4, dirs.size());
        assertEquals("trunk/a", dirs.get(0));
        assertEquals("trunk/b", dirs.get(1));
        assertEquals("trunk/x", dirs.get(2));
        assertEquals("trunk/c", dirs.get(3));
    }

    @Test
    void testReverseSortByName() throws Exception {
        SimpleSVNDirEntryHandler handler = new SimpleSVNDirEntryHandler(null);
        addEntries2(handler);
        List<String> dirs = handler.getDirs(false, true);

        assertEquals(4, dirs.size());
        assertEquals("trunk/x", dirs.get(0));
        assertEquals("trunk/c", dirs.get(1));
        assertEquals("trunk/b", dirs.get(2));
        assertEquals("trunk/a", dirs.get(3));
    }

    @Test
    void testFilter() throws Exception {
        SimpleSVNDirEntryHandler handler = new SimpleSVNDirEntryHandler(".*a.*");
        addEntries2(handler);
        List<String> dirs = handler.getDirs();

        assertEquals(1, dirs.size());
        assertEquals("trunk/a", dirs.get(0));
    }

    private void addEntries(SimpleSVNDirEntryHandler handler) throws Exception {
        handler.handleDirEntry(getEntry("2011-11-01", "trunk/a"));
        handler.handleDirEntry(getEntry("2011-11-01", "trunk/b"));
        handler.handleDirEntry(getEntry("2011-10-01", "trunk/x"));
        handler.handleDirEntry(getEntry("2011-09-01", "trunk/c"));
    }

    private SVNDirEntry getEntry(String lastChanged, String directoryName) throws Exception {
        SVNDirEntry entry = Mockito.mock(SVNDirEntry.class);
        Mockito.when(entry.getDate()).thenReturn(df.parse(lastChanged));
        Mockito.when(entry.getName()).thenReturn(directoryName);
        return entry;
    }

    private SVNDirEntry getEntry2(String lastChanged, String directoryName) {
        SVNDirEntry entry = Mockito.mock(SVNDirEntry.class);
        Mockito.when(entry.getName()).thenReturn(directoryName);
        return entry;
    }

    private void addEntries2(SimpleSVNDirEntryHandler handler) throws Exception {
        handler.handleDirEntry(getEntry2("2011-11-01", "trunk/a"));
        handler.handleDirEntry(getEntry2("2011-11-01", "trunk/b"));
        handler.handleDirEntry(getEntry2("2011-10-01", "trunk/x"));
        handler.handleDirEntry(getEntry2("2011-09-01", "trunk/c"));
    }
}
