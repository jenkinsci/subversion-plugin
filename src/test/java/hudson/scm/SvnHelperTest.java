/*
 * The MIT License
 *
 * Copyright (c) 2013, Synopsys Inc., Oleg Nenashev
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

import hudson.scm.subversion.SvnHelper;
import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;

/**
 * Contains tests for {@link SvnHelper}.
 * @author Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 * @since TODO
 */
public class SvnHelperTest {
    
    private static final String URL_PREFIX = "http://very/complex/path/to/SVN";
       
    private void testGetUrlWithoutRevision(String serverURL, String expected) {
        String value = SvnHelper.getUrlWithoutRevision(serverURL);
        Assert.assertEquals("URL w/o revision differs from expected value", expected, value);
    }
    
    private void testGetUrlWithoutRevision(String serverUrl) {
        testGetUrlWithoutRevision(serverUrl, URL_PREFIX);
    }
    
    @Test
    public void testGetUrlWithoutRevision_minimal() {
        testGetUrlWithoutRevision(URL_PREFIX);
    }
     
    @Test
    public void testGetUrlWithoutRevision_withSuffix() {
        testGetUrlWithoutRevision(URL_PREFIX+"@HEAD");
        testGetUrlWithoutRevision(URL_PREFIX+"@100500");
        testGetUrlWithoutRevision(URL_PREFIX+"@LABEL",URL_PREFIX+"@LABEL"); // Actually, it is not a revision  
    }
    
    @Test
    public void testGetUrlWithoutRevision_withEndingSlash() {
        testGetUrlWithoutRevision(URL_PREFIX+"/");
        testGetUrlWithoutRevision(URL_PREFIX+"//");  
        testGetUrlWithoutRevision(URL_PREFIX+"////////");   
    }
    
    @Test
    @Bug(20344)
    public void testGetUrlWithoutRevision_withSlashAndSuffix() {
        testGetUrlWithoutRevision(URL_PREFIX+"/@HEAD");
        testGetUrlWithoutRevision(URL_PREFIX+"//@HEAD");       
    }
}
