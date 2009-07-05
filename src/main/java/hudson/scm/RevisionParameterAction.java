package hudson.scm;

import hudson.model.InvisibleAction;
import hudson.scm.SubversionSCM.SvnInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Action containing a list of SVN revisions that should be checked out. Used for parameterized builds.
 * 
 * @author Tom Huybrechts
 */
public class RevisionParameterAction extends InvisibleAction {
	
	private final List<SvnInfo> revisions;

	public RevisionParameterAction(List<SvnInfo> revisions) {
		super();
		this.revisions = revisions;
	}
	
	public RevisionParameterAction(SvnInfo... revisions) {
		this.revisions = new ArrayList<SvnInfo>(Arrays.asList(revisions));
	}

	public List<SvnInfo> getRevisions() {
		return revisions;
	}
	
	public SVNRevision getRevision(String url) {
		for (SvnInfo revision: revisions) {
			if (revision.url.equals(url)) {
				return SVNRevision.create(revision.revision);
			}
		}
		return null;
	}

}
