/*
 * The MIT License
 *
 * Copyright (c) 2011, Oracle Corporation, Nikita Levyankov
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

import static hudson.scm.SubversionChangeLogUtil.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * SubversionChangeLogSet test cases.
 * <p/>
 * Date: 6/28/11
 *
 * @author Nikita Levyankov
 */
public class SubversionChangeLogSetTest {

    @Test
    public void testRemoveDuplicateEntries() throws Exception{
        //One duplicated entry. 7 unique, 8 total entries.
        List<SubversionChangeLogSet.LogEntry> items = new ArrayList<>();
        items.add(buildChangeLogEntry(1, "Test msg"));
        items.add(buildChangeLogEntry(2, "Test msg"));
        items.add(buildChangeLogEntry(1, "Test msg"));
        items.add(buildChangeLogEntry(3, "Test msg"));
        items.add(buildChangeLogEntry(4, "Test msg"));
        items.add(buildChangeLogEntry(5, "Test msg"));
        items.add(buildChangeLogEntry(6, "Test msg"));
        items.add(buildChangeLogEntry(1, "Test msg1"));
        Assert.assertEquals("Items size is not equals to expected", items.size(), 8);
        List<SubversionChangeLogSet.LogEntry> resultItems = SubversionChangeLogSet.removeDuplicatedEntries(items);
        Assert.assertEquals(resultItems.size(), 7);

        //No duplicated entries. Total 7
        items = new ArrayList<>();
        items.add(buildChangeLogEntry(1, "Test msg"));
        items.add(buildChangeLogEntry(2, "Test msg"));
        items.add(buildChangeLogEntry(3, "Test msg"));
        items.add(buildChangeLogEntry(4, "Test msg"));
        items.add(buildChangeLogEntry(5, "Test msg"));
        items.add(buildChangeLogEntry(6, "Test msg"));
        items.add(buildChangeLogEntry(1, "Test msg1"));
        Assert.assertEquals("Items size is not equals to expected", items.size(), 7);
        resultItems = SubversionChangeLogSet.removeDuplicatedEntries(items);
        Assert.assertEquals(resultItems.size(), 7);
    }
}