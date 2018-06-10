/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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
package jenkins.scm.impl.subversion;

import hudson.remoting.ProxyException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;

/**
 * Version of {@link SVNErrorMessage}, which can be serialized over the channel.
 * This version does not serialize random {@link Object} instances.
 * @author Oleg Nenashev
 * @since TODO
 */
public class RemotableSVNErrorMessage extends SVNErrorMessage {

    public RemotableSVNErrorMessage(SVNErrorCode code) {
        super(code, "", new Object[]{}, null, 0);
    }

    public RemotableSVNErrorMessage(SVNErrorCode code, String message) {
        super(code, message == null ? "" : message, new Object[]{}, null, 0);
    }

    public RemotableSVNErrorMessage(SVNErrorCode code, Throwable cause) {
        super(code, "", new Object[]{}, new ProxyException(cause), 0);
    }

    public RemotableSVNErrorMessage(SVNErrorCode code, String message, Throwable cause) {
        super(code, message == null ? "" : message, new Object[]{}, new ProxyException(cause), 0);
    }
}
