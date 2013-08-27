/*
 * The MIT License
 *
 * Copyright (c) 2009-2010, Tom Huybrechts, Yahoo! Inc.
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

import hudson.model.InvisibleAction;
import hudson.model.Action;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.queue.FoldableAction;
import hudson.scm.SubversionSCM.SvnInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Action containing a list of SVN revisions that should be checked out. Used for parameterized builds.
 * 
 * @author Tom Huybrechts
 */
public class RevisionParameterAction extends InvisibleAction implements Serializable, FoldableAction {
	
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Logger.getLogger(RevisionParameterAction.class.getName());
	private final List<SvnInfo> revisions;

	public RevisionParameterAction(List<SvnInfo> revisions) {
		super();
		this.revisions = revisions;
	}
	
	public RevisionParameterAction(RevisionParameterAction action) {
		super();
		this.revisions = new ArrayList<SvnInfo>(action.revisions);
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
		
	public void foldIntoExisting(Queue.Item item, Task owner, List<Action> otherActions) {
		RevisionParameterAction existing = item.getAction(RevisionParameterAction.class);
		if (existing!=null) {
		    existing.mergeRevisions(this.revisions);
		    return;
		}
		// no RevisionParameterAction found, so add a copy of this one
		item.getActions().add(new RevisionParameterAction(this));
	}
	
	private void mergeRevisions(List<SvnInfo> newRevisions) {
		
		for (SvnInfo newRev : newRevisions) {
			boolean found = false;
			for (SvnInfo oldRev : this.revisions) {
				if (oldRev.url.equals(newRev.url)) {

					LOGGER.log(Level.FINE, "Updating revision parameter for {0} from {1} to {2}", new Object[] {oldRev.url, oldRev.revision, newRev.revision});

					this.revisions.add(new SvnInfo(oldRev.url, newRev.revision));
					this.revisions.remove(oldRev);
					found = true;
					break;
				}
			}
			if (!found) {
				this.revisions.add(newRev);
			}
		}
	}

	@Override
	public String toString() {
		String result = "[RevisionParameterAction ";
		for(SvnInfo i : revisions) {
			result += i.url + "(" + i.revision + ") ";
		}
		return result + "]";
	}
}
