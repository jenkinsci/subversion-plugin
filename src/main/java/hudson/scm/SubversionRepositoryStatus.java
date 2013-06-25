package hudson.scm;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.WARNING;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.triggers.SCMTrigger;
import hudson.util.QueryParameterMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.tmatesoft.svn.core.SVNException;

/**
 * Per repository status.
 * <p>
 * Receives post-commit hook notifications.
 *
 * @author Kohsuke Kawaguchi
 * @see SubversionStatus
 */
public class SubversionRepositoryStatus extends AbstractPostCommitHookReceiver {
    public final UUID uuid;

    public SubversionRepositoryStatus(UUID uuid) {
        this.uuid = uuid;
    }

    public String getDisplayName() {
        return uuid.toString();
    }

    public String getSearchUrl() {
        return uuid.toString();
    }

    @Override
    protected ModuleMatcher matcher(StaplerRequest req) {
        return new ModuleMatcher() {
            public boolean match(AbstractProject<?, ?> p, ModuleLocation loc) throws SVNException {
                return loc.getUUID(p).equals(uuid);
            }
        };
    }

    @Override
    protected long getRevision(StaplerRequest req) {
        // we can't reliably use req.getParameter() as it can try to parse the payload, which we've already consumed above.
        // servlet container relies on Content-type to decide if it wants to parse the payload or not, and at least
        // in case of Jetty, it doesn't check if the payload is
        QueryParameterMap query = new QueryParameterMap(req);
        String revParam = query.get("rev");
        if (revParam == null) {
            revParam = req.getHeader("X-Hudson-Subversion-Revision");
        }

        long rev = -1;
        if (revParam != null) {
            rev = Long.parseLong(revParam);
        }
        return rev;
    }

    @Override
    protected Set<String> getAffectedPath(StaplerRequest req) throws IOException {
        // compute the affected paths
        Set<String> affectedPath = new HashSet<String>();
        String line;
        BufferedReader r = new BufferedReader(req.getReader());

        try {
	        while((line=r.readLine())!=null) {
	        	if (LOGGER.isLoggable(FINER)) {
	        		LOGGER.finer("Reading line: "+line);
	        	}
	            affectedPath.add(line.substring(4));
	            if (line.startsWith("svnlook changed --revision ")) {
	                String msg = "Expecting the output from the svnlook command but instead you just sent me the svnlook invocation command line: " + line;
	                LOGGER.warning(msg);
	                throw new IllegalArgumentException(msg);
	            }
	        }
        } finally {
        	IOUtils.closeQuietly(r);
        }
        return affectedPath;
    }


    private static final Logger LOGGER = Logger.getLogger(SubversionRepositoryStatus.class.getName());
}
