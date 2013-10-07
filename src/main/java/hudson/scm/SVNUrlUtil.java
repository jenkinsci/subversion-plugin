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

/**
 * A collection of static methods working with subversion repository URLs
 *
 * @author Jifeng Zhang
 */
public class SVNUrlUtil {

    /**
     * Expand the SVN url according to the globally configured env vars.
     * @param The original SVN url might or might not contain one or several env vars.
     *
     * @return A URL with env vars expanded
     */
    public static String getExpandedUrl(String url) {
        EnvironmentVariablesNodeProperty evnp = Jenkins.getInstance().getGlobalNodeProperties().get(EnvironmentVariablesNodeProperty.class);

        if(evnp == null){
            return url;
        }

        EnvVars globalConfiguredEnvVars = evnp.getEnvVars();
        return globalConfiguredEnvVars.expand(url);
    }
}