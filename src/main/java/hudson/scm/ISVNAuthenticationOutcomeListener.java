/*
 * The MIT License
 * 
 * Copyright (c) 2014 schristou88
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

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.SVNAuthentication;

/**
 * @author schristou88
 */
public interface ISVNAuthenticationOutcomeListener {
   /**
     * Accepts the given authentication if it was successfully accepted by a
     * repository server, or not if authentication failed. As a result the
     * provided credential may be cached (authentication succeeded) or deleted
     * from the cache (authentication failed).
     *
     * @param accepted       <span class="javakeyword">true</span> if
     *                       the credential was accepted by the server,
     *                       otherwise <span class="javakeyword">false</span>
     * @param kind           a credential kind ({@link #PASSWORD} or {@link #SSH} or {@link #USERNAME})
     * @param realm          a repository authentication realm
     * @param errorMessage   the reason of the authentication failure
     * @param authentication a user credential to accept/drop
     * @throws SVNException
     */
    public void acknowledgeAuthentication(boolean accepted,
                                          String kind,
                                          String realm,
                                          SVNErrorMessage errorMessage,
                                          SVNAuthentication authentication) throws SVNException;
}