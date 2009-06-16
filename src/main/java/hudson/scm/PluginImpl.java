package hudson.scm;

import hudson.Plugin;
import hudson.model.Hudson;

/**
 * @author Kohsuke Kawaguchi
 */
public class PluginImpl extends Plugin {
    @Override
    public void start() throws Exception {
        // TODO: after 1.311 I don't need this
        Hudson.getInstance().getActions().add(new SubversionStatus());
    }
}
