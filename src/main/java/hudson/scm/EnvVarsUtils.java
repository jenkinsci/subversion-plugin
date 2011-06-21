/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc.,
 * Manufacture Francaise des Pneumatiques Michelin, Romain Seguy
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
import hudson.Platform;
import java.io.File;
import java.util.Map;

/**
 * Just a collection of utility (static) methods to work with {@link EnvVars}.
 */
public class EnvVarsUtils {

    /**
     * Changes the behavior of {@link EnvVars#overrideAll(java.util.Map)} which
     * drops variables which have value a null or a 0-length value: This
     * implementation doesn't.
     *
     * <p>This is a fix for JENKINS-10045.</p>
     * 
     * @see EnvVars#overrideAll(java.util.Map)
     */
    public static void overrideAll(EnvVars env, Map<String,String> all) {
        for (Map.Entry<String, String> e : all.entrySet()) {
            override(env, e.getKey(), e.getValue());
        }
    }

    /**
     * @see #override(hudson.EnvVars, java.lang.String, java.lang.String)
     * @see EnvVars#override(java.lang.String, java.lang.String)
     */
    private static void override(EnvVars env, String key, String value) {
        // this implementation doesn't  drop empty variables (JENKINS-10045)
        //if(value == null || value.length() == 0) {
        //    remove(key);
        //    return;
        //}
        
        int idx = key.indexOf('+');
        if(idx > 0) {
            String realKey = key.substring(0, idx);
            String v = env.get(realKey);
            if(v == null) {
                v = value;
            }
            else {
                // EnvVars.platform is private with no getter, but we really need it
                Platform platform = null;
                try {
                    platform = (Platform) EnvVars.class.getField("platform").get(env);
                } catch (Exception e) {
                    // nothing we can really do
                }

                char ch = platform == null ? File.pathSeparatorChar : platform.pathSeparator;
                v = value + ch + v;
            }
            env.put(realKey, v);
            return;
        }

        env.put(key, value);
    }

}
