/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class SubversionStatusTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    @Issue("SECURITY-724")
    public void shouldNotBeAbleToSearch() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        checkUrl(wc, "subversion/search/");
        checkUrl(wc, "subversion/search/?q=a");
    }
    
    @Test
    @Issue("SECURITY-724")
    public void shouldNotBeAbleToSearchUsingDynamic() throws Exception {
        String uuid = "12345678-1234-1234-1234-123456789012";
        JenkinsRule.WebClient wc = j.createWebClient();
        checkUrl(wc, "subversion/" + uuid + "/search/");
        checkUrl(wc, "subversion/" + uuid + "/search/?q=a");
    }
    
    @Test
    public void canStillProvideTheCommitNotifyAction() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        
        String uuid = "12345678-1234-1234-1234-123456789012";
        JenkinsRule.WebClient wc = j.createWebClient();
        
        String relativeUrl = "subversion/" + uuid + "/notifyCommit/";
        
        try {
            // protected against GET request
            wc.goTo(relativeUrl);
            fail();
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(405, e.getStatusCode());
        }
        
        WebRequest request = new WebRequest(new URL(j.getURL() + relativeUrl), HttpMethod.POST);
        HtmlPage page = wc.getPage(request);
        j.assertGoodStatus(page);
    }

    private void checkUrl(JenkinsRule.WebClient wc, String url) throws Exception {
        try {
            wc.goTo(url);
            fail();
        } catch (FailingHttpStatusCodeException e) {
            WebResponse response = e.getResponse();
            assertEquals(404, response.getStatusCode());
            String content = response.getContentAsString();
            assertFalse(content.contains("Search for"));
        }
    }
}
