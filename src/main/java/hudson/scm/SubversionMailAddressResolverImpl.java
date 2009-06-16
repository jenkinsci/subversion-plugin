package hudson.scm;

import hudson.tasks.MailAddressResolver;
import hudson.Extension;
import hudson.model.User;
import hudson.model.AbstractProject;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SubversionMailAddressResolverImpl extends MailAddressResolver {
    public String findMailAddressFor(User u) {
        for (AbstractProject<?,?> p : u.getProjects()) {
            SCM scm = p.getScm();
            if (scm instanceof SubversionSCM) {
                SubversionSCM svn = (SubversionSCM) scm;
                for (SubversionSCM.ModuleLocation loc : svn.getLocations(p.getLastBuild())) {
                    String s = findMailAddressFor(u,loc.remote);
                    if(s!=null) return s;
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
        for (Map.Entry<Pattern, String> e : RULE_TABLE.entrySet())
            if(e.getKey().matcher(scm).matches())
                return u.getId()+e.getValue();
        return null;
    }

    private static final Map<Pattern,String/*suffix*/> RULE_TABLE = new HashMap<Pattern, String>();

    static {
        {// java.net
            Pattern svnurl = Pattern.compile("https://[^.]+.dev.java.net/svn/([^/]+)(/.*)?");
            RULE_TABLE.put(svnurl,"@dev.java.net");
        }

        {// source forge
            Pattern svnUrl = Pattern.compile("(http|https)://[^.]+.svn.(sourceforge|sf).net/svnroot/([^/]+)(/.*)?");

            RULE_TABLE.put(svnUrl,"@users.sourceforge.net");
        }

        // TODO: read some file under $HUDSON_HOME?
    }
}
