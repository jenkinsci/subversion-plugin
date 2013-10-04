/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Bruce Chapman, Yahoo! Inc., Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy
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

import hudson.EnvVars;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import jenkins.model.Jenkins;
import org.junit.Test;

/**
 * Tests for SVNUrlUtil class
 *
 * @author Jifeng Zhang
 */
public class SVNUrlUtilTest extends AbstractSubversionTest{
    @Test
    public void testGetExpandedUrl() throws Exception {
        Jenkins jenkins = Jenkins.getInstance();
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        envVars.put("SVN_HOST", "dev01");
        envVars.put("SVN_PORT", "12345");
        jenkins.getGlobalNodeProperties().add(prop);

        String urlBefore = "http://$SVN_HOST/repo/";
        String urlAfter = "http://dev01/repo/";
        assertEquals(SVNUrlUtil.getExpandedUrl(urlBefore), urlAfter);

        urlBefore = "http://dev01/repo/";
        assertEquals(SVNUrlUtil.getExpandedUrl(urlBefore), urlBefore);

        urlBefore = "http://$SVN_HOST:$SVN_PORT/repo/";
        urlAfter = "http://dev01:12345/repo/";
        assertEquals(SVNUrlUtil.getExpandedUrl(urlBefore), urlAfter);
    }
}
