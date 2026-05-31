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

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@WithJenkins
class SubversionStatusTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Test
    @Issue("SECURITY-724")
    void shouldNotBeAbleToSearch() {
        JenkinsRule.WebClient wc = r.createWebClient();
        checkUrl(wc, "subversion/search/");
        checkUrl(wc, "subversion/search/?q=a");
    }

    @Test
    @Issue("SECURITY-724")
    void shouldNotBeAbleToSearchUsingDynamic() {
        String uuid = "12345678-1234-1234-1234-123456789012";
        JenkinsRule.WebClient wc = r.createWebClient();
        checkUrl(wc, "subversion/" + uuid + "/search/");
        checkUrl(wc, "subversion/" + uuid + "/search/?q=a");
    }

    @Test
    void canStillProvideTheCommitNotifyAction() throws Exception {
        r.jenkins.setCrumbIssuer(null);

        String uuid = "12345678-1234-1234-1234-123456789012";
        JenkinsRule.WebClient wc = r.createWebClient();

        String relativeUrl = "subversion/" + uuid + "/notifyCommit/";

        // protected against GET request
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo(relativeUrl));
        assertEquals(405, e.getStatusCode());

        WebRequest request = new WebRequest(new URL(r.getURL() + relativeUrl), HttpMethod.POST);
        HtmlPage page = wc.getPage(request);
        r.assertGoodStatus(page);
    }

    private void checkUrl(JenkinsRule.WebClient wc, String url) {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo(url));

        WebResponse response = e.getResponse();
        assertEquals(404, response.getStatusCode());
        String content = response.getContentAsString();
        assertFalse(content.contains("Search for"));
    }
}
