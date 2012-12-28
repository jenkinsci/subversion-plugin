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
import org.tmatesoft.svn.core.SVNException;

/**
 * Per repository status.
 * <p>
 * Receives post-commit hook notifications.
 *
 * @author Kohsuke Kawaguchi
 * @see SubversionStatus
 */
public class SubversionRepositoryStatus extends AbstractModelObject {
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
    
    static interface JobProvider {
        @SuppressWarnings("rawtypes")
        List<AbstractProject> getAllJobs();
    }
    
    private JobProvider jobProvider = new JobProvider() {
        @SuppressWarnings("rawtypes")
        @Override
        public List<AbstractProject> getAllJobs() {
            return Hudson.getInstance().getAllItems(AbstractProject.class);
        }
    };
    private static Method IS_IGNORE_POST_COMMIT_HOOKS_METHOD;
    
    // for tests
    void setJobProvider(JobProvider jobProvider) {
        this.jobProvider = jobProvider;
    }

    /**
     * Notify the commit to this repository.
     *
     * <p>
     * Because this URL is not guarded, we can't really trust the data that's sent to us. But we intentionally
     * don't protect this URL to simplify <tt>post-commit</tt> script set up.
     */
    public void doNotifyCommit(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        requirePOST();

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

        if(LOGGER.isLoggable(FINE))
            LOGGER.fine("Change reported to Subversion repository "+uuid+" on "+affectedPath);
        boolean scmFound = false, triggerFound = false, uuidFound = false, pathFound = false;

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

        for (AbstractProject<?,?> p : this.jobProvider.getAllJobs()) {
            if(p.isDisabled()) continue;
            try {
                SCM scm = p.getScm();
                if (scm instanceof SubversionSCM) scmFound = true; else continue;

                SCMTrigger trigger = p.getTrigger(SCMTrigger.class);
                if (trigger!=null && !doesIgnorePostCommitHooks(trigger)) triggerFound = true; else continue;

                SubversionSCM sscm = (SubversionSCM) scm;
                
                List<SvnInfo> infos = new ArrayList<SvnInfo>();
                
                boolean projectMatches = false; 
                for (ModuleLocation loc : sscm.getProjectLocations(p)) {
                    if (loc.getUUID(p).equals(uuid)) uuidFound = true; else continue;

                    String m = loc.getSVNURL().getPath();
                    String n = loc.getRepositoryRoot(p).getPath();
                    if(!m.startsWith(n))    continue;   // repository root should be a subpath of the module path, but be defensive

                    String remaining = m.substring(n.length());
                    if(remaining.startsWith("/"))   remaining=remaining.substring(1);
                    String remainingSlash = remaining + '/';

                    if ( rev != -1 ) {
                        infos.add(new SvnInfo(loc.getURL(), rev));
                    }

                    for (String path : affectedPath) {
                        if(path.equals(remaining) /*for files*/ || path.startsWith(remainingSlash) /*for dirs*/
                        || remaining.length()==0/*when someone is checking out the whole repo (that is, m==n)*/) {
                            // this project is possibly changed. poll now.
                            // if any of the data we used was bogus, the trigger will not detect a change
                            projectMatches = true;
                            pathFound = true;
                        }
                    }
                }
                
                if (projectMatches) {
                    LOGGER.fine("Scheduling the immediate polling of "+p);
                    
                    final RevisionParameterAction[] actions;
                    if (infos.isEmpty()) {
                        actions = new RevisionParameterAction[0];
                    } else {
                        actions = new RevisionParameterAction[] {
                                new RevisionParameterAction(infos)};
                    }
                    
                    trigger.run(actions);
                }
                
            } catch (SVNException e) {
                LOGGER.log(WARNING,"Failed to handle Subversion commit notification",e);
            }
        }

        if (!scmFound)          LOGGER.warning("No subversion jobs found");
        else if (!triggerFound) LOGGER.warning("No subversion jobs using SCM polling or all jobs using SCM polling are ignoring post-commit hooks");
        else if (!uuidFound)    LOGGER.warning("No subversion jobs using repository: " + uuid);
        else if (!pathFound)    LOGGER.fine("No jobs found matching the modified files");

        rsp.setStatus(SC_OK);
    }
    
    private boolean doesIgnorePostCommitHooks(SCMTrigger trigger) {
        if (IS_IGNORE_POST_COMMIT_HOOKS_METHOD == null)
            return false;
        
        try {
            return (Boolean)IS_IGNORE_POST_COMMIT_HOOKS_METHOD.invoke(trigger, (Object[])null);
        } catch (Exception e) {
            LOGGER.log(WARNING,"Failure when calling isIgnorePostCommitHooks",e);
            return false;
        }
    }

    static {
        try {
            IS_IGNORE_POST_COMMIT_HOOKS_METHOD = SCMTrigger.class.getMethod("isIgnorePostCommitHooks", (Class[])null);
        } catch (Exception e) {
            // we're running in an older Jenkins version which doesn't have this method
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SubversionRepositoryStatus.class.getName());
}
