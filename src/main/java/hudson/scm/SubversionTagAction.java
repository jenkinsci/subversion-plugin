/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jean-Baptiste Quenot,
 * Seiji Sogabe, Alan Harder, Vojtech Habarta, Yahoo! Inc.
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

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.scm.subversion.Messages;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.security.ACL;
import hudson.util.CopyOnWriteMap;
import hudson.security.Permission;
import hudson.util.ListBoxModel;
import hudson.util.MultipartFormDataParser;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link Action} that lets people create tag for the given build.
 * 
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class SubversionTagAction extends AbstractScmTagAction implements Describable<SubversionTagAction> {

    /**
     * Map is from the repository URL to the URLs of tags.
     * If a module is not tagged, the value will be empty list.
     * Never an empty map.
     */
    private final Map<SvnInfo,List<String>> tags = new CopyOnWriteMap.Tree<SvnInfo, List<String>>();

    /*package*/ SubversionTagAction(Run build,Collection<SvnInfo> svnInfos) {
        super(build);
        Map<SvnInfo,List<String>> m = new HashMap<SvnInfo,List<String>>();
        for (SvnInfo si : svnInfos)
            m.put(si,new ArrayList<String>());
        tags.putAll(m);
    }

    /**
     * Was any tag created by the user already?
     */
    public boolean hasTags() {
        return isTagged();
    }

    public String getIconFileName() {
        if(!isTagged() && !getACL().hasPermission(getPermission()))
            return null;
        return "save.gif";
    }

    public String getDisplayName() {
        int nonNullTag = 0;
        for (List<String> v : tags.values()) {
            if(!v.isEmpty()) {
                nonNullTag++;
                if(nonNullTag>1)
                    break;
            }
        }
        if(nonNullTag==0)
            return Messages.SubversionTagAction_DisplayName_HasNoTag();
        if(nonNullTag==1)
            return Messages.SubversionTagAction_DisplayName_HasATag();
        else
            return Messages.SubversionTagAction_DisplayName_HasTags();
    }

    /**
     * @see #tags
     */
    public Map<SvnInfo,List<String>> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    @Exported(name="tags")
    public List<TagInfo> getTagInfo() {
        List<TagInfo> data = new ArrayList<TagInfo>();
        for (Entry<SvnInfo,List<String>> e : tags.entrySet()) {
            String module = e.getKey().toString();
            for (String url : e.getValue())
                data.add(new TagInfo(module, url));
        }
        return data;
    }

    @ExportedBean
    public static class TagInfo {
        private String module, url;
        private TagInfo(String module, String url) {
            this.module = module;
            this.url = url;
        }
        @Exported
        public String getModule() {
            return module;
        }
        @Exported
        public String getUrl() {
            return url;
        }
    }

    /**
     * Returns true if this build has already been tagged at least once.
     */
    @Override
    public boolean isTagged() {
        for (List<String> t : tags.values()) {
            if(!t.isEmpty())    return true;
        }
        return false;
    }

    @Override
    public String getTooltip() {
        String tag = null;
        for (List<String> v : tags.values()) {
            for (String s : v) {
                if (tag != null) return Messages.SubversionTagAction_Tooltip(); // Multiple tags
                tag = s;
            }
        }
        if(tag!=null)  return Messages.SubversionTagAction_Tooltip_OneTag(tag);
        else           return null;
    }

    private static final Pattern TRUNK_BRANCH_MARKER = Pattern.compile("/(trunk|branches)(/|$)");

    /**
     * Creates a URL, to be used as the default value of the module tag URL.
     *
     * @return
     *      null if failed to guess.
     */
    public String makeTagURL(SvnInfo si) {
        // assume the standard trunk/branches/tags repository layout
        Matcher m = TRUNK_BRANCH_MARKER.matcher(si.url);
        if(!m.find())
            return null;    // doesn't have 'trunk' nor 'branches'

        return si.url.substring(0,m.start())+"/tags/"+getBuild().getProject().getName()+"-"+getBuild().getNumber();
    }

    /**
     * Invoked to actually tag the workspace.
     */
    public synchronized void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        getACL().checkPermission(getPermission());

        MultipartFormDataParser parser = new MultipartFormDataParser(req);

        Map<SvnInfo,String> newTags = new HashMap<SvnInfo,String>();

        int i=-1;
        for (SvnInfo e : tags.keySet()) {
            i++;
            if(tags.size()>1 && parser.get("tag"+i)==null)
                continue; // when tags.size()==1, UI won't show the checkbox.
            newTags.put(e,parser.get("name" + i));
        }

        String credentialsId = parser.get("_.credentialsId");
        StandardCredentials upc = null;
        if (credentialsId != null) {
            Item context = req.findAncestorObject(Item.class);
            final List<Authentication> authentications = new ArrayList<Authentication>(2);
            authentications.add(Jenkins.getAuthentication());
            if (context.hasPermission(Item.CONFIGURE)) { // TODO should this check EXTENDED_READ?
                authentications.add(ACL.SYSTEM);
            }
            for (Authentication a : authentications) {
                upc = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(StandardCredentials.class,
                        context,
                        a,
                        Collections.<DomainRequirement>emptyList()),
                        CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId), CredentialsMatchers.anyOf(
                                CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                CredentialsMatchers.instanceOf(StandardCertificateCredentials.class),
                                CredentialsMatchers.instanceOf(SSHUserPrivateKey.class)
                        )
                        )
                );
                if (upc != null) {
                    break;
                }
            }
        }
        new TagWorkerThread(newTags,upc,parser.get("comment")).start();

        rsp.sendRedirect(".");
    }

    @Override
    public Permission getPermission() {
        return SubversionSCM.TAG;
    }

    /**
     * The thread that performs tagging operation asynchronously.
     */
    public final class TagWorkerThread extends TaskThread {
        private final Map<SvnInfo,String> tagSet;
        /**
         * If the user provided a separate credential, this object represents that.
         */
        private final StandardCredentials upc;
        private final String comment;

        public TagWorkerThread(Map<SvnInfo,String> tagSet, StandardCredentials upc, String comment) {
            super(SubversionTagAction.this,ListenerAndText.forMemory(SubversionTagAction.this));
            this.tagSet = tagSet;
            this.upc = upc;
            this.comment = comment;
        }

        @Override
        protected void perform(TaskListener listener) {
            try {
                File configDir = SVNWCUtil.getDefaultConfigurationDirectory();
                ISVNAuthenticationManager sam = new SVNAuthenticationManager(configDir, null, null);
                sam.setAuthenticationProvider(new CredentialsSVNAuthenticationProviderImpl(upc));
                final SvnClientManager cm = new SvnClientManager(
                        SVNClientManager.newInstance(SubversionSCM.createDefaultSVNOptions(), sam)
                );
                try {
                    for (Entry<SvnInfo, String> e : tagSet.entrySet()) {
                        PrintStream logger = listener.getLogger();
                        logger.println("Tagging "+e.getKey()+" to "+e.getValue());

                        try {
                            SVNURL src = SVNURL.parseURIDecoded(e.getKey().url);
                            SVNURL dst = SVNURL.parseURIDecoded(e.getValue());

                            SVNCopyClient svncc = cm.getCopyClient();
                            SVNRevision sourceRevision = SVNRevision.create(e.getKey().revision);
                            SVNCopySource csrc = new SVNCopySource(sourceRevision, sourceRevision, src);
                            svncc.doCopy(
                                    new SVNCopySource[]{csrc},
                                    dst, false, true, false, comment, null);
                        } catch (SVNException x) {
                            x.printStackTrace(listener.error("Failed to tag"));
                            return;
                        }
                    }

                    // completed successfully
                    for (Entry<SvnInfo,String> e : tagSet.entrySet())
                        SubversionTagAction.this.tags.get(e.getKey()).add(e.getValue());
                    getBuild().save();
                    workerThread = null;
                } finally {
                    cm.dispose();
                }
           } catch (Throwable e) {
               e.printStackTrace(listener.fatalError(e.getMessage()));
           }
        }
    }

    public Descriptor<SubversionTagAction> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Just for assisting form related stuff.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<SubversionTagAction> {
        public String getDisplayName() {
            return null;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @AncestorInPath Run run) {
            if (/* TODO consider making available in global context as well */context == null || !context.hasPermission(SCM.TAG)) {
                return new ListBoxModel();
            }
            Set<StandardCredentials> c = new LinkedHashSet<StandardCredentials>();
            SubversionTagAction action = run != null ? run.getAction(SubversionTagAction.class) : null;
            List<DomainRequirement> domainRequirements = Collections.<DomainRequirement>emptyList();
            if (action != null) {
                for (SvnInfo info: action.getTags().keySet()) {
                    domainRequirements = URIRequirementBuilder.fromUri(info.url).build();
                    break;
                }
            }
            c.addAll(CredentialsProvider.lookupCredentials(StandardCredentials.class,
                    context,
                    Jenkins.getAuthentication(),
                    domainRequirements)
            );
            if (context.hasPermission(Item.EXTENDED_READ)) {
                c.addAll(CredentialsProvider.lookupCredentials(StandardCredentials.class,
                                context,
                                ACL.SYSTEM,
                                domainRequirements)
                );
            }
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                    CredentialsMatchers.instanceOf(StandardCertificateCredentials.class),
                                    CredentialsMatchers.instanceOf(SSHUserPrivateKey.class)
                            ),
                            c
                    );
        }
    }
}
