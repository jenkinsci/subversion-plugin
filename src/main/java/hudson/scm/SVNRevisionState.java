package hudson.scm;

import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;

/**
 * {@link SCMRevisionState} for {@link SubversionSCM}. {@link Serializable} since we compute
 * this remote.
 */
final class SVNRevisionState extends SCMRevisionState implements Serializable {
    /**
     * All the remote locations that we checked out. This includes those that are specified
     * explicitly via {@link SubversionSCM#getLocations()} as well as those that
     * are implicitly pulled in via svn:externals, but it excludes those locations that
     * are added via svn:externals in a way that fixes revisions.
     */
    final Map<String,Long> revisions;

    SVNRevisionState(Map<String, Long> revisions) {
        this.revisions = revisions;
    }

//    public PartialOrder compareTo(SCMRevisionState rhs) {
//        SVNRevisionState that = (SVNRevisionState)rhs;
//        return PartialOrder.from(that.hasNew(this), this.hasNew(that));
//    }
//
//    /**
//     * Does this object has something newer than the given object?
//     */
//    private boolean hasNew(SVNRevisionState that) {
//        for (Entry<String,Long> e : revisions.entrySet()) {
//            Long rhs = that.revisions.get(e.getKey());
//            if (rhs==null || e.getValue().compareTo(rhs)>0)
//                return true;
//        }
//        return false;
//    }

    private static final long serialVersionUID = 1L;
}
