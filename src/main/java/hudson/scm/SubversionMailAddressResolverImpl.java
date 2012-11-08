package hudson.scm;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.tasks.MailAddressResolver;
import hudson.Extension;
import hudson.model.User;
import hudson.model.AbstractProject;
import hudson.tasks.Mailer;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link MailAddressResolver} that checks for well-known repositories that and computes user e-mail address.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SubversionMailAddressResolverImpl extends MailAddressResolver implements Describable<SubversionMailAddressResolverImpl> {
    public String findMailAddressFor(User u) {
        for (AbstractProject<?,?> p : u.getProjects()) {
            SCM scm = p.getScm();
            if (scm instanceof SubversionSCM) {
                SubversionSCM svn = (SubversionSCM) scm;
                for (SubversionSCM.ModuleLocation loc : svn.getLocations(p.getLastBuild())) {
                    String s = findMailAddressFor(u,loc.remote);
                    if(s!=null) {
                        return s;
                    }
                }
            }
        }

        // didn't hit any known rules
        return null;
    }

    /**
     *
     * @param scm
     *      String that represents SCM connectivity.
     */
    protected String findMailAddressFor(User u, String scm) {
        for (Rule rule : DESCRIPTOR.rules) {
            if (rule.getMatcher(scm).matches())
                return u.getId() + rule.getDomain();
        }
        return null;
    }

    public static class Rule {

        private String pattern;
        private String domain;
        private transient Pattern compiled;

        public Rule(String pattern, String domain) {
            this.pattern = pattern;
            this.domain = domain;
        }

        public String getPattern() {
            return pattern;
        }

        public String getDomain() {
            return domain;
        }

        public Matcher getMatcher(String scm) {
            if (compiled == null)
                compiled = Pattern.compile(pattern);
            return compiled.matcher(scm);
        }
    }

    public Descriptor<SubversionMailAddressResolverImpl> getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public final static DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<SubversionMailAddressResolverImpl> {

        private Rule[] rules;

        public DescriptorImpl() {
            load();
            if (rules == null) {
                rules = new Rule[] {
                    new Rule("https://[^.]+.dev.java.net/svn/([^/]+)(/.*)?", "@dev.java.net"),
                    new Rule("(http|https)://[^.]+.svn.(sourceforge|sf).net/svnroot/([^/]+)(/.*)?", "@users.sourceforge.net")
                };
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            rules = req.bindJSONToList(Rule.class, json.get("rules")).toArray(new Rule[0]);
            save();
            return true;
        }

        public Rule[] getRules() {
            return rules;
        }

        @Override
        public String getDisplayName() {
            return "Extrapolate user email from svn repository";
        }
    }
}
