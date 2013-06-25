package hudson.scm;

import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.triggers.SCMTrigger;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.tmatesoft.svn.core.SVNException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class AbstractPostCommitHookReceiver extends AbstractModelObject {

    protected abstract ModuleMatcher matcher(StaplerRequest req);

    protected abstract long getRevision(StaplerRequest req);

    protected abstract Set<String> getAffectedPath(StaplerRequest req) throws IOException;


    boolean doesIgnorePostCommitHooks(SCMTrigger trigger) {
        if (IS_IGNORE_POST_COMMIT_HOOKS_METHOD == null)
            return false;

        try {
            return (Boolean)IS_IGNORE_POST_COMMIT_HOOKS_METHOD.invoke(trigger, (Object[])null);
        } catch (Exception e) {
            LOGGER.log(WARNING,"Failure when calling isIgnorePostCommitHooks",e);
            return false;
        }
    }

    /**
     * Notify the commit to this repository.
     *
     * <p>
     * Because this URL is not guarded, we can't really trust the data that's sent to us. But we intentionally
     * don't protect this URL to simplify <tt>post-commit</tt> script set up.
     */
    @RequirePOST
    public void doNotifyCommit(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        Set<String> affectedPath = getAffectedPath(req);

        if(LOGGER.isLoggable(FINE))
            LOGGER.fine("Change reported to Subversion repository "+getDisplayName()+" on "+affectedPath);

        long rev = getRevision(req);

        boolean scmFound = false, triggerFound = false, repositoryFound = false, pathFound = false;
        ModuleMatcher matcher = matcher(req);

        if (matcher == null) {
            rsp.setStatus(SC_BAD_REQUEST);
            return;
        }

        for (AbstractProject<?,?> p : this.jobProvider.getAllJobs()) {
            if(p.isDisabled()) continue;
            try {
                SCM scm = p.getScm();
                if (scm instanceof SubversionSCM) scmFound = true; else continue;

                SCMTrigger trigger = p.getTrigger(SCMTrigger.class);
                if (trigger!=null && !doesIgnorePostCommitHooks(trigger)) triggerFound = true; else continue;

                SubversionSCM sscm = (SubversionSCM) scm;

                List<SubversionSCM.SvnInfo> infos = new ArrayList<SubversionSCM.SvnInfo>();

                boolean projectMatches = false;
                for (SubversionSCM.ModuleLocation loc : sscm.getProjectLocations(p)) {
                    if (matcher.match(p, loc)) {
                        repositoryFound = true;
                    } else continue;

                    String m = loc.getSVNURL().getPath();
                    String n = loc.getRepositoryRoot(p).getPath();
                    if(!m.startsWith(n))    continue;   // repository root should be a subpath of the module path, but be defensive

                    String remaining = m.substring(n.length());
                    if(remaining.startsWith("/"))   remaining=remaining.substring(1);
                    String remainingSlash = remaining + '/';

                    if ( rev != -1 ) {
                        infos.add(new SubversionSCM.SvnInfo(loc.getURL(), rev));
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
        else if (!repositoryFound)    LOGGER.warning("No subversion jobs using repository: " + getDisplayName());
        else if (!pathFound)    LOGGER.fine("No jobs found matching the modified files");

        rsp.setStatus(SC_OK);
    }

    static interface ModuleMatcher {

        /**
         * Determine if the subversion module on specified project match the repository this receiver is managing post
         * commit hook for.
         */
        boolean match(AbstractProject<?, ?> p, SubversionSCM.ModuleLocation loc) throws SVNException;

    }

    static interface JobProvider {
        @SuppressWarnings("rawtypes")
        List<AbstractProject> getAllJobs();
    }

    // for tests
    void setJobProvider(JobProvider jobProvider) {
        this.jobProvider = jobProvider;
    }

    protected SubversionRepositoryStatus.JobProvider jobProvider = new SubversionRepositoryStatus.JobProvider() {
        @SuppressWarnings("rawtypes")
        // @Override
        public List<AbstractProject> getAllJobs() {
            return Hudson.getInstance().getAllItems(AbstractProject.class);
        }
    };


    private static Method IS_IGNORE_POST_COMMIT_HOOKS_METHOD;
    static {
        try {
            IS_IGNORE_POST_COMMIT_HOOKS_METHOD = SCMTrigger.class.getMethod("isIgnorePostCommitHooks", (Class[])null);
        } catch (Exception e) {
            // we're running in an older Jenkins version which doesn't have this method
        }
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractPostCommitHookReceiver.class.getName());

}
